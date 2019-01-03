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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.model.TestId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MailboxListenerRegistryTest {
    private static final MailboxIdRegistrationKey KEY_1 = new MailboxIdRegistrationKey(TestId.of(42));

    private MailboxListenerRegistry testee;

    @BeforeEach
    void setUp() {
        testee = new MailboxListenerRegistry();
    }

    @Test
    void getLocalMailboxListenersShouldReturnEmptyWhenNone() {
        assertThat(testee.getLocalMailboxListeners(KEY_1).collectList().block())
            .isEmpty();
    }

    @Test
    void getLocalMailboxListenersShouldReturnPreviouslyAddedListener() {
        MailboxListener listener = mock(MailboxListener.class);
        testee.addListener(KEY_1, listener);

        assertThat(testee.getLocalMailboxListeners(KEY_1).collectList().block())
            .containsOnly(listener);
    }

    @Test
    void getLocalMailboxListenersShouldReturnPreviouslyAddedListeners() {
        MailboxListener listener1 = mock(MailboxListener.class);
        MailboxListener listener2 = mock(MailboxListener.class);
        testee.addListener(KEY_1, listener1);
        testee.addListener(KEY_1, listener2);

        assertThat(testee.getLocalMailboxListeners(KEY_1).collectList().block())
            .containsOnly(listener1, listener2);
    }

    @Test
    void getLocalMailboxListenersShouldNotReturnRemovedListeners() {
        MailboxListener listener1 = mock(MailboxListener.class);
        MailboxListener listener2 = mock(MailboxListener.class);
        testee.addListener(KEY_1, listener1);
        testee.addListener(KEY_1, listener2);

        testee.removeListener(KEY_1, listener2);

        assertThat(testee.getLocalMailboxListeners(KEY_1).collectList().block())
            .containsOnly(listener1);
    }
}