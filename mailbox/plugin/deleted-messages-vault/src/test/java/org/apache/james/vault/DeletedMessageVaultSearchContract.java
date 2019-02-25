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

package org.apache.james.vault;

import static org.apache.james.vault.DeletedMessageFixture.CONTENT;
import static org.apache.james.vault.DeletedMessageFixture.DELETION_DATE;
import static org.apache.james.vault.DeletedMessageFixture.DELIVERY_DATE;
import static org.apache.james.vault.DeletedMessageFixture.MAILBOX_ID_1;
import static org.apache.james.vault.DeletedMessageFixture.USER;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT1;
import static org.apache.mailet.base.MailAddressFixture.SENDER;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.core.MaybeSender;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.vault.search.CriterionFactory;
import org.apache.james.vault.search.Query;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DeletedMessageVaultSearchContract {
    DeletedMessageVault getVault();

    interface AllContracts extends SubjectContract, DeletionDateContract, DeliveryDateContract {
    }

    interface DeliveryDateContract extends DeletedMessageVaultSearchContract {

        default DeletedMessage storeMessageWithDeliveryDate(ZonedDateTime deliveryDate) {
            DeletedMessage deletedMessage = DeletedMessage.builder()
                .messageId(InMemoryMessageId.of(MESSAGE_ID_GENERATOR.incrementAndGet()))
                .originMailboxes(MAILBOX_ID_1)
                .user(USER)
                .deliveryDate(deliveryDate)
                .deletionDate(DELETION_DATE)
                .sender(MaybeSender.of(SENDER))
                .recipients(RECIPIENT1)
                .content(() -> new ByteArrayInputStream(CONTENT))
                .hasAttachment(false)
                .build();

            Mono.from(getVault().append(USER, deletedMessage))
                .block();
            return deletedMessage;
        }

        @Test
        default void shouldReturnMessagesWithDeliveryBeforeDateWhenBeforeOrEquals() {
            DeletedMessage message1 = storeMessageWithDeliveryDate(DELIVERY_DATE);
            DeletedMessage message2 = storeMessageWithDeliveryDate(DELIVERY_DATE.plusMinutes(60));

            assertThat(search(Query.of(CriterionFactory.deliveryDate().beforeOrEquals(DELIVERY_DATE.plusMinutes(30)))))
                .containsOnly(message1);
        }

        @Test
        default void shouldReturnMessagesWithDeliveryEqualDateWhenBeforeOrEquals() {
            DeletedMessage message1 = storeMessageWithDeliveryDate(DELIVERY_DATE);
            DeletedMessage message2 = storeMessageWithDeliveryDate(DELIVERY_DATE.plusMinutes(60));

            assertThat(search(Query.of(CriterionFactory.deliveryDate().beforeOrEquals(DELIVERY_DATE))))
                .containsOnly(message1);
        }

        @Test
        default void shouldReturnMessagesWithDeliveryAfterDateWhenAfterOrEquals() {
            DeletedMessage message1 = storeMessageWithDeliveryDate(DELIVERY_DATE);
            DeletedMessage message2 = storeMessageWithDeliveryDate(DELIVERY_DATE.plusMinutes(60));

            assertThat(search(Query.of(CriterionFactory.deliveryDate().afterOrEquals(DELIVERY_DATE.plusMinutes(30)))))
                .containsOnly(message2);
        }

        @Test
        default void shouldReturnMessagesWithDeliveryEqualDateWhenAfterOrEquals() {
            DeletedMessage message1 = storeMessageWithDeliveryDate(DELIVERY_DATE);
            DeletedMessage message2 = storeMessageWithDeliveryDate(DELIVERY_DATE.plusMinutes(60));

            assertThat(search(Query.of(CriterionFactory.deliveryDate().afterOrEquals(DELIVERY_DATE.plusMinutes(60)))))
                .containsOnly(message2);
        }
    }

    interface DeletionDateContract extends DeletedMessageVaultSearchContract {

        default DeletedMessage storeMessageWithDeletionDate(ZonedDateTime delitionDate) {
            DeletedMessage deletedMessage = DeletedMessage.builder()
                .messageId(InMemoryMessageId.of(MESSAGE_ID_GENERATOR.incrementAndGet()))
                .originMailboxes(MAILBOX_ID_1)
                .user(USER)
                .deliveryDate(DELIVERY_DATE)
                .deletionDate(delitionDate)
                .sender(MaybeSender.of(SENDER))
                .recipients(RECIPIENT1)
                .content(() -> new ByteArrayInputStream(CONTENT))
                .hasAttachment(false)
                .build();

            Mono.from(getVault().append(USER, deletedMessage))
                .block();
            return deletedMessage;
        }

        @Test
        default void shouldReturnMessagesWithDeletionBeforeDateWhenBeforeOrEquals() {
            DeletedMessage message1 = storeMessageWithDeletionDate(DELIVERY_DATE);
            DeletedMessage message2 = storeMessageWithDeletionDate(DELIVERY_DATE.plusMinutes(60));

            assertThat(search(Query.of(CriterionFactory.deletionDate().beforeOrEquals(DELIVERY_DATE.plusMinutes(30)))))
                .containsOnly(message1);
        }

        @Test
        default void shouldReturnMessagesWithDeletionEqualDateWhenBeforeOrEquals() {
            DeletedMessage message1 = storeMessageWithDeletionDate(DELIVERY_DATE);
            DeletedMessage message2 = storeMessageWithDeletionDate(DELIVERY_DATE.plusMinutes(60));

            assertThat(search(Query.of(CriterionFactory.deletionDate().beforeOrEquals(DELIVERY_DATE))))
                .containsOnly(message1);
        }

        @Test
        default void shouldReturnMessagesWithDeletionAfterDateWhenAfterOrEquals() {
            DeletedMessage message1 = storeMessageWithDeletionDate(DELIVERY_DATE);
            DeletedMessage message2 = storeMessageWithDeletionDate(DELIVERY_DATE.plusMinutes(60));

            assertThat(search(Query.of(CriterionFactory.deletionDate().afterOrEquals(DELIVERY_DATE.plusMinutes(30)))))
                .containsOnly(message2);
        }

        @Test
        default void shouldReturnMessagesWithDeletionEqualDateWhenAfterOrEquals() {
            DeletedMessage message1 = storeMessageWithDeletionDate(DELIVERY_DATE);
            DeletedMessage message2 = storeMessageWithDeletionDate(DELIVERY_DATE.plusMinutes(60));

            assertThat(search(Query.of(CriterionFactory.deletionDate().afterOrEquals(DELIVERY_DATE.plusMinutes(60)))))
                .containsOnly(message2);
        }
    }

    interface SubjectContract extends DeletedMessageVaultSearchContract {

        String APACHE_JAMES_PROJECT = "apache james project";
        String OPEN_SOURCE_SOFTWARE = "open source software";

        default DeletedMessage storeMessageWithSubject(String subject) {
            DeletedMessage deletedMessage = DeletedMessage.builder()
                .messageId(InMemoryMessageId.of(MESSAGE_ID_GENERATOR.incrementAndGet()))
                .originMailboxes(MAILBOX_ID_1)
                .user(USER)
                .deliveryDate(DELIVERY_DATE)
                .deletionDate(DELETION_DATE)
                .sender(MaybeSender.of(SENDER))
                .recipients(RECIPIENT1)
                .content(() -> new ByteArrayInputStream(CONTENT))
                .hasAttachment(false)
                .subject(subject)
                .build();

            Mono.from(getVault().append(USER, deletedMessage))
                .block();
            return deletedMessage;
        }

        @Test
        default void shouldReturnMessagesContainsAtTheMiddle() {
            DeletedMessage message1 = storeMessageWithSubject(APACHE_JAMES_PROJECT);
            DeletedMessage message2 = storeMessageWithSubject(OPEN_SOURCE_SOFTWARE);

            assertThat(search(Query.of(CriterionFactory.subject().contains("james"))))
                .containsOnly(message1);
        }

        @Test
        default void shouldReturnMessagesContainsAtTheBeginning() {
            DeletedMessage message1 = storeMessageWithSubject(APACHE_JAMES_PROJECT);
            DeletedMessage message2 = storeMessageWithSubject(OPEN_SOURCE_SOFTWARE);

            assertThat(search(Query.of(CriterionFactory.subject().contains("apache"))))
                .containsOnly(message1);
        }

        @Test
        default void shouldReturnMessagesContainsAtTheEnd() {
            DeletedMessage message1 = storeMessageWithSubject(APACHE_JAMES_PROJECT);
            DeletedMessage message2 = storeMessageWithSubject(OPEN_SOURCE_SOFTWARE);

            assertThat(search(Query.of(
                    CriterionFactory.subject().contains("software"))))
                .containsOnly(message2);
        }

        @Test
        default void shouldNotReturnMessagesContainsIgnoreCaseWhenContains() {
            DeletedMessage message1 = storeMessageWithSubject(APACHE_JAMES_PROJECT);
            DeletedMessage message2 = storeMessageWithSubject(OPEN_SOURCE_SOFTWARE);

            assertThat(search(Query.of(CriterionFactory.subject().contains("SoftWare"))))
                .isEmpty();
        }

        @Test
        default void shouldReturnMessagesContainsIgnoreCaseAtTheMiddle() {
            DeletedMessage message1 = storeMessageWithSubject(APACHE_JAMES_PROJECT);
            DeletedMessage message2 = storeMessageWithSubject(OPEN_SOURCE_SOFTWARE);

            assertThat(search(Query.of(CriterionFactory.subject().containsIgnoreCase("JAmEs"))))
                .containsOnly(message1);
        }

        @Test
        default void shouldReturnMessagesContainsIgnoreCaseAtTheBeginning() {
            DeletedMessage message1 = storeMessageWithSubject(APACHE_JAMES_PROJECT);
            DeletedMessage message2 = storeMessageWithSubject(OPEN_SOURCE_SOFTWARE);

            assertThat(search(Query.of(CriterionFactory.subject().containsIgnoreCase("SouRCE"))))
                .containsOnly(message2);
        }

        @Test
        default void shouldReturnMessagesContainsIgnoreCaseAtTheEnd() {
            DeletedMessage message1 = storeMessageWithSubject(APACHE_JAMES_PROJECT);
            DeletedMessage message2 = storeMessageWithSubject(OPEN_SOURCE_SOFTWARE);

            assertThat(search(Query.of(
                    CriterionFactory.subject().containsIgnoreCase("ProJECT"))))
                .containsOnly(message1);
        }

        @Test
        default void shouldReturnMessagesContainsWhenContainsIgnoreCase() {
            DeletedMessage message1 = storeMessageWithSubject(APACHE_JAMES_PROJECT);
            DeletedMessage message2 = storeMessageWithSubject(OPEN_SOURCE_SOFTWARE);

            assertThat(search(Query.of(
                    CriterionFactory.subject().containsIgnoreCase("project"))))
                .containsOnly(message1);
        }

        @Test
        default void shouldReturnMessagesStrictlyEquals() {
            DeletedMessage message1 = storeMessageWithSubject(APACHE_JAMES_PROJECT);
            DeletedMessage message2 = storeMessageWithSubject(OPEN_SOURCE_SOFTWARE);

            assertThat(search(Query.of(CriterionFactory.subject().equals(APACHE_JAMES_PROJECT))))
                .containsOnly(message1);
        }

        @Test
        default void shouldNotReturnMessagesContainsWhenEquals() {
            DeletedMessage message1 = storeMessageWithSubject(APACHE_JAMES_PROJECT);
            DeletedMessage message2 = storeMessageWithSubject(OPEN_SOURCE_SOFTWARE);

            assertThat(search(Query.of(CriterionFactory.subject().equals("james"))))
                .isEmpty();
        }

        @Test
        default void shouldNotReturnMessagesContainsIgnoreCaseWhenEquals() {
            DeletedMessage message1 = storeMessageWithSubject(APACHE_JAMES_PROJECT);
            DeletedMessage message2 = storeMessageWithSubject(OPEN_SOURCE_SOFTWARE);

            assertThat(search(Query.of(CriterionFactory.subject().equals("proJECT"))))
                .isEmpty();
        }

        @Test
        default void shouldNotReturnMessagesEqualsIgnoreCaseWhenEquals() {
            DeletedMessage message1 = storeMessageWithSubject(APACHE_JAMES_PROJECT);
            DeletedMessage message2 = storeMessageWithSubject(OPEN_SOURCE_SOFTWARE);

            assertThat(search(Query.of(CriterionFactory.subject().equals("Apache James Project"))))
                .isEmpty();
        }
    }

    @Test
    default void searchShouldNotReturnMessagesFromAnotherUser() {

    }

    AtomicLong MESSAGE_ID_GENERATOR = new AtomicLong(0);

    default List<DeletedMessage> search(Query query) {
        return Flux.from(getVault().search(USER, query)).collectList().block();
    }
}
