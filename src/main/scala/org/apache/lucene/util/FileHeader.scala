package org.apache.lucene.util

import java.io.RandomAccessFile

// TODO: may need to make this a java file?

abstract class FileHeader(raf: RandomAccessFile) {
  def writeHeader(): Long
  def readHeader(): Unit
}
