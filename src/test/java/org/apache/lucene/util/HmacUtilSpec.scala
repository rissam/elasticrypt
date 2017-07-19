package org.apache.lucene.util

import java.security.InvalidKeyException
import javax.crypto.spec.SecretKeySpec

import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}

class HmacUtilSpec extends FlatSpec with Matchers with MockitoSugar {

  def getFixedBytes(numBytes: Int): Array[Byte] = (1 to numBytes).map(_.toByte).toArray

  val encodedKeyBytes = getFixedBytes(32)
  val plainTextBytes = getFixedBytes(8)
  val secretKeySpec = new SecretKeySpec(encodedKeyBytes, 0, encodedKeyBytes.length, HmacUtil.DATA_CIPHER_ALGORITHM)

  behavior of "#hmacValue"
  it should "throw an exception if the plaintext parameter is null." in {
    val errorMessage = "HMAC Error - No plaintext was provided"
    val exception = the[CryptoException] thrownBy {
      HmacUtil.hmacValue(null, secretKeySpec)
    }
    exception.getMessage shouldEqual errorMessage
  }

  it should "throw an exception if the plaintext parameter is an empty array." in {
    val errorMessage = "HMAC Error - No plaintext was provided"

    val exception = the[CryptoException] thrownBy {
      HmacUtil.hmacValue(new Array[Byte](0), secretKeySpec)
    }
    exception.getMessage shouldEqual errorMessage
  }

  it should "throw an exception if the provided key is null." in {
    val errorMessage = "HMAC Error - Null key"

    val exception = the[CryptoException] thrownBy {
      HmacUtil.hmacValue(plainTextBytes, null)
    }
    exception.getMessage shouldEqual errorMessage
  }

  it should "throw an exception if the key algorithm is not AES." in {
    val tempSecretKeySpec = new SecretKeySpec(encodedKeyBytes, 0, encodedKeyBytes.length, "ECDSA")
    val errorMessage = "HMAC Error - Expected key of type AES, but found: ECDSA"

    val exception = the[CryptoException] thrownBy {
      HmacUtil.hmacValue(plainTextBytes, tempSecretKeySpec)
    }
    exception.getMessage shouldEqual errorMessage
  }

  it should "throw an exception if the provided key length is too small." in {
    val tempKeyBytes = getFixedBytes(31)
    val tempSecretKeySpec = new SecretKeySpec(tempKeyBytes, 0, tempKeyBytes.length, HmacUtil.DATA_CIPHER_ALGORITHM)

    val errorMessage = "HMAC Error - Key is too small. Expected at least 256 bits but found: 248"

    val exception = the[CryptoException] thrownBy {
      HmacUtil.hmacValue(plainTextBytes, tempSecretKeySpec)
    }
    exception.getMessage shouldEqual errorMessage
  }

  it should "throw a CryptoException if InvalidKeyException is thrown" in {
    class FailingKeySpec(var1: Array[Byte], var2: Int, var3: Int, var4: String) extends SecretKeySpec(var1, var2, var3, var4) {
      override def getEncoded: Array[Byte] = {
        throw new InvalidKeyException("Key is invalid")
      }
    }

    val secretKeySpec = new FailingKeySpec(encodedKeyBytes, 0, encodedKeyBytes.length, HmacUtil.DATA_CIPHER_ALGORITHM)

    val errorMessage = "HMAC Error - InvalidKeyException"
    val exception = the[CryptoException] thrownBy {
      HmacUtil.hmacValue(plainTextBytes, secretKeySpec)
    }
    exception.getMessage shouldEqual errorMessage
  }

  it should "properly compute the HMAC value for a given plaintext and AES key" in {
    val expectedResult = Array[Byte](-96, 116, -121, -67, -3, -39, 46, 36, 113, -119, -73, 77, 36,
      -33, 20, -48, -30, 85, 30, -38, 10, -25, -85, -12, 54, -8, -127, -61, -60, 102, -26, -87)
    HmacUtil.hmacValue(plainTextBytes, secretKeySpec) shouldEqual expectedResult
  }

  behavior of "#generateRandomBytes"
  it should "generate random bytes from ThreadLocalRandom.current()" in {
    !(HmacUtil.generateRandomBytes(3) sameElements HmacUtil.generateRandomBytes(3)) shouldBe true
  }
}
