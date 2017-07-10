package com.workday.elasticrypt.translog

import java.io.File
import java.nio.file.spi.FileSystemProvider
import java.nio.file.{FileSystem, Path, Paths}
import javax.crypto.spec.SecretKeySpec

import org.elasticsearch.cache.recycler.PageCacheRecycler
import org.elasticsearch.common.logging.ESLogger
import org.elasticsearch.common.settings.{ImmutableSettings, Settings}
import org.elasticsearch.common.unit.ByteSizeValue
import org.elasticsearch.common.util.BigArrays
import org.elasticsearch.index.Index
import org.elasticsearch.index.settings.IndexSettingsService
import org.elasticsearch.index.shard.ShardId
import org.elasticsearch.index.store.{IndexStore, NodeKeyProviderComponent}
import org.elasticsearch.index.store.ram.RamIndexStore
import org.elasticsearch.index.translog.fs.FsTranslogFile.Type
import org.elasticsearch.index.translog.{EncryptedTranslogStream, Translog}
import org.elasticsearch.threadpool.ThreadPool
import org.mockito.Matchers._
import org.mockito.Mockito.{doReturn, spy, times, verify}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}

class EncryptedTranslogTest extends FlatSpec with Matchers with MockitoSugar {

  def getEFT = {
    val shard = mock[ShardId]
    doReturn(new Index("test")).when(shard).index()
    doReturn(123).when(shard).id()

    val indexStore = mock[IndexStore]
    val path = mock[Path]
    doReturn(Array(path)).when(indexStore).shardTranslogLocations(any[ShardId])
    val fs = mock[FileSystem]
    doReturn(fs).when(path).getFileSystem
    val provider = mock[FileSystemProvider]
    doReturn(provider).when(fs).provider()

    val settings = mock[Settings]
    doReturn(settings).when(settings).getComponentSettings(any[Class[_]])
    doReturn("simple").when(settings).get("type", Type.BUFFERED.name)
    doReturn(new ByteSizeValue(100)).when(settings).getAsBytesSize("buffer_size", ByteSizeValue.parseBytesSizeValue("64k"))
    doReturn(new ByteSizeValue(100)).when(settings).getAsBytesSize("transient_buffer_size", ByteSizeValue.parseBytesSizeValue("8k"))

    new EncryptedTranslog(shard, settings, mock[IndexSettingsService], mock[BigArrays], indexStore, mock[NodeKeyProviderComponent])
  }

  behavior of "#createRafReference"
  it should "create EncryptedRafReference" in {
    val file = new File("/tmp/eft_test")
    val logger = mock[ESLogger]

    val eft = getEFT
    eft.createRafReference(file, logger) shouldBe an[EncryptedRafReference]
  }

  behavior of "#translogStreamFor"
  it should "create EncryptedTranslogStream" in {
    val eft = getEFT

    val file = new File("/tmp/eft_test")
    eft.translogStreamFor(file) shouldBe an[EncryptedTranslogStream]
  }

  behavior of "#add"
  it should "request the key" in {
    val shard = mock[ShardId]
    doReturn(new Index("test")).when(shard).index()
    doReturn(123).when(shard).id()

    val settings = ImmutableSettings.builder().build()
    val indexStore = mock[RamIndexStore]
    val threadPool = new ThreadPool(settings)
    val recycler = new PageCacheRecycler(settings, threadPool)
    val breakerService = null
    val bigArrays = new BigArrays(recycler, breakerService)
    doReturn(Seq(Paths.get("/tmp/test")).toArray[Path]).when(indexStore).shardTranslogLocations(any[ShardId])

    val eft = spy(new EncryptedTranslog(shard, settings, mock[IndexSettingsService], bigArrays, indexStore, mock[NodeKeyProviderComponent]))
    val requester = spy(eft.getKeyProvider)
    doReturn(requester).when(eft).getKeyProvider

    val keyBytes = (1 to 32).map(_.toByte).toArray[Byte]
    val keySpec = new SecretKeySpec(keyBytes, "AES")
    doReturn(keySpec).when(requester).getKey(anyString())

    eft.newTranslog(12345L)

    val op = new Translog.Create("type1", "id1", Seq(0.toByte).toArray[Byte])
    verify(requester, times(0)).getKey(anyString())
    eft.add(op) shouldBe an[Translog.Location]
    verify(requester, times(1)).getKey(anyString())
  }

}
