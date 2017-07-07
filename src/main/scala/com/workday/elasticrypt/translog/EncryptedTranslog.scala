/*
 * Copyright 2017 Workday, Inc.
 *
 * This software is available under the MIT license.
 * Please see the LICENSE.txt file in this project.
 */

package com.workday.elasticrypt.translog

import java.io.File

import com.workday.elasticrypt.KeyIdParser
import org.elasticsearch.common.inject.Inject
import org.elasticsearch.common.logging.ESLogger
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.util.BigArrays
import org.elasticsearch.index.settings.{IndexSettings, IndexSettingsService}
import org.elasticsearch.index.shard.ShardId
import org.elasticsearch.index.store.{IndexStore, NodeKeyProviderComponent}
import org.elasticsearch.index.translog.fs.FsTranslog
import org.elasticsearch.index.translog.{EncryptedTranslogStream, Translog, TranslogStream}

/**
  * Extends org.elasticsearch.index.translog.fs.FsTranslog and overrides createRafReference() and translogStreamFor()
  * to return an EncryptedRafReference and EncryptedTranslogStream respectively.
  * Both createRafReference() and translogStreamFor() are small methods that we added to FsTranslog
  * so that they could be overriden here.
  */
class EncryptedTranslog @Inject()(shardId: ShardId,
                                  @IndexSettings indexSettings: Settings,
                                  indexSettingsService: IndexSettingsService,
                                  bigArrays: BigArrays,
                                  indexStore: IndexStore,
                                  component: NodeKeyProviderComponent)
  extends FsTranslog(shardId, indexSettings, indexSettingsService, bigArrays, indexStore) {

  private[this] val pageSize = 64
  private[translog] val keyId = KeyIdParser.indexNameParser.parseKeyId(shardId.index)

  override def add(operation: Translog.Operation): Translog.Location = {
    component.keyProvider.getKey(keyId) // make sure that we can get the key for this tenant, don't retry so we can fail fast
    super.add(operation)
  }

  override protected[translog] def createRafReference(file: File, logger: ESLogger) = {
    new EncryptedRafReference(file, logger, pageSize, component.keyProvider, keyId)
  }

  override def translogStreamFor(translogFile: File): TranslogStream = {
    new EncryptedTranslogStream(pageSize, component.keyProvider, keyId)
  }

}
