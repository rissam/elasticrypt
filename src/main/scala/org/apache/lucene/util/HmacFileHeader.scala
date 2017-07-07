/*
 * Copyright 2017 Workday, Inc.
 *
 * This software is available under the MIT license.
 * Please see the LICENSE.txt file in this project.
 */

package org.apache.lucene.util

import java.io._

import com.workday.elasticrypt.KeyProvider

case class HmacHeaderContents(
  keyId: String,
  plainTextBytes: Array[Byte],
  hmacBytes: Array[Byte]
)

/**
  * Implementation of the FileHeader interface that adds a MAC hash that is
  * used to verify that the correct key is being used to decrypt a file.
  */
class HmacFileHeader(raf: RandomAccessFile, keyProvider: KeyProvider, keyId: String) extends FileHeader[HmacHeaderContents](raf) {
  override def headerContent = {
    val numBytes = 8
    val plainTextBytes = HmacUtil.generateRandomBytes(numBytes)
    val hmacBytes = HmacUtil.hmacValue(plainTextBytes, keyProvider.getKey(keyId))
    HmacHeaderContents(keyId, plainTextBytes, hmacBytes)
  }
}
