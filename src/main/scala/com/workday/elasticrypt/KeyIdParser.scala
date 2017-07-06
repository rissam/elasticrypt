package com.workday.elasticrypt

import org.elasticsearch.cluster.metadata.AliasMetaData
import org.elasticsearch.index.Index

/**
  * Contains functions to retrieve the key ID.
  */
object KeyIdParser {
  val indexNameParser = new KeyIdParser[Index]((index: Index) => index.getName.split("@").head)
  val aliasNameParser = new KeyIdParser[AliasMetaData]((aliasMetaData: AliasMetaData) => aliasMetaData.alias.split("@").head)
}

/**
  * Utilities for parsing a key ID from Elasticsearch metadata such as an index name or alias name.
  */
class KeyIdParser[T](parseFn: T => String) {
  /**
    * Returns the key ID.
    */
  def parseKeyId(source: T): String = parseFn(source)
}
