package org.apache.lucene.util;

import com.workday.elasticrypt.KeyProvider;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
  * AESWriter is responsible for writing AES encrypted files to disk.
  * This class will abstract the encryption process from the user.
  * Encrypted file contains:
  *  1. A file header that holds unencrypted metadata about the file. This header data can be customized by providing
  *     different implementations of `FileHeader` when constructing an `AESReader`. The header is useful for storing
  *     information that can be used to verify the proper key is being used to decrypt the file.
  *  2. Initialization Vector per file page is same size as the BLOCKSIZE constant = 16 bytes.
  *  3. Encrypted Page of size (page_size * BLOCKSIZE) bytes where page_size is provided by the user.
  *     The last page to be encrypted and written to disk has some padding added to it if the last block is not
  *     exactly 16  bytes long. Therefore, number of encrypted bytes of data might be more than actual data bytes
  *     in the file.
  *
  * Sample of the physical encrypted file where unencrypted virtual file is 2017(1024 + 993) bytes long and
  * Page size = 1024 bytes:
  *
  * Header(custom number of bytes)
  * IV1(16 bytes)
  * 1024 bytes of encrypted Text
  * IV2(16 bytes)
  * 1008 bytes of encrypted Text(993 bytes of actual data + 15 bytes of padding to complete a 16 byte last block.)
  *
  * <br />
  * All rights reserved by the IIT IR Lab. (c)2009 Jordan Wilberding(jordan@ir.iit.edu) and Jay Mundrawala(mundra@ir.iit.edu)
  *
  * @author Jay Mundrawala
  * @author Jordan Wilberding
  */
public class AESWriter
{
    /* AES using 16 byte block sizes */
    private static final int BLOCKSIZE = 16;
    /* An object to sync on */
    private final Object lock = new Object();
    /* Random Access file object used to read the physical encrypted file on disk.*/
    private RandomAccessFile raf;
    /* Encryption Cipher */
    private final Cipher ecipher;
    /* Decryption Cipher. This is needed if a seek occurs and entire blocks are not overwritten. */
    private final Cipher dcipher;
    /* Encryption pending buffer cache. If there is a block that is not entirely filled, this buffer
    * will be used. */
    private final byte[] buffer;
    private byte[] ciphertext;
    /* Initialization vector(16 bytes) to be used to encrypt the buffer.
    * IV vectors are unique per page buffer in a file. So,
    * A 4 page(1024 bytes/page) file will have a encrypted page of size (1024(DATA) + 16(IV)) and
    * total file size of (1024 + 16) * 4 bytes.
    */
    private byte[] cur_iv;
    /* header_offset for the file header */
    private long header_offset = 0;
    /* Current byte in the buffer which is caching the data for write.*/
    private int buffer_pos;
    /* Start position of buffer in reference to byte in the Virtual File without encryption meta-data(IV).
    * buffer_start is usually at the start of a page.
    */
    private long buffer_start;
    /* Number of bytes in the buffer which is caching the data for write.*/
    private int buffer_size;
    /* Length of the encrypted file without header and IV/page bytes.*/
    private long end;
    /* Contains the state of the padding */
    private boolean isPadded;
    /* Encryption Key */
    private SecretKeySpec key;
    /* Encryption Key ID */
    private String indexName;
    /* Used to generate initialization vectors */
    private KeyGenerator ivgen;
    /* Number of blocks(based on BLOCKSIZE = 16 bytes) per page */
    private final int page_size;
    /* Total number of bytes per page(page_size * BLOCKSIZE).*/
    private final long page_size_in_bytes;
    /* File pointer in the encrypted file without accounting of file header and
    * Initialization Vector/Page. Assume,
    * header_offset      = 20 bytes
    * page_size_in_bytes = 1024 bytes
    * IV size            = 16 bytes
    * Unencrypted Byte to be accessed = 1030
    * Physical byte in the encrypted file = 20 + (16 + 1024) + (16 + 6) = 1082
    * cur_fp = Unencrypted byte to be accessed = 1030*/
    private long cur_fp;
    /* Indicates if the cache buffer has been modified.*/
    private boolean modified;
    /* File name */
    private final String name;
    /* Whether file header is written */
    private Boolean headerWritten = false;
    private final KeyProvider keyProvider;

