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

package org.apache.james.mailrepository.properties.impl.eventsourcing.cassandra;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.eventsourcing.EventId;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mailrepository.api.properties.MailRepositoryProperties;
import org.apache.james.mailrepository.api.properties.impl.eventsourcing.PropertiesAggregateId;
import org.apache.james.mailrepository.api.properties.impl.eventsourcing.UpdatePropertiesEvent;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

class DTOTest {

    static final String EVENT_JSON = ClassLoaderUtils.getSystemResourceAsString("json/event.json");
    static final UpdatePropertiesEventDTO EVENT_DTO = UpdatePropertiesEventDTO.from(
        new UpdatePropertiesEvent(
            EventId.fromSerialized(1),
            new PropertiesAggregateId(MailRepositoryUrl.from("cassandra://var/mail/properties")),
            MailRepositoryProperties.builder()
                .canNotBrowse()
                .build()),
"mail-repository-properties-update");

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new GuavaModule());
    }

    @Test
    void shouldSerializeDTO() throws Exception {
        String serializedEvent = objectMapper.writeValueAsString(EVENT_DTO);

        assertThatJson(serializedEvent).isEqualTo(EVENT_JSON);
    }

    @Test
    void shouldDeserializeDTO() throws Exception {
        UpdatePropertiesEventDTO dto = objectMapper.readValue(EVENT_JSON, UpdatePropertiesEventDTO.class);

        assertThat(dto).isEqualTo(EVENT_DTO);
    }
}
