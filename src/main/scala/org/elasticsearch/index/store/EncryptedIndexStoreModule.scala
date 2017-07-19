/*
 * Copyright 2017 Workday, Inc.
 *
 * This software is available under the MIT license.
 * Please see the LICENSE.txt file in this project.
 */

package org.elasticsearch.index.store

import org.elasticsearch.common.inject.AbstractModule

/**
  * An org.elasticsearch.common.inject.AbstractModule that enables injection of EncryptedIndexStore.
  */
class EncryptedIndexStoreModule extends AbstractModule {
  //$COVERAGE-OFF$f
  /**
    * Sets the EncryptedIndexStore.
    */
  override protected def configure(): Unit = {
    bind(classOf[IndexStore]).to(classOf[EncryptedIndexStore]).asEagerSingleton()
  }
  //$COVERAGE-ON$
}
