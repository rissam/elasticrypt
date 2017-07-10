/*
 * Copyright 2017 Workday, Inc.
 *
 * This software is available under the MIT license.
 * Please see the LICENSE.txt file in this project.
 */

package org.elasticsearch.plugins

import java.util
import java.util.Collections

import com.workday.elasticrypt.modules.EncryptedNodeModule
import org.elasticsearch.common.inject.Module

/**
  * Entry point for the plugin. Defines plugin name (Elasticrypt) and description.
  */
class ElasticryptPlugins extends AbstractPlugin {
  override def name(): String = "elasticrypt"

  override def description(): String = "An Elasticsearch plug-in that provides tenanted encryption at rest."

  override def modules(): util.Collection[Class[_ <: Module]] = Collections.singletonList(classOf[EncryptedNodeModule])
}
