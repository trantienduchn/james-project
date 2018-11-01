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

package org.apache.james.modules.objectstore;

import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang3.StringUtils;

import com.google.common.base.MoreObjects;

public class BlobStoreChoosingConfiguration {

    public enum BlobStoreImplName {
        CASSANDRA("cassandra"),
        SWIFT("swift");

        static BlobStoreImplName from(String name) {
            return Stream.of(values())
                .filter(blobName -> blobName.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(String.format("%s is not a valid name of BlobStores", name)));
        }

        private final String name;

        BlobStoreImplName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    static final String BLOBSTORE_IMPLEMENTATION_PROPERTY = "objectstore.implementation";
    static final BlobStoreImplName DEFAULT_IMPLEMENTATION = BlobStoreImplName.SWIFT;

    static BlobStoreChoosingConfiguration from(Configuration configuration) {
        BlobStoreImplName blobStoreImplName = Optional.ofNullable(configuration.getString(BLOBSTORE_IMPLEMENTATION_PROPERTY))
            .filter(StringUtils::isNotBlank)
            .map(StringUtils::trim)
            .map(BlobStoreImplName::from)
            .orElse(DEFAULT_IMPLEMENTATION);

        return new BlobStoreChoosingConfiguration(blobStoreImplName);
    }

    private final BlobStoreImplName implementation;

    BlobStoreChoosingConfiguration(BlobStoreImplName implementation) {
        this.implementation = implementation;
    }

    BlobStoreImplName getImplementation() {
        return implementation;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("implementation", implementation)
            .toString();
    }
}
