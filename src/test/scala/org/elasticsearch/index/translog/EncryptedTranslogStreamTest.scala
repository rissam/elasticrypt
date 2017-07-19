package org.elasticsearch.index.translog

import java.io.{EOFException, File, IOException, RandomAccessFile}
import java.nio.channels.FileChannel
import javax.crypto.spec.SecretKeySpec

import com.workday.elasticrypt.KeyProvider
import org.apache.lucene.util.{FileHeader, HmacUtil}
import org.scalatest.BeforeAndAfterEach
import sun.nio.ch.ChannelInputStream

//scalastyle: off
import scala.collection.mutable._
//scalastyle: on
import org.apache.lucene.util.AESWriter
import org.elasticsearch.common.io.stream.InputStreamStreamInput
import org.mockito.Matchers._
import org.mockito.Mockito.{doThrow, spy, when}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}

class EncryptedTranslogStreamTest extends FlatSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  val indexName = "test"
  val fileName = "/tmp/ets_test"
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

  behavior of "#writeHeader"
  it should "return 0" in {
    val keyProvider = mock[KeyProvider]
    val ets = new EncryptedTranslogStream(10, keyProvider, indexName)

    ets.writeHeader(mock[FileChannel]) shouldBe 0
  }

  behavior of "#openInput"
  it should "rethrow EOFException as TruncatedTranslogException" in {
    val keyProvider = mock[KeyProvider]
    when(keyProvider.getKey(indexName)).thenReturn(mock[SecretKeySpec])

    val ets = spy(new EncryptedTranslogStream(10, keyProvider, indexName))
    doThrow(new EOFException(indexName)).when(ets).createInputStreamStreamInput(any[ChannelInputStream])

    val translogFile = new File(fileName)
    an[TruncatedTranslogException] shouldBe thrownBy {
      ets.openInput(translogFile)
    }
  }

  it should "rethrow IOException as TruncatedTranslogException" in {
    val keyProvider = mock[KeyProvider]
    when(keyProvider.getKey(indexName)).thenReturn(mock[SecretKeySpec])

    val ets = spy(new EncryptedTranslogStream(10, keyProvider, indexName))
    doThrow(new IOException(indexName)).when(ets).createInputStreamStreamInput(any[ChannelInputStream])

    val translogFile = new File(fileName)
    an[TranslogCorruptedException] shouldBe thrownBy {
      ets.openInput(translogFile)
    }
  }

  it should "return InputStreamStreamInput" in {
    val keyBytes = (1 to 32).map(_.toByte).toArray[Byte]
    val secretKeySpec = new SecretKeySpec(keyBytes, 0, keyBytes.length, HmacUtil.DATA_CIPHER_ALGORITHM)
    val keyProvider = mock[KeyProvider]
    when(keyProvider.getKey(indexName)).thenReturn(secretKeySpec)

    val ets = spy(new EncryptedTranslogStream(10, keyProvider, indexName))
    val existingTranslogFile = spy(new File(fileName))

    if (existingTranslogFile.exists()) {
      existingTranslogFile.delete()
    }

    def writeInt(writer: AESWriter, n: Int) = {
      val nBytes = Seq((n >> 24) % 256, (n >> 16) % 256, (n >> 8) % 256,  n % 256).map(_.toByte).toArray[Byte]
      writer.write(nBytes, 0, nBytes.length)
    }

    val fileHeader = mock[FileHeader]
    val aesWriter = new AESWriter("test", new RandomAccessFile(fileName, "rw"), 100, keyProvider, indexName, fileHeader)
    val codecName = "translog"
    writeInt(aesWriter, 1071082519) // Fixed seed
    aesWriter.write((Seq(codecName.length.toChar) ++ codecName).map(_.toByte).toArray[Byte], 0, codecName.length + 1)
    writeInt(aesWriter, 1)
    aesWriter.close()

    val translogFile = new File(fileName)
    ets.openInput(translogFile) shouldBe an[InputStreamStreamInput]
  }

}
