package org.elasticsearch.index.store

import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito.{when,verify,times}
import org.elasticsearch.common.inject.Binder
import org.elasticsearch.common.inject.binder.{AnnotatedBindingBuilder, ScopedBindingBuilder}

class EncryptedIndexStoreModuleTest extends FlatSpec with Matchers with MockitoSugar {

  behavior of "#configure"
  it should "not fail" in {
    val module = new EncryptedIndexStoreModule()
    val binder = mock[Binder]
    val annotatedBinder = mock[AnnotatedBindingBuilder[IndexStore]]
    val scopedBuilder = mock[ScopedBindingBuilder]

    when(binder.bind(classOf[IndexStore])).thenReturn(annotatedBinder)
    when(annotatedBinder.to(classOf[EncryptedIndexStore])).thenReturn(scopedBuilder)

    module.configure(binder)

    verify(scopedBuilder, times(1)).asEagerSingleton()
  }

}
