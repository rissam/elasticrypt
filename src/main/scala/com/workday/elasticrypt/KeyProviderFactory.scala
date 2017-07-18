package com.workday.elasticrypt

import javax.crypto.spec.SecretKeySpec

/**
  * A trait describing the basic key provider.
  */
trait KeyProvider {
  def getKey(indexName: String): SecretKeySpec
}

/**
  * A factory that outputs key providers. The user must implement createKeyProvider.
  */
object KeyProviderFactory {
  def createKeyProvider: KeyProvider = new HardcodedKeyProvider()
}
