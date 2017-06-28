package org.elasticsearch.index.store

import java.io.{File, IOException}

import org.apache.lucene.store.OutputStreamIndexOutput
import org.apache.lucene.util.AESWriter

object AESIndexOutput {
  private[store] val WRITE_CHUNK_SIZE: Int = 8192
}

/**
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

  @throws[IOException]
  override def close() {
    try
      onIndexOutputClosed(name)
    finally super.close()
  }
}
