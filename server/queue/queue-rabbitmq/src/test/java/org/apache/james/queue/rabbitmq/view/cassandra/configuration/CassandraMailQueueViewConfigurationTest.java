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

package org.apache.james.queue.rabbitmq.view.cassandra.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class CassandraMailQueueViewConfigurationTest {

    private static final int DEFAULT_BUCKET_COUNT = 1;
    private static final int DEFAULT_UPDATE_BROWSE_START_PACE = 100;
    private static final String ONE_HOUR_STRING = "1 hour";

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(CassandraMailQueueViewConfiguration.class).verify();
    }

    @Test
    void fromShouldThrowWhenAllPropertiesAreMissing() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();

        assertThatThrownBy(() -> CassandraMailQueueViewConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify the mailqueueview.cassandra.bucketCount property. " +
                "The higher the bucket count is, the better the data will be spread in the cluster. " +
                "You might want the bucket count to exceed your Cassandra node count.");
    }

    @Test
    void fromShouldThrowWhenBucketCountIsMissing() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("mailqueueview.cassandra.updateBrowseStartPace", DEFAULT_UPDATE_BROWSE_START_PACE);
        configuration.addProperty("mailqueueview.cassandra.slideWindow", ONE_HOUR_STRING);

        assertThatThrownBy(() -> CassandraMailQueueViewConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify the mailqueueview.cassandra.bucketCount property. " +
                "The higher the bucket count is, the better the data will be spread in the cluster. " +
                "You might want the bucket count to exceed your Cassandra node count.");
    }

    @Test
    void fromShouldThrowWhenUpdateBrowseStartPaceIsMissing() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("mailqueueview.cassandra.bucketCount", DEFAULT_BUCKET_COUNT);
        configuration.addProperty("mailqueueview.cassandra.slideWindow", ONE_HOUR_STRING);

        assertThatThrownBy(() -> CassandraMailQueueViewConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify the mailqueueview.cassandra.updateBrowseStartPace property. " +
                "The mailQueue will use this number to randomly update the browsing start point of the mail queue. " +
                "The lower that number is, the more the start point will be up to date (and thus browse quick) but the more unnecessary work will be performed upon dequeues/deletes.");
    }

    @Test
    void fromShouldThrowWhenSliceWindowIsMissing() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("mailqueueview.cassandra.bucketCount", DEFAULT_BUCKET_COUNT);
        configuration.addProperty("mailqueueview.cassandra.updateBrowseStartPace", DEFAULT_UPDATE_BROWSE_START_PACE);

        assertThatThrownBy(() -> CassandraMailQueueViewConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify the mailqueueview.cassandra.sliceWindow property as the seconds long of each enqueued window");
    }

    @Test
    void fromShouldReturnCorrespondingConfigurationWhenGiven() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("mailqueueview.cassandra.bucketCount", DEFAULT_BUCKET_COUNT);
        configuration.addProperty("mailqueueview.cassandra.updateBrowseStartPace", DEFAULT_UPDATE_BROWSE_START_PACE);
        configuration.addProperty("mailqueueview.cassandra.sliceWindow", ONE_HOUR_STRING);

        CassandraMailQueueViewConfiguration mailQueueViewConfiguration = CassandraMailQueueViewConfiguration.builder()
            .bucketCount(DEFAULT_BUCKET_COUNT)
            .updateBrowseStartPace(DEFAULT_UPDATE_BROWSE_START_PACE)
            .sliceWindow(Duration.ofHours(1))
            .build();

        assertThat(CassandraMailQueueViewConfiguration.from(configuration))
            .isEqualTo(mailQueueViewConfiguration);
    }

    @Test
    void validateConfigurationChangeShouldAcceptIdentity() {
        CassandraMailQueueViewConfiguration configuration = CassandraMailQueueViewConfiguration.builder()
            .bucketCount(2)
            .updateBrowseStartPace(1000)
            .sliceWindow(Duration.ofHours(1))
            .build();

        assertThatCode(() -> configuration.validateConfigurationChange(configuration))
            .doesNotThrowAnyException();
    }

    @Test
    void validateConfigurationChangeShouldAcceptBucketCountIncrease() {
        CassandraMailQueueViewConfiguration configuration = CassandraMailQueueViewConfiguration.builder()
            .bucketCount(2)
            .updateBrowseStartPace(1000)
            .sliceWindow(Duration.ofHours(1))
            .build();

        assertThatCode(() -> configuration.validateConfigurationChange(
            CassandraMailQueueViewConfiguration.builder()
                .bucketCount(3)
                .updateBrowseStartPace(1000)
                .sliceWindow(Duration.ofHours(1))
                .build()))
            .doesNotThrowAnyException();
    }

    @Test
    void validateConfigurationChangeShouldAcceptDividingSliceWindow() {
        CassandraMailQueueViewConfiguration configuration = CassandraMailQueueViewConfiguration.builder()
            .bucketCount(2)
            .updateBrowseStartPace(1000)
            .sliceWindow(Duration.ofHours(1))
            .build();

        assertThatCode(() -> configuration.validateConfigurationChange(
            CassandraMailQueueViewConfiguration.builder()
                .bucketCount(2)
                .updateBrowseStartPace(1000)
                .sliceWindow(Duration.ofMinutes(20))
                .build()))
            .doesNotThrowAnyException();
    }

    @Test
    void validateConfigurationChangeShouldRejectArbitraryDecreaseSliceWindow() {
        CassandraMailQueueViewConfiguration configuration = CassandraMailQueueViewConfiguration.builder()
            .bucketCount(2)
            .updateBrowseStartPace(1000)
            .sliceWindow(Duration.ofHours(1))
            .build();

        assertThatThrownBy(() -> configuration.validateConfigurationChange(
            CassandraMailQueueViewConfiguration.builder()
                .bucketCount(2)
                .updateBrowseStartPace(1000)
                .sliceWindow(Duration.ofMinutes(25))
                .build()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateConfigurationChangeShouldRejectDecreaseBucketCount() {
        CassandraMailQueueViewConfiguration configuration = CassandraMailQueueViewConfiguration.builder()
            .bucketCount(2)
            .updateBrowseStartPace(1000)
            .sliceWindow(Duration.ofHours(1))
            .build();

        assertThatThrownBy(() -> configuration.validateConfigurationChange(
            CassandraMailQueueViewConfiguration.builder()
                .bucketCount(1)
                .updateBrowseStartPace(1000)
                .sliceWindow(Duration.ofHours(1))
                .build()))
            .isInstanceOf(IllegalArgumentException.class);
    }
}