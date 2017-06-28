package com.workday.elasticrypt.modules

import org.elasticsearch.common.inject.{AbstractModule, Singleton}
import org.elasticsearch.index.store.NodeKeyProviderComponent

class EncryptedNodeModule extends AbstractModule {
  //$COVERAGE-OFF$
  override protected def configure(): Unit = {
    bind(classOf[NodeKeyProviderComponent]).in(classOf[Singleton])
  }
  //$COVERAGE-ON$

}
