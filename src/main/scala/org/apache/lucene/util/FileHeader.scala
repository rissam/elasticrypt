/*
 * Copyright 2017 Workday, Inc.
 *
 * This software is available under the MIT license.
 * Please see the LICENSE.txt file in this project.
 */

package org.apache.lucene.util

import java.io._

/**
  * Interface for writing unencrypted metadata at the beginning of an encrypted file.
  */
abstract class FileHeader[T: Serializable](raf: RandomAccessFile) {

  def headerContent: T

  /**
    * Writes the file header.
    */
  def writeHeader(): Long = {
    writeByteArray(serialize(headerContent))

    // return the current file pointer (i.e. header offset)
    raf.getFilePointer
  }
  /**
    * Reads the file header.
    */
  def readHeader(): T = {
    deserialize(readBytesFromCurrentFilePointer())
  }

  protected def serialize(value: T): Array[Byte] = {
    val stream: ByteArrayOutputStream = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(stream)
    oos.writeObject(value)
    oos.close
    stream.toByteArray
  }

  protected def deserialize(bytes: Array[Byte]): T = {
    val ois = new ObjectInputStream(new ByteArrayInputStream(bytes))
    val value = ois.readObject.asInstanceOf[T]
    ois.close
    value
  }

  /**
    * Writes the byte array.
    */
  protected def writeByteArray(byteArray: Array[Byte]) {
    raf.writeInt(byteArray.length)
    raf.write(byteArray)
  }

  /**
    * Read current bytes.
    */
  @throws[java.io.IOException]
  protected def readBytesFromCurrentFilePointer(): Array[Byte] = {
    /* Read the length of the following byte array in the file.*/
    val num_bytes = raf.readInt
    val byteArray = new Array[Byte](num_bytes)
    raf.readFully(byteArray)
    byteArray
  }
}
