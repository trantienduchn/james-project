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

import static com.jayway.awaitility.Awaitility.await;
import static org.apache.james.mailbox.events.EventBusContract.ALL_KEYS;
import static org.apache.james.mailbox.events.EventBusContract.EVENT;
import static org.apache.james.mailbox.events.EventBusContract.KEY_1;
import static org.apache.james.mailbox.events.EventBusContract.KEY_2;
import static org.apache.james.mailbox.events.EventBusContract.KEY_3;
import static org.apache.james.mailbox.events.RabbitMQEventBusConcurrentExtension.COUNTING_LISTENER_1;
import static org.apache.james.mailbox.events.RabbitMQEventBusConcurrentExtension.COUNTING_LISTENER_2;
import static org.apache.james.mailbox.events.RabbitMQEventBusConcurrentExtension.COUNTING_LISTENER_3;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.james.backend.rabbitmq.RabbitMQExtension;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;
import org.testcontainers.shaded.com.google.common.collect.ImmutableSet;

import com.jayway.awaitility.core.ConditionFactory;

class RabbitMQEventBusConcurrentTest {

    private static final Duration FIVE_SECONDS = Duration.ofSeconds(5);
    private static final ConditionFactory AWAIT_CONDITION = await().timeout(new com.jayway.awaitility.Duration(5, TimeUnit.SECONDS));
    private static final ImmutableSet<RegistrationKey> NO_KEYS = ImmutableSet.of();

    private static final int THREAD_COUNT = 10;
    private static final int OPERATION_COUNT = 30;
    private static final int TOTAL_DISPATCH_OPERATIONS = THREAD_COUNT * OPERATION_COUNT;

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = new RabbitMQExtension();
    @RegisterExtension
    static RabbitMQEventBusConcurrentExtension testExtension = new RabbitMQEventBusConcurrentExtension(rabbitMQExtension);

    @BeforeAll
    static void beforeAll() {
        rabbitMQExtension.beforeAll(null);
    }

    @AfterAll
    static void afterAll() {
        rabbitMQExtension.afterAll(null);
    }

    private static void runConcurrent(ConcurrentTestRunner.ConcurrentOperation operation) throws Exception {
        ConcurrentTestRunner.builder()
            .operation(operation)
            .threadCount(THREAD_COUNT)
            .operationCount(OPERATION_COUNT)
            .runSuccessfullyWithin(FIVE_SECONDS);
    }


    @Nested
    class SingleEventBus {

        private RabbitMQEventBus eventBus1;

        @BeforeEach
        void setUp() {
            eventBus1 = testExtension.newEventBus();
            eventBus1.start();
        }

        @AfterEach
        void tearDown() {
            eventBus1.stop();
        }

        @Test
        void concurrentDispatchGroupShouldDeliverAllEventsToListeners() throws Exception {
            eventBus1.register(COUNTING_LISTENER_1, new EventBusContract.GroupA());
            eventBus1.register(COUNTING_LISTENER_2, new EventBusContract.GroupB());
            eventBus1.register(COUNTING_LISTENER_3, new EventBusContract.GroupC());
            int totalGlobalRegistrations = 3; // GroupA + GroupB + GroupC

            runConcurrent((threadNumber, operationNumber) -> eventBus1.dispatch(EVENT, NO_KEYS));

            AWAIT_CONDITION.until(() -> assertThat(RabbitMQEventBusConcurrentExtension.totalEventsReceived())
                .isEqualTo(totalGlobalRegistrations * TOTAL_DISPATCH_OPERATIONS));
        }

        @Test
        void concurrentDispatchKeyShouldDeliverAllEventsToListeners() throws Exception {
            eventBus1.register(COUNTING_LISTENER_1, KEY_1);
            eventBus1.register(COUNTING_LISTENER_2, KEY_2);
            eventBus1.register(COUNTING_LISTENER_2, KEY_3);
            int totalKeyListenerRegistrations = 3; // KEY1 + KEY2 + KEY3
            int totalEventBus = 1;

            runConcurrent((threadNumber, operationNumber) -> eventBus1.dispatch(EVENT, ALL_KEYS));

            AWAIT_CONDITION.until(() -> assertThat(RabbitMQEventBusConcurrentExtension.totalEventsReceived())
                .isEqualTo(totalKeyListenerRegistrations * totalEventBus * TOTAL_DISPATCH_OPERATIONS));
        }

        @Test
        void concurrentDispatchShouldDeliverAllEventsToListeners() throws Exception {
            eventBus1.register(COUNTING_LISTENER_1, new EventBusContract.GroupA());
            eventBus1.register(COUNTING_LISTENER_2, new EventBusContract.GroupB());
            eventBus1.register(COUNTING_LISTENER_3, new EventBusContract.GroupC());
            int totalGlobalRegistrations = 3; // GroupA + GroupB + GroupC
            int totalEventDeliveredGlobally = totalGlobalRegistrations * TOTAL_DISPATCH_OPERATIONS;

            eventBus1.register(COUNTING_LISTENER_1, KEY_1);
            eventBus1.register(COUNTING_LISTENER_2, KEY_2);
            eventBus1.register(COUNTING_LISTENER_2, KEY_3);
            int totalKeyListenerRegistrations = 3; // KEY1 + KEY2 + KEY3
            int totalEventDeliveredByKeys = totalKeyListenerRegistrations * TOTAL_DISPATCH_OPERATIONS;

            runConcurrent((threadNumber, operationNumber) -> eventBus1.dispatch(EVENT, ALL_KEYS));

            AWAIT_CONDITION.until(() -> assertThat(RabbitMQEventBusConcurrentExtension.totalEventsReceived())
                .isEqualTo(totalEventDeliveredGlobally + totalEventDeliveredByKeys));
        }
    }

