/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.james.modules;

import static org.apache.james.backend.rabbitmq.RabbitMQFixture.DEFAULT_MANAGEMENT_CREDENTIAL;

import java.net.URISyntaxException;
import java.time.Duration;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.james.backend.rabbitmq.DockerRabbitMQ;
import org.apache.james.backend.rabbitmq.RabbitMQConfiguration;
import org.apache.james.queue.rabbitmq.RabbitMQManagementApi;
import org.apache.james.queue.rabbitmq.view.cassandra.configuration.CassandraMailQueueViewConfiguration;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;

public class TestRabbitMQModule extends AbstractModule {

    public static class QueueCleanUp {
        private final RabbitMQManagementApi api;

        @Inject
        public QueueCleanUp(RabbitMQManagementApi api) {
            this.api = api;
        }

        @PreDestroy
        public void deleteQueues() {
            api.deleteAllQueues();
        }
    }

    private final DockerRabbitMQ rabbitMQ;

    public TestRabbitMQModule(DockerRabbitMQ rabbitMQ) {
        this.rabbitMQ = rabbitMQ;
    }

    @Override
    protected void configure() {
        bind(CassandraMailQueueViewConfiguration.class).toInstance(CassandraMailQueueViewConfiguration
            .builder()
            .bucketCount(1)
            .updateBrowseStartPace(1000)
            .sliceWindow(Duration.ofHours(1))
            .build());

        bind(QueueCleanUp.class).in(Scopes.SINGLETON);
    }

    @Provides
    @Singleton
    protected RabbitMQConfiguration provideRabbitMQConfiguration() throws URISyntaxException {
        return RabbitMQConfiguration.builder()
                .amqpUri(rabbitMQ.amqpUri())
                .managementUri(rabbitMQ.managementUri())
                .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
                .build();
    }
}
