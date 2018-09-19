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

import java.util.Optional;

import org.apache.james.eventsourcing.AggregateId;

import com.google.common.base.Preconditions;

class ConfigurationAggregate {

    private static class State {

        private static State initial() {
            return new State(Optional.empty());
        }

        private Optional<CassandraMailQueueViewConfiguration> maybeConfiguration;

        State(Optional<CassandraMailQueueViewConfiguration> maybeConfiguration) {
            Preconditions.checkNotNull(maybeConfiguration);
            this.maybeConfiguration = maybeConfiguration;
        }

        void set(CassandraMailQueueViewConfiguration configuration) {
            validateConfiguration(configuration);

            maybeConfiguration = Optional.of(configuration);
        }

        private void validateConfiguration(CassandraMailQueueViewConfiguration configuration) {
            Preconditions.checkNotNull(configuration);

            maybeConfiguration.ifPresent(currentConfiguration -> {
                Preconditions.checkArgument(currentConfiguration.getBucketCount() < configuration.getBucketCount(),
                    "can not set bucket count to be less than the current one: " + currentConfiguration.getBucketCount());
            });
        }
    }

    private static final String CONFIGURATION_AGGREGATE_KEY = "CassandraMailQueueViewConfiguration";
    static final AggregateId CONFIGURATION_AGGREGATE_ID = () -> CONFIGURATION_AGGREGATE_KEY;

}
