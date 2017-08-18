package com.workday.elasticrypt

import java.io.{BufferedReader, InputStreamReader}
import java.net.URI
import javax.crypto.spec.SecretKeySpec

import com.google.gson.Gson
import org.apache.http.client.methods.{CloseableHttpResponse, HttpGet}
import org.apache.http.client.utils.URIBuilder
import org.apache.http.impl.client.HttpClients

/**
  * Extends the KeyProvider trait and sends HTTP requests via a client to retrieve the key.
  * @param uri url to connect with
  */
class HttpKeyProvider(uri: URI) extends KeyProvider {
  private[this] val httpclient = HttpClients.createDefault
  val ALGORITHM_AES = "AES"

  /**
    * Returns the key for the given index name.
    * @param indexName name of the index used to retrieve key
    */
  def getKey(indexName: String): SecretKeySpec = {
    val uriWithParam: URI = new URIBuilder(uri).addParameter("indexName", indexName).build()
    val httpGet = new HttpGet(uriWithParam)
    val res: CloseableHttpResponse = httpclient.execute(httpGet)
    try {
      // Retrieve keys from response
      val content = new BufferedReader(new InputStreamReader(res.getEntity().getContent))
      val jsonString = Iterator.continually(content.readLine).takeWhile(_ != null).mkString
      val jsonMap = new Gson().fromJson(jsonString, classOf[java.util.HashMap[String, String]])
      val key = jsonMap.get("key").getBytes.slice(0, 32)
      val keyPadded = key ++ Array.fill[Byte](32 - key.length)(1)
      new SecretKeySpec(keyPadded, ALGORITHM_AES)
    } finally {
      res.close()
    }
  }

  /**
    * Returns the key for the given index name, similar to the getKey above.
    * Added the ability to attempt getting the key again in case of failure.
    * @param indexName name of index used to retrieve key
    * @param retry true if want to try getting the key again upon failure; false otherwise
    * @param timeout how long (in milliseconds) to keep attempting to get the key
    * @param throttle how long to wait (in milliseconds) before trying again within the retry period
    */
  def getKey(indexName: String, retry: Boolean = true, timeout: Long, throttle: Long) = {
    val time = if (retry) timeout else 0
    this.retry(System.currentTimeMillis() + time, throttle, indexName)
  }

  /**
    * Recursive function that attempts to fetch the key until time is up.
    * @param timeoutMillis how long (in milliseconds) to keep attempting to get the key
    * @param period how long to wait (in milliseconds) before trying again within the retry period
    * @param indexName name of index used to retrieve key
    */
  @annotation.tailrec
  private[this] def retry(timeoutMillis: Long, period: Long, indexName: String): SecretKeySpec = {
    val key = getKey(indexName)
    key match {
      case x: SecretKeySpec => x
      case _ if System.currentTimeMillis() < timeoutMillis => Thread.sleep(period)
        retry(timeoutMillis, period, indexName)
    }
  }
}