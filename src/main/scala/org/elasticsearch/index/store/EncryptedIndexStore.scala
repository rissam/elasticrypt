/*
 * Copyright 2017 Workday, Inc.
 *
 * This software is available under the MIT license.
 * Please see the LICENSE.txt file in this project.
 */

package org.elasticsearch.index.store

import org.elasticsearch.client.Client
import org.elasticsearch.common.inject.Inject
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.env.NodeEnvironment
import org.elasticsearch.index.{Index, IndexService}
import org.elasticsearch.index.settings.IndexSettings
import org.elasticsearch.index.store.fs.FsIndexStore
import org.elasticsearch.indices.store.IndicesStore

/**
  * Extends org.elasticsearch.index.store.fs.FsIndexStore and overrides shardDirectory()
  * to return the class of EncryptedDirectoryService.
  */
class EncryptedIndexStore @Inject() (index: Index,
                                     @IndexSettings indexSettings: Settings,
                                     indexService: IndexService,
                                     indicesStore: IndicesStore,
                                     nodeEnv: NodeEnvironment,
                                     client: Client)
  extends FsIndexStore(index, indexSettings, indexService, indicesStore, nodeEnv) {

  override def shardDirectory(): Class[_ <: DirectoryService] = classOf[EncryptedDirectoryService]
}