    @Nested
    class MultipleEventBus {

        private RabbitMQEventBus eventBus1;
        private RabbitMQEventBus eventBus2;
        private RabbitMQEventBus eventBus3;

        private List<RabbitMQEventBus> allEventBus;

        @BeforeEach
        void setUp() {
            eventBus1 = testExtension.newEventBus();
            eventBus2 = testExtension.newEventBus();
            eventBus3 = testExtension.newEventBus();

            eventBus1.start();
            eventBus2.start();
            eventBus3.start();

            allEventBus = ImmutableList.of(eventBus1, eventBus2, eventBus3);
        }

        @AfterEach
        void tearDown() {
            allEventBus.forEach(RabbitMQEventBus::stop);
        }

        @Test
        void concurrentDispatchGroupShouldDeliverAllEventsToListeners() throws Exception {
            eventBus1.register(COUNTING_LISTENER_1, new EventBusContract.GroupA());
            eventBus1.register(COUNTING_LISTENER_2, new EventBusContract.GroupB());
            eventBus1.register(COUNTING_LISTENER_3, new EventBusContract.GroupC());

            eventBus2.register(COUNTING_LISTENER_1, new EventBusContract.GroupA());
            eventBus2.register(COUNTING_LISTENER_2, new EventBusContract.GroupB());
            eventBus2.register(COUNTING_LISTENER_3, new EventBusContract.GroupC());

            int totalGlobalRegistrations = 3; // GroupA + GroupB + GroupC

            runConcurrent((threadNumber, operationNumber) -> eventBus1.dispatch(EVENT, NO_KEYS));

            AWAIT_CONDITION.until(() -> assertThat(RabbitMQEventBusConcurrentExtension.totalEventsReceived())
                .isEqualTo(totalGlobalRegistrations * TOTAL_DISPATCH_OPERATIONS));
        }

        @Test
        void concurrentDispatchKeyShouldDeliverAllEventsToListeners() throws Exception {
            eventBus1.register(COUNTING_LISTENER_1, KEY_1);
            eventBus1.register(COUNTING_LISTENER_2, KEY_2);
            eventBus1.register(COUNTING_LISTENER_3, KEY_3);

            eventBus2.register(COUNTING_LISTENER_1, KEY_1);
            eventBus2.register(COUNTING_LISTENER_2, KEY_2);
            eventBus2.register(COUNTING_LISTENER_3, KEY_3);

            int totalKeyListenerRegistrations = 3; // KEY1 + KEY2 + KEY3
            int totalEventBus = 2; // eventBus1 + eventBus2

            runConcurrent((threadNumber, operationNumber) -> eventBus1.dispatch(EVENT, ALL_KEYS));

            AWAIT_CONDITION.until(() -> assertThat(RabbitMQEventBusConcurrentExtension.totalEventsReceived())
                .isEqualTo(totalKeyListenerRegistrations * totalEventBus * TOTAL_DISPATCH_OPERATIONS));
        }

        @Test
        void concurrentDispatchShouldDeliverAllEventsToListeners() throws Exception {
            eventBus2.register(COUNTING_LISTENER_1, new EventBusContract.GroupA());
            eventBus2.register(COUNTING_LISTENER_2, new EventBusContract.GroupB());
            eventBus2.register(COUNTING_LISTENER_3, new EventBusContract.GroupC());
            int totalGlobalRegistrations = 3; // GroupA + GroupB + GroupC
            int totalEventDeliveredGlobally = totalGlobalRegistrations * TOTAL_DISPATCH_OPERATIONS;

            eventBus1.register(COUNTING_LISTENER_1, KEY_1);
            eventBus1.register(COUNTING_LISTENER_2, KEY_2);

            eventBus2.register(COUNTING_LISTENER_1, KEY_1);
            eventBus2.register(COUNTING_LISTENER_2, KEY_2);

            eventBus3.register(COUNTING_LISTENER_3, KEY_1);
            eventBus3.register(COUNTING_LISTENER_3, KEY_2);

            int totalKeyListenerRegistrations = 2; // KEY1 + KEY2
            int totalEventBus = 3; // eventBus1 + eventBus2 + eventBus3
            int totalEventDeliveredByKeys = totalKeyListenerRegistrations * totalEventBus * TOTAL_DISPATCH_OPERATIONS;

            runConcurrent((threadNumber, operationNumber) -> eventBus1.dispatch(EVENT, ALL_KEYS));

            AWAIT_CONDITION.until(() -> assertThat(RabbitMQEventBusConcurrentExtension.totalEventsReceived())
                .isEqualTo(totalEventDeliveredGlobally + totalEventDeliveredByKeys));
        }
    }
}
