# elasticrypt

This plugin attempts to provide tenanted encryption at rest in Elasticsearch 1.7. Elasticrypt currently runs with Scala 2.12.2 and Java 1.8.0_45 and is dependent on the version of Elasticsearch released on Sonatype.


## How To Install

Download the Elasticsearch 1.7.6 tar (Windows users should download the zip):
```
curl -L -O https://download.elastic.co/elasticsearch/elasticsearch/elasticsearch-1.7.6.tar.gz
```
Extract the tar file (Windows users should unzip the zip package):
```
tar -xvf elasticsearch-1.7.6.tar.gz
```
See https://www.elastic.co/guide/en/elasticsearch/reference/1.7/_installation.html for more information.

Download the jar file and copy it into the `lib` folder:
```
cp <path to jar> lib/
```

Clone this repository and generate a zip package by running this command in the elasticrypt directory:
```
sbt assembleZip
```

Install the elasticrypt plugin into Elasticsearch:
```
./bin/plugin --url file:///path/to/plugin --install plugin-name
```

Start up Elasticsearch:
```
./bin/elasticsearch
```


## Documentation

### Plugin

**ElasticryptPlugin.scala**
Entry point for the plugin. Defines plugin name and description.


### Low-Level Encrypted I/O

**AESReader.java**
Core decryption class that uses AES 256-bit ciphers to decrypt a given file. Adapted from https://issues.apache.org/jira/browse/LUCENE-2228

**AESWriter.java**
Core encryption class that uses AES 256-bit ciphers to encrypt a given file. Adapted from https://issues.apache.org/jira/browse/LUCENE-2228

**FileHeader.scala**
Interface for writing unencrypted metadata at the beginning of an encrypted file.

**HmacFileHeader.scala**
Implementation of the `FileHeader` interface that adds a MAC hash that is used to verify that the correct key is being used to decrypt a file.

**HmacUtil.scala**
Utility functions used in the `HmacFileHeader` class.

**EncryptedFileChannel.scala**
Extension of `java.nio.channels.FileChannel` that instantiates an `AESReader` and `AESWriter` to encrypt all reads and writes. Utilized in `EncryptedRafReference` and `EncryptedTranslogStream`.

**EncryptedRafReference.scala**
Extends `org.elasticsearch.index.translog.fs.RafReference` and overrides the `channel()` method to return an `EncryptedFileChannel`.


### Key Management

**KeyProviderFactory.scala**
A singleton object that acts as a factory for key providers. Includes the KeyProvider trait, an outline for a basic key provider.

**HardcodedKeyProvider.scala**
Dummy implementation of the `KeyProvider` trait as a proof of concept.

**EncryptedNodeModule.scala**
An `org.elasticsearch.common.inject.AbstractModule` that enables injection of `NodeKeyProviderComponent`.

**NodeKeyProviderComponent.scala**
Defines the `KeyProvider` to be used by this node.


### Translog Encryption

**EncryptedTranslog.scala**
Extends `org.elasticsearch.index.translog.fs.FsTranslog` and overrides `createRafReference()` and `translogStreamFor()` to return an `EncryptedRafReference` and `EncryptedTranslogStream` respectively. Both `createRafReference()` and `translogStreamFor()` are small methods that we added to `FsTranslog` so that they could be overriden here.

**EncryptedTranslogStream.scala**
Extension of `org.elasticsearch.index.translog.ChecksummedTranslogStream` that overrides `openInput()` to use a `ChannelInputStream` that wraps an  `EncryptedFileChannel`.


### Lucene Directory-Level Encryption

**EncryptedDirectory.scala**
This class extends `org.apache.lucene.store.NIOFSDirectory` and overrides `createOutput()` and `openInput()` to include encryption and decryption via `AESIndexOutput` and `AESIndexInput` respectively. Code is based on the existing implementation in `NIOFSDirectory`:
 - https://github.com/apache/lucene-solr/blob/master/lucene/core/src/java/org/apache/lucene/store/NIOFSDirectory.java
 - https://www.elastic.co/guide/en/elasticsearch/reference/1.7/index-modules-store.html#default_fs

 **AESIndexOutput.scala**
Class that extends `org.apache.lucene.store.OutputStreamIndexOutput`, using `AESChunkedOutputStreamBuilder` to build a `ChunkedOutputStream` that wraps an `AESWriterOutputStream`.

**AESIndexInput.scala**
Extension of `org.apache.lucene.store.BufferedIndexInput` that uses an instance of `AESReader` to perform reads on encrypted files. Utilized in `EncryptedDirectory` on `openInput()`.

**AESChunkedOutputStreamBuilder.scala**
Builder that creates a `ChunkedOutputStream` that wraps an `AESWriterOutputStream`.

**ChunkedOutputStream.scala**
This code is based on the existing implementation in `FSDirectory`:
 - https://github.com/apache/lucene-solr/blob/master/lucene/core/src/java/org/apache/lucene/store/FSDirectory.java#L412

**AESWriterOutputStream.scala**
Extension of `java.io.OutputStream` that wraps an AESWriter and routes all writes through it.

**EncryptedDirectoryService.scala**
Class that extends `org.elasticsearch.index.store.fs.FsDirectoryService` and overrides `newFSDirectory()` to return an `EncryptedDirectory`.

**EncryptedIndexStore.scala**
Extension of `org.elasticsearch.index.store.fs.FsIndexStore` that overrides `shardDirectory()` to return the class of `EncryptedDirectoryService`.

**EncryptedIndexStoreModule.scala**
An `org.elasticsearch.common.inject.AbstractModule` that enables injection of `EncryptedIndexStore`.


## Building, Testing & Contributing

This is an SBT-based project, so building and testing locally is done simply by using:
```
sbt clean coverage test
```
Generate the code coverage report with:
```
sbt coverageReport
```
This project aims for 100% test coverage, so any new code should be covered by test code.
Before contributing, please read the [Contributing Document](https://github.com/Workday/elasticrypt/blob/feature/readme/CONTRIBUTING). Create a separate branch for your patch and obtain a passing CI build before submitting a pull request.


## Authors

Please note that the commit history does not accurately reflect authorship, because much of the code was ported from an internal repository. Please view the [Contributors List](https://github.com/Workday/elasticrypt/blob/master/CONTRIBUTORS) for a full list of people who have contributed to the project.

## License

Copyright 2017 Workday, Inc.

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
