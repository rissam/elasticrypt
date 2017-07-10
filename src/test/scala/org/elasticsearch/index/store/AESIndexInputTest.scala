package org.elasticsearch.index.store

import java.io.IOException

import org.apache.lucene.util.AESReader
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}
import org.mockito.Mockito.{times, verify, when}
import org.mockito.Matchers.{any, anyInt}

class AESIndexInputTest extends FlatSpec with Matchers with MockitoSugar {

  behavior of "#close"
  it should "close if not a clone" in {
    val reader = mock[AESReader]
    val input = new AESIndexInput("test", reader, 0, 10000, 100)
    input.isClone = false

    input.close()
    verify(reader, times(1)).close()
  }
  it should "not close if a clone" in {
    val reader = mock[AESReader]
    val input = new AESIndexInput("test", reader, 0, 10000, 100)
    input.isClone = true

    input.close()
    verify(reader, times(0)).close()
  }

  behavior of "#clone"
  it should "clone the object" in {
    val reader = mock[AESReader]
    val input = new AESIndexInput("test", reader, 0, 10000, 100)
    val clone = input.clone

    clone.isClone shouldBe true
    clone.length() shouldBe input.length()
  }

  behavior of "#slice"
  it should "return sliced AESIndexInput" in {
    val reader = mock[AESReader]
    val input = new AESIndexInput("test", reader, 0, 10000, 100)
    val slice = input.slice("test", 100, 200)

    slice.length() should not be input.length()
  }

  it should "throw an IllegalArgumentException if offset < 0" in {
    val reader = mock[AESReader]
    val input = new AESIndexInput("test", reader, 0, 10000, 100)

    an[IllegalArgumentException] should be thrownBy {
      input.slice("test", -1, 200)
    }
  }

  it should "throw an IllegalArgumentException if length < 0" in {
    val reader = mock[AESReader]
    val input = new AESIndexInput("test", reader, 0, 10000, 100)

    an[IllegalArgumentException] should be thrownBy {
      input.slice("test", 100, -1)
    }
  }

  it should "throw an IllegalArgumentException if offset + length > file length" in {
    val reader = mock[AESReader]
    val input = new AESIndexInput("test", reader, 0, 10000, 100)

    an[IllegalArgumentException] should be thrownBy {
      input.slice("test", 5000, 6000)
    }
  }

  behavior of "#length"
  it should "return file length" in {
    val reader = mock[AESReader]
    val input = new AESIndexInput("test", reader, 0, 10000, 100)

    input.length() shouldBe 10000
  }

  behavior of "#readInternal"
  it should "read from the reader w/o offset" in {
    val reader = mock[AESReader]
    val input = new AESIndexInput("test", reader, 0, 10000, 100)

    when(reader.read(any[Array[Byte]], anyInt, anyInt)).thenReturn(100)

    input.readInt()
    verify(reader, times(0)).seek(any[Long])
    verify(reader, times(1)).read(any[Array[Byte]], anyInt, anyInt)
  }
  it should "read from the reader with offset" in {
    val reader = mock[AESReader]
    val input = new AESIndexInput("test", reader, 0, 10000, 100)
    val slice = input.slice("test", 100, 400)

    when(reader.read(any[Array[Byte]], anyInt, anyInt)).thenReturn(100)

    val bytes = new Array[Byte](100)
    slice.readBytes(bytes, 100, 100)
    verify(reader, times(1)).seek(any[Long])
    verify(reader, times(1)).read(any[Array[Byte]], anyInt, anyInt)
  }
  it should "read in chunks" in {
    val reader = mock[AESReader]
    val input = new AESIndexInput("test", reader, 0, 10000, 100)
    val slice = input.slice("test", 100, 400)

    when(reader.read(any[Array[Byte]], anyInt, anyInt)).thenReturn(100)

    val bytes = new Array[Byte](200)
    slice.readBytes(bytes, 100, 200)
    verify(reader, times(1)).seek(any[Long])
    verify(reader, times(2)).read(any[Array[Byte]], anyInt, anyInt)
  }
  it should "throw IOException when reading past EOF" in {
    val reader = mock[AESReader]
    val input = new AESIndexInput("test", reader, 0, 10000, 100)

    when(reader.read(any[Array[Byte]], anyInt, anyInt)).thenReturn(-1)

    val bytes = new Array[Byte](200)
    an[IOException] should be thrownBy {
      input.readBytes(bytes, 100, 200)
    }
  }
  it should "rethrow IOException on read" in {
    val reader = mock[AESReader]
    val input = new AESIndexInput("test", reader, 0, 10000, 100)

    when(reader.read(any[Array[Byte]], anyInt, anyInt)).thenThrow(new IOException("oops!"))

    val bytes = new Array[Byte](200)
    an[IOException] should be thrownBy {
      input.readBytes(bytes, 100, 200)
    }
  }
  it should "rethrow other exceptions as RuntimeException on read" in {
    val reader = mock[AESReader]
    val input = new AESIndexInput("test", reader, 0, 10000, 100)

    when(reader.read(any[Array[Byte]], anyInt, anyInt)).thenThrow(new IllegalArgumentException("oops!"))

    val bytes = new Array[Byte](200)
    an[RuntimeException] should be thrownBy {
      input.readBytes(bytes, 100, 200)
    }
  }

  behavior of "#seekInternal"
  it should "noop" in {
    val reader = mock[AESReader]
    val input = new AESIndexInput("test", reader, 0, 10000, 100)

    input.seek(0)
    verify(reader, times(0)).seek(any[Long])
  }

}
