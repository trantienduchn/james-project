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

import java.util.Objects;

import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.EventId;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTO;
import org.apache.james.mailrepository.api.properties.MailRepositoryProperties;
import org.apache.james.mailrepository.api.properties.impl.eventsourcing.PropertiesAggregateId;
import org.apache.james.mailrepository.api.properties.impl.eventsourcing.UpdatePropertiesEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;

public class UpdatePropertiesEventDTO implements EventDTO {

    public static UpdatePropertiesEventDTO from(UpdatePropertiesEvent updateEvent, String pojoTypeName) {
        return new UpdatePropertiesEventDTO(
            pojoTypeName,
            updateEvent.eventId().serialize(),
            updateEvent.getAggregateId().asAggregateKey(),
            updateEvent.getProperties().isBrowsable());
    }

    private final String type;
    private final int eventId;
    private final String aggregateId;
    private final boolean browsable;

    @JsonCreator
    public UpdatePropertiesEventDTO(
        @JsonProperty("type") String type,
        @JsonProperty("eventId") int eventId,
        @JsonProperty("aggregateId") String aggregateId,
        @JsonProperty("browsable") boolean browsable) {

        this.type = type;
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.browsable = browsable;
    }

    public boolean isBrowsable() {
        return browsable;
    }

    public String getType() {
        return type;
    }

    public int getEventId() {
        return eventId;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof UpdatePropertiesEventDTO) {
            UpdatePropertiesEventDTO that = (UpdatePropertiesEventDTO) o;

            return Objects.equals(this.eventId, that.eventId)
                && Objects.equals(this.browsable, that.browsable)
                && Objects.equals(this.type, that.type)
                && Objects.equals(this.aggregateId, that.aggregateId);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(type, eventId, aggregateId, browsable);
    }

    @JsonIgnore
    @Override
    public Event toEvent() {
        return new UpdatePropertiesEvent(
            EventId.fromSerialized(eventId),
            PropertiesAggregateId.parse(aggregateId),
            MailRepositoryProperties.builder()
                .browsable(browsable)
                .build());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("type", type)
            .add("eventId", eventId)
            .add("aggregateId", aggregateId)
            .add("browsable", browsable)
            .toString();
    }
}
