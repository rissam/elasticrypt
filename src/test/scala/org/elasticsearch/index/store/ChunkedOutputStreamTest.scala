package org.elasticsearch.index.store

import java.io.OutputStream

import org.mockito.Matchers.{any, anyInt}
import org.mockito.Mockito.{times, verify}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}

class ChunkedOutputStreamTest extends FlatSpec with Matchers with MockitoSugar {

  behavior of "#write"
  it should "does write less than CHUNK_SIZE bytes" in {
    val os = mock[OutputStream]
    val bytes = Array[Byte](1, 2, 3)

    val cos = new ChunkedOutputStream(os, 10)
    cos.write(bytes, 0, 3)

    verify(os, times(1)).write(any[Array[Byte]], anyInt, anyInt)
  }

  it should "does not write more than CHUNK_SIZE bytes at once" in {
    val os = mock[OutputStream]
    val bytes = Array[Byte](1, 2, 3)

    val cos = new ChunkedOutputStream(os, 2)
    cos.write(bytes, 0, 3)

    verify(os, times(1)).write(bytes, 0, 2)
    verify(os, times(1)).write(bytes, 2, 1)
  }

}
