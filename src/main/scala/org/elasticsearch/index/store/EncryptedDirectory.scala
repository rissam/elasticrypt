/*
 * Copyright 2017 Workday, Inc.
 *
 * This software is available under the MIT license.
 * Please see the LICENSE.txt file in this project.
 */

package org.elasticsearch.index.store

// scalastyle:off underscore.import
import java.io._
import com.workday.elasticrypt.KeyProvider
import org.apache.lucene.store._
import org.apache.lucene.util.{AESReader, AESWriter, FileHeader, HmacFileHeader}
// scalastyle:on underscore.import

import org.apache.lucene.codecs.lucene46.Lucene46SegmentInfoFormat
import org.apache.lucene.index.IndexFileNames
import org.elasticsearch.client.Client
import org.elasticsearch.common.logging.{ESLogger, ESLoggerFactory}
import org.elasticsearch.index.shard.ShardId

/**
  * This class extends org.apache.lucene.store.NIOFSDirectory and overrides createOutput() and openInput()
  * to include encryption and decryption via AESIndexOutput and AESIndexInput respectively. Code is based on the existing implementation in NIOFSDirectory:
  * Much of this code is based on the existing implementation in NIOFSDirectory.
  *
  * https://www.elastic.co/guide/en/elasticsearch/reference/1.7/index-modules-store.html#default_fs
  * https://github.com/apache/lucene-solr/blob/master/lucene/core/src/java/org/apache/lucene/store/NIOFSDirectory.java
  *
  * @param path File to use
  * @param lockFactory the lock factory to use
  * @param shardId the ID of the shard used to get the index name
  * @param esClient Elasticsearch client
  * @param component NodeKeyProviderComponent to provide the key provider
  */
class EncryptedDirectory(path: File, lockFactory: LockFactory, shardId: ShardId, esClient: Client, component: NodeKeyProviderComponent)
  extends NIOFSDirectory(path, lockFactory) {
  private[this] val logger: ESLogger = ESLoggerFactory.getRootLogger

  private[this] val pageSize = 64

  private[this] val indexName = shardId.getIndex

  /**
    * Returns a FileHeader. This method is currently hardcoded to return a HmacFileHeader.
    * The user can rewrite or override this method to customize the FileHeader.
    * @param raf file pointer
    */
  protected[this] def buildFileHeader(raf: RandomAccessFile): FileHeader = new HmacFileHeader(raf, component.keyProvider, indexName)

  /**
    * Creates and returns an IndexOutput for the file.
    * @param name file name
    * @param context object whose information is used to create a new IndexOutput
    */
  @throws[IOException]
  override def createOutput(name: String, context: IOContext): IndexOutput = {
    if (isSegmentMetadataFile(name)) {
      // ES reads segment metadata without going through this plugin, so they need to be unencrypted
      super.createOutput(name, context)
    } else {
      ensureOpen()
      ensureCanWrite(name)

      new AESIndexOutput(directory, name, pageSize,
        (name) => { onIndexOutputClosed(name) },
        (directory: File, name: String, pageSize: Int) => {
          val path = new File(directory, name)
          val writerRaf = new RandomAccessFile(path, "rw")
          val writerFileHeader = buildFileHeader(writerRaf)
          createAESWriter(path, writerRaf, pageSize, component.keyProvider, writerFileHeader)
        })
    }
  }

  /**
    * Creates and returns an IndexInput for the file.
    * @param name file name
    * @param context object whose information is used to create a new IndexInput
    */
  @throws[IOException]
  override def openInput(name: String, context: IOContext): IndexInput = {
    if (isSegmentMetadataFile(name)) {
      // Segment metadata is not encrypted
      super.openInput(name, context)
    } else {
      val path = new File(getDirectory, name) // getDirectory calls ensureOpen()
      val readerRaf = new RandomAccessFile(path, "r")
      val readerFileHeader = buildFileHeader(readerRaf)

      val reader = createAESReader(path, readerRaf, pageSize, component.keyProvider, readerFileHeader)
      new AESIndexInput("AESIndexInput(path=\"" + path + "\")", reader, context)
    }
  }

  /**
    * Checks for the metadata file.
    * @param fileName name of the file
    * @return true if the file exists; false otherwise
    */
  private def isSegmentMetadataFile(fileName: String): Boolean = {
    fileName == IndexFileNames.SEGMENTS_GEN ||
      fileName.startsWith(IndexFileNames.SEGMENTS + "_") ||
      fileName.contains("." + IndexFileNames.SEGMENTS + "_") ||
      fileName.endsWith("." + Lucene46SegmentInfoFormat.SI_EXTENSION)
  }

  /**
    * Creates and returns an AESWriter.
    * @param path File to use
    * @param raf file to create
    * @param pageSize number of 16-byte blocks per page
    * @param keyProvider encryption key information getter
    * @param fileHeader creates the file header
    */
  protected[store] def createAESWriter(path: File, raf: RandomAccessFile, pageSize: Int, keyProvider: KeyProvider, fileHeader: FileHeader) = {
    new AESWriter(path.getName, raf, pageSize, keyProvider, indexName, fileHeader)
  }

  /**
    * Creates and returns an AESReader.
    * @param path File to use
    * @param raf file to create
    * @param pageSize number of 16-byte blocks per page
    * @param keyProvider encryption key information getter
    * @param fileHeader creates the file header
    */
  protected[store] def createAESReader(path: File, raf: RandomAccessFile, pageSize: Int, keyProvider: KeyProvider, fileHeader: FileHeader) = {
    new AESReader(path.getName, raf, pageSize, keyProvider, indexName, fileHeader)
  }
}
