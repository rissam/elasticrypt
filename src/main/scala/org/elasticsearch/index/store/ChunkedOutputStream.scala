/*
 * Copyright 2017 Workday, Inc.
 *
 * This software is available under the MIT license.
 * Please see the LICENSE.txt file in this project.
 */

package org.elasticsearch.index.store

import java.io.{FilterOutputStream, OutputStream}

/**
  * Much of this code is based on the existing implementation in FSDirectory.
  * This logic is factored out from the AESDirectory patch.
  *
  * https://www.elastic.co/guide/en/elasticsearch/reference/1.7/index-modules.html
  * https://github.com/apache/lucene-solr/blob/master/lucene/core/src/java/org/apache/lucene/store/FSDirectory.java#L412
  *
  * @param os output stream of bytes
  * @param chunkSize maximum number of bytes we want to write each chunk
  */
private[store] class ChunkedOutputStream(os: OutputStream, chunkSize: Int) extends FilterOutputStream(os) {

  /**
    * Writes chunk by chunk. Ensures that we never write more than CHUNK_SIZE bytes.
    * @throws IndexOutOfBoundsException if offset and length are not reasonable
    * @throws NullPointerException if b is null
    * @param b array of bytes to write
    * @param offset offset in the data
    * @param length number of bytes to write
    */
  override def write(b: Array[Byte], offset: Int, length: Int) {
    var l = length
    var o = offset
    while (l > 0) {
      val chunk: Int = Math.min(l, chunkSize)
      out.write(b, o, chunk)
      l = l - chunk
      o = o + chunk
    }
  }
}
