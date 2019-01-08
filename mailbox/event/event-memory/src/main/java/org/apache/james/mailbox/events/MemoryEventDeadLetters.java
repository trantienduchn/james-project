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

package org.apache.james.mailbox.events;

import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.mailbox.Event;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MemoryEventDeadLetters implements EventDeadLetters {

    private static final String REGISTERED_GROUP_CANNOT_BE_NULL = "registeredGroup cannot be null";
    private static final String FAIL_DELIVERED_EVENT_CANNOT_BE_NULL = "failDeliveredEvent cannot be null";
    private static final String FAIL_DELIVERED_ID_EVENT_CANNOT_BE_NULL = "failDeliveredEventId cannot be null";

    private final Multimap<Group, Event> deadLetters;

    MemoryEventDeadLetters() {
        this.deadLetters = Multimaps.synchronizedSetMultimap(HashMultimap.create());
    }

    @Override
    public Mono<Void> store(Group registeredGroup, Event failDeliveredEvent) {
        Preconditions.checkArgument(registeredGroup != null, REGISTERED_GROUP_CANNOT_BE_NULL);
        Preconditions.checkArgument(failDeliveredEvent != null, FAIL_DELIVERED_EVENT_CANNOT_BE_NULL);

        deadLetters.put(registeredGroup, failDeliveredEvent);
        return Mono.empty();
    }

    @Override
    public Mono<Void> remove(Group registeredGroup, Event.EventId failDeliveredEventId) {
        Preconditions.checkArgument(registeredGroup != null, REGISTERED_GROUP_CANNOT_BE_NULL);
        Preconditions.checkArgument(failDeliveredEventId != null, FAIL_DELIVERED_ID_EVENT_CANNOT_BE_NULL);

        deadLetters.get(registeredGroup)
            .removeIf(event -> event.getEventId().equals(failDeliveredEventId));

        return Mono.empty();
    }

    @Override
    public Mono<Event.EventId> failedEventId(Group registeredGroup, Event.EventId failDeliveredEventId) {
        Preconditions.checkArgument(registeredGroup != null, REGISTERED_GROUP_CANNOT_BE_NULL);
        Preconditions.checkArgument(failDeliveredEventId != null, FAIL_DELIVERED_ID_EVENT_CANNOT_BE_NULL);

        return findEventId(registeredGroup, failDeliveredEventId);
    }

    @Override
    public Flux<Event.EventId> failedEventIds(Group registeredGroup) {
        Preconditions.checkArgument(registeredGroup != null, REGISTERED_GROUP_CANNOT_BE_NULL);

        return Flux.fromStream(findEventIds(registeredGroup));
    }

    @Override
    public Flux<Group> groupsWithFailedEvents() {
        return Flux.fromIterable(deadLetters.keySet());
    }

    private Mono<Event.EventId> findEventId(Group registeredGroup, Event.EventId failDeliveredEventId) {
        Optional<Event.EventId> failedEventIdMaybe = findEventIds(registeredGroup)
            .filter(eventId -> eventId.equals(failDeliveredEventId))
            .findAny();

        return Mono.justOrEmpty(failedEventIdMaybe);
    }

    private Stream<Event.EventId> findEventIds(Group registeredGroup) {
        return deadLetters.get(registeredGroup)
            .stream()
            .map(Event::getEventId);
    }
}
