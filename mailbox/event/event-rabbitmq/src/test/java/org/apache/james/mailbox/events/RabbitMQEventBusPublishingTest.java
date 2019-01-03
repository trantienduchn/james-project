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

import static org.apache.james.backend.rabbitmq.Constants.AUTO_DELETE;
import static org.apache.james.backend.rabbitmq.Constants.DURABLE;
import static org.apache.james.backend.rabbitmq.Constants.EXCLUSIVE;
import static org.apache.james.backend.rabbitmq.Constants.NO_ARGUMENTS;
import static org.apache.james.mailbox.events.EventBusContract.EVENT;
import static org.apache.james.mailbox.events.RabbitMQEventBus.EMPTY_ROUTING_KEY;
import static org.apache.james.mailbox.events.RabbitMQEventBus.MAILBOX_EVENT;
import static org.apache.james.mailbox.events.RabbitMQEventBus.MAILBOX_EVENT_EXCHANGE_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.apache.james.backend.rabbitmq.RabbitMQConnectionFactory;
import org.apache.james.backend.rabbitmq.RabbitMQExtension;
import org.apache.james.mailbox.Event;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.ReceiverOptions;

class RabbitMQEventBusPublishingTest {
    private static final String MAILBOX_WORK_QUEUE_NAME = MAILBOX_EVENT + "-workQueue";

    static RabbitMQExtension rabbitMQExtension = new RabbitMQExtension();
    @RegisterExtension
    static RabbitMQEventBusExtension testExtension = new RabbitMQEventBusExtension(rabbitMQExtension);

    @BeforeAll
    static void beforeAll() {
        rabbitMQExtension.beforeAll(null);
    }

    @AfterAll
    static void afterAll() {
        rabbitMQExtension.afterAll(null);
    }

    private RabbitMQEventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = testExtension.newEventBus();
        eventBus.start();

        createQueue();
    }

    private void createQueue() {
        testExtension.sender.declareQueue(QueueSpecification.queue(MAILBOX_WORK_QUEUE_NAME)
            .durable(DURABLE)
            .exclusive(!EXCLUSIVE)
            .autoDelete(!AUTO_DELETE)
            .arguments(NO_ARGUMENTS))
            .block();
        testExtension.sender.bind(BindingSpecification.binding()
            .exchange(MAILBOX_EVENT_EXCHANGE_NAME)
            .queue(MAILBOX_WORK_QUEUE_NAME)
            .routingKey(EMPTY_ROUTING_KEY))
            .block();
    }

    @Test
    void dispatchShouldPublishSerializedEventToRabbitMQ() {
        eventBus.dispatch(EVENT, ImmutableSet.of()).block();

        assertThat(dequeueEvent()).isEqualTo(EVENT);
    }

    @Test
    void dispatchShouldPublishSerializedEventToRabbitMQWhenNotBlocking() {
        eventBus.dispatch(EVENT, ImmutableSet.of());

        assertThat(dequeueEvent()).isEqualTo(EVENT);
    }


    private Event dequeueEvent() {
        RabbitMQConnectionFactory connectionFactory = rabbitMQExtension.getConnectionFactory();
        Receiver receiver = RabbitFlux.createReceiver(new ReceiverOptions().connectionMono(Mono.just(connectionFactory.create())));

        byte[] eventInBytes = receiver.consumeAutoAck(MAILBOX_WORK_QUEUE_NAME)
            .blockFirst()
            .getBody();

        return testExtension.eventSerializer.fromJson(new String(eventInBytes, StandardCharsets.UTF_8))
            .get();
    }
}
