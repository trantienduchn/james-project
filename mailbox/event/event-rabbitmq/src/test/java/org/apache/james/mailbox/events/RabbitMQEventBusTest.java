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

import org.apache.james.backend.rabbitmq.RabbitMQExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class RabbitMQEventBusTest implements EventBusContract {

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
    }

    @AfterEach
    void tearDown() {
        eventBus.stop();
    }

    @Override
    public EventBus eventBus() {
        return eventBus;
    }

    @Override
    @Test
    @Disabled("This test is failing by design as the different registration keys are handled by distinct messages")
    public void dispatchShouldCallListenerOnceWhenSeveralKeysMatching() {

    }
}
