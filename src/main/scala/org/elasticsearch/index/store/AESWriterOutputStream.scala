package org.elasticsearch.index.store

import java.io.OutputStream

import org.apache.lucene.util.AESWriter

/** Extension of java.io.OutputStream that wraps an AESWriter and routes all writes through it. */
class AESWriterOutputStream(writer: AESWriter) extends OutputStream {
  // The Javadoc explains that a byte is always passed here and the Int is just some kind of convenience
  override def write(b: Int): Unit = write(Array[Byte](b.toByte), 0, 1)

  override def write(b: Array[Byte], off: Int, len: Int): Unit = {
    writer.write(b, off, len)
  }

  override def flush(): Unit = writer.flush()

  override def close(): Unit = writer.close()
}
