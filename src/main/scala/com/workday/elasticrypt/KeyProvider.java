package com.workday.elasticrypt;

import javax.crypto.spec.SecretKeySpec;

public interface KeyProvider {
    SecretKeySpec getKey(String keyId);
}
