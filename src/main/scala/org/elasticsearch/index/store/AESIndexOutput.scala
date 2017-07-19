/*
 * Copyright 2017 Workday, Inc.
 *
 * This software is available under the MIT license.
 * Please see the LICENSE.txt file in this project.
 */

package org.elasticsearch.index.store

import java.io.{File, IOException}

import org.apache.lucene.store.OutputStreamIndexOutput
import org.apache.lucene.util.AESWriter

object AESIndexOutput {
  private[store] val WRITE_CHUNK_SIZE: Int = 8192
}

/**
  * Extends org.apache.lucene.store.OutputStreamIndexOutput,
  * using AESChunkedOutputStreamBuilder to build a ChunkedOutputStream that wraps an AESWriterOutputStream.
  *
  * WRITE_CHUNK_SIZE Taken from FSDirectory.FSIndexOutput
  *
  * The maximum chunk size is 8192 bytes, because {@link FileOutputStream} mallocs
  * a native buffer outside of stack if the write buffer size is larger.
  */
final private[store] class AESIndexOutput(directory: File, val name: String,
                                          pageSize: Int,
                                          onIndexOutputClosed: (String) => Unit,
                                          createAESWriter: (File, String, Int) => AESWriter)
  extends OutputStreamIndexOutput(
    AESChunkedOutputStreamBuilder.build(directory, name, pageSize, createAESWriter),
    AESIndexOutput.WRITE_CHUNK_SIZE) {

  /**
    * TODO
    */
  @throws[IOException]
  override def close() {
    try
      onIndexOutputClosed(name)
    finally super.close()
  }
}
