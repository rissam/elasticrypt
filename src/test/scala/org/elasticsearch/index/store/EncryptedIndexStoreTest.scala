package org.elasticsearch.index.store

import org.elasticsearch.client.Client
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.unit.ByteSizeValue
import org.elasticsearch.env.NodeEnvironment
import org.elasticsearch.index.settings.IndexSettingsService
import org.elasticsearch.index.{Index, IndexService}
import org.elasticsearch.indices.store.IndicesStore
import org.scalatest.FlatSpec
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito.when
import org.mockito.Matchers

class EncryptedIndexStoreTest extends FlatSpec with org.scalatest.Matchers with MockitoSugar {

  behavior of "#shardDirectory"
  it should "shardDirectory" in {
    val settings = mock[Settings]
    when(settings.getComponentSettings(Matchers.any())).thenReturn(null)
    when(settings.get(Matchers.eq("index.store.throttle.type"), Matchers.anyString())).thenReturn("all")
    when(settings.getAsBytesSize(Matchers.eq("index.store.throttle.max_bytes_per_sec"), Matchers.any())).thenReturn(new ByteSizeValue(1000))
    when(settings.get(Matchers.eq("index.store.throttle.type"), Matchers.anyString())).thenReturn("all")
    val nodeEnv = mock[NodeEnvironment]
    when(nodeEnv.hasNodeFile).thenReturn(false)
    val indexService = mock[IndexService]
    val indexSettingsService = mock[IndexSettingsService]
    when(indexService.settingsService).thenReturn(indexSettingsService)
    val eis = new EncryptedIndexStore(mock[Index], settings, indexService, mock[IndicesStore], nodeEnv, mock[Client])
    eis.shardDirectory() shouldBe classOf[EncryptedDirectoryService]
  }

}
