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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.james.core.User;
import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.Test;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import reactor.core.publisher.Mono;

interface EventDeadLettersContract {

    class Group0 extends Group{}
    class Group1 extends Group{}
    class Group2 extends Group{}
    class Group3 extends Group{}
    class Group4 extends Group{}
    class Group5 extends Group{}
    class Group6 extends Group{}
    class Group7 extends Group{}
    class Group8 extends Group{}
    class Group9 extends Group{}

    static ConcurrentHashMap<Integer, Group> concurrentGroups() {
        ConcurrentHashMap<Integer, Group> concurrentGroups = new ConcurrentHashMap<>();
        concurrentGroups.put(0, new Group0());
        concurrentGroups.put(1, new Group1());
        concurrentGroups.put(2, new Group2());
        concurrentGroups.put(3, new Group3());
        concurrentGroups.put(4, new Group4());
        concurrentGroups.put(5, new Group5());
        concurrentGroups.put(6, new Group6());
        concurrentGroups.put(7, new Group7());
        concurrentGroups.put(8, new Group8());
        concurrentGroups.put(9, new Group9());
        return concurrentGroups;
    }

    static void runConcurrent(ConcurrentTestRunner.ConcurrentOperation concurrentOperation) throws Exception {
        ConcurrentTestRunner.builder()
            .operation(concurrentOperation)
            .threadCount(THREAD_COUNT)
            .operationCount(OPERATION_COUNT)
            .runSuccessfullyWithin(Duration.ofSeconds(5));
    }

    static Event event(Event.EventId eventId) {
        return new MailboxListener.MailboxAdded(SESSION_ID, USER, MAILBOX_PATH, MAILBOX_ID, eventId);
    }

    int THREAD_COUNT = 10;
    int OPERATION_COUNT = 50;
    int TOTAL_OPERATIONS = THREAD_COUNT * OPERATION_COUNT;
    List<Integer> OPERATION_INDEXES = IntStream.range(0, TOTAL_OPERATIONS).boxed()
        .collect(Guavate.toImmutableList());

    MailboxPath MAILBOX_PATH = new MailboxPath(MailboxConstants.USER_NAMESPACE, "user", "mailboxName");
    User USER = User.fromUsername("user");
    MailboxSession.SessionId SESSION_ID = MailboxSession.SessionId.of(235);
    TestId MAILBOX_ID = TestId.of(563);
    Event.EventId EVENT_ID_1 = Event.EventId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b4");
    Event.EventId EVENT_ID_2 = Event.EventId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b5");
    Event.EventId EVENT_ID_3 = Event.EventId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b6");
    MailboxListener.MailboxAdded EVENT_1 = new MailboxListener.MailboxAdded(SESSION_ID, USER, MAILBOX_PATH, MAILBOX_ID, EVENT_ID_1);
    MailboxListener.MailboxAdded EVENT_2 = new MailboxListener.MailboxAdded(SESSION_ID, USER, MAILBOX_PATH, MAILBOX_ID, EVENT_ID_2);
    MailboxListener.MailboxAdded EVENT_3 = new MailboxListener.MailboxAdded(SESSION_ID, USER, MAILBOX_PATH, MAILBOX_ID, EVENT_ID_3);

    Group GROUP_A = new EventBusContract.GroupA();
    Group GROUP_B = new EventBusContract.GroupB();
    Group NULL_GROUP = null;
    Event NULL_EVENT = null;
    Event.EventId NULL_EVENT_ID = null;

    EventDeadLetters eventDeadLetters();

    default Stream<Event.EventId> allEventIds() {
        EventDeadLetters eventDeadLetters = eventDeadLetters();

        return eventDeadLetters.groupsWithFailedEvents()
            .flatMap(eventDeadLetters::failedEventIds)
            .toStream();
    }

    interface StoreContract extends EventDeadLettersContract {

