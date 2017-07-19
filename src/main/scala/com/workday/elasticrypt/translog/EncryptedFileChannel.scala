/*
 * Copyright 2017 Workday, Inc.
 *
 * This software is available under the MIT license.
 * Please see the LICENSE.txt file in this project.
 */

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
  *
  * Two issues here:
  * (1) AESReader should only be instantiated for existent files - maybe there should be an open method?
  * (2) AESWriter should only be instantiated on non-existent files - does ES guarantee that for translog?
  *
  * In any case, to support both, we have to at least lazily open AESReader and AESWriter only as needed....
  * Simply using lazy here seems too easy.
  */
class EncryptedFileChannel(name: String, raf: RandomAccessFile, pageSize: Int, keyProvider: KeyProvider, indexName: String)
  extends FileChannel {

  private[translog] lazy val fileHeader = new HmacFileHeader(raf, keyProvider, indexName)
  private[translog] lazy val reader = new AESReader(name, raf, pageSize, keyProvider, indexName, fileHeader)
  private[translog] lazy val writer = new AESWriter(name, raf, pageSize, keyProvider, indexName, fileHeader)

  /** @constructor creates an EncryptedFileChannel by creating a new RandomAccessFile
    * @param file File used
    * @param pageSize number of 16-byte blocks per page
    * @param keyProvider encryption key information getter
    * @param indexName name of the index used to retrieve the key
    */
  def this(file: File, pageSize: Int, keyProvider: KeyProvider, indexName: String) =
    this(file.getName(), new RandomAccessFile(file, "rw"), pageSize, keyProvider, indexName)

  /**
    * Overrides tryLock to throw an UnsupportedOperationException.
    * @param position the position at which the locked region is to start; must be non-negative
    * @param size the size of the locked region; must be non-negative, and the sum position size must be non-negative
    * @param shared true to request a shared lock; false to request an exclusive lock
    */
  override def tryLock(position: Long, size: Long, shared: Boolean): FileLock =
    throw new UnsupportedOperationException

  /**
    * Overrides transferFrom to throw an UnsupportedOperationException.
    * @param src the source channel
    * @param position the position within the file at which the transfer is to begin; must be non-negative
    * @param count the maximum number of bytes to be transferred; must be non-negative
    */
  override def transferFrom(src: ReadableByteChannel, position: Long, count: Long): Long =
    throw new UnsupportedOperationException

  /**
    * Overrides position to throw an UnsupportedOperationException.
    */
  override def position(): Long =
    throw new UnsupportedOperationException

  /**
    * Overrides position to throw an UnsupportedOperationException.
    * @param newPosition the new position, a non-negative integer counting the number of bytes from the start of the entity
    */
  override def position(newPosition: Long): FileChannel =
    throw new UnsupportedOperationException

  /**
    * Overrides transferTo to throw an UnsupportedOperationException.
    * @param position the position within the file at which the transfer is to begin; must be non-negative
    * @param count  the maximum number of bytes to be transferred; must be non-negative
    * @param target the target channel
    */
  override def transferTo(position: Long, count: Long, target: WritableByteChannel): Long =
    throw new UnsupportedOperationException

  /**
    * Overrides size to throw an UnsupportedOperationException.
    */
  override def size(): Long =
    throw new UnsupportedOperationException

  /**
    * Overrides truncate to throw an UnsupportedOperationException.
    * @param size the new size, a non-negative byte count
    */
  override def truncate(size: Long): FileChannel =
    throw new UnsupportedOperationException

  /**
    * Overrides lock to throw an UnsupportedOperationException.
    * @param position the position at which the locked region is to start; must be non-negative
    * @param size the size of the locked region; must be non-negative, and the sum position size must be non-negative
    * @param shared true to request a shared lock (this channel must be open for reading and possibly writing);
    *               false to request an exclusive lock (this channel must be open for writing and possibly reading)
    */
  override def lock(position: Long, size: Long, shared: Boolean): FileLock =
    throw new UnsupportedOperationException

  /**
    * Writes data to disk.
    * @param src the buffer from which bytes are to be retrieved
    * @return the number of bytes copied into the buffer cache
    */
  override def write(src: ByteBuffer): Int = {
    writer.write(src)
  }

  /**
    * Writes a section of data to disk.
    * @param srcs data to be written
    * @param offset offset in the data
    * @param length number of bytes to be written
    * @return the number of bytes written into disk
    */
  override def write(srcs: Array[ByteBuffer], offset: Int, length: Int): Long = {
    srcs.slice(offset, offset + length).map(write(_)).sum
  }

  /**
    * Writes data to disk starting from a given position.
    * @param src the buffer from which bytes are to be transferred
    * @param position the file position at which the transfer is to begin; must be non-negative
    * @return the number of bytes copied into the buffer cache
    */
  override def write(src: ByteBuffer, position: Long): Int = {
    writer.seek(position)
    write(src)
  }

  /**
    * Read bytes from the file into the given byte array.
    * @param dst byte array to copy bytes to
    * @return -1 if eof has been reached, the number of bytes copied into b otherwise.
    */
  override def read(dst: ByteBuffer): Int = {
    reader.read(dst)
  }

  /**
    * Read bytes from the file into the given byte array.
    * @param dsts byte array to copy bytes to
    * @param offset position in b to start copying data
    * @param length number of bytes to be copied
    * @return -1 if eof has been reached, the number of bytes copied into b otherwise.
    */
  override def read(dsts: Array[ByteBuffer], offset: Int, length: Int): Long = {
    dsts.slice(offset, offset + length).map(read(_)).sum
  }

  /**
    * Read bytes from the file into the given byte array.
    * @param dst byte array to copy bytes to
    * @param position the file position at which the transfer is to begin; must be non-negative
    * @return -1 if eof has been reached, the number of bytes copied into b otherwise.
    */
  override def read(dst: ByteBuffer, position: Long): Int = {
    // Locking happens in the caller in FsTranslog so we don't need to worry about concurrent read/writes
    writer.flush()

    // Reader assumes an immutable file, but the code breaks that assumption.
    // The simplest fix here is to fix up the known length.
    reader.setLength(writer.length())

    /**
      * Note that writer and reader are sharing the same RandomAccessFile instance, so there's a risk of file positions
      * getting mixed up here. Today this code works because reader and writer keep track of their own positions
      * and always re-seek before doing any ops.
      */
    reader.seek(position)
    read(dst)
  }

  /**
    * Flushes out the data from the writer buffer to the disk.
    * @param metaData true if need to force changes to both the file's content and the metadata written to storage;
    *                 otherwise, only need to force content changes to be written
    */
  override def force(metaData: Boolean): Unit = writer.flush()

  /**
    * Overrides map to throw an UnsupportedOperationException.
    * @param mode file is to be mapped read-only, read/write, or privately (copy-on-write), respectively
    * @param position the position within the file at which the mapped region is to start; must be non-negative
    * @param size the size of the region to be mapped; must be non-negative and no greater than MAX_VALUE
    */
  override def map(mode: MapMode, position: Long, size: Long): MappedByteBuffer =
    throw new UnsupportedOperationException

  /**
    * Closes the reader and the writer.
    */
  override def implCloseChannel(): Unit = {
    writer.close()
    reader.close() // Shouldn't be necessary since writer.close() invokes raf.close() but shouldn't hurt either
  }

}
