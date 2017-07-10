package org.elasticsearch.index.store

import java.io.File

import org.elasticsearch.client.Client
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.index.Index
import org.elasticsearch.index.shard.ShardId
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}

class EncryptedDirectoryServiceTest extends FlatSpec with Matchers with MockitoSugar {

  behavior of "#newFSDirectory"
  it should "return EncryptedDirectory" in {
    val shardId = mock[ShardId]
    when(shardId.index()).thenReturn(new Index("test"))
    when(shardId.id()).thenReturn(0)

    val settings = mock[Settings]
    when(settings.getAsBoolean("logger.logHostAddress", false)).thenReturn(false)
    when(settings.getAsBoolean("logger.logHostName", false)).thenReturn(false)
    when(settings.get("name")).thenReturn("test")

    val file = new File("/tmp")

    val eds = new EncryptedDirectoryService(shardId, settings, mock[EncryptedIndexStore], mock[Client], mock[NodeKeyProviderComponent])
    eds.newFSDirectory(file, null) shouldBe an[EncryptedDirectory]
  }

}
