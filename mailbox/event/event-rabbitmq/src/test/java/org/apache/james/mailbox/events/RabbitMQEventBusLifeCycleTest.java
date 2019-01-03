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

import static org.apache.james.backend.rabbitmq.Constants.DIRECT_EXCHANGE;
import static org.apache.james.mailbox.events.EventBusContract.EVENT;
import static org.apache.james.mailbox.events.EventBusContract.KEY_1;
import static org.apache.james.mailbox.events.RabbitMQEventBus.MAILBOX_EVENT_EXCHANGE_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.james.backend.rabbitmq.RabbitMQExtension;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class RabbitMQEventBusLifeCycleTest {

    private static class GroupA extends Group {}

    static RabbitMQExtension rabbitMQExtension = new RabbitMQExtension();
    @RegisterExtension
    static RabbitMQEventBusExtension testExtension = new RabbitMQEventBusExtension(rabbitMQExtension);

    @BeforeAll
    static void setUp() {
        rabbitMQExtension.beforeAll(null);
    }

    @AfterAll
    static void tearDown() {
        rabbitMQExtension.afterAll(null);
    }

    @Nested
    class SingleEventBus {

        private RabbitMQEventBus eventBus;

        @BeforeEach
        void setUp() {
            eventBus = testExtension.newEventBus();
        }

        @AfterEach
        void tearDown() {
            eventBus.stop();
        }

        @Test
        void startShouldCreateEventExchange() {
            eventBus.start();
            assertThat(testExtension.rabbitMQManagement.listExchanges())
                .filteredOn(exchange -> exchange.getName().equals(MAILBOX_EVENT_EXCHANGE_NAME))
                .hasOnlyOneElementSatisfying(exchange -> {
                    assertThat(exchange.isDurable()).isTrue();
                    assertThat(exchange.getType()).isEqualTo(DIRECT_EXCHANGE);
                });
        }

        @Test
        void startShouldCreateKeyRegistrationWorkQueue() {
            eventBus.start();
            assertThat(testExtension.rabbitMQManagement.listQueues())
                .hasSize(1);
        }

        @Test
        void stopShouldNotDeleteEventBusExchange() {
            eventBus.start();
            eventBus.stop();

            assertThat(testExtension.rabbitMQManagement.listExchanges())
                .anySatisfy(exchange -> exchange.getName().equals(MAILBOX_EVENT_EXCHANGE_NAME));
        }

        @Test
        void stopShouldNotDeleteGroupRegistrationWorkQueue() {
            eventBus.start();
            eventBus.register(mock(MailboxListener.class), new GroupA());
            eventBus.stop();

            assertThat(testExtension.rabbitMQManagement.listQueues())
                .anySatisfy(queue -> queue.getName().contains(GroupA.class.getName()));
        }

        @Test
        void stopShouldDeleteKeyRegistrationWorkQueue() {
            eventBus.start();
            eventBus.stop();

            assertThat(testExtension.rabbitMQManagement.listQueues())
                .isEmpty();
        }

        @Test
        void eventBusShouldNotThrowWhenContinuouslyStartAndStop() {
            assertThatCode(() -> {
                eventBus.start();
                eventBus.stop();
                eventBus.stop();
                eventBus.start();
                eventBus.start();
                eventBus.start();
                eventBus.stop();
                eventBus.stop();
            }).doesNotThrowAnyException();
        }

        @Test
        void registrationsShouldNotHandleEventsAfterStop() throws Exception {
            eventBus.start();

            EventBusContract.MailboxListenerCountingSuccessfulExecution listener = new EventBusContract.MailboxListenerCountingSuccessfulExecution();
            eventBus.register(listener, new EventBusContract.GroupA()); // (1)

            int threadCount = 10;
            int operationCount = 1000;
            int maxEventsDispatched = threadCount * operationCount;
            ConcurrentTestRunner.builder()
                .operation((threadNumber, step) -> eventBus.dispatch(EVENT, KEY_1))
                .threadCount(10)
                .operationCount(1000)
                .runSuccessfullyWithin(Duration.ofSeconds(5));

            eventBus.stop();
            int callsAfterStop = listener.numberOfEventCalls();

            TimeUnit.SECONDS.sleep(1);
            assertThat(listener.numberOfEventCalls())
                .isEqualTo(callsAfterStop)
                .isLessThan(maxEventsDispatched);
        }
    }

    @Nested
    class MultipleEventBus {

        private RabbitMQEventBus eventBus1;
        private RabbitMQEventBus eventBus2;
        private RabbitMQEventBus eventBus3;

        @BeforeEach
        void setUp() {
            eventBus1 = testExtension.newEventBus();
            eventBus2 = testExtension.newEventBus();
            eventBus3 = testExtension.newEventBus();

            eventBus1.start();
            eventBus2.start();
            eventBus3.start();
        }

        @AfterEach
        void tearDown() {
            eventBus1.stop();
            eventBus2.stop();
            eventBus3.stop();
        }

        @Test
        void multipleEventBusStartShouldCreateOnlyOneEventExchange() {
            assertThat(testExtension.rabbitMQManagement.listExchanges())
                .filteredOn(exchange -> exchange.getName().equals(MAILBOX_EVENT_EXCHANGE_NAME))
                .hasSize(1);
        }

        @Test
        void multipleEventBusStartShouldCreateKeyRegistrationPerEachEventBus() {
            assertThat(testExtension.rabbitMQManagement.listQueues())
                .hasSize(3);
        }

        @Test
        void multipleEventBusShouldNotThrowWhenStartAndStopContinuously() {
            assertThatCode(() -> {
                eventBus1.start();
                eventBus1.start();
                eventBus2.start();
                eventBus2.start();
                eventBus1.stop();
                eventBus1.stop();
                eventBus1.stop();
                eventBus3.start();
                eventBus3.start();
                eventBus3.start();
                eventBus3.stop();
                eventBus1.start();
                eventBus2.start();
                eventBus1.stop();
                eventBus2.stop();
            }).doesNotThrowAnyException();
        }

        @Test
        void multipleEventBusStopShouldNotDeleteEventBusExchange() {
            eventBus1.stop();
            eventBus2.stop();
            eventBus3.stop();

            assertThat(testExtension.rabbitMQManagement.listExchanges())
                .anySatisfy(exchange -> exchange.getName().equals(MAILBOX_EVENT_EXCHANGE_NAME));
        }

        @Test
        void multipleEventBusStopShouldNotDeleteGroupRegistrationWorkQueue() {
            eventBus1.register(mock(MailboxListener.class), new GroupA());

            eventBus1.stop();
            eventBus2.stop();
            eventBus3.stop();

            assertThat(testExtension.rabbitMQManagement.listQueues())
                .anySatisfy(queue -> queue.getName().contains(GroupA.class.getName()));
        }

        @Test
        void multipleEventBusStopShouldDeleteAllKeyRegistrationsWorkQueue() {
            eventBus1.stop();
            eventBus2.stop();
            eventBus3.stop();

            assertThat(testExtension.rabbitMQManagement.listQueues())
                .isEmpty();
        }

        @Test
        void registrationsShouldNotHandleEventsAfterStop() throws Exception {
            eventBus1.start();
            eventBus2.start();

            EventBusContract.MailboxListenerCountingSuccessfulExecution listener = new EventBusContract.MailboxListenerCountingSuccessfulExecution();
            eventBus1.register(listener, new EventBusContract.GroupA());
            eventBus2.register(listener, new EventBusContract.GroupA());

            int threadCount = 10;
            int operationCount = 1000;
            int maxEventsDispatched = threadCount * operationCount;
            ConcurrentTestRunner.builder()
                .operation((threadNumber, step) -> eventBus1.dispatch(EVENT, KEY_1))
                .threadCount(10)
                .operationCount(1000)
                .runSuccessfullyWithin(Duration.ofSeconds(5));

            eventBus1.stop();
            eventBus2.stop();
            int callsAfterStop = listener.numberOfEventCalls();

            TimeUnit.SECONDS.sleep(1);
            assertThat(listener.numberOfEventCalls())
                .isEqualTo(callsAfterStop)
                .isLessThan(maxEventsDispatched);
        }
    }
}
