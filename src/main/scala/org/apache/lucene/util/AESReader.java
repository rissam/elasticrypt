package org.apache.lucene.util;

import com.workday.elasticrypt.KeyProvider;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
  * AESReader provides the ability to read an AES encrypted random access file.
  * This class will abstract the decryption process from the user.
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
public class AESReader
{
    /* AES using 16 byte block sizes */
    private static final int BLOCKSIZE = 16;
    /* Random Access file object used to write to the physical encrypted file on disk. */
    private RandomAccessFile raf;
    /* Decryption Cipher */
    private final Cipher dcipher;
    /* Current Initialization Vector for the page. */
    private final byte[] cur_iv;

    /* header_offset for the file header */
    private long header_offset = 0;
    private FileHeader fileHeader;

    /* Encryption/Decryption buffer cache. */
    private final byte[] buffer;
    /* Internal filePos. We cannot use raf's because that one will always be aligned a 16 byte boundary */
    private long filePos;
    /* Start position of buffer in reference to byte in the Virtual File without encryption meta-data(IV).
     * buffer_start is usually at the start of a page. */
    private long bufferStart;
    /* Number of valid bytes in the buffer */
    private int bufferLength;
    /* Current position in buffer */
    private int bufferPosition;
    /* Last byte in the file without metadata such as Header offset and IV/page. */
    private long end;
    /* Blocks per page */
    private final int page_size;
    /* Key used to decrypt data */
    private final SecretKeySpec key;
    /* Object to sync on */
    private final Object lock = new Object();
    /* Name of file */
    private final String name;
    /* Key ID used to retrieve key */
    private String indexName;

   /**
     * @constructor
     * Creates an encrypted random access file reader that uses the AES encryption algorithm in CBC mode.
     * @param name File name.
     * @param raf file to read.
     * @param page_size number of 16-byte blocks per page. Must be the same number used when writing the file.
     * @param keyProvider getter for key used to initialize the ciphers.
     * @param indexName used to retrieve the key using keyProvider.
     * @param fileHeader creates the file header.
     */
   public AESReader(String name, RandomAccessFile raf, int page_size, KeyProvider keyProvider, String indexName, FileHeader fileHeader) throws IOException,
          NoSuchAlgorithmException,
          InvalidKeyException,
          ShortBufferException,
          NoSuchPaddingException,
          InvalidAlgorithmParameterException,
          IllegalBlockSizeException,
          BadPaddingException
   {
       long page_size_in_bytes;
       int nread;
       int buf_size;
       int no_padding;
       int no_data;

       try {
           this.name = name;
           this.raf = raf;
           this.indexName = indexName;
           this.fileHeader = fileHeader;
           /* Read the file header. */
           this.readFileHeader();

           /* Retrieve the key based on the index obtained via the shard. */
           this.key = keyProvider.getKey(indexName);

           page_size_in_bytes = page_size * BLOCKSIZE;
           this.dcipher = Cipher.getInstance("AES/CBC/NoPadding");
           this.buffer = new byte[page_size * BLOCKSIZE];
           this.cur_iv = new byte[BLOCKSIZE];
           this.page_size = page_size;

           /* Check padding and determine end (file length). Read the last page. */
           this.raf.seek(Math.max(this.raf.length() - page_size_in_bytes, this.header_offset));

           /* Initialize the Initialization Vector for the last page by reading it from the file. */
           this.raf.readFully(this.cur_iv);

           /* Read encrypted text from the file into the buffer cache and decrypt it. */
           nread = this.raf.read(buffer);
           dcipher.init(Cipher.DECRYPT_MODE, this.key, new IvParameterSpec(this.cur_iv));
           buf_size = dcipher.doFinal(buffer, 0, nread, buffer, 0);

           /* Ensure that the padding is correct. */
           if (buf_size != nread)
               throw new IOException("Not enough bytes decrypted");

           no_padding = buffer[buf_size - 1];
           no_data = BLOCKSIZE - no_padding;

           if (no_data < 0 || no_data > BLOCKSIZE)
               throw new IOException("Bad padding: " + no_padding);

           for (int i = buf_size - BLOCKSIZE + no_data; i < buf_size; i++) {
               if (no_padding != buffer[i])
                   throw new IOException(
                           "Bad padding @ byte " + (buf_size - i) + ". Expected: "
                                   + no_padding + ". Value: " + buffer[i]
                   );
           }

           /* Determine the end of the file after removing padding bytes. */
           long blocks = (this.raf.length() - this.header_offset) / BLOCKSIZE - 1;
           long pageivs = blocks / (page_size + 1) + 1;
           this.end = this.raf.length() - no_padding - (pageivs * BLOCKSIZE) - this.header_offset;

           /* Refill the buffer cache (by seeking to the beginning of the file after the header)
            * Seek already accounts for header offset, so seeking to pos 0 will point us at the
            * beginning of the payload after the header. */
           seek(0);
       } catch(Exception ex) {
           // On error, make sure we close the file
           this.raf.close();
           throw ex;
       }
   }

