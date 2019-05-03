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

package org.apache.james.mailrepository.api.properties.impl.eventsourcing;

import javax.inject.Inject;

import org.apache.james.eventsourcing.EventSourcingSystem;
import org.apache.james.eventsourcing.Subscriber;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mailrepository.api.properties.MailRepositoryProperties;
import org.apache.james.mailrepository.api.properties.MailRepositoryPropertiesStore;
import org.reactivestreams.Publisher;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class EventSourcingMailRepositoryPropertiesStore implements MailRepositoryPropertiesStore {

    private static final ImmutableSet<Subscriber> NO_SUBSCRIBER = ImmutableSet.of();

    private final EventStore eventStore;
    private final EventSourcingSystem eventSourcingSystem;

    @Inject
    public EventSourcingMailRepositoryPropertiesStore(EventStore eventStore) {
        this.eventStore = eventStore;
        this.eventSourcingSystem = new EventSourcingSystem(
            ImmutableSet.of(UpdatePropertiesCommandHandler.with(eventStore)),
            NO_SUBSCRIBER,
            eventStore);
    }

    @Override
    public Publisher<Void> store(MailRepositoryUrl url, MailRepositoryProperties properties) {
        return Mono.fromRunnable(() -> eventSourcingSystem.dispatch(new UpdatePropertiesCommand(url, properties)))
            .subscribeOn(Schedulers.elastic())
            .then();
    }

    @Override
    public Publisher<MailRepositoryProperties> retrieve(MailRepositoryUrl url) {
        return retrievePropertiesAggregate(url)
            .map(PropertiesAggregate::retrieveProperties)
            .flatMap(Mono::justOrEmpty)
            .subscribeOn(Schedulers.elastic());
    }

    private Mono<PropertiesAggregate> retrievePropertiesAggregate(MailRepositoryUrl url) {
        return Mono.just(new PropertiesAggregateId(url))
            .map(aggregateId -> PropertiesAggregate.load(eventStore.getEventsOfAggregate(aggregateId), aggregateId));
    }
}
