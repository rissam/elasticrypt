package org.elasticsearch.plugins

import java.util.Collections

import com.workday.elasticrypt.modules.EncryptedNodeModule
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.mockito.MockitoSugar

class ElasticryptPluginsTest extends FlatSpec with Matchers with MockitoSugar {
  behavior of "#name"
  it should "return plugin name" in {
    val plugin = new ElasticryptPlugins()
    plugin.name() shouldBe "elasticrypt"
  }

  behavior of "#description"
  it should "return plugin description" in {
    val plugin = new ElasticryptPlugins()
    plugin.description() shouldBe "An Elasticsearch plug-in that provides tenanted encryption at rest."
  }

  behavior of "#modules"
  it should "return singletonList" in {
    val plugin = new ElasticryptPlugins()
    plugin.modules() shouldBe Collections.singletonList(classOf[EncryptedNodeModule])
  }

}
