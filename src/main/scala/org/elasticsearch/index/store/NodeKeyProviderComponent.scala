package org.elasticsearch.index.store

import com.workday.elasticrypt.{HardcodedKeyProvider, KeyProvider}
import org.elasticsearch.common.component.AbstractComponent
import org.elasticsearch.common.inject.Inject
import org.elasticsearch.common.settings.Settings

class NodeKeyProviderComponent @Inject()(settings: Settings) extends AbstractComponent(settings) {
  val keyProvider: KeyProvider = new HardcodedKeyProvider
}
