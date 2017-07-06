package com.workday.elasticrypt.translog

import java.io.{File, RandomAccessFile}
import java.nio.channels.FileChannel.MapMode
import java.nio.channels.{FileChannel, FileLock, ReadableByteChannel, WritableByteChannel}
import java.nio.{ByteBuffer, MappedByteBuffer}

import com.workday.elasticrypt.KeyProvider
import org.apache.lucene.util.{AESReader, AESWriter, HmacFileHeader}

/**
  * Extension of java.nio.channels.FileChannel that instantiates an AESReader and AESWriter to encrypt all reads and writes.
  * Utilized in EncryptedRafReference and EncryptedTranslogStream.
  */
class EncryptedFileChannel(name: String, raf: RandomAccessFile, pageSize: Int, keyProvider: KeyProvider, keyId: String)
  extends FileChannel {
  /**
    * Two issues here:
    * (1) AESReader should only be instantiated for existent files - maybe there should be an open method?
    * (2) AESWriter should only be instantiated on non-existent files - does ES guarantee that for translog?
    * in any case, to support both, we have to at least lazily open AESReader and AESWriter only as needed....
    *
    * Simply using lazy here seems too easy.
    */

  private[translog] lazy val fileHeader = new HmacFileHeader(raf, keyProvider, keyId)
  private[translog] lazy val reader = new AESReader(name, raf, pageSize, keyProvider, keyId, fileHeader)
  private[translog] lazy val writer = new AESWriter(name, raf, pageSize, keyProvider, keyId, fileHeader)

  def this(file: File, pageSize: Int, keyProvider: KeyProvider, keyId: String) =
    this(file.getName(), new RandomAccessFile(file, "rw"), pageSize, keyProvider, keyId)

  override def tryLock(position: Long, size: Long, shared: Boolean): FileLock =
    throw new UnsupportedOperationException

  override def transferFrom(src: ReadableByteChannel, position: Long, count: Long): Long =
    throw new UnsupportedOperationException

  override def position(): Long =
    throw new UnsupportedOperationException

  override def position(newPosition: Long): FileChannel =
    throw new UnsupportedOperationException

  override def transferTo(position: Long, count: Long, target: WritableByteChannel): Long =
    throw new UnsupportedOperationException

  override def size(): Long =
    throw new UnsupportedOperationException

  override def truncate(size: Long): FileChannel =
    throw new UnsupportedOperationException

  override def lock(position: Long, size: Long, shared: Boolean): FileLock =
    throw new UnsupportedOperationException

  override def write(src: ByteBuffer): Int = {
    writer.write(src)
  }

  override def write(srcs: Array[ByteBuffer], offset: Int, length: Int): Long = {
    srcs.slice(offset, offset + length).map(write(_)).sum
  }

  override def write(src: ByteBuffer, position: Long): Int = {
    writer.seek(position)
    write(src)
  }

  override def read(dst: ByteBuffer): Int = {
    reader.read(dst)
  }

  override def read(dsts: Array[ByteBuffer], offset: Int, length: Int): Long = {
    dsts.slice(offset, offset + length).map(read(_)).sum
  }

  override def read(dst: ByteBuffer, position: Long): Int = {
    /**
      * Locking happens in the caller in FsTranslog so we don't need to worry about concurrent read/writes
      */
    writer.flush()

    // reader assumes an immutable file, but our code breaks that assumption. the simplest fix here is to
    // fix up the known length
    reader.setLength(writer.length())

    /**
      * Note that writer and reader are sharing the same RandomAccessFile instance, so there's a risk of file positions
      * getting mixed up here. Today this code works because reader and writer keep track of their own positions
      * and always re-seek before doing any ops.
      */
    reader.seek(position)
    read(dst)
  }

  override def force(metaData: Boolean): Unit = writer.flush()

  override def map(mode: MapMode, position: Long, size: Long): MappedByteBuffer =
    throw new UnsupportedOperationException

  override def implCloseChannel(): Unit = {
    writer.close()
    reader.close() // should be unnecessary since writer.close() invokes raf.close() but shouldn't hurt either
  }

}