        @Test
        default void storeShouldThrowWhenNullGroup() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            assertThatThrownBy(() -> eventDeadLetters.store(NULL_GROUP, EVENT_1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        default void storeShouldThrowWhenNullEvent() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            assertThatThrownBy(() -> eventDeadLetters.store(GROUP_A, NULL_EVENT))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        default void storeShouldThrowWhenBothGroupAndEventAreNull() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            assertThatThrownBy(() -> eventDeadLetters.store(NULL_GROUP, NULL_EVENT))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        default void storeShouldStoreGroupWithCorrespondingEvent() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            eventDeadLetters.store(GROUP_A, EVENT_1).block();
            assertThat(eventDeadLetters.failedEventId(GROUP_A, EVENT_1.getEventId()).block())
                .isEqualTo(EVENT_ID_1);
        }

        @Test
        default void storeShouldIgnoreStoreDuplicatedEventsPerGroup() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            eventDeadLetters.store(GROUP_A, EVENT_1).block();
            eventDeadLetters.store(GROUP_A, EVENT_1).block();
            eventDeadLetters.store(GROUP_A, EVENT_1).block();

            assertThat(eventDeadLetters.failedEventIds(GROUP_A).toStream())
                .containsExactly(EVENT_ID_1);
        }

        @Test
        default void storeShouldKeepConsistencyWhenConcurrentStore() throws Exception {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            ConcurrentHashMap<Integer, Group> groups = concurrentGroups();
            Multimap<Integer, Event.EventId> storedEventIds = Multimaps.synchronizedSetMultimap(HashMultimap.create());
            runConcurrent((threadNumber, step) -> {
                Event.EventId eventId = Event.EventId.random();
                storedEventIds.put(threadNumber, eventId);
                eventDeadLetters.store(groups.get(threadNumber), event(eventId));
            });

            groups.forEach((groupId, group) -> {
                Group storedGroup = groups.get(groupId);
                assertThat(eventDeadLetters.failedEventIds(storedGroup).toStream())
                    .hasSameElementsAs(storedEventIds.get(groupId));
            });
        }
    }

    interface RemoveContract extends EventDeadLettersContract {

