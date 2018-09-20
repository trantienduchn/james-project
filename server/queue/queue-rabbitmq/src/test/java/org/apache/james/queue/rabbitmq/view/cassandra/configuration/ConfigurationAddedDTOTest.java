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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.apache.james.eventsourcing.EventId;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class ConfigurationAddedDTOTest {

    private static final int EVENT_ID_SERIALIZED = 10;
    private static final EventId EVENT_ID = EventId.fromSerialized(EVENT_ID_SERIALIZED);
    private static final Duration ONE_HOUR = Duration.ofHours(1L);
    private static final int BUCKET_COUNT = 2;
    private static final int UPDATE_PACE = 3;

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(ConfigurationAddedDTO.class)
            .verify();
    }

    @Test
    void fromShouldThrowWhenConfigurationAddedIsNull() {
        assertThatThrownBy(() -> ConfigurationAddedDTO.from(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void fromShouldReturnCorrespondingDTO() {
        ConfigurationAdded configurationAdded = new ConfigurationAdded(
            EVENT_ID,
            CassandraMailQueueViewConfiguration.builder()
                .bucketCount(BUCKET_COUNT)
                .updateBrowseStartPace(UPDATE_PACE)
                .sliceWindow(ONE_HOUR)
                .build());

        ConfigurationAddedDTO dto = ConfigurationAddedDTO.from(configurationAdded);
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(dto.getEventId()).isEqualTo(EVENT_ID_SERIALIZED);
        softly.assertThat(dto.getType()).isEqualTo(CassandraMailQueueViewConfigurationModule.TYPE);
        softly.assertThat(dto.getBucketCount()).isEqualTo(BUCKET_COUNT);
        softly.assertThat(dto.getUpdateBrowseStartPace()).isEqualTo(UPDATE_PACE);
        softly.assertThat(dto.getSliceWindow()).isEqualTo(ONE_HOUR);
        softly.assertAll();
    }
}