   /**
     * Reads the unencrypted file header from the start of the file.
     * @throws IOException
     */
   private void readFileHeader() throws IOException
   {
       this.fileHeader.readHeader();
       this.header_offset = this.raf.getFilePointer();
   }

   /**
     * Close the underlying RandomAccessFile.
     */
   public void close() throws IOException
   {
      this.raf.close();
   }

   /**
     * Get the current virtual position in the file.
     * @return current position in the file
     */
   public long getFilePointer() throws IOException
   {
      return this.filePos;
   }

   /**
     * Sets a new length in bytes for the encrypted file.
     * @param newEnd is the new length
     */
   public void setLength(long newEnd) { this.end = newEnd; }

   /**
     * Get the number of bytes in the encrypted file. This size is equal to the physical file size
     * minus the number of padding blocks, IV/page, and header offset. Basically, same size as unencrypted file.
     * @return size of file
     */
   public long length()
   {
      return this.end;
   }

   /**
     * Read the next byte from the file.
     * @return -1 if eof has been reached, the next byte otherwise.
     */
   public int read() throws IOException,
           javax.crypto.ShortBufferException,
           javax.crypto.IllegalBlockSizeException,
           javax.crypto.BadPaddingException,
           java.security.InvalidAlgorithmParameterException,
           java.security.InvalidKeyException
   {
      byte[] b = new byte[1];
      int numRead = read(b);
      if(numRead == -1)
         return -1;

      return (int) b[0] & 0xFF;
   }

   /**
     * Try to fill the given buffer with the next bytes from the file.
     * @param b byte array to fill with bytes
     * @return -1 if eof has been reached, the number of bytes copied into the given buffer otherwise.
     */
   public int read(byte[] b) throws IOException,
           javax.crypto.ShortBufferException,
           javax.crypto.IllegalBlockSizeException,
           javax.crypto.BadPaddingException,
           java.security.InvalidAlgorithmParameterException,
           java.security.InvalidKeyException
   {
      return this.read(b, 0,b.length);
   }

   /**
     * Read bytes from the file into the given byte array.
     * @param b The buffer into which bytes are to be transferred
     * @param offset position in b to start copying data
     * @param len number of bytes to copy to the given byte array
     * @return -1 if eof has been reached, the number of bytes copied into b otherwise.
     */
   public int read(byte[] b, int offset, int len) throws IOException,
          javax.crypto.ShortBufferException,
          javax.crypto.IllegalBlockSizeException,
          javax.crypto.BadPaddingException,
          java.security.InvalidKeyException,
          java.security.InvalidAlgorithmParameterException {
       ByteBuffer dst = ByteBuffer.wrap(b, offset, len);
       return read(dst);
   }

