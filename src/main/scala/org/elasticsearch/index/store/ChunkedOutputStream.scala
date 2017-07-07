/*
 * Copyright 2017 Workday, Inc.
 *
 * This software is available under the MIT license.
 * Please see the LICENSE.txt file in this project.
 */

package org.elasticsearch.index.store

import java.io.{FilterOutputStream, OutputStream}

/**
  * Much of this code is based on the existing implementation in FSDirectory
  *
  * https://www.elastic.co/guide/en/elasticsearch/reference/1.7/index-modules.html
  * https://github.com/apache/lucene-solr/blob/master/lucene/core/src/java/org/apache/lucene/store/FSDirectory.java#L412
  */

// This logic is factored out from the AESDirectory patch
private[store] class ChunkedOutputStream(os: OutputStream, chunkSize: Int) extends FilterOutputStream(os) {
  // This implementation ensures, that we never write more than CHUNK_SIZE bytes:
  // throws IOException

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
