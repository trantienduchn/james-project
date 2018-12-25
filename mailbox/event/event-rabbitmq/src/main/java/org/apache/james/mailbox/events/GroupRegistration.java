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

import static org.apache.james.mailbox.events.RabbitMQEventBus.DURABLE;
import static org.apache.james.mailbox.events.RabbitMQEventBus.EMPTY_ROUTING_KEY;
import static org.apache.james.mailbox.events.RabbitMQEventBus.MAILBOX_EVENT;
import static org.apache.james.mailbox.events.RabbitMQEventBus.MAILBOX_EVENT_EXCHANGE_NAME;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.apache.james.event.json.EventSerializer;
import org.apache.james.mailbox.MailboxListener;

import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Delivery;

import play.api.libs.json.JsResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.ReceiverOptions;
import reactor.rabbitmq.Sender;

class GroupRegistration implements Registration {

    private static final boolean AUTO_DELETE = true;
    private static final boolean EXCLUSIVE = true;
    private static final ImmutableMap<String, Object> NO_ARGUMENTS = ImmutableMap.of();

    static final String MAILBOX_EVENT_WORK_QUEUE_PREFIX = MAILBOX_EVENT + "-workQueue-";

    private final MailboxListener mailboxListener;
    private final String queueName;
    private final Receiver receiver;
    private final Runnable unregisterGroup;
    private final Sender sender;
    private final EventSerializer eventSerializer;

    GroupRegistration(Mono<Connection> connectionSupplier, Sender sender, EventSerializer eventSerializer,
                              MailboxListener mailboxListener, Group group, Runnable unregisterGroup) {
        this.eventSerializer = eventSerializer;
        this.mailboxListener = mailboxListener;
        this.queueName = MAILBOX_EVENT_WORK_QUEUE_PREFIX + group.getClass().getName();
        this.sender = sender;
        this.receiver = RabbitFlux.createReceiver(new ReceiverOptions().connectionMono(connectionSupplier));
        this.unregisterGroup = unregisterGroup;
    }

    void registerGroup() {
        createGroupWorkQueue()
            .doOnSuccess(any -> this.subscribeWorkQueue())
            .block();
    }

    private Mono<Void> createGroupWorkQueue() {
        return Flux.concat(
            sender.declareQueue(QueueSpecification.queue(queueName)
                .durable(DURABLE)
                .exclusive(!EXCLUSIVE)
                .autoDelete(!AUTO_DELETE)
                .arguments(NO_ARGUMENTS)),
            sender.bind(BindingSpecification.binding()
                .exchange(MAILBOX_EVENT_EXCHANGE_NAME)
                .queue(queueName)
                .routingKey(EMPTY_ROUTING_KEY)))
            .then();
    }

    private void subscribeWorkQueue() {
        receiver.consumeAutoAck(queueName)
            .subscribeOn(Schedulers.parallel())
            .map(Delivery::getBody)
            .filter(Objects::nonNull)
            .map(eventInBytes -> new String(eventInBytes, StandardCharsets.UTF_8))
            .map(eventSerializer::fromJson)
            .map(JsResult::get)
            .subscribeOn(Schedulers.elastic())
            .subscribe(mailboxListener::event);
    }

    @Override
    public void unregister() {
        receiver.close();
        unregisterGroup.run();
    }
}
