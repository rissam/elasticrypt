package com.workday.elasticrypt.translog

import java.io.{File, IOException, RandomAccessFile}
import javax.crypto.spec.SecretKeySpec

import com.workday.elasticrypt.KeyProvider
import org.apache.lucene.util.HmacUtil
import org.elasticsearch.index.translog.EncryptedTranslogStream

import org.apache.lucene.util.{AESReader, AESWriter}
import org.elasticsearch.common.logging.ESLogger
import org.mockito.Mockito._
import org.mockito.Matchers._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}

class EncryptedRafReferenceTest extends FlatSpec with Matchers with MockitoSugar {

  def getKeyProvider = {
    val encodedKeyBytes = (1 to 32).map(_.toByte).toArray
    val secretKeySpec = new SecretKeySpec(encodedKeyBytes, 0, encodedKeyBytes.length, HmacUtil.DATA_CIPHER_ALGORITHM)
    val keyProvider = mock[KeyProvider]
    doReturn(secretKeySpec).when(keyProvider).getKey(anyString())

    keyProvider
  }

  behavior of "#channel"
  it should "return EncryptedFileChannel" in {
    val file = new File("/tmp/err_test")
    val err = new EncryptedRafReference(file, mock[ESLogger], 10, mock[KeyProvider], "test")

    err.channel() shouldBe an[EncryptedFileChannel]
  }

  behavior of "#translogStreamFor"
  it should "return EncryptedTranslogStream" in {
    val file = new File("/tmp/err_test")
    val err = new EncryptedRafReference(file, mock[ESLogger], 10, mock[KeyProvider], "test")

    err.translogStreamFor shouldBe an[EncryptedTranslogStream]
  }

  behavior of "#increaseRefCount"
  it should "increment reference counter" in {
    val file = new File("/tmp/err_test")
    val err = new EncryptedRafReference(file, mock[ESLogger], 10, mock[KeyProvider], "test")

    err.refCount.intValue() shouldBe 1
    err.increaseRefCount()
    err.refCount.intValue() shouldBe 2
  }

  def getEFC(file: File) = {
    // We don't want `delete` here to mess with the tests
    val tmpFile = new File(file.getAbsolutePath)
    if (tmpFile.exists()) {
      tmpFile.delete()
    }

    spy(new EncryptedFileChannel("test", new RandomAccessFile(file.getAbsolutePath, "rw"), 10, getKeyProvider, "test"))
  }

  behavior of "#decreaseRefCount"
  it should "decrement reference counter" in {
    val file = spy(new File("/tmp/err_test"))
    val err = spy(new EncryptedRafReference(file, mock[ESLogger], 10, getKeyProvider, "test"))
    val efc = getEFC(file)
    doReturn(efc).when(err).channel()
    doNothing().when(efc).implCloseChannel()

    err.refCount.intValue() shouldBe 1
    err.decreaseRefCount(deleteFile = false)
    err.refCount.intValue() shouldBe 0

    verify(file, times(0)).delete()
    verify(efc, times(1)).implCloseChannel()
  }

  it should "decrement reference counter and delete file" in {
    val file = spy(new File("/tmp/err_test"))
    val err = spy(new EncryptedRafReference(file, mock[ESLogger], 10, getKeyProvider, "test"))
    val efc = getEFC(file)

    doReturn(efc).when(err).channel()
    doNothing().when(efc).implCloseChannel()

    err.refCount.intValue() shouldBe 1
    err.decreaseRefCount(deleteFile = true)
    err.refCount.intValue() shouldBe 0

    verify(file, times(1)).delete()
    verify(efc, times(1)).implCloseChannel()
  }

  it should "decrement reference counter and not delete the file if there are references left" in {
    val file = spy(new File("/tmp/err_test"))
    val err = spy(new EncryptedRafReference(file, mock[ESLogger], 10, getKeyProvider, "test"))
    val efc = getEFC(file)

    doReturn(efc).when(err).channel()
    doNothing().when(efc).implCloseChannel()
    err.increaseRefCount()

    err.refCount.intValue() shouldBe 2
    err.decreaseRefCount(deleteFile = true)
    err.refCount.intValue() shouldBe 1

    verify(file, times(0)).delete()
    verify(efc, times(0)).implCloseChannel()
  }

  it should "handle IOExceptions" in {
    val file = spy(new File("/tmp/err_test"))
    val err = spy(new EncryptedRafReference(file, mock[ESLogger], 10, getKeyProvider, "test"))
    val efc = getEFC(file)

    doReturn(efc).when(err).channel()

    val writer = mock[AESWriter]
    doReturn(writer).when(efc).writer

    val reader = mock[AESReader]
    doReturn(reader).when(efc).reader

    doThrow(new IOException("oops")).when(writer).close()

    err.refCount.intValue() shouldBe 1
    err.decreaseRefCount(deleteFile = true)
    err.refCount.intValue() shouldBe 0

    verify(file, times(0)).delete()
  }

}
