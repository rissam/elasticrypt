package org.elasticsearch.index.store

import java.io.IOException

import org.apache.lucene.store.{BufferedIndexInput, IOContext, IndexInput}
import org.apache.lucene.util.AESReader

/**
  * Extension of org.apache.lucene.store.BufferedIndexInput that uses an instance of AESReader
  * to perform reads on encrypted files. Utilized in EncryptedDirectory on openInput().
  */
// scalastyle:off no.clone
final private[store] class AESIndexInput(resourceDesc: String, bufferSize: Int) extends BufferedIndexInput(resourceDesc, bufferSize) {

  private[this] var reader: AESReader = _

  /** Whether this instance is a clone and hence cannot own the file */
  private[store] var isClone: Boolean = false
  /** Start offset: non-zero in the slice case */
  final protected var off: Long = 0L
  /** End offset: start + length */
  final protected var end: Long = 0L

  def this(resourceDesc: String, reader: AESReader, context: IOContext) {
    this(resourceDesc, BufferedIndexInput.bufferSize(context))
    this.reader = reader
    this.off = 0L
    this.end = reader.length
  }

  def this(resourceDesc: String, reader: AESReader, off: Long, length: Long, bufferSize: Int) {
    this(resourceDesc, bufferSize)
    this.reader = reader
    this.off = off
    this.end = off + length
    this.isClone = true
  }

  @throws[IOException]
  def close() {
    if (!isClone) reader.close()
  }

  override def clone: AESIndexInput = {
    val clone: AESIndexInput = super.clone.asInstanceOf[AESIndexInput]
    clone.isClone = true
    clone
  }

  @throws[IOException]
  override def slice(sliceDescription: String, offset: Long, length: Long): IndexInput = {
    if (offset < 0 || length < 0 || offset + length > this.length)
      throw new IllegalArgumentException("slice() " + sliceDescription + " out of bounds: " + this)

    new AESIndexInput(sliceDescription, reader, off + offset, length, getBufferSize)
  }

  override final def length(): Long = end - off

  // This is taken from the AESDirectory patch
  @throws[IOException]
  protected def readInternal(b: Array[Byte], offset: Int, len: Int) {
    reader synchronized {
      try {
        /**
          * Multiple EncryptedNIOFSIndexInput instances can share one AESReader instance (see slice()).  Also,
          * each IndexInput keeps track of where it *thinks* the file position is in AESReader.  But since one
          * AESReader is being multiplexed across multiple IndexInput's, that file position is not accurate.  Hence,
          * when reading, we need to reposition AESReader back to where we think it is, then do the read.  (That's
          * also why we synchronize on the reader.)
          */
        val position: Long = getFilePointer + off
        if (position != reader.getFilePointer) {
          reader.seek(position)
        }
        var total: Int = 0
        do {
          val i: Int = reader.read(b, offset + total, len - total)
          if (i == -1) throw new IOException("read past EOF")
          total += i
        } while (total < len)
      } catch {
        case e: IOException => {
          throw e
        }
        case e: Exception => {
          throw new RuntimeException(e)
        }
      }
    }
  }

  @throws[IOException]
  protected def seekInternal(pos: Long) {
  }
}
// scalastyle:on no.clone
