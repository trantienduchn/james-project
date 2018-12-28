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

import static org.apache.james.mailbox.events.RabbitMQEventBus.MAILBOX_EVENT_EXCHANGE_NAME;

import java.util.List;

import org.apache.james.backend.rabbitmq.RabbitMQConnectionFactory;
import org.apache.james.backend.rabbitmq.RabbitMQExtension;
import org.apache.james.event.json.EventSerializer;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

import com.rabbitmq.client.Connection;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.Sender;
import reactor.rabbitmq.SenderOptions;

class RabbitMQEventBusConcurrentExtension implements BeforeEachCallback, AfterEachCallback {

    static final EventBusContract.MailboxListenerCountingSuccessfulExecution COUNTING_LISTENER_1 =
        new EventBusContract.MailboxListenerCountingSuccessfulExecution();
    static final EventBusContract.MailboxListenerCountingSuccessfulExecution COUNTING_LISTENER_2 =
        new EventBusContract.MailboxListenerCountingSuccessfulExecution();
    static final EventBusContract.MailboxListenerCountingSuccessfulExecution COUNTING_LISTENER_3 =
        new EventBusContract.MailboxListenerCountingSuccessfulExecution();

    private static final List<EventBusContract.MailboxListenerCountingSuccessfulExecution> ALL_LISTENERS =
                ImmutableList.of(COUNTING_LISTENER_1, COUNTING_LISTENER_2, COUNTING_LISTENER_3);

    private EventSerializer eventSerializer;
    private RabbitMQConnectionFactory connectionFactory;
    private RoutingKeyConverter routingKeyConverter;
    private Sender sender;
    private final RabbitMQExtension rabbitMQExtension;

    RabbitMQEventBusConcurrentExtension(RabbitMQExtension rabbitMQExtension) {
        this.rabbitMQExtension = rabbitMQExtension;
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        ALL_LISTENERS.forEach(listener -> listener.clear());
        rabbitMQExtension.beforeEach(context);

        connectionFactory = rabbitMQExtension.getConnectionFactory();

        TestId.Factory mailboxIdFactory = new TestId.Factory();
        routingKeyConverter = RoutingKeyConverter.forFactories(new MailboxIdRegistrationKey.Factory(mailboxIdFactory));
        eventSerializer = new EventSerializer(mailboxIdFactory, new TestMessageId.Factory());

        RabbitMQConnectionFactory connectionFactory = rabbitMQExtension.getConnectionFactory();
        Mono<Connection> connectionMono = Mono.fromSupplier(connectionFactory::create).cache();
        sender = RabbitFlux.createSender(new SenderOptions().connectionMono(connectionMono));
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        rabbitMQExtension.managementAPI().listQueues()
            .forEach(queue -> sender.delete(QueueSpecification.queue(queue.getName())).block());
        sender.delete(ExchangeSpecification.exchange(MAILBOX_EVENT_EXCHANGE_NAME)).block();
        sender.close();

        rabbitMQExtension.afterEach(context);
    }

    RabbitMQEventBus newEventBus() {
        return new RabbitMQEventBus(connectionFactory, eventSerializer, routingKeyConverter);
    }

    static int totalEventsReceived() {
        return ALL_LISTENERS.stream()
            .map(listener -> listener.numberOfEventCalls())
            .reduce(Integer::sum)
            .get();
    }
}
