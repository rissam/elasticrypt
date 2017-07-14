package com.workday.elasticrypt.translog

import java.io.{File, RandomAccessFile}
import java.nio.channels.FileChannel.MapMode
import java.nio.channels.{ReadableByteChannel, WritableByteChannel}
import javax.crypto.spec.SecretKeySpec

import com.workday.elasticrypt.KeyProvider
import org.apache.lucene.util.HmacUtil
import org.scalatest.BeforeAndAfterEach

//scalastyle: off
import scala.collection.mutable._
//scalastyle: on

import org.apache.lucene.util.{AESReader, AESWriter}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}

class EncryptedFileChannelTest extends FlatSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  val indexName = "test"
  val fileName = "/tmp/efc_test"
  val f = new File(fileName)

  override def beforeEach = {
    if (f.exists()) {
      f.delete()
    }
    super.beforeEach()
  }

  override def afterEach = {
    if (f.exists()) {
      f.delete()
    }
    super.afterEach()
  }

  def getMockKeyProvider = {
    val keyProvider = mock[KeyProvider]
    val encodedKeyBytes = (1 to 32).map(_.toByte).toArray
    val secretKeySpec = new SecretKeySpec(encodedKeyBytes, 0, encodedKeyBytes.length, HmacUtil.DATA_CIPHER_ALGORITHM)
    when(keyProvider.getKey(indexName)).thenReturn(secretKeySpec)
    keyProvider
  }

  def getMockChannel = new EncryptedFileChannel(new File("test"), 10, getMockKeyProvider, indexName)

  behavior of "#reader & #writer"
  it should "return an AESReader and AESWriter" in {
    val efc_writer = new EncryptedFileChannel("test", new RandomAccessFile(fileName, "rw"), 10, getMockKeyProvider, indexName)
    val testData = "READ_WRITE_TEST"
    val aesWriter = efc_writer.writer
    aesWriter.write(testData.map(_.toByte).toArray[Byte], 0, testData.length)
    aesWriter.close()

    val efc_reader = new EncryptedFileChannel("test", new RandomAccessFile(fileName, "r"), 10, getMockKeyProvider, indexName)
    val aesReader = efc_reader.reader
    val bytes = new Array[Byte](testData.length)
    aesReader.read(bytes)
    aesReader.close()

    bytes shouldBe testData.map(_.toByte).toArray[Byte]
  }

  behavior of "#EncryptedFileChannel"
  it should "instantiate from File" in {
    val efc = new EncryptedFileChannel("test", new RandomAccessFile(fileName, "rw"), 10, getMockKeyProvider, indexName)
    efc.writer.length() shouldBe 0
  }

  behavior of "#tryLock"
  it should "throw an exception" in {
    val efc = getMockChannel

    an[UnsupportedOperationException] shouldBe thrownBy {
      efc.tryLock(0, 100, shared = true)
    }
  }

  behavior of "#transferFrom"
  it should "throw an exception" in {
    val efc = getMockChannel
    val src = mock[ReadableByteChannel]

    an[UnsupportedOperationException] shouldBe thrownBy {
      efc.transferFrom(src, 0, 100)
    }
  }

  behavior of "#position"
  it should "throw an exception with no parameters" in {
    val efc = getMockChannel

    an[UnsupportedOperationException] shouldBe thrownBy {
      efc.position()
    }
  }
  it should "throw an exception with parameters" in {
    val efc = getMockChannel

    an[UnsupportedOperationException] shouldBe thrownBy {
      efc.position(100)
    }
  }

  behavior of "#transferTo"
  it should "throw an exception" in {
    val efc = getMockChannel
    val dst = mock[WritableByteChannel]

    an[UnsupportedOperationException] shouldBe thrownBy {
      efc.transferTo(0, 100, dst)
    }
  }

  behavior of "#size"
  it should "throw an exception" in {
    val efc = getMockChannel

    an[UnsupportedOperationException] shouldBe thrownBy {
      efc.size()
    }
  }

  behavior of "#truncate"
  it should "throw an exception" in {
    val efc = getMockChannel

    an[UnsupportedOperationException] shouldBe thrownBy {
      efc.truncate(100)
    }
  }

  behavior of "#lock"
  it should "throw an exception" in {
    val efc = getMockChannel

    an[UnsupportedOperationException] shouldBe thrownBy {
      efc.lock(0, 100, shared = true)
    }
  }

  behavior of "#map"
  it should "throw an exception" in {
    val efc = getMockChannel

    an[UnsupportedOperationException] shouldBe thrownBy {
      efc.map(mock[MapMode], 0, 100)
    }
  }

  behavior of "#write"
  it should "write ByteBuffer to writer" in {
    val efc = spy(getMockChannel)
    val mockWriter = mock[AESWriter]
    doReturn(mockWriter).when(efc).writer

    val bytes = java.nio.ByteBuffer.wrap(Array[Byte](10))
    efc.write(bytes)

    verify(mockWriter, times(0)).seek(anyInt)
    verify(mockWriter, times(1)).write(bytes)
  }

  it should "write ByteBuffer to writer with an offset" in {
    val efc = spy(getMockChannel)
    val mockWriter = mock[AESWriter]
    doReturn(mockWriter).when(efc).writer

    val bytes = java.nio.ByteBuffer.wrap(Array[Byte](10))
    efc.write(bytes, 2)

    verify(mockWriter, times(1)).seek(2)
    verify(mockWriter, times(1)).write(bytes)
  }

  it should "write Array of ByteBuffers to writer" in {
    val efc = spy(getMockChannel)
    val mockWriter = mock[AESWriter]
    doReturn(mockWriter).when(efc).writer

    val bytes = java.nio.ByteBuffer.wrap(Array[Byte](10))
    val byteArrays = Seq(bytes, bytes, bytes).toArray
    efc.write(byteArrays, 1, 100)

    verify(mockWriter, times(0)).seek(anyInt)
    verify(mockWriter, times(2)).write(bytes)
  }

  behavior of "#read"
  it should "read ByteBuffer from reader" in {
    val efc = spy(getMockChannel)
    val mockReader = mock[AESReader]
    doReturn(mockReader).when(efc).reader

    val bytes = java.nio.ByteBuffer.wrap(Array[Byte](10))
    efc.read(bytes)

    verify(mockReader, times(0)).seek(anyInt)
    verify(mockReader, times(1)).read(bytes)
  }

  it should "read ByteBuffer from reader with offset" in {
    val efc = spy(getMockChannel)
    val mockReader = mock[AESReader]
    doReturn(mockReader).when(efc).reader
    val mockWriter = mock[AESWriter]
    doReturn(mockWriter).when(efc).writer

    doReturn(10L).when(mockWriter).length()

    val bytes = java.nio.ByteBuffer.wrap(Array[Byte](10))
    efc.read(bytes, 100)

    verify(mockWriter, times(1)).flush()
    verify(mockReader, times(1)).setLength(10)
    verify(mockReader, times(1)).seek(100)
    verify(mockReader, times(1)).read(bytes)
  }

  it should "read Array of ByteBuffers from reader" in {
    val efc = spy(getMockChannel)
    val mockReader = mock[AESReader]
    doReturn(mockReader).when(efc).reader

    val bytes = java.nio.ByteBuffer.wrap(Array[Byte](10))
    val byteArrays = Seq(bytes, bytes, bytes).toArray
    efc.read(byteArrays, 1, 3)

    verify(mockReader, times(0)).seek(anyInt)
    verify(mockReader, times(2)).read(bytes)
  }

  behavior of "#force"
  it should "flush the writer" in {
    val efc = spy(getMockChannel)
    val mockWriter = mock[AESWriter]
    doReturn(mockWriter).when(efc).writer

    efc.force(metaData = true)
    verify(mockWriter, times(1)).flush()

    efc.force(metaData = false)
    verify(mockWriter, times(2)).flush()
  }

  behavior of "#implCloseChannel"
  it should "close both reader & writer" in {
    val efc = spy(getMockChannel)
    val mockWriter = mock[AESWriter]
    doReturn(mockWriter).when(efc).writer
    val mockReader = mock[AESReader]
    doReturn(mockReader).when(efc).reader

    efc.implCloseChannel()
    verify(mockReader, times(1)).close()
    verify(mockWriter, times(1)).close()
  }

}
