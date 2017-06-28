package org.elasticsearch.index.store

import org.elasticsearch.common.inject.AbstractModule

class EncryptedIndexStoreModule extends AbstractModule {
  //$COVERAGE-OFF$
  override protected def configure(): Unit = {
    bind(classOf[IndexStore]).to(classOf[EncryptedIndexStore]).asEagerSingleton()
  }
  //$COVERAGE-ON$
}
