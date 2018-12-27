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

import java.nio.charset.StandardCharsets;

import org.apache.james.event.json.EventSerializer;
import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Delivery;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.ReceiverOptions;
import reactor.rabbitmq.Sender;

class KeyRegistrationHandler {
    private final MailboxListenerRegistry mailboxListenerRegistry;
    private final EventSerializer eventSerializer;
    private final Sender sender;
    private final RoutingKeyConverter routingKeyConverter;
    private final Receiver receiver;
    private String registrationQueue;
    private RegistrationBinder registrationBinder;

    KeyRegistrationHandler(EventSerializer eventSerializer, Sender sender, Mono<Connection> connectionMono, RoutingKeyConverter routingKeyConverter) {
        this.eventSerializer = eventSerializer;
        this.sender = sender;
        this.routingKeyConverter = routingKeyConverter;
        this.mailboxListenerRegistry = new MailboxListenerRegistry();
        this.receiver = RabbitFlux.createReceiver(new ReceiverOptions().connectionMono(connectionMono));
    }

    void start() {
        registrationQueue = sender.declareQueue(QueueSpecification.queue()
            .durable(DURABLE)
            .exclusive(EXCLUSIVE)
            .autoDelete(AUTO_DELETE)
            .arguments(NO_ARGUMENTS))
            .map(AMQP.Queue.DeclareOk::getQueue)
            .block();
        registrationBinder = new RegistrationBinder(sender, registrationQueue);

        receiver.consumeAutoAck(registrationQueue)
            .subscribeOn(Schedulers.parallel())
            .flatMap(this::handleDelivery)
            .subscribe();
    }

    void stop() {
        receiver.close();
        sender.delete(QueueSpecification.queue(registrationQueue)).block();
    }

    Registration register(MailboxListener listener, RegistrationKey key) {
        Runnable bindIfEmpty = () -> registrationBinder.bind(key).block();
        Runnable unbindIfEmpty = () -> registrationBinder.unbind(key).block();
        Runnable unregister = () -> mailboxListenerRegistry.removeListener(key, listener, unbindIfEmpty);

        KeyRegistration keyRegistration = new KeyRegistration(unregister);
        mailboxListenerRegistry.addListener(key, listener, bindIfEmpty);

        return keyRegistration;
    }

    private Mono<Void> handleDelivery(Delivery delivery) {
        if (delivery.getBody() == null) {
            return Mono.empty();
        }
        String routingKey = delivery.getEnvelope().getRoutingKey();
        RegistrationKey registrationKey = routingKeyConverter.toRegistrationKey(routingKey);
        Event event = toEvent(delivery);

        return mailboxListenerRegistry.getLocalMailboxListeners(registrationKey)
            .flatMap(listener -> Mono.fromRunnable(() -> listener.event(event)))
            .subscribeOn(Schedulers.elastic())
            .then();
    }

    private Event toEvent(Delivery delivery) {
        return eventSerializer.fromJson(new String(delivery.getBody(), StandardCharsets.UTF_8)).get();
    }
}
