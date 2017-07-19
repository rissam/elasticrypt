/*
 * Copyright 2017 Workday, Inc.
 *
 * This software is available under the MIT license.
 * Please see the LICENSE.txt file in this project.
 */

package com.workday.elasticrypt.translog

import java.io.{File, IOException}
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicInteger

import com.workday.elasticrypt.KeyProvider
import org.elasticsearch.common.logging.ESLogger
import org.elasticsearch.index.translog.fs.RafReference
import org.elasticsearch.index.translog.{EncryptedTranslogStream, TranslogStream}

/**
  * We extend ES's RafReference (org.elasticsearch.index.translog.fs.RafReference) so that we do not need to copy
  * even more of ES's code into our own codebase. Overrides the channel() method to return an EncryptedFileChannel.
  */
class EncryptedRafReference(file: File, logger: ESLogger, pageSize: Int, keyProvider: KeyProvider, indexName: String)
  extends RafReference(file, logger) {
  private[this] val encryptedFileChannel = new EncryptedFileChannel(file.getName, raf(), pageSize, keyProvider, indexName)

  // Shadow the RafReference refCount because we need to override decreaseRefCount()
  private[translog] val refCount: AtomicInteger = new AtomicInteger

  refCount.incrementAndGet()

  /**
    * Return EncryptedFileChannel.
    */
  override def channel(): FileChannel = {
    this.encryptedFileChannel
  }

  /**
    * Overriding this method only necessary because we had to override decreaseRefCount() and refCount.
    * @return true if refCount + 1 is greater than 1, false otherwise
    */
  override def increaseRefCount(): Boolean = refCount.incrementAndGet > 1

  /**
    * We need to override this so that we can intercept raf.close() to first flush the AESWriter.
    * @param deleteFile true if want to delete the file
    */
  override def decreaseRefCount(deleteFile: Boolean): Unit = {
    val refsCount = refCount.decrementAndGet()
    if (refsCount <= 0) try {
      logger.trace("closing RAF reference delete: {} length: {} file: {}",
        deleteFile.toString, raf.length.toString, file.getAbsolutePath)
      // below will call EncryptedFileChannel.implCloseChannel(), which will call AESWriter.close(), which will
      // flush and call raf.close()
      channel().close()
      if (deleteFile) {
        file.delete()
      }
    } catch {
      case e: IOException => {
        logger.debug("failed to close RAF file", e)
        // ignore
      }
    }
  }

  /**
    * Creates and returns EncryptedTranslogStream.
    */
  @Override
  def translogStreamFor: TranslogStream = {
    new EncryptedTranslogStream(pageSize, keyProvider, indexName)
  }
}
