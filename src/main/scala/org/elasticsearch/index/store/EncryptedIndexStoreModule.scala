package org.elasticsearch.index.store

import org.elasticsearch.common.inject.AbstractModule

/**
  * An org.elasticsearch.common.inject.AbstractModule that enables injection of EncryptedIndexStore.
  */
class EncryptedIndexStoreModule extends AbstractModule {
  //$COVERAGE-OFF$f
  override protected def configure(): Unit = {
    bind(classOf[IndexStore]).to(classOf[EncryptedIndexStore]).asEagerSingleton()
  }
  //$COVERAGE-ON$
}
