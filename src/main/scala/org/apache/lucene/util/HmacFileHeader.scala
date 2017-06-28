package org.apache.lucene.util

import java.io.RandomAccessFile

import com.workday.elasticrypt.KeyProvider

/**
  * Implementation of FileHeader that appends an HMAC hashed text to validate that the proper key is being used
  * to decrypt the file
  *
  * @param raf
  */

class HmacFileHeader(raf: RandomAccessFile, keyProvider: KeyProvider, keyId: String) extends FileHeader(raf) {

  // scalastyle:off null
  var keyIdBytes: Array[Byte] = null // TODO: move to FileHeader interface
  private var plainTextBytes: Array[Byte] = null
  private var hmacBytes: Array[Byte] = null
  // scalastyle:on null

  def writeHeader(): Long = {
    // Write keyId
    val keyIdBytes: Array[Byte] = keyId.getBytes
    writeByteArray(keyIdBytes)

    // Write plaintext bytes
    val numBytes = 8
    val plainTextBytes: Array[Byte] = HmacUtil.generateRandomBytes(numBytes)
    writeByteArray(plainTextBytes)

    // Write HMAC bytes
    val hmacBytes: Array[Byte] = HmacUtil.hmacValue(plainTextBytes, keyProvider.getKey(keyId))
    writeByteArray(hmacBytes)

    // return the current file pointer (i.e. header offset)
    raf.getFilePointer
  }

  private def writeByteArray(byteArray: Array[Byte]) {
    raf.writeInt(byteArray.length)
    raf.write(byteArray)
  }

  def readHeader(): Unit = {
    raf.seek(0)

    keyIdBytes = readBytesFromCurrentFilePointer // TODO: expose these
    plainTextBytes = readBytesFromCurrentFilePointer
    hmacBytes = readBytesFromCurrentFilePointer
  }

  @throws[java.io.IOException]
  private def readBytesFromCurrentFilePointer: Array[Byte] = {
    /* Read the length of the following byte array in the file.*/
    val num_bytes: Int = raf.readInt
    val byteArray: Array[Byte] = new Array[Byte](num_bytes)
    raf.readFully(byteArray)
    byteArray
  }

}
