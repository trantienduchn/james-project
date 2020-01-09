/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.backends.cassandra.encryption;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.subtle.AesGcmJce;

public class AesEncryptionCodec implements EncryptionCodec {
    private static final byte[] EMPTY_ASSOCIATED_DATA = new byte[0];
    private static final int PBKDF2_ITERATIONS = 65536;
    private static final int KEY_SIZE = 256;
    private static final String SECRET_KEY_FACTORY_ALGORITHM = "PBKDF2WithHmacSHA256";

    private static SecretKey deriveKey(EncryptionConfiguration cryptoConfig) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] saltBytes = cryptoConfig.getSalt();
        SecretKeyFactory skf = SecretKeyFactory.getInstance(SECRET_KEY_FACTORY_ALGORITHM);
        PBEKeySpec spec = new PBEKeySpec(cryptoConfig.getPassword(), saltBytes, PBKDF2_ITERATIONS, KEY_SIZE);
        return skf.generateSecret(spec);
    }

    private final Aead aead;

    public AesEncryptionCodec(EncryptionConfiguration cryptoConfig) {
        try {
            AeadConfig.register();

            SecretKey secretKey = deriveKey(cryptoConfig);
            aead = new AesGcmJce(secretKey.getEncoded());
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Error while starting AESPayloadCodec", e);
        }
    }

    @Override
    public ByteBuffer encrypt(ByteBuffer data) {
        try {
            byte[] bytes = asBytes(data);
            byte[] encryptedData = aead.encrypt(bytes, EMPTY_ASSOCIATED_DATA);
            return ByteBuffer.wrap(encryptedData);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Error encrypting data", e);
        }
    }

    @Override
    public ByteBuffer decrypt(ByteBuffer data) {
        try {
            byte[] bytes = asBytes(data);
            byte[] encryptedData = aead.decrypt(bytes, EMPTY_ASSOCIATED_DATA);
            return ByteBuffer.wrap(encryptedData);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Error encrypting data", e);
        }
    }

    private byte[] asBytes(ByteBuffer data) {
        int remaining = data.remaining();
        byte[] bytes = new byte[remaining];
        data.get(bytes);
        return bytes;
    }
}
