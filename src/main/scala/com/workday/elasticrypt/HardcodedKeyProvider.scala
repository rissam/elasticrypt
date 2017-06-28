package com.workday.elasticrypt

import javax.crypto.spec.SecretKeySpec

object HardcodedKeyProvider {
  val ALGORITHM_AES = "AES"
  val DEFAULT_KEY: Array[Byte] = Array.fill[Byte](32)(1)
}

class HardcodedKeyProvider(keyValue: Array[Byte] = HardcodedKeyProvider.DEFAULT_KEY) extends KeyProvider {
  def getKey(keyId: String): SecretKeySpec = new SecretKeySpec(keyValue, HardcodedKeyProvider.ALGORITHM_AES) // TODO: retry?
}
