package com.workday.elasticrypt

import org.elasticsearch.cluster.metadata.AliasMetaData
import org.elasticsearch.index.Index

object KeyIdParser {
  val indexNameParser = new KeyIdParser[Index]((index: Index) => index.getName.split("@").head)
  val aliasNameParser = new KeyIdParser[AliasMetaData]((aliasMetaData: AliasMetaData) => aliasMetaData.alias.split("@").head)
}

class KeyIdParser[T](parseFn: T => String) {
  def parseKeyId(source: T): String = parseFn(source)
}
