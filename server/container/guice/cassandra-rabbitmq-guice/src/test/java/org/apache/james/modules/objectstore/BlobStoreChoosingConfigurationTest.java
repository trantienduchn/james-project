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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

class BlobStoreChoosingConfigurationTest {

    private static final String SWIFT = "swift";

    @Test
    void fromShouldReturnSwiftWhenBlobStoreImplIsMissing() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();

        assertThat(
            BlobStoreChoosingConfiguration.from(configuration)
                .getImplementation()
                .getName())
            .isEqualTo(SWIFT);
    }

    @Test
    void fromShouldReturnSwiftWhenBlobStoreImplIsNull() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("objectstore.implementation", null);

        assertThat(
            BlobStoreChoosingConfiguration.from(configuration)
                .getImplementation()
                .getName())
            .isEqualTo(SWIFT);
    }

    @Test
    void fromShouldReturnSwiftWhenBlobStoreImplIsEmpty() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("objectstore.implementation", "");

        assertThat(
            BlobStoreChoosingConfiguration.from(configuration)
                .getImplementation()
                .getName())
            .isEqualTo(SWIFT);
    }

    @Test
    void fromShouldThrowWhenBlobStoreImplIsNotInSupportedList() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("objectstore.implementation", "un_supported");

        assertThatThrownBy(() -> BlobStoreChoosingConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("un_supported is not a valid name of BlobStores");
    }

    @Test
    void fromShouldReturnConfigurationWhenBlobStoreImplIsCassandra() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("objectstore.implementation", "cassandra");

        assertThat(
            BlobStoreChoosingConfiguration.from(configuration)
                .getImplementation()
                .getName())
            .isEqualTo("cassandra");
    }

    @Test
    void fromShouldReturnConfigurationWhenBlobStoreImplIsSwift() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("objectstore.implementation", SWIFT);

        assertThat(
            BlobStoreChoosingConfiguration.from(configuration)
                .getImplementation()
                .getName())
            .isEqualTo(SWIFT);
    }

    @Test
    void fromShouldReturnConfigurationWhenBlobStoreImplIsSupportedAndCaseInsensitive() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("objectstore.implementation", "SWifT");

        assertThat(
            BlobStoreChoosingConfiguration.from(configuration)
                .getImplementation()
                .getName())
            .isEqualTo(SWIFT);
    }
}