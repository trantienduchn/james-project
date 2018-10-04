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

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.james.CleanupTasksPerformer;
import org.apache.james.backend.rabbitmq.DockerRabbitMQ;
import org.apache.james.backend.rabbitmq.RabbitMQConfiguration;
import org.apache.james.queue.rabbitmq.RabbitMQManagementApi;
import org.apache.james.queue.rabbitmq.view.cassandra.configuration.CassandraMailQueueViewConfiguration;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;

public class TestRabbitMQModule extends AbstractModule {

    private static final int DEFAULT_BUCKET_COUNT = 1;
    private static final int DEFAULT_BROWSE_START_PACE = 1000;
    private static final Duration SLICE_WINDOW = Duration.ofHours(1);

    private final DockerRabbitMQ rabbitMQ;

    public TestRabbitMQModule(DockerRabbitMQ rabbitMQ) {
        this.rabbitMQ = rabbitMQ;
    }

    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), CleanupTasksPerformer.CleanupTask.class)
            .addBinding()
            .to(QueueCleanUp.class);
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

    @Provides
    @Singleton
    protected CassandraMailQueueViewConfiguration provideMailQueueViewConfiguration() throws URISyntaxException {
        return CassandraMailQueueViewConfiguration.builder()
            .bucketCount(DEFAULT_BUCKET_COUNT)
            .updateBrowseStartPace(DEFAULT_BROWSE_START_PACE)
            .sliceWindow(SLICE_WINDOW)
            .build();
    }

    public static class QueueCleanUp implements CleanupTasksPerformer.CleanupTask {
        private final RabbitMQManagementApi api;

        @Inject
        public QueueCleanUp(RabbitMQManagementApi api) {
            this.api = api;
        }

        @Override
        public Result run() {
            api.deleteAllQueues();

            return Result.COMPLETED;
        }
    }
}
