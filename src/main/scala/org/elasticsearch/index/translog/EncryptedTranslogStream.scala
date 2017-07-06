package org.elasticsearch.index.translog

import java.io.{EOFException, File, IOException}
import java.nio.channels.FileChannel

import com.workday.elasticrypt.KeyProvider
import com.workday.elasticrypt.translog.EncryptedFileChannel
import org.apache.lucene.util.IOUtils
import org.elasticsearch.common.io.stream.{InputStreamStreamInput, StreamInput}
import org.elasticsearch.common.logging.{ESLogger, ESLoggerFactory}
//scalastyle:off
import sun.nio.ch.ChannelInputStream
//scalastyle:on

/**
  * Extension of org.elasticsearch.index.translog.ChecksummedTranslogStream that overrides openInput()
  * to use a ChannelInputStream that wraps an EncryptedFileChannel. This class must be located in
  * org.elasticsearch.index.translog in order to access the no-arg constructor of ChecksummedTranslogStream.
  */
class EncryptedTranslogStream(pageSize: Int, keyProvider: KeyProvider, keyId: String) extends ChecksummedTranslogStream {

  private[this] val logger: ESLogger = ESLoggerFactory.getRootLogger

  /**
    * Override this method to remove the translog header.
    */
  override def writeHeader(channel: FileChannel): Int = {
    0
  }

  @throws[EOFException]
  @throws[IOException]
  private[translog] def createInputStreamStreamInput(encryptedFileInputStream: ChannelInputStream) = {
    new InputStreamStreamInput(encryptedFileInputStream)
  }

  /**
    * Copied from ChecksummedTranslogStream but modified to use EncryptedFileChannel (and removed CodecUtil.checkHeader).
    */
  override def openInput(translogFile: File): StreamInput = {
    val encryptedFileInputStream = new ChannelInputStream(new EncryptedFileChannel(translogFile, pageSize, keyProvider, keyId))
    var success = false
    try {
      val in = createInputStreamStreamInput(encryptedFileInputStream)
      success = true
      in
    } catch {
      case e: EOFException => {
        throw new TruncatedTranslogException("translog header truncated", e)
      }
      case e: IOException => {
        throw new TranslogCorruptedException("translog header corrupted", e)
      }
    } finally {
      if (!success) IOUtils.closeWhileHandlingException(encryptedFileInputStream)
    }
  }
}
