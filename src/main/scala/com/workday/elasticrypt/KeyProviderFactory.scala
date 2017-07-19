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

  /**
    * The method in the factory that creates and outputs the product, a KeyProvider.
    * This is currently programmed to output a HardcodedKeyProvider.
    * The user must implement this according to the needs of his/her project.
    * @return a KeyProvider that returns keys
    */
  def createKeyProvider: KeyProvider = new HardcodedKeyProvider()
}
