/*
 * Copyright 2017 Workday, Inc.
 *
 * This software is available under the MIT license.
 * Please see the LICENSE.txt file in this project.
 */

package com.workday.elasticrypt.modules

import org.elasticsearch.common.inject.{AbstractModule, Singleton}
import org.elasticsearch.index.store.NodeKeyProviderComponent

/**
  * An org.elasticsearch.common.inject.AbstractModule that enables injection of NodeKeyProviderComponent.
  */
class EncryptedNodeModule extends AbstractModule {

  /**
    * Sets the NodeKeyProviderComponent, which allows us to fetch keys.
    */
  //$COVERAGE-OFF$
  override protected def configure(): Unit = {
    bind(classOf[NodeKeyProviderComponent]).in(classOf[Singleton])
  }
  //$COVERAGE-ON$

}
