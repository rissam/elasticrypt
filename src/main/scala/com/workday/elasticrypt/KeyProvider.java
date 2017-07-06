package com.workday.elasticrypt;

import javax.crypto.spec.SecretKeySpec;

/**
  * An interface for fetching encryption keys.
  */
public interface KeyProvider {
    /**
      * Returns key.
      */
    SecretKeySpec getKey(String keyId);
//    SecretKeySpec getKey(String keyId, Boolean retry, int timeout);
}
