/*
 * Copyright 2017 Workday, Inc.
 *
 * This software is available under the MIT license.
 * Please see the LICENSE.txt file in this project.
 */

package com.workday.elasticrypt

import javax.crypto.spec.SecretKeySpec

/**
  * Contains constants to set the hardcoded key.
  */
object HardcodedKeyProvider {
  val ALGORITHM_AES = "AES"
  val DEFAULT_KEY: Array[Byte] = Array.fill[Byte](32)(1)
}

/**
  * Dummy implementation of the KeyProvider interface as a proof of concept.
  * We plan to introduce more complex KeyProviders such as HttpKeyProvider in a followup PR.
  */
class HardcodedKeyProvider(keyValue: Array[Byte] = HardcodedKeyProvider.DEFAULT_KEY) extends KeyProvider {
  def getKey(keyId: String): SecretKeySpec = new SecretKeySpec(keyValue, HardcodedKeyProvider.ALGORITHM_AES)
}
