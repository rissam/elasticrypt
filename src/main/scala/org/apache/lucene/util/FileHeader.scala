package org.apache.lucene.util

import java.io.RandomAccessFile

/**
  * Interface for writing unencrypted metadata at the beginning of an encrypted file.
  */
abstract class FileHeader(raf: RandomAccessFile) {
  // scalastyle:off null
  var keyIdBytes: Array[Byte] = null
  // scalastyle:on null

  /**
    * Writes the file header.
    */
  def writeHeader(): Long
  /**
    * Reads the file header.
    */
  def readHeader(): Unit
}
