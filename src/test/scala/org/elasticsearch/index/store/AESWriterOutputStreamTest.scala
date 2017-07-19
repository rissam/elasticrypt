package org.elasticsearch.index.store

import org.apache.lucene.util.AESWriter

import org.mockito.Mockito.{verify, times}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}

class AESWriterOutputStreamTest extends FlatSpec with Matchers with MockitoSugar {

  behavior of "#write"
  it should "write single int" in {
    val writer = mock[AESWriter]
    val stream = new AESWriterOutputStream(writer: AESWriter)

    stream.write(123)
    verify(writer, times(1)).write(Seq(123.toByte).toArray[Byte], 0, 1)
  }

}
