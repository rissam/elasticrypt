package com.workday.elasticrypt

import javax.crypto.spec.SecretKeySpec

/**
  * A factory that outputs key providers. The user must implement createKeyProvider.
  */

trait KeyProvider {
  def getKey(indexName: String): SecretKeySpec
}

object KeyProviderFactory {
  def createKeyProvider: KeyProvider = new HardcodedKeyProvider()
}
