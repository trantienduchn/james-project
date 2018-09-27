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

import static org.apache.james.queue.rabbitmq.view.cassandra.configuration.ConfigurationAggregate.CONFIGURATION_AGGREGATE_ID;

import java.util.List;

import org.apache.james.eventsourcing.CommandHandler;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.EventStore;

class LoadConfigurationCommandHandler implements CommandHandler<LoadConfigurationCommand> {

    private final EventStore eventStore;

    LoadConfigurationCommandHandler(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    @Override
    public Class<LoadConfigurationCommand> handledClass() {
        return LoadConfigurationCommand.class;
    }

    @Override
    public List<? extends Event> handle(LoadConfigurationCommand command) {
        return ConfigurationAggregate
            .load(eventStore.getEventsOfAggregate(CONFIGURATION_AGGREGATE_ID))
            .applyConfiguration(command.getConfiguration());
    }
}
