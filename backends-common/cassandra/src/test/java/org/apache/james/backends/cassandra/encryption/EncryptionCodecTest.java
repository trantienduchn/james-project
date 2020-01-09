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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

import com.google.common.hash.HashCode;
import com.google.crypto.tink.subtle.Hex;

class EncryptionCodecTest {
    @Test
    void fromShouldReturnNoEncryptionByDefault() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();

        EncryptionCodec encryptionCodec = EncryptionCodec.from(configuration);

        assertThat(encryptionCodec).isInstanceOf(NoEncryptionCodec.class);
    }

    @Test
    void fromShouldReturnNoEncryptionWhenDefault() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("cassandra.encryption.algorithm", "default");

        EncryptionCodec encryptionCodec = EncryptionCodec.from(configuration);

        assertThat(encryptionCodec).isInstanceOf(NoEncryptionCodec.class);
    }

    @Test
    void fromAlgorithmShouldBeCaseIncentive() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("cassandra.encryption.algorithm", "DeFaUlT");

        EncryptionCodec encryptionCodec = EncryptionCodec.from(configuration);

        assertThat(encryptionCodec).isInstanceOf(NoEncryptionCodec.class);
    }

    @Test
    void fromShouldThrowWhenInvalidAlgorithm() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("cassandra.encryption.algorithm", "invalid");

        assertThatThrownBy(() -> EncryptionCodec.from(configuration)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromShouldThrowWhenAES256ParametersAreNotSupplied() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("cassandra.encryption.algorithm", "aes256");

        assertThatThrownBy(() -> EncryptionCodec.from(configuration))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("'cassandra.aes256.salt' is compulsory");
    }

    @Test
    void fromShouldThrowWhenAES256PasswordIsMissing() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("cassandra.encryption.algorithm", "aes256");
        configuration.addProperty("cassandra.aes256.salt", "c603a7327ee3dcbc031d8d34b1096c605feca5e1");

        assertThatThrownBy(() -> EncryptionCodec.from(configuration))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("'cassandra.aes256.password' is compulsory");
    }

    @Test
    void fromShouldThrowWhenAES256SaltIsMissing() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("cassandra.encryption.algorithm", "aes256");
        configuration.addProperty("cassandra.aes256.password", "password");

        assertThatThrownBy(() -> EncryptionCodec.from(configuration))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("'cassandra.aes256.salt' is compulsory");
    }

    @Test
    void fromShouldThrowWhenAES256SaltIsNotHexaDecimal() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("cassandra.encryption.algorithm", "aes256");
        configuration.addProperty("cassandra.aes256.password", "password");
        configuration.addProperty("cassandra.aes256.salt", "not hexa-decimal");

        assertThatThrownBy(() -> EncryptionCodec.from(configuration)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromShouldReturnAesEncryptionCodecWhenCorrectlyConfigured() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("cassandra.encryption.algorithm", "aes256");
        configuration.addProperty("cassandra.aes256.password", "password");
        configuration.addProperty("cassandra.aes256.salt", "c603a7327ee3dcbc031d8d34b1096c605feca5e1");

        assertThat(EncryptionCodec.from(configuration)).isInstanceOf(AesEncryptionCodec.class);
    }
    
    @Test
    void encryptShouldReturnADifferentByteArray() {
        EncryptionCodec codec = aes256Codec();

        byte[] payload = "toBeEncoded".getBytes(StandardCharsets.UTF_8);
        byte[] encryptedPayload = codec.encrypt(ByteBuffer.wrap(payload)).array();

        HashCode payloadHashCode = HashCode.fromBytes(payload);
        HashCode encryptedPayloadHashCode = HashCode.fromBytes(encryptedPayload);

        assertThat(payloadHashCode).isNotEqualTo(encryptedPayloadHashCode);
    }

    @Test
    void decryptShouldReturnRawData() {
        EncryptionCodec codec = aes256Codec();

        byte[] payload = "toBeEncoded".getBytes(StandardCharsets.UTF_8);
        byte[] encryptedPayload = Hex.decode("51a5c135875267685c17acedbaaa11838bb92aac134c3a1201ae5cf2ac85be8de1a07dedb4f9eb");
        byte[] decryptedPayload = codec.decrypt(ByteBuffer.wrap(encryptedPayload)).array();

        assertThat(new ByteArrayInputStream(payload)).hasSameContentAs(new ByteArrayInputStream(decryptedPayload));
    }

    @Test
    void decryptShouldAllowToRetrieveEncryptedData() {
        EncryptionCodec codec = aes256Codec();

        byte[] payload = "toBeEncoded".getBytes(StandardCharsets.UTF_8);
        ByteBuffer encryptedPayload = codec.encrypt(ByteBuffer.wrap(payload));
        byte[] decryptedPayload = codec.decrypt(encryptedPayload).array();

        assertThat(new ByteArrayInputStream(payload)).hasSameContentAs(new ByteArrayInputStream(decryptedPayload));
    }

    private EncryptionCodec aes256Codec() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("cassandra.encryption.algorithm", "aes256");
        configuration.addProperty("cassandra.aes256.password", "password");
        configuration.addProperty("cassandra.aes256.salt", "c603a7327ee3dcbc031d8d34b1096c605feca5e1");

        return EncryptionCodec.from(configuration);
    }
}