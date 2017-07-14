package org.elasticsearch.index.store

import java.io.File

import org.apache.lucene.util.AESWriter
import org.mockito.Matchers.{any, anyInt}
import org.mockito.Mockito.{times, verify}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}

class AESIndexOutputTest extends FlatSpec with Matchers with MockitoSugar {

  behavior of "#write"
  it should "write data into writer" in {
    val dir = mock[File]
    val writer = mock[AESWriter]

    var counter = 0
    def closeHandler(name: String) = { counter += 1 }
    def createAESWriter(dir: File, name: String, pageSize: Int) = { writer }

    val output = new AESIndexOutput(dir, "test", 100, closeHandler, createAESWriter)

    val bytes = (1 to 10000).map(_.toByte).toArray[Byte] // We need more data than 8192 byte write buffer
    output.writeBytes(bytes, 0, 9000)
    verify(writer, times(2)).write(any[Array[Byte]], anyInt, anyInt)
  }

  behavior of "#flush"
  it should "flush the writer" in {
    val dir = mock[File]
    val writer = mock[AESWriter]

    var counter = 0
    def closeHandler(name: String) = { counter += 1 }
    def createAESWriter(dir: File, name: String, pageSize: Int) = { writer }

    val output = new AESIndexOutput(dir, "test", 100, closeHandler, createAESWriter)

    output.flush()
    verify(writer, times(1)).flush()
  }

  behavior of "#close"
  it should "call the handler" in {
    val dir = mock[File]
    val writer = mock[AESWriter]

    var counter = 0
    def closeHandler(name: String) = { counter += 1 }
    def createAESWriter(dir: File, name: String, pageSize: Int) = { writer }

    val output = new AESIndexOutput(dir, "test", 100, closeHandler, createAESWriter)

    counter shouldBe 0
    output.close()
    counter shouldBe 1
  }

}
