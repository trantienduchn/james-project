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

import java.util.Optional;

import org.apache.commons.configuration2.Configuration;

import com.google.common.base.Preconditions;
import com.google.crypto.tink.subtle.Hex;

public class EncryptionConfiguration {
    private static final String SALT_PROPERTY = "cassandra.aes256.salt";
    private static final String PASSWORD_PROPERTY = "cassandra.aes256.password";

    public interface Builder {
        @FunctionalInterface
        interface RequireSalt {
            RequirePassword salt(String salt);
        }

        @FunctionalInterface
        interface RequirePassword {
            EncryptionConfiguration password(char[] password);
        }
    }

    public static Builder.RequireSalt builder() {
        return salt -> password -> new EncryptionConfiguration(salt, password);
    }

    public static EncryptionConfiguration from(Configuration configuration) {
        return builder()
            .salt(configuration.getString(SALT_PROPERTY, null))
            .password(Optional.ofNullable(configuration.getString(PASSWORD_PROPERTY, null))
                .map(String::toCharArray)
                .orElse(null));
    }

    private final String salt;
    private final char[] password;

    private EncryptionConfiguration(String salt, char[] password) {
        Preconditions.checkNotNull(salt, String.format("'%s' is compulsory", SALT_PROPERTY));
        Preconditions.checkNotNull(password, String.format("'%s' is compulsory", PASSWORD_PROPERTY));

        this.salt = salt;
        this.password = password;
    }

    public byte[] getSalt() {
        return Hex.decode(salt);
    }

    public char[] getPassword() {
        return password;
    }
}
