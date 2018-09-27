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

import static org.apache.james.queue.rabbitmq.view.cassandra.configuration.CassandraMailQueueViewConfigurationModule.TYPE_NAME;
import static org.apache.james.queue.rabbitmq.view.cassandra.configuration.ConfigurationAggregate.CONFIGURATION_AGGREGATE_ID;
import static org.apache.james.queue.rabbitmq.view.cassandra.configuration.ConfigurationAggregate.CONFIGURATION_AGGREGATE_KEY;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.apache.james.eventsourcing.EventId;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class ConfigurationEditedDTOTest {

    private static final int EVENT_ID_SERIALIZED = 10;
    private static final EventId EVENT_ID = EventId.fromSerialized(EVENT_ID_SERIALIZED);
    private static final Duration ONE_HOUR = Duration.ofHours(1L);
    private static final int BUCKET_COUNT = 2;
    private static final int UPDATE_PACE = 3;

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(ConfigurationEditedDTO.class)
            .verify();
    }

    @Test
    void fromShouldThrowWhenConfigurationAddedIsNull() {
        assertThatThrownBy(() -> ConfigurationEditedDTO.from(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void fromShouldReturnCorrespondingDTO() {
        ConfigurationEdited configurationEdited = new ConfigurationEdited(
            CONFIGURATION_AGGREGATE_ID,
            EVENT_ID,
            CassandraMailQueueViewConfiguration.builder()
                .bucketCount(BUCKET_COUNT)
                .updateBrowseStartPace(UPDATE_PACE)
                .sliceWindow(ONE_HOUR)
                .build());


        ConfigurationEditedDTO dto = ConfigurationEditedDTO.from(configurationEdited);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(dto.getEventId()).isEqualTo(EVENT_ID_SERIALIZED);
            softly.assertThat(dto.getType()).isEqualTo(TYPE_NAME);
            softly.assertThat(dto.getBucketCount()).isEqualTo(BUCKET_COUNT);
            softly.assertThat(dto.getUpdateBrowseStartPace()).isEqualTo(UPDATE_PACE);
            softly.assertThat(dto.getSliceWindow()).isEqualTo(ONE_HOUR);
        });
    }

    @Test
    void toEventShouldReturnCorrespondingConfigurationEditedEvent() {
        ConfigurationEditedDTO dto = new ConfigurationEditedDTO(
            EVENT_ID_SERIALIZED,
            CONFIGURATION_AGGREGATE_KEY,
            TYPE_NAME,
            BUCKET_COUNT,
            UPDATE_PACE,
            ONE_HOUR);
        ConfigurationEdited event = (ConfigurationEdited) dto.toEvent();
        CassandraMailQueueViewConfiguration mailQueueViewConfiguration = event.getConfiguration();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(event.eventId()).isEqualTo(EVENT_ID);
            softly.assertThat(event.getAggregateId().asAggregateKey()).isEqualTo(CONFIGURATION_AGGREGATE_KEY);
            softly.assertThat(mailQueueViewConfiguration.getBucketCount()).isEqualTo(BUCKET_COUNT);
            softly.assertThat(mailQueueViewConfiguration.getUpdateBrowseStartPace()).isEqualTo(UPDATE_PACE);
            softly.assertThat(mailQueueViewConfiguration.getSliceWindow()).isEqualTo(ONE_HOUR);
        });
    }
}