    private FileHeader fileHeader;

    /* force gets set if setLength is called.
    * It means that the file cannot grow after setLength as been called */
    private boolean force;
    private final ESLogger logger = ESLoggerFactory.getRootLogger();

    /**
      * @constructor
      * Creates an encrypted random access file that uses the AES encryption algorithm in CBC mode.
      * @param name File name.
      * @param raf File to create.
      * @param page_size Number of 16-byte blocks per page.
      * @param keyProvider Encryption Key information getter.
      * @param indexName
      * @param fileHeader
      */
    public AESWriter(String name, RandomAccessFile raf, int page_size, KeyProvider keyProvider, String indexName, FileHeader fileHeader) throws Exception
    {
        try {
            this.name = name;
            this.raf = raf;
            this.keyProvider = keyProvider;
            this.indexName = indexName;
            this.fileHeader = fileHeader;

           /* Only allow writing on new files. Lucene specifies that a new writer
            * will be created only for new files.
            */
            if(raf.length() != 0)
                throw new RuntimeException("File already Exists");

           /* unpadding with decipher does not work for some reason.
            * It seems that it wants the last 2 blocks of memory before it
            * decrypts. That is why unpadding is done manually */
            this.ecipher = Cipher.getInstance("AES/CBC/NoPadding");
            this.dcipher = Cipher.getInstance("AES/CBC/NoPadding");

            this.page_size = page_size;
            this.page_size_in_bytes = BLOCKSIZE*this.page_size;

           /* Initialize the internal buffer cache. Decrypted blocks are stored here.*/
            this.buffer = new byte[BLOCKSIZE*page_size];

           /* Buffer containing the encrypted data.*/
            this.ciphertext = new byte[buffer.length];

            this.isPadded = false;
        } catch(Exception ex) {
            // On error, make sure we close the file
            this.raf.close();
            throw ex;
        }
    }

    /**
      * Writes the unencrypted file header to the start of the file.
      * @throws IOException
      */
    private synchronized void writeFileHeaderLazy() throws
            IOException,
            InvalidAlgorithmParameterException,
            NoSuchAlgorithmException,
            InvalidKeyException {
        try {
            if (!headerWritten) {
                /* Set the header offset to be the size of bytes written for the header.*/
                this.header_offset = this.fileHeader.writeHeader();

                this.key = keyProvider.getKey(this.indexName);

                /* Initialize the ciphers. We should clean this up depending on the mode.*/
                this.ivgen = KeyGenerator.getInstance("AES");
                this.initCiphers(generateIV());

                headerWritten = true;
            }
        } catch(Exception ex) {
            // On error, make sure we close the file
            this.raf.close();
            throw ex;
        }
    }

    /**
      * Generates a random initialization vector.
      * @return a randomly generated IV
      */
    private IvParameterSpec generateIV(){
        return new IvParameterSpec(ivgen.generateKey().getEncoded());
    }

    /**
      * Initialize the encryption and decryption ciphers.
      * @param ivps The initialization vector to use or null. If null, an IV will be randomly generated.
      */
    private void initCiphers(IvParameterSpec ivps) throws InvalidKeyException,
            InvalidAlgorithmParameterException
    {
        if(ivps == null)
            ivps = generateIV();

       /* Store the IV bytes */
        this.cur_iv = ivps.getIV();

       /* Init the ciphers with ivps */
        this.ecipher.init(Cipher.ENCRYPT_MODE, this.key, ivps);
        this.dcipher.init(Cipher.DECRYPT_MODE, this.key, ivps);
    }

