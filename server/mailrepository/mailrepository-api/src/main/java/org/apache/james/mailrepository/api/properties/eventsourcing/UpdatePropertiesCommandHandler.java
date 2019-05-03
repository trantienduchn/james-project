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

package org.apache.james.mailrepository.api.properties.eventsourcing;

import java.util.List;

import org.apache.james.eventsourcing.CommandHandler;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.EventStore;

import com.google.common.base.Preconditions;

public class UpdatePropertiesCommandHandler implements CommandHandler<UpdatePropertiesCommand> {

    static UpdatePropertiesCommandHandler with(EventStore eventStore) {
        return new UpdatePropertiesCommandHandler(eventStore);
    }

    private final EventStore eventStore;

    private UpdatePropertiesCommandHandler(EventStore eventStore) {
        Preconditions.checkNotNull(eventStore);

        this.eventStore = eventStore;
    }

    @Override
    public Class<UpdatePropertiesCommand> handledClass() {
        return UpdatePropertiesCommand.class;
    }

    @Override
    public List<? extends Event> handle(UpdatePropertiesCommand command) {
        PropertiesAggregateId aggregateId = new PropertiesAggregateId(command.getUrl());
        PropertiesAggregate propertiesAggregate = PropertiesAggregate.load(
            eventStore.getEventsOfAggregate(aggregateId),
            aggregateId);

        return propertiesAggregate.updateProperties(command.getProperties());
    }
}
