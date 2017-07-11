package org.elasticsearch.index.store

import java.io.{File, PrintWriter, RandomAccessFile}
import javax.crypto.spec.SecretKeySpec

import com.workday.elasticrypt.{HardcodedKeyProvider, KeyProvider}
import org.apache.lucene.util.FileHeader
import org.apache.lucene.store.{FlushInfo, IOContext, LockFactory}
import org.apache.lucene.util.AESReader
import org.elasticsearch.client.Client
import org.elasticsearch.common.collect.ImmutableMap
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.index.Index
import org.elasticsearch.index.shard.ShardId
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}

class EncryptedDirectoryTest extends FlatSpec with Matchers with MockitoSugar {

  def getMockShardId = {
    val mockShardId = mock[ShardId]
    val mockIndex = mock[Index]
    when(mockIndex.getName).thenReturn("env@tenant")
    when(mockShardId.index).thenReturn(mockIndex)
    mockShardId
  }

  behavior of "#openInput"
  it should "open raw input for segment files" in {
    new PrintWriter("/tmp/segments_test") {
      write(""); close()
    }
    val path = new File("/tmp")
    val context = new IOContext(new FlushInfo(1, 1))

    val ed = new EncryptedDirectory(path, mock[LockFactory], getMockShardId, mock[Client], mock[NodeKeyProviderComponent])
    ed.openInput("segments_test", context).toString.contains("AESIndexInput") shouldBe false
  }

  it should "open encrypted input for non-segment files" in {
    new PrintWriter("/tmp/edt_test") {
      write(""); close()
    }
    val path = new File("/tmp")
    val context = new IOContext(new FlushInfo(1, 1))
    val settings = mock[Settings]
    when(settings.get("url")).thenReturn("test") // TODO: symanurl?
    when(settings.getAsMap).thenReturn(ImmutableMap.of("url", "test"))

    val ed = spy(new EncryptedDirectory(path, mock[LockFactory], getMockShardId, mock[Client], mock[NodeKeyProviderComponent]))
    doReturn(mock[AESReader]).when(ed).createAESReader(any(), any(), any(), any(), any())
    ed.openInput("edt_test", context).toString.contains("AESIndexInput") shouldBe true
  }

  behavior of "#createOutput"
  it should "open raw input for segment files" in {
    new PrintWriter("/tmp/segments_test") {
      write(""); close()
    }
    val path = new File("/tmp")
    val context = new IOContext(new FlushInfo(1, 1))

    val ed = new EncryptedDirectory(path, mock[LockFactory], getMockShardId, mock[Client], mock[NodeKeyProviderComponent])
    ed.createOutput("segments_test", context).toString.contains("AESIndexOutput") shouldBe false
  }

  it should "open encrypted input for non-segment files" in {
    new PrintWriter("/tmp/edt_test") {
      write(""); close()
    }
    val path = new File("/tmp")
    val context = new IOContext(new FlushInfo(1, 1))
    val settings = mock[Settings]
    when(settings.get("url")).thenReturn("test") // TODO: symanurl?
    when(settings.getAsMap).thenReturn(ImmutableMap.of("url", "test"))

    val component = mock[NodeKeyProviderComponent]

    val keySpec = mock[SecretKeySpec]
    val ed = spy(new EncryptedDirectory(path, mock[LockFactory], getMockShardId, mock[Client], component))
    ed.createOutput("edt_test", context).toString.contains("AESIndexOutput") shouldBe true
  }

  behavior of "#createAESWriter and createAESReader"
  it should "write and read data intact" in {
    val encodedKeyBytes = (1 to 32).map(_.toByte).toArray
//    val secretKeySpec = new SecretKeySpec(encodedKeyBytes, 0, encodedKeyBytes.length, HmacUtil.DATA_CIPHER_ALGORITHM)
    val hardcodedKeyProvider = new HardcodedKeyProvider(encodedKeyBytes)
    val key = hardcodedKeyProvider.getKey("test")

    val path = new File("/tmp")
//    val context = new IOContext(new FlushInfo(1, 1))
    val settings = mock[Settings]
    when(settings.get("url")).thenReturn("test") // TODO: symanurl?
    when(settings.getAsMap).thenReturn(ImmutableMap.of("url", "test"))

    val ed = spy(new EncryptedDirectory(path, mock[LockFactory], getMockShardId, mock[Client], mock[NodeKeyProviderComponent]))

    val f = new File("/tmp/edt_test")
    if (f.exists()) {
      f.delete()
    }

    val testData = "READ_WRITE_TEST"
    val keyProvider = mock[KeyProvider]
    when(keyProvider.getKey("test")).thenReturn(key)

    val aesWriter = ed.createAESWriter(path, new RandomAccessFile(f, "rw"), 8192, keyProvider, mock[FileHeader])
    aesWriter.write(testData.map(_.toByte).toArray[Byte], 0, testData.length)
    aesWriter.close()

    val pathTest = new File("/tmp/edt_test")
    val aesReader = ed.createAESReader(pathTest, new RandomAccessFile(pathTest, "r"), 8192, keyProvider, mock[FileHeader])
    val bytes = new Array[Byte](testData.length)
    aesReader.read(bytes)
    aesReader.close()

    bytes shouldBe testData.map(_.toByte).toArray[Byte]
  }

}