    /**
      * Encrypts and flushes all remaining data from the buffer cache to disk, pads the file,
      * and then closes the underlying file stream.
      */
    public void close() throws IOException,
            javax.crypto.ShortBufferException,
            javax.crypto.IllegalBlockSizeException,
            javax.crypto.BadPaddingException,
            InvalidKeyException,
            InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        synchronized(lock){
            if(!isPadded || modified)
                writePage(true, true);
            if(!this.isPadded){
                throw new RuntimeException("NO PADDING: this.end=" + this.end + ";this.cur_fp=" + this.cur_fp + ";this.buffer_size="+this.buffer_size +
                        ";this.buffer_pos=" + this.buffer_pos + ";this.buffer_start=" + this.buffer_start + ";this.force=" + this.force);
            }
            this.raf.close();
        }
    }

    /**
      * Writes any data in the buffer to disk with any additional padding needed.
      */
    public void flush() throws IOException,
            javax.crypto.ShortBufferException,
            javax.crypto.IllegalBlockSizeException,
            javax.crypto.BadPaddingException,
            InvalidKeyException,
            InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        synchronized(lock){
            writePage(true, false);
        }
    }

    /**
      * Returns the file position in the encrypted file without the accounting for IVs and File header.
      * @return Returns the current position in this file, where the next write will occur.
      */
    public long getFilePointer() throws IOException
    {
        return this.cur_fp;
    }

    /**
      * The number of bytes in the file without (IV/page and File Header).
      * @return the size off the file
      */
    public long length() throws IOException
    {
        return end;
    }

    /**
      * Sets the position where the next write will occurs.
      * @param pos the position where the next write will occur
      */
    public void seek(long pos) throws IOException,
            javax.crypto.ShortBufferException,
            javax.crypto.IllegalBlockSizeException,
            javax.crypto.BadPaddingException,
            InvalidKeyException,
            InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        synchronized(lock){
            this._seek(pos);
        }
    }

    /**
      * Sets the virtual position(cur_fp) where the next write will occurs and moves the physical encrypted
      * file pointer to the correct position.
      * @param pos the position where the next write will occur
      */
    private void _seek(long pos) throws IOException,
            javax.crypto.ShortBufferException,
            javax.crypto.IllegalBlockSizeException,
            javax.crypto.BadPaddingException,
            InvalidKeyException,
            InvalidAlgorithmParameterException, NoSuchAlgorithmException {
      /* Physical starting address of the page that contains pos */
        long _page;

      /* Flush data in buffer */
        writePage(true, false);

      /* Convert virtual position in file to actual position in the physical encrypted file. */
        long _phys_pos = this.encryptedAddrToPhysicalAddr(pos);

      /* Convert physical encrypted block to physical starting address of the page.*/
        _page = _phys_pos/(this.page_size_in_bytes + BLOCKSIZE);
        _page *= (this.page_size_in_bytes + BLOCKSIZE);

      /* Move the physical file pointer to (_page + header_offset) address.*/
        offset_seek(_page);

      /* Set cur_fp to virtual file address */
        this.cur_fp = pos;

      /* Load the buffer cache with data from the physical file, decrypt it, and
      * Set the Initialization Vector(IV) for encryption.*/
        this.fillBuffer();

      /* Set buffer start to the start address of the page in the virtual file.*/
        this.buffer_start = (pos / page_size_in_bytes) * page_size_in_bytes;

      /* Set buffer_pos to point to pos in the buffer */
        this.buffer_pos = (int)(this.cur_fp % this.page_size_in_bytes);
    }

    /**
      * Calls this.raf.seek and adds the header_offset.  The only time this shouldn't be used over this.raf.seek is
      * if you actually want to read the header.
      * @param pos the file position to seek to
      * @throws IOException
      */
    private void offset_seek(long pos) throws IOException
    {
        this.raf.seek(pos + this.header_offset);
    }

   /**
     * Writes the given array of bytes to the file.
     * 1. Fills the cache buffer with unencrypted data from the input buffer.
     * 2. Once the cache buffer is full, the data in the cache buffer is encrypted and written to disk.
     * @param b array of bytes to write
     * @param off offset in b to start
     * @param len number of bytes to write
     */
    public void write(byte[] b, int off, int len) throws IOException,
           javax.crypto.ShortBufferException,
           javax.crypto.IllegalBlockSizeException,
           InvalidKeyException,
           InvalidAlgorithmParameterException,
           javax.crypto.BadPaddingException, NoSuchAlgorithmException {
        ByteBuffer bb = ByteBuffer.wrap(b, off, len);
        write(bb);
    }

    /**
      * TODO
      * @param b
      * @return
      * @throws IOException
      * @throws javax.crypto.ShortBufferException
      * @throws javax.crypto.IllegalBlockSizeException
      * @throws InvalidKeyException
      * @throws InvalidAlgorithmParameterException
      * @throws javax.crypto.BadPaddingException
      * @throws NoSuchAlgorithmException
      */
    public int write(ByteBuffer b) throws IOException,
            javax.crypto.ShortBufferException,
            javax.crypto.IllegalBlockSizeException,
            InvalidKeyException,
            InvalidAlgorithmParameterException,
            javax.crypto.BadPaddingException, NoSuchAlgorithmException {
        int bytesCopied = 0;
        int _len;
        synchronized(lock){
            while(b.hasRemaining()) {
                this.modified = true;

                int bufferLength = b.limit() - b.position();
                //Minimum of len and available is the number of bytes to write
                _len = (int)Math.min(bufferLength, this.page_size_in_bytes - this.buffer_pos);
                //System.arraycopy(b, off, this.buffer, this.buffer_pos, _len);
                b.get(this.buffer, this.buffer_pos, _len);

                if(this.cur_fp + _len > this.end){
                    this.isPadded = false;
                }

             /* Update the length of bytes left to be written and current offset by using number of bytes already
             * copied into the buffer cache.*/
                bytesCopied += _len;
                this.buffer_pos += _len;
                this.cur_fp += _len;
                this.buffer_size = Math.max(this.buffer_size, this.buffer_pos);
                this.end = Math.max(this.cur_fp, this.end);

                if(this.buffer_pos == this.page_size_in_bytes){
                 /* Encrypt and write current page from the buffer cache to disk along with IV.*/
                    this.writePage();
                 /* Load next page from disk: initialize the IV vector and load and decrypt the data from disk into buffer cache.*/
                    this.fillBuffer();
                }

            }
        }
        return bytesCopied;
    }

    /**
      * TODO
      * @throws IOException
      * @throws javax.crypto.ShortBufferException
      * @throws javax.crypto.IllegalBlockSizeException
      * @throws javax.crypto.BadPaddingException
      * @throws InvalidKeyException
      * @throws InvalidAlgorithmParameterException
      * @throws NoSuchAlgorithmException
      */
    private void writePage() throws IOException,
            javax.crypto.ShortBufferException,
            javax.crypto.IllegalBlockSizeException,
            javax.crypto.BadPaddingException, InvalidKeyException,
            InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        writePage(false, false);
    }

    /**
      * Writes the internal buffer cache to disk.
      * 1. Write iv.
      * 2. Buffer Cache:
      *       i. Determine if last block in the buffer needs to be padded and pad it.
      *      ii. Encrypt data in the buffer with needed padding and write to disk.
      * @param flush_pad if set to true, padding is guaranteed to be added. Otherwise, padding will only be added if
      * there are not enough bytes to make a complete block.
      */
    private void writePage(boolean flush_pad, boolean isFileClose) throws IOException,
            javax.crypto.ShortBufferException,
            javax.crypto.IllegalBlockSizeException,
            javax.crypto.BadPaddingException,
            InvalidKeyException,
            InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        /*
           In the case of a translog flush before any data is written to the file, we need to bypass the rest of writePage,
           since it tries to encrypt the buffer and accesses the ciphers. The ciphers are impossible to initialize
           without access to tenant keys.
         */
        if (isFileClose && !this.headerWritten) {
            if (this.buffer_size == 0) {
                this.isPadded = true;
                return;
            } else {
                logger.info("Attempting to close file with non-empty buffer and no header written. Will attempt to " +
                        "write header one last time. file: " + this.name + ", buffer_size: " + this.buffer_size);
            }
        }

        /* Write the file header */
        this.writeFileHeaderLazy();
        this.modified = false;

       /* Set underlying file position to the start of the file page without initialization vector.*/
        offset_seek(encryptedAddrToPhysicalAddr(this.buffer_start) - BLOCKSIZE);
        this.raf.write(this.cur_iv);

       /* determine number of bytes to write */
        int len = (this.buffer_size + BLOCKSIZE-1)/BLOCKSIZE*BLOCKSIZE;

       /* number of padding bytes */
        int no_padding = BLOCKSIZE - (this.buffer_size % BLOCKSIZE);

        if(no_padding < BLOCKSIZE && no_padding > 0)
        {
           /* Does not need a full block of padding...easy */
            this.isPadded = true;
            for(int i = buffer_size; i < len;i++){
                this.buffer[i]=(byte)no_padding;
            }
        }else if(flush_pad && no_padding == BLOCKSIZE && this.cur_fp/page_size_in_bytes == this.end/page_size_in_bytes){
           /* Needs a full block of padding...only pad if on the last page and flush_pad is true */
            if(!this.isPadded)
            {
               /* Write padding bytes to buffer */
                for(int i = len; i< len + BLOCKSIZE; i++)
                    this.buffer[i] = BLOCKSIZE;
                if(this.page_size_in_bytes - this.buffer_size >= BLOCKSIZE){
                   /* Make sure the page isn't full...should never happen */
                    this.isPadded = true;
                    len += BLOCKSIZE;
                }else{
                    throw new RuntimeException("Page should not be full: " + buffer_size);
                }
            }
        }

       /* Encrypt data in the buffer cache. */
        this.ecipher.doFinal(this.buffer,0,len,this.ciphertext,0);
       /* Write encrypted data to disk. */
        this.raf.write(this.ciphertext,0,len);
    }

    /**
      * Reads a page from the underlying file and initialize the ciphers.
      * If there is no page to read, the ciphers get initialized
      * with a random IV.
      */
    private void fillBuffer() throws IOException,
            javax.crypto.ShortBufferException,
            javax.crypto.IllegalBlockSizeException,
            InvalidKeyException,
            InvalidAlgorithmParameterException,
            javax.crypto.BadPaddingException
    {
        int       _end;
        long      _cur_fp;
        byte[]    _iv;

        _iv       = new byte[BLOCKSIZE];

       /* Buffer start byte position at beginning of the page in the file where cur_fp is located.
        * The buffer_start does not contain metadata offset(IVs + file header).*/
        this.buffer_start = this.cur_fp/page_size_in_bytes*page_size_in_bytes;

       /* Current file pointer in the physical encrypted file at the start of the page.
        * It includes all the IVs for the pages before the current page and the file header.*/
        _cur_fp   = encryptedAddrToPhysicalAddr(this.buffer_start) - BLOCKSIZE;
        _cur_fp += this.header_offset;

       /* If there is no IV in the page then,
        *    generate one and write it to disk. Else,
        *    read Initialization Vector(IV).
        */
        this.raf.seek(_cur_fp);
        if(_cur_fp  >= this.raf.length()){
           /* generate a random IV and write it to disk */
            _iv = generateIV().getIV();
            this.raf.write(_iv);
        }else{
            this.raf.readFully(_iv);
        }

       /* Move physical file fp to account for the iv.*/
        _cur_fp += BLOCKSIZE;

       /* Read page */
       /* Read encrypted data into buffer cache.
        * Update buffer size to number of data bytes read.*/
        this.buffer_size = _end = this.raf.read(this.buffer,0, (int)this.page_size_in_bytes);

       /* If not bytes are read then,
        * Initialize the cyphers and set buffer_size = buffer_pos = 0.*/
        if(_end == -1){
            this.buffer_size = 0;
            this.initCiphers(new IvParameterSpec(_iv));
            this.buffer_pos = 0;
            return;
        }

       /* Re-initialize ciphers and decrypt page */
        this.initCiphers(new IvParameterSpec(_iv));
        this.buffer_pos = 0;

       /* Decrypt the encrypted data and write it back to the buffer cache.*/
        this.dcipher.doFinal(this.buffer,0,_end,this.buffer,0);
    }

    /**
      * Set the size of the file. To use this method, it must be called immediately after this object is created.
      * Also, when this method is called, the file length may not be extended.
      * 1. Sets the IV vector for each page and inserts padding.
      * 2. Seeks to the beginning of file and sets the end of the file to newLen and cur_fp to 0.
      * 3. Buffer Cache: Sets the buf pos and start to 0.
      * @param newLen The new size of the file.
      */
    public void setLength(long newLen) throws IOException,
            javax.crypto.ShortBufferException,
            javax.crypto.BadPaddingException,
            InvalidKeyException,
            javax.crypto.IllegalBlockSizeException,
            InvalidAlgorithmParameterException
    {
        synchronized(lock){
            force = true;
            /* Total number of bytes in terms of full block bytes.*/
            long _len = newLen + BLOCKSIZE - (newLen % BLOCKSIZE);
            /* Number of bytes needed in padding for a block.*/
            int no_padding = (int)(BLOCKSIZE - (newLen % BLOCKSIZE));
            /* Total blocks calculation is done this way for newlen being multiple of 16.*/
            long num_blocks = (_len + BLOCKSIZE - 1)/BLOCKSIZE;
            long num_pages = (num_blocks + this.page_size-1)/this.page_size;

            this.raf.setLength(_len + num_pages*BLOCKSIZE + this.header_offset);
            this.end = newLen;

            byte[] _iv;
            offset_seek(0);
            this.buffer_start = 0;

            long _cur = 0;

            for(int i =0; i < num_pages; i++){
                if((this.raf.getFilePointer() - this.header_offset) % (page_size_in_bytes + BLOCKSIZE) != 0)
                    throw new RuntimeException("Debug::Incorrect file postion;fp=" + this.raf.getFilePointer());

                if(i == num_pages - 1){
                  /* Last page needs padding */
                    _iv = generateIV().getIV();
                    this.raf.write(_iv);
                  /* Number of bytes in the last page.*/
                    int _num = (int)(_len - _cur);
                    byte[] data = new byte[_num];
                    for(int j = _num -1; j >= _num - no_padding; j--)
                    {
                        data[j] = (byte)no_padding;
                    }
                    this.initCiphers(new IvParameterSpec(_iv));
                    this.ecipher.doFinal(data,0,_num,data,0);
                    this.raf.write(data);

                    this.isPadded = true;
                    _cur += _num;
                }else{
                  /* generate and write random IV to file */
                    _iv = generateIV().getIV();
                    this.raf.write(_iv);
                    this.raf.seek(this.raf.getFilePointer() + page_size_in_bytes);

                    _cur+= page_size_in_bytes;
                }
            }

          /* Seek to the beginning of the file */
            offset_seek(0);
            this.cur_fp = 0;
            this.buffer_start = 0;
            this.buffer_pos = 0;

          /* load first page */
            this.fillBuffer();
        }
    }

    /**
      * Calculates the number of init vectors preceding a given block. The block of virtual address m
      * is determined by m/BLOCKSIZE.
      * @param block how many IVs are found before this block
      * @return number of IVs found before block
      */
    private long numPageIVBlocksAt(long block){
        return (block/((long)page_size)) +1;
    }

    /**
      * Calculates the physical address of the given virtual address.
      * @param m the virtual file pointer
      * @return the address of where m actually lies in the underlying file
      */
    private long encryptedAddrToPhysicalAddr(long m){
        long block = m/BLOCKSIZE;
        return ((block + (numPageIVBlocksAt(block))) * BLOCKSIZE) + (m % BLOCKSIZE);
    }
}
