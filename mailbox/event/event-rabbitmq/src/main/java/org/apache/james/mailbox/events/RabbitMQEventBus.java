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

import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.james.backend.rabbitmq.RabbitMQConnectionFactory;
import org.apache.james.event.json.EventSerializer;
import org.apache.james.metrics.api.MetricFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.SendOptions;
import reactor.rabbitmq.Sender;
import reactor.rabbitmq.SenderOptions;

public class RabbitMQEventBus implements EventBus {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQEventBus.class);

    static final String MAILBOX_EVENT = "mailboxEvent";
    static final String MAILBOX_EVENT_EXCHANGE_NAME = MAILBOX_EVENT + "-exchange";
    static final String EVENT_BUS_ID = "eventBusId";

    private final Mono<Connection> connectionMono;
    private final EventSerializer eventSerializer;
    private final RoutingKeyConverter routingKeyConverter;
    private final RetryBackoffConfiguration retryBackoff;
    private final EventBusId eventBusId;
    private final EventDeadLetters eventDeadLetters;
    private final MailboxListenerExecutor mailboxListenerExecutor;
    private final SendOptions sendOptions;
    private final Mono<Channel> channelMono;
    private final BiConsumer<SignalType, Channel> channelCloseHandler;
    private final Set<Channel> channels = new HashSet<>();

    private volatile boolean isRunning;
    private volatile boolean isStopping;
    private GroupRegistrationHandler groupRegistrationHandler;
    private KeyRegistrationHandler keyRegistrationHandler;
    private EventDispatcher eventDispatcher;
    private Sender sender;

    @Inject
    public RabbitMQEventBus(RabbitMQConnectionFactory rabbitMQConnectionFactory, EventSerializer eventSerializer,
                     RetryBackoffConfiguration retryBackoff,
                     RoutingKeyConverter routingKeyConverter,
                     EventDeadLetters eventDeadLetters, MetricFactory metricFactory) {
        this.mailboxListenerExecutor = new MailboxListenerExecutor(metricFactory);
        this.eventBusId = EventBusId.random();
        this.connectionMono = rabbitMQConnectionFactory.connectionMono();
        this.eventSerializer = eventSerializer;
        this.routingKeyConverter = routingKeyConverter;
        this.retryBackoff = retryBackoff;
        this.eventDeadLetters = eventDeadLetters;
        this.isRunning = false;
        this.isStopping = false;
        this.channelMono = connectionMono.map(Throwing.function(this::createChannel));
        this.channelCloseHandler = (signalType, channel) -> LOGGER.info("Do not close channel {} by signal {}", channel, signalType);
        this.sendOptions = new SendOptions()
            .channelMono(channelMono)
            .channelCloseHandler(channelCloseHandler);
    }

    private Channel createChannel(Connection connection) throws IOException {
        Channel channel = connection.createChannel();
        channels.add(channel);
        return channel;
    }

    public void start() {
        if (!isRunning && !isStopping) {
            sender = RabbitFlux.createSender(new SenderOptions().resourceManagementChannelMono(channelMono).channelCloseHandler(channelCloseHandler));
            LocalListenerRegistry localListenerRegistry = new LocalListenerRegistry();
            keyRegistrationHandler = new KeyRegistrationHandler(eventBusId, eventSerializer, sender, connectionMono, routingKeyConverter, localListenerRegistry, mailboxListenerExecutor);
            groupRegistrationHandler = new GroupRegistrationHandler(eventSerializer, sender, sendOptions, connectionMono, retryBackoff, eventDeadLetters, mailboxListenerExecutor);
            eventDispatcher = new EventDispatcher(eventBusId, eventSerializer, sender, sendOptions, localListenerRegistry, mailboxListenerExecutor);

            eventDispatcher.start();
            keyRegistrationHandler.start();
            isRunning = true;
        }
    }

    @PreDestroy
    public void stop() {
        if (isRunning && !isStopping) {
            isStopping = true;
            isRunning = false;
            groupRegistrationHandler.stop();
            keyRegistrationHandler.stop();
            channels.forEach(this::closeChannel);
            sender.close();
        }
    }

    @Override
    public Registration register(MailboxListener listener, RegistrationKey key) {
        if (isRunning) {
            return keyRegistrationHandler.register(listener, key);
        }
        throw new IllegalStateException("Event Bus is not running");
    }

    @Override
    public Registration register(MailboxListener listener, Group group) {
        if (isRunning) {
            return groupRegistrationHandler.register(listener, group);
        }
        throw new IllegalStateException("Event Bus is not running");
    }

    @Override
    public Mono<Void> dispatch(Event event, Set<RegistrationKey> key) {
        if (isRunning) {
            if (!event.isNoop()) {
                return eventDispatcher.dispatch(event, key);
            }
            return Mono.empty();
        }
        throw new IllegalStateException("Event Bus is not running");
    }

    @VisibleForTesting
    SendOptions getSendOptions() {
        return sendOptions;
    }

    @VisibleForTesting
    Set<Channel> getChannels() {
        return channels;
    }

    private void closeChannel(Channel channel) {
        Optional.ofNullable(channel)
            .filter(Channel::isOpen)
            .ifPresent(Throwing.consumer(Channel::close));
    }
}