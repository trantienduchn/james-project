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
import java.util.Arrays;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;

public interface EncryptionCodec {
    enum Factory {
        DEFAULT {
            @Override
            public EncryptionCodec create(Configuration configuration) {
                return new NoEncryptionCodec();
            }
        },
        AES256 {
            @Override
            public EncryptionCodec create(Configuration configuration) {
                return new AesEncryptionCodec(EncryptionConfiguration.from(configuration));
            }
        };

        private static EncryptionCodec.Factory from(Configuration configuration) {
            return Optional.ofNullable(configuration.getString("cassandra.encryption.algorithm", null))
                .map(name -> from(name)
                    .orElseThrow(() -> new IllegalArgumentException("'" + name + "' is not a valid encryption codec")))
                .orElse(DEFAULT);
        }

        private static Optional<EncryptionCodec.Factory> from(String factoryName) {
            return Arrays.stream(values())
                .filter(factory -> factory.toString().equalsIgnoreCase(factoryName))
                .findFirst();
        }

        public abstract EncryptionCodec create(Configuration configuration);
    }

    static EncryptionCodec from(Configuration configuration) {
        return EncryptionCodec.Factory.from(configuration)
            .create(configuration);
    }

    ByteBuffer encrypt(ByteBuffer data);

    ByteBuffer decrypt(ByteBuffer data);
}
