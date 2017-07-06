package org.elasticsearch.index.store

import com.workday.elasticrypt.{HardcodedKeyProvider, KeyProvider}
import org.elasticsearch.common.component.AbstractComponent
import org.elasticsearch.common.inject.Inject
import org.elasticsearch.common.settings.Settings

/**
  * Defines the KeyProvider to be used by this node.
  */
class NodeKeyProviderComponent @Inject()(settings: Settings) extends AbstractComponent(settings) {
  // Override this to customize file header
  val keyProvider: KeyProvider = new HardcodedKeyProvider
}