    /**
      * Read bytes from the file into the given byte array.
      * @param dst The buffer into which bytes are to be transferred
      * @return -1 if eof has been reached, the number of bytes copied into b otherwise.
      */
    public int read(ByteBuffer dst) throws IOException,
            javax.crypto.ShortBufferException,
            javax.crypto.IllegalBlockSizeException,
            javax.crypto.BadPaddingException,
            java.security.InvalidKeyException,
            java.security.InvalidAlgorithmParameterException {
      int len = dst.remaining();
      if(this.filePos >= this.end){
         return -1;
      }
      if(len <= 0)
         return 0;

      synchronized(lock){
         int remaining = len;
         /* Time to get next page when position in the buffer is geq its length. */
         if(bufferPosition >= bufferLength)
            refill();

         if(len <= bufferLength - bufferPosition){
             /* Enough bytes in the buffer cache to fill the request buffer...just copy them to b. */
             dst.put(buffer, bufferPosition, len);
            bufferPosition += len;
            filePos += len;
            remaining = 0;
         }else{
             /* Will need to start loading next pages to read len bytes. */
            while(remaining > 0 && filePos < end){
               int available = bufferLength - bufferPosition;
               /* If bytes are available in the buffer cache then, copy them to the request buffer. */
               if(available > 0){
                  int to_read = Math.min(available,remaining);
                   dst.put(buffer, bufferPosition, to_read);
                  remaining -= to_read;
                  bufferPosition += to_read;
                  filePos += to_read;
               }else{
                  /* If all the bytes in the buffer cache have been read then, read and decrypt
                   * next page from disk into the buffer cache. */
                  refill();
               }
            }
         }
         return len - remaining;
      }
   }

   /**
     * Sets the virtual file pointer so that the next byte read will be at pos.
     * Seeking past the end of the file is not allowed.
     * @param pos position to seek to
     */
   public void seek(long pos) throws IOException,
           javax.crypto.ShortBufferException,
           javax.crypto.IllegalBlockSizeException,
           javax.crypto.BadPaddingException,
           java.security.InvalidKeyException,
           java.security.InvalidAlgorithmParameterException
   {
      // flush buffer
      if(pos >= end || pos < 0){
         throw new RuntimeException("Pos: " + pos + " end: " + end + " file: " + name);
      }
      synchronized(lock){
         this.filePos = pos;
         refill();
      }
   }

   /**
     * Refill will make sure that this.filePos is in the internal buffer and decrypted. It reads
     * 1 page from disk including the IV used to encrypt the page, and decrpyts the page which is
     * then stored in the internal buffer.
     */
   private void refill() throws IOException,
            javax.crypto.ShortBufferException,
            javax.crypto.IllegalBlockSizeException,
            javax.crypto.BadPaddingException,
            java.security.InvalidKeyException,
            java.security.InvalidAlgorithmParameterException
   {
      int buf_size;
      int nread;

      /* Get the address which accounts for encryption IV/page. */
      long strt_addr = encryptedAddrToPhysicalAddr(this.filePos);

      /* Set bufferStart to the first byte of the IV of the page that contains filePos. */
      this.bufferStart = strt_addr/((long)page_size*BLOCKSIZE + BLOCKSIZE);
      this.bufferStart *= ((long)page_size*BLOCKSIZE + BLOCKSIZE);

      /* Seek to the IV by adding the header offset. */
      this.raf.seek(bufferStart + this.header_offset);
      /* Update the bufferStart to take the IV size into account. */
      this.bufferStart += BLOCKSIZE;

      /* Read the IV. */
      this.raf.readFully(this.cur_iv);

      /* Initialize the cipher with the IV that was read. */
      this.dcipher.init(Cipher.DECRYPT_MODE,this.key,new IvParameterSpec(this.cur_iv));

      /* Read and decrypt the cipher text into the buffer cache. */
      nread = this.raf.read(buffer);
      buf_size = dcipher.doFinal(buffer,0,nread,buffer,0);

      if(buf_size != nread)
          throw new IOException("Not enough bytes decrypted");

      this.bufferLength = buf_size;
      this.bufferPosition = (int)(this.filePos % buffer.length);
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
