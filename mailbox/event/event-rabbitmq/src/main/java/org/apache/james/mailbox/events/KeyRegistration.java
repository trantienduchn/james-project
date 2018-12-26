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

import reactor.core.publisher.Mono;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.Sender;

class KeyRegistration implements Registration {
    private final Sender sender;
    private final RegistrationKey key;
    private final RegistrationQueueName registrationQueue;
    private final Runnable unregister;

    KeyRegistration(Sender sender, RegistrationKey key, RegistrationQueueName registrationQueue, Runnable unregister) {
        this.sender = sender;
        this.key = key;
        this.registrationQueue = registrationQueue;
        this.unregister = unregister;
    }

    Mono<Void> createRegistrationBinding() {
        return sender.bind(bindingSpecification())
            .then();
    }

    private BindingSpecification bindingSpecification() {
        String routingKey = RoutingKeyConverter.toRoutingKey(key);
        return BindingSpecification.binding()
            .exchange(MAILBOX_EVENT_EXCHANGE_NAME)
            .queue(registrationQueue.asString())
            .routingKey(routingKey);
    }

    @Override
    public void unregister() {
        unregister.run();
    }
}
