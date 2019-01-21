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

import org.apache.james.mailbox.events.delivery.InVmEventDelivery;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class InVMEventBusTest implements KeyContract.SingleEventBusKeyContract, GroupContract.SingleEventBusGroupContract,
    ErrorHandlingContract{

    private InVMEventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new InVMEventBus(
            new InVmEventDelivery(
                new NoopMetricFactory(), RetryBackoffConfiguration.DEFAULT));
    }

    @Override
    public EventBus eventBus() {
        return eventBus;
    }

    @Override
    public EventDeadLetters deadLetter() {
        throw new RuntimeException("this method is not a part of this task contents, will be handled in another pull request");
    }

    @Test
    @Disabled("this test is not a part of this task contents, will be handled in another pull request")
    @Override
    public void deadLettersIsNotAppliedForKeyRegistrations() {
    }

    @Test
    @Disabled("this test is not a part of this task contents, will be handled in another pull request")
    @Override
    public void deadLetterShouldNotStoreWhenFailsLessThanMaxRetries() {
    }

    @Test
    @Disabled("this test is not a part of this task contents, will be handled in another pull request")
    @Override
    public void deadLetterShouldStoreWhenFailsGreaterThanMaxRetries() {
    }
}