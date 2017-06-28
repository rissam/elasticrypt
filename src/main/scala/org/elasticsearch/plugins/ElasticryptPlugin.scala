package org.elasticsearch.plugins

import java.util
import java.util.Collections

import com.workday.elasticrypt.modules.EncryptedNodeModule
import org.elasticsearch.common.inject.Module

class ElasticryptPlugin extends AbstractPlugin {
  override def name(): String = "elasticrypt"

  override def description(): String = "An Elasticsearch plug-in that provides tenanted encryption at rest."

  override def modules(): util.Collection[Class[_ <: Module]] = Collections.singletonList(classOf[EncryptedNodeModule])
}