        @Test
        default void removeShouldThrowWhenGroupIsNull() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            assertThatThrownBy(() -> eventDeadLetters.remove(NULL_GROUP, EVENT_ID_1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        default void removeShouldThrowWhenEventIdIsNull() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            assertThatThrownBy(() -> eventDeadLetters.remove(GROUP_A, NULL_EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        default void removeShouldThrowWhenBothGroupAndEventIdAreNull() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            assertThatThrownBy(() -> eventDeadLetters.remove(NULL_GROUP, NULL_EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        default void removeShouldRemoveMatched() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            eventDeadLetters.store(GROUP_A, EVENT_1).block();
            eventDeadLetters.store(GROUP_A, EVENT_2).block();

            eventDeadLetters.remove(GROUP_A, EVENT_1.getEventId()).block();

            assertThat(eventDeadLetters.failedEventId(GROUP_A, EVENT_1.getEventId()).block())
                .isNull();
        }

        @Test
        default void removeShouldKeepNonMatched() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            eventDeadLetters.store(GROUP_A, EVENT_1).block();
            eventDeadLetters.store(GROUP_A, EVENT_2).block();
            eventDeadLetters.store(GROUP_A, EVENT_3).block();

            eventDeadLetters.remove(GROUP_A, EVENT_1.getEventId()).block();

            assertThat(eventDeadLetters.failedEventIds(GROUP_A).toStream())
                .containsOnly(EVENT_ID_2, EVENT_ID_3);
        }

        @Test
        default void removeShouldNotThrowWhenNoMatched() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            eventDeadLetters.store(GROUP_A, EVENT_1).block();

            assertThatCode(() -> eventDeadLetters.remove(GROUP_A, EVENT_2.getEventId()).block())
                .doesNotThrowAnyException();
        }

        @Test
        default void removeShouldKeepConsistencyWhenConcurrentRemove() throws Exception {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            ConcurrentHashMap<Integer, Group> groups = concurrentGroups();
            ConcurrentHashMap<Integer, Event.EventId> storedEventIds = new ConcurrentHashMap<>();
            OPERATION_INDEXES.forEach(operationIndex -> {
                int groupNumber = operationIndex / OPERATION_COUNT;
                Event.EventId eventId = Event.EventId.random();
                storedEventIds.put(operationIndex, eventId);
                eventDeadLetters.store(groups.get(groupNumber), event(eventId));
            });

            runConcurrent((threadNumber, step) -> {
                int operationIndex = threadNumber * OPERATION_COUNT + step;
                eventDeadLetters.remove(groups.get(threadNumber), storedEventIds.get(operationIndex));
            });

            assertThat(allEventIds())
                .isEmpty();
        }
    }

    interface FailedEventContract extends EventDeadLettersContract {

        @Test
        default void failedEventShouldThrowWhenGroupIsNull() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            assertThatThrownBy(() -> eventDeadLetters.failedEventId(NULL_GROUP, EVENT_ID_1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        default void failedEventShouldThrowWhenEventIdIsNull() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            assertThatThrownBy(() -> eventDeadLetters.failedEventId(GROUP_A, NULL_EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        default void failedEventShouldThrowWhenBothGroupAndEventIdAreNull() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            assertThatThrownBy(() -> eventDeadLetters.failedEventId(NULL_GROUP, NULL_EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        default void failedEventShouldReturnEmptyWhenNotFound() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            eventDeadLetters.store(GROUP_A, EVENT_1).block();
            eventDeadLetters.store(GROUP_A, EVENT_2).block();

            assertThat(eventDeadLetters.failedEventId(GROUP_A, EVENT_ID_3))
                .isEqualTo(Mono.empty());
        }

        @Test
        default void failedEventShouldReturnEventWhenContains() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            eventDeadLetters.store(GROUP_A, EVENT_1).block();
            eventDeadLetters.store(GROUP_A, EVENT_2).block();

            assertThat(eventDeadLetters.failedEventId(GROUP_A, EVENT_1.getEventId()).block())
                .isEqualTo(EVENT_ID_1);
        }

        @Test
        default void failedEventShouldNotRemoveEvent() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            eventDeadLetters.store(GROUP_A, EVENT_1).block();
            eventDeadLetters.store(GROUP_A, EVENT_2).block();
            eventDeadLetters.store(GROUP_A, EVENT_3).block();

            eventDeadLetters.failedEventId(GROUP_A, EVENT_1.getEventId()).block();

            assertThat(allEventIds())
                .containsOnly(EVENT_ID_1, EVENT_ID_2, EVENT_ID_3);
        }
    }

    interface FailedEventsContract extends EventDeadLettersContract {

        @Test
        default void failedEventsShouldThrowWhenGroupIsNull() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            assertThatThrownBy(() -> eventDeadLetters.failedEventIds(NULL_GROUP))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        default void failedEventsByGroupShouldReturnEmptyWhenNonMatch() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            eventDeadLetters.store(GROUP_A, EVENT_1).block();
            eventDeadLetters.store(GROUP_A, EVENT_2).block();
            eventDeadLetters.store(GROUP_A, EVENT_3).block();

            assertThat(eventDeadLetters.failedEventIds(GROUP_B).toStream())
                .isEmpty();
        }

        @Test
        default void failedEventsByGroupShouldReturnAllEventsCorrespondingToGivenGroup() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            eventDeadLetters.store(GROUP_A, EVENT_1).block();
            eventDeadLetters.store(GROUP_A, EVENT_2).block();
            eventDeadLetters.store(GROUP_B, EVENT_3).block();

            assertThat(eventDeadLetters.failedEventIds(GROUP_A).toStream())
                .containsOnly(EVENT_ID_1, EVENT_ID_2);
        }

        @Test
        default void failedEventsByGroupShouldNotRemoveEvents() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            eventDeadLetters.store(GROUP_A, EVENT_1).block();
            eventDeadLetters.store(GROUP_A, EVENT_2).block();
            eventDeadLetters.store(GROUP_B, EVENT_3).block();

            eventDeadLetters.failedEventIds(GROUP_A).toStream();

            assertThat(allEventIds())
                .containsOnly(EVENT_ID_1, EVENT_ID_2, EVENT_ID_3);
        }
    }

    interface GroupsWithFailedEventsContract extends EventDeadLettersContract {
        @Test
        default void groupsWithFailedEventsShouldReturnAllStoredGroups() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();
            eventDeadLetters.store(GROUP_A, EVENT_1).block();
            eventDeadLetters.store(GROUP_B, EVENT_1).block();

            assertThat(eventDeadLetters.groupsWithFailedEvents().toStream())
                .containsOnly(GROUP_A, GROUP_B);
        }

        @Test
        default void groupsWithFailedEventsShouldReturnEmptyWhenNoStored() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            assertThat(eventDeadLetters.groupsWithFailedEvents().toStream()).isEmpty();
        }
    }
}