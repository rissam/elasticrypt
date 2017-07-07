/*
 * Copyright 2017 Workday, Inc.
 *
 * This software is available under the MIT license.
 * Please see the LICENSE.txt file in this project.
 */

package org.apache.lucene.util

import java.io.RandomAccessFile

import com.workday.elasticrypt.KeyProvider

/**
  * Implementation of the FileHeader interface that adds a MAC hash that is
  * used to verify that the correct key is being used to decrypt a file.
  */
class HmacFileHeader(raf: RandomAccessFile, keyProvider: KeyProvider, keyId: String) extends FileHeader(raf) {

  // scalastyle:off null
  private var hmacBytes: Array[Byte] = null
  private var plainTextBytes: Array[Byte] = null
  // scalastyle:on null

  /**
    * Writes the file header.
    */
  def writeHeader(): Long = {
    // Write keyId
    keyIdBytes = keyId.getBytes
    writeByteArray(keyIdBytes)

    // Write plaintext bytes
    val numBytes = 8
    plainTextBytes = HmacUtil.generateRandomBytes(numBytes)
    writeByteArray(plainTextBytes)

    // Write HMAC bytes
    hmacBytes = HmacUtil.hmacValue(plainTextBytes, keyProvider.getKey(keyId))
    writeByteArray(hmacBytes)

    // return the current file pointer (i.e. header offset)
    raf.getFilePointer
  }

  /**
    * Writes the byte array.
    */
  private def writeByteArray(byteArray: Array[Byte]) {
    raf.writeInt(byteArray.length)
    raf.write(byteArray)
  }

  /**
    * Reads the file header.
    */
  def readHeader(): Unit = {
    raf.seek(0)

    keyIdBytes = readBytesFromCurrentFilePointer
    plainTextBytes = readBytesFromCurrentFilePointer
    hmacBytes = readBytesFromCurrentFilePointer
  }

  /**
    * Read current bytes.
    */
  @throws[java.io.IOException]
  private def readBytesFromCurrentFilePointer: Array[Byte] = {
    /* Read the length of the following byte array in the file.*/
    val num_bytes: Int = raf.readInt
    val byteArray: Array[Byte] = new Array[Byte](num_bytes)
    raf.readFully(byteArray)
    byteArray
  }

}
