/*
 * Copyright 2017 Workday, Inc.
 *
 * This software is available under the MIT license.
 * Please see the LICENSE.txt file in this project.
 */

package com.workday.elasticrypt

import org.elasticsearch.cluster.metadata.AliasMetaData
import org.elasticsearch.index.Index

/**
  * Contains functions to retrieve the key ID.
  */
object KeyIdParser {
  val indexNameParser = new KeyIdParser[Index]((index: Index) => {
    val name = index.getName
    name
  })
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
