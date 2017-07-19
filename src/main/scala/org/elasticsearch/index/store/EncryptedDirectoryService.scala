/*
 * Copyright 2017 Workday, Inc.
 *
 * This software is available under the MIT license.
 * Please see the LICENSE.txt file in this project.
 */

package org.elasticsearch.index.store

import java.io.File

import org.apache.lucene.store.{Directory, LockFactory}
import org.elasticsearch.common.inject.Inject
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.client.Client
import org.elasticsearch.index.settings.IndexSettings
import org.elasticsearch.index.shard.ShardId
import org.elasticsearch.index.store.fs.FsDirectoryService

/**
  * Extends org.elasticsearch.index.store.fs.FsDirectoryService and overrides newFSDirectory().
  * Returns an EncryptedDirectory.
  */
class EncryptedDirectoryService @Inject() (shardId: ShardId,
                                           @IndexSettings indexSettings: Settings,
                                           indexStore: EncryptedIndexStore,
                                           client: Client,
                                           component: NodeKeyProviderComponent)
  extends FsDirectoryService(shardId, indexSettings, indexStore) {

  /**
    * Returns an EncryptedDirectory.
    * @param location path of the directory
    * @param lockFactory the lock factory to use, or null for the default
    */
  override def newFSDirectory(location: File, lockFactory: LockFactory): Directory = {
    new EncryptedDirectory(location, lockFactory, shardId, client, component)
  }
}
