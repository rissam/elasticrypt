/*
 * Copyright 2017 Workday, Inc.
 *
 * This software is available under the MIT license.
 * Please see the LICENSE.txt file in this project.
 */

package org.elasticsearch.index.store

import java.io.OutputStream

import org.apache.lucene.util.AESWriter

/** Extension of java.io.OutputStream that wraps an AESWriter and routes all writes through it. */
class AESWriterOutputStream(writer: AESWriter) extends OutputStream {

  /**
    * Writes data to file beginning from the start.
    * Javadoc explains that a byte is always passed here and the Int is just some kind of convenience.
    * @param b array of bytes to write
    */
  override def write(b: Int): Unit = write(Array[Byte](b.toByte), 0, 1)

  /**
    * Writes the given data to file.
    * @param b   array of bytes to write
    * @param off offset in b to start
    * @param len number of bytes to write
    */
  override def write(b: Array[Byte], off: Int, len: Int): Unit = {
    writer.write(b, off, len)
  }

  /**
    * Writes any data in the buffer to disk with any additional padding needed.
    */
  override def flush(): Unit = writer.flush()

  /**
    * Encrypts and flushes all remaining data from the buffer cache to disk, pads the file,
    * and then closes the underlying file stream.
    */
  override def close(): Unit = writer.close()
}
