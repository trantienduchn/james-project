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

package org.apache.james.jmap.mailet.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.User;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.jmap.mailet.filter.JMAPFilteringExtension.JMAPFilteringTestSystem;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.common.collect.ImmutableList;

@ExtendWith(JMAPFilteringExtension.class)
class JMAPFilteringTest {

    interface WithContainsCommandRule {
        void mailDirectiveShouldBeSetWhenContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception;
        void mailDirectiveShouldNotBeSetdWhenAllDoNotContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception;
        void mailDirectiveShouldBeSetWhenAtLeastOneContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception;
        void mailDirectiveShouldBeSetWhenUnscrambledContentContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception;
        void mailDirectiveShouldBeSetWhenFoldedContentContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception;
        void mailDirectiveShouldBeSetWhenMultipleHeaders(JMAPFilteringTestSystem testSystem) throws Exception;
    }

    interface WithNotContainsCommandRule {
        void mailDirectiveShouldBeSetWhenDoesntContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception;
        void mailDirectiveShouldNotBeSetWhenAtleastOneContentDoesntContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception;
        void mailDirectiveShoulNotBeSetWhenAllContentsContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception;
        void mailDirectiveShouldBeSetWhenUnscrambledContentDoenstContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception;
        void mailDirectiveShouldBeSetWhenFoldedContentDoesntContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception;
        void mailDirectiveShouldBeSetWhenMultipleHeaders(JMAPFilteringTestSystem testSystem) throws Exception;
    }

    interface WithExactlyEqualsCommandRule {
        void mailDirectiveShouldBeSetWhenExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception;
        void mailDirectiveShouldBeSetWhenAtLeastOneExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception;
        void mailDirectiveShouldNotBeSetWhenAllDoNotExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception;
        void mailDirectiveShouldBeSetWhenUnscrambledContentExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception;
        void mailDirectiveShouldBeSetWhenFoldedContentExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception;
        void mailDirectiveShouldBeSetWhenMultipleHeaders(JMAPFilteringTestSystem testSystem) throws Exception;
    }

    interface WithNotExactlyEqualsCommandRule {
        void mailDirectiveShouldBeSetWhenDoNotExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception;
        void mailDirectiveShouldBeSetWhenAllDoNotExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception;
        void mailDirectiveShouldNotBeSetWhenExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception;
        void mailDirectiveShouldBeSetWhenUnscrambledContentDoesntExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception;
        void mailDirectiveShouldBeSetWhenFoldedContentDoesntExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception;
        void mailDirectiveShouldBeSetWhenMultipleHeaders(JMAPFilteringTestSystem testSystem) throws Exception;
    }

    interface WithRecipientFiltering {
        void mailDirectiveShouldIgnoreBccHeaders(JMAPFilteringTestSystem testSystem) throws Exception;
    }

    private static final String DELIVERY_PATH_PREFIX = "DeliveryPath_";

    private static final Rule.Id RULE_ID_1 = Rule.Id.of("1");
    private static final String RULE_NAME_1 = "rule 1";

    private static final String SENDER_1 = "sender1@james.org";
    private static final String SENDER_1_USERNAME = "sender1";

    private static final String SENDER_2 = "sender2@james.org";

    private static final String RECIPIENT_1 = "recipient1@james.org";
    static final String RECIPIENT_1_USERNAME = "recipient1";
    static final String RECIPIENT_1_MAILBOX_1 = "recipient1_maibox1";

    private static final String RECIPIENT_2 = "recipient2@james.org";

    static final String FRED_MARTIN_USERNAME = "fred.martin";
    static final String FRED_MARTIN_INBOX = "fred.martin.inbox";
    private static final String FRED_MARTIN = "fred.martin@linagora.com";

    @Nested
    class FromFiltering {

        @Nested
        class FromFilteringWithContainsCommandRule implements WithContainsCommandRule {

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.FROM, Rule.Condition.Comparator.CONTAINS, SENDER_1_USERNAME))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("from", "sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenUnscrambledContentContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.FROM, Rule.Condition.Comparator.CONTAINS, "Frédéric MAR"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("from", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>, sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenFoldedContentContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.FROM, Rule.Condition.Comparator.CONTAINS, "Frédéric MAR"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("from", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenMultipleHeaders(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.FROM, Rule.Condition.Comparator.CONTAINS, "Frédéric MAR"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("from", "sender2 <sender2@james.org>")
                            .addHeader("from", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldNotBeSetdWhenAllDoNotContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.FROM, Rule.Condition.Comparator.CONTAINS, SENDER_1_USERNAME))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("from", "sender2 <sender2@james.org>, lina <lina@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isNull();
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenAtLeastOneContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.FROM, Rule.Condition.Comparator.CONTAINS, SENDER_1_USERNAME))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("from", "sender1 <sender1@james.org>, lina <lina@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }
        }

        @Nested
        class FromFilteringWithNotContainsCommandRule implements WithNotContainsCommandRule {

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenDoesntContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.FROM, Rule.Condition.Comparator.NOT_CONTAINS, SENDER_1_USERNAME))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("from", "sender2 <sender2@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }

            @Test
            @Override
            public void mailDirectiveShouldNotBeSetWhenAtleastOneContentDoesntContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.FROM, Rule.Condition.Comparator.NOT_CONTAINS, SENDER_1_USERNAME))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("from", "sender1 <sender1@james.org>, lina <lina@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isNull();
            }

            @Test
            @Override
            public void mailDirectiveShoulNotBeSetWhenAllContentsContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.FROM, Rule.Condition.Comparator.NOT_CONTAINS, SENDER_1_USERNAME))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("from", "sender1 <sender1@james.org>, sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isNull();
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenUnscrambledContentDoenstContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.FROM, Rule.Condition.Comparator.NOT_CONTAINS, "Frédéric_MARTIN_alternative_name"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("from", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>, sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenFoldedContentDoesntContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.FROM, Rule.Condition.Comparator.NOT_CONTAINS, "Frédéric_MARTIN_alternative_name"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("from", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenMultipleHeaders(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.FROM, Rule.Condition.Comparator.NOT_CONTAINS, "Frédéric_MARTIN_alternative_name"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("from", "sender2 <sender2@james.org>")
                            .addHeader("from", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }
        }

        @Nested
        class FromFilteringWithExactlyEqualsCommandRule implements WithExactlyEqualsCommandRule {

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.FROM, Rule.Condition.Comparator.EXACTLY_EQUALS, SENDER_1))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("from", SENDER_1))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenAtLeastOneExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.FROM, Rule.Condition.Comparator.EXACTLY_EQUALS, SENDER_1))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addFrom(SENDER_1)
                            .addFrom(SENDER_2))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }

            @Test
            @Override
            public void mailDirectiveShouldNotBeSetWhenAllDoNotExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.FROM, Rule.Condition.Comparator.EXACTLY_EQUALS, FRED_MARTIN))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addFrom(SENDER_1)
                            .addFrom(SENDER_2))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isNull();
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenUnscrambledContentExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.FROM, Rule.Condition.Comparator.EXACTLY_EQUALS, "Frédéric MARTIN <fred.martin@linagora.com>"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("from", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>, sender1 <sender1@james.org>")
                            .build())
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenFoldedContentExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.FROM, Rule.Condition.Comparator.EXACTLY_EQUALS, "Frédéric MARTIN <fred.martin@linagora.com>"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("from", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>")
                            .build())
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenMultipleHeaders(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.FROM, Rule.Condition.Comparator.EXACTLY_EQUALS, "Frédéric MARTIN <fred.martin@linagora.com>"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addFrom(SENDER_1)
                            .addHeader("from", "sender2 <sender2@james.org>")
                            .addHeader("from", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>")
                            .build())
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            // TODO How about...
            // Rule sender1@domain.com
            // Header Sender 1 <sender1@domain.com>
            // It should match

            // TODO How about...
            // Rule Benoit Tellier
            // Header Benoit Tellier <sender1@domain.com>
            // It should match
        }

        @Nested
        class FromFilteringWithNotExactlyEqualsCommandRule implements WithNotExactlyEqualsCommandRule {

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenDoNotExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.FROM, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, FRED_MARTIN))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addFrom(SENDER_1))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenAllDoNotExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.FROM, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, SENDER_2))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addFrom(SENDER_1)
                            .addFrom(SENDER_2))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isNull();
            }

            @Test
            @Override
            public void mailDirectiveShouldNotBeSetWhenExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.FROM, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, SENDER_1))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addFrom(SENDER_1))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isNull();
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenUnscrambledContentDoesntExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.FROM, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, "Frédéric MARTIN"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("from", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>, sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenFoldedContentDoesntExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.FROM, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, "Frédéric MARTIN"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("from", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenMultipleHeaders(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.FROM, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, "Frédéric MARTIN"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("from", "sender2 <sender2@james.org>")
                            .addHeader("from", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }
        }
    }

    @Nested
    class ToFiltering {

        @Nested
        class ToFilteringWithContainsCommandRule implements WithContainsCommandRule {

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.TO, Rule.Condition.Comparator.CONTAINS, SENDER_1_USERNAME))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("to", "sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenUnscrambledContentContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.TO, Rule.Condition.Comparator.CONTAINS, "Frédéric MAR"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("to", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>, sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenFoldedContentContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.TO, Rule.Condition.Comparator.CONTAINS, "Frédéric MAR"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("to", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenMultipleHeaders(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.TO, Rule.Condition.Comparator.CONTAINS, "Frédéric MAR"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("to", "sender2 <sender2@james.org>")
                            .addHeader("to", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldNotBeSetdWhenAllDoNotContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.TO, Rule.Condition.Comparator.CONTAINS, SENDER_1_USERNAME))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("to", "sender2 <sender2@james.org>, lina <lina@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isNull();
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenAtLeastOneContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.TO, Rule.Condition.Comparator.CONTAINS, SENDER_1_USERNAME))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("to", "sender1 <sender1@james.org>, lina <lina@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }
        }

        @Nested
        class ToFilteringWithNotContainsCommandRule implements WithNotContainsCommandRule {

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenDoesntContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.TO, Rule.Condition.Comparator.NOT_CONTAINS, SENDER_1_USERNAME))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("to", "sender2 <sender2@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }

            @Test
            @Override
            public void mailDirectiveShouldNotBeSetWhenAtleastOneContentDoesntContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.TO, Rule.Condition.Comparator.NOT_CONTAINS, SENDER_1_USERNAME))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("to", "sender1 <sender1@james.org>, lina <lina@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isNull();
            }

            @Test
            @Override
            public void mailDirectiveShoulNotBeSetWhenAllContentsContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.TO, Rule.Condition.Comparator.NOT_CONTAINS, SENDER_1_USERNAME))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("to", "sender1 <sender1@james.org>, sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isNull();
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenUnscrambledContentDoenstContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.TO, Rule.Condition.Comparator.NOT_CONTAINS, "Frédéric_MARTIN_alternative_name"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("to", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>, sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenFoldedContentDoesntContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.TO, Rule.Condition.Comparator.NOT_CONTAINS, "Frédéric_MARTIN_alternative_name"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("to", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenMultipleHeaders(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.TO, Rule.Condition.Comparator.NOT_CONTAINS, "Frédéric_MARTIN_alternative_name"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("to", "sender2 <sender2@james.org>")
                            .addHeader("to", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }
        }

        @Nested
        class ToFilteringWithExactlyEqualsCommandRule implements WithExactlyEqualsCommandRule {

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.TO, Rule.Condition.Comparator.EXACTLY_EQUALS, SENDER_1))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("to", SENDER_1))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenAtLeastOneExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.TO, Rule.Condition.Comparator.EXACTLY_EQUALS, SENDER_1))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addToRecipient(SENDER_1)
                            .addToRecipient(SENDER_2))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }

            @Test
            @Override
            public void mailDirectiveShouldNotBeSetWhenAllDoNotExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.TO, Rule.Condition.Comparator.EXACTLY_EQUALS, FRED_MARTIN))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addToRecipient(SENDER_1)
                            .addToRecipient(SENDER_2))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isNull();
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenUnscrambledContentExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.TO, Rule.Condition.Comparator.EXACTLY_EQUALS, "Frédéric MARTIN <fred.martin@linagora.com>"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("to", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>, sender1 <sender1@james.org>")
                            .build())
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenFoldedContentExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.TO, Rule.Condition.Comparator.EXACTLY_EQUALS, "Frédéric MARTIN <fred.martin@linagora.com>"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("to", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>")
                            .build())
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenMultipleHeaders(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.TO, Rule.Condition.Comparator.EXACTLY_EQUALS, "Frédéric MARTIN <fred.martin@linagora.com>"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addToRecipient(SENDER_1)
                            .addHeader("to", "sender2 <sender2@james.org>")
                            .addHeader("to", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>")
                            .build())
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }
        }

        @Nested
        class ToFilteringWithNotExactlyEqualsCommandRule implements WithNotExactlyEqualsCommandRule {

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenDoNotExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.TO, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, FRED_MARTIN))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addToRecipient(SENDER_1))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenAllDoNotExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.TO, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, SENDER_2))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addToRecipient(SENDER_1)
                            .addToRecipient(SENDER_2))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isNull();
            }

            @Test
            @Override
            public void mailDirectiveShouldNotBeSetWhenExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.TO, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, SENDER_1))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addToRecipient(SENDER_1))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isNull();
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenUnscrambledContentDoesntExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.TO, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, "Frédéric MARTIN"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("to", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>, sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenFoldedContentDoesntExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.TO, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, "Frédéric MARTIN"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("to", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenMultipleHeaders(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.TO, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, "Frédéric MARTIN"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("to", "sender2 <sender2@james.org>")
                            .addHeader("to", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }
        }
    }

    @Nested
    class CcFiltering {

        @Nested
        class CcFilteringWithContainsCommandRule implements WithContainsCommandRule {

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.CC, Rule.Condition.Comparator.CONTAINS, SENDER_1_USERNAME))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("cc", "sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenUnscrambledContentContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.CC, Rule.Condition.Comparator.CONTAINS, "Frédéric MAR"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("cc", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>, sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenFoldedContentContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.CC, Rule.Condition.Comparator.CONTAINS, "Frédéric MAR"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("cc", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenMultipleHeaders(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.CC, Rule.Condition.Comparator.CONTAINS, "Frédéric MAR"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("cc", "sender2 <sender2@james.org>")
                            .addHeader("cc", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldNotBeSetdWhenAllDoNotContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.CC, Rule.Condition.Comparator.CONTAINS, SENDER_1_USERNAME))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("cc", "sender2 <sender2@james.org>, lina <lina@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isNull();
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenAtLeastOneContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.CC, Rule.Condition.Comparator.CONTAINS, SENDER_1_USERNAME))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("cc", "sender1 <sender1@james.org>, lina <lina@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }
        }

        @Nested
        class CcFilteringWithNotContainsCommandRule implements WithNotContainsCommandRule {

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenDoesntContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.CC, Rule.Condition.Comparator.NOT_CONTAINS, SENDER_1_USERNAME))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("cc", "sender2 <sender2@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }

            @Test
            @Override
            public void mailDirectiveShouldNotBeSetWhenAtleastOneContentDoesntContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.CC, Rule.Condition.Comparator.NOT_CONTAINS, SENDER_1_USERNAME))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("cc", "sender1 <sender1@james.org>, lina <lina@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isNull();
            }

            @Test
            @Override
            public void mailDirectiveShoulNotBeSetWhenAllContentsContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.CC, Rule.Condition.Comparator.NOT_CONTAINS, SENDER_1_USERNAME))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("cc", "sender1 <sender1@james.org>, sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isNull();
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenUnscrambledContentDoenstContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.CC, Rule.Condition.Comparator.NOT_CONTAINS, "Frédéric_MARTIN_alternative_name"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("cc", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>, sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenFoldedContentDoesntContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.CC, Rule.Condition.Comparator.NOT_CONTAINS, "Frédéric_MARTIN_alternative_name"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("cc", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenMultipleHeaders(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.CC, Rule.Condition.Comparator.NOT_CONTAINS, "Frédéric_MARTIN_alternative_name"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("cc", "sender2 <sender2@james.org>")
                            .addHeader("cc", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }
        }

        @Nested
        class CcFilteringWithExactlyEqualsCommandRule implements WithExactlyEqualsCommandRule {

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.CC, Rule.Condition.Comparator.EXACTLY_EQUALS, SENDER_1))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("cc", SENDER_1))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenAtLeastOneExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.CC, Rule.Condition.Comparator.EXACTLY_EQUALS, SENDER_1))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addCcRecipient(SENDER_1)
                            .addCcRecipient(SENDER_2))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }

            @Test
            @Override
            public void mailDirectiveShouldNotBeSetWhenAllDoNotExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.CC, Rule.Condition.Comparator.EXACTLY_EQUALS, FRED_MARTIN))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addCcRecipient(SENDER_1)
                            .addCcRecipient(SENDER_2))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isNull();
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenUnscrambledContentExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.CC, Rule.Condition.Comparator.EXACTLY_EQUALS, "Frédéric MARTIN <fred.martin@linagora.com>"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("cc", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>, sender1 <sender1@james.org>")
                            .build())
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenFoldedContentExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.CC, Rule.Condition.Comparator.EXACTLY_EQUALS, "Frédéric MARTIN <fred.martin@linagora.com>"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("cc", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>")
                            .build())
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenMultipleHeaders(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.CC, Rule.Condition.Comparator.EXACTLY_EQUALS, "Frédéric MARTIN <fred.martin@linagora.com>"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addCcRecipient(SENDER_1)
                            .addHeader("cc", "sender2 <sender2@james.org>")
                            .addHeader("cc", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>")
                            .build())
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }
        }

        @Nested
        class CcFilteringWithNotExactlyEqualsCommandRule implements WithNotExactlyEqualsCommandRule {

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenDoNotExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.CC, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, FRED_MARTIN))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addCcRecipient(SENDER_1))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenAllDoNotExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.CC, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, SENDER_2))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addCcRecipient(SENDER_1)
                            .addCcRecipient(SENDER_2))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isNull();
            }

            @Test
            @Override
            public void mailDirectiveShouldNotBeSetWhenExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.CC, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, SENDER_1))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addCcRecipient(SENDER_1))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isNull();
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenUnscrambledContentDoesntExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.CC, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, "Frédéric MARTIN"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("cc", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>, sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenFoldedContentDoesntExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.CC, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, "Frédéric MARTIN"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("cc", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenMultipleHeaders(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.CC, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, "Frédéric MARTIN"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("cc", "sender2 <sender2@james.org>")
                            .addHeader("cc", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }
        }
    }

    @Nested
    class RecipientFiltering {

        @Nested
        class RecipientFilteringWithContainsCommandRule implements WithContainsCommandRule, WithRecipientFiltering {

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.RECIPIENT, Rule.Condition.Comparator.CONTAINS, SENDER_1_USERNAME))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addCcRecipient("sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenUnscrambledContentContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.RECIPIENT, Rule.Condition.Comparator.CONTAINS, "Frédéric MAR"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addCcRecipient("=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenFoldedContentContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.RECIPIENT, Rule.Condition.Comparator.CONTAINS, "Frédéric MAR"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("cc", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenMultipleHeaders(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.RECIPIENT, Rule.Condition.Comparator.CONTAINS, "Frédéric MAR"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addToRecipient( "sender2 <sender2@james.org>")
                            .addCcRecipient( "linagora <linagora@james.org>")
                            .addHeader("to", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldNotBeSetdWhenAllDoNotContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.RECIPIENT, Rule.Condition.Comparator.CONTAINS, SENDER_1_USERNAME))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("to", "sender2 <sender2@james.org>, lina <lina@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isNull();
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenAtLeastOneContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.RECIPIENT, Rule.Condition.Comparator.CONTAINS, SENDER_1_USERNAME))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("to", "sender1 <sender1@james.org>, lina <lina@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }

            @Test
            @Override
            public void mailDirectiveShouldIgnoreBccHeaders(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.RECIPIENT, Rule.Condition.Comparator.CONTAINS, SENDER_1_USERNAME))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addBccRecipient("sender1 <sender1@james.org>")
                            .addHeader("bcc", "sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isNull();
            }
        }

        @Nested
        class RecipientFilteringWithNotContainsCommandRule implements WithNotContainsCommandRule, WithRecipientFiltering {

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenDoesntContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.RECIPIENT, Rule.Condition.Comparator.NOT_CONTAINS, SENDER_1_USERNAME))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addToRecipient("sender2 <sender2@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }

            @Test
            @Override
            public void mailDirectiveShouldNotBeSetWhenAtleastOneContentDoesntContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.RECIPIENT, Rule.Condition.Comparator.NOT_CONTAINS, SENDER_1_USERNAME))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("to", "sender1 <sender1@james.org>, lina <lina@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isNull();
            }

            @Test
            @Override
            public void mailDirectiveShoulNotBeSetWhenAllContentsContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.RECIPIENT, Rule.Condition.Comparator.NOT_CONTAINS, SENDER_1_USERNAME))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("cc", "sender1 <sender1@james.org>, sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isNull();
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenUnscrambledContentDoenstContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.RECIPIENT, Rule.Condition.Comparator.NOT_CONTAINS, "Frédéric_MARTIN_alternative_name"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("to", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>, sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenFoldedContentDoesntContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.RECIPIENT, Rule.Condition.Comparator.NOT_CONTAINS, "Frédéric_MARTIN_alternative_name"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("to", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenMultipleHeaders(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.RECIPIENT, Rule.Condition.Comparator.NOT_CONTAINS, "Frédéric_MARTIN_alternative_name"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("to", "sender2 <sender2@james.org>")
                            .addHeader("cc", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldIgnoreBccHeaders(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.RECIPIENT, Rule.Condition.Comparator.NOT_CONTAINS, SENDER_1_USERNAME))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("bcc", "sender1 <sender1@james.org>")
                            .addBccRecipient("sender1 <sender1@james.org>")
                            .addHeader("to", "sender2 <sender2@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }
        }

        @Nested
        class RecipientFilteringWithExactlyEqualsCommandRule implements WithExactlyEqualsCommandRule, WithRecipientFiltering {

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.RECIPIENT, Rule.Condition.Comparator.EXACTLY_EQUALS, SENDER_1))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("to", SENDER_1))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenAtLeastOneExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.RECIPIENT, Rule.Condition.Comparator.EXACTLY_EQUALS, SENDER_1))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addCcRecipient(SENDER_1)
                            .addToRecipient(SENDER_2))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }

            @Test
            @Override
            public void mailDirectiveShouldNotBeSetWhenAllDoNotExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.RECIPIENT, Rule.Condition.Comparator.EXACTLY_EQUALS, FRED_MARTIN))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addCcRecipient(SENDER_1)
                            .addCcRecipient(SENDER_2))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isNull();
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenUnscrambledContentExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.RECIPIENT, Rule.Condition.Comparator.EXACTLY_EQUALS, "Frédéric MARTIN <fred.martin@linagora.com>"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("to", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>, sender1 <sender1@james.org>")
                            .build())
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenFoldedContentExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.RECIPIENT, Rule.Condition.Comparator.EXACTLY_EQUALS, "Frédéric MARTIN <fred.martin@linagora.com>"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("cc", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>")
                            .build())
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenMultipleHeaders(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.RECIPIENT, Rule.Condition.Comparator.EXACTLY_EQUALS, "Frédéric MARTIN <fred.martin@linagora.com>"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addCcRecipient(SENDER_1)
                            .addHeader("to", "sender2 <sender2@james.org>")
                            .addHeader("cc", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>")
                            .build())
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldIgnoreBccHeaders(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.RECIPIENT, Rule.Condition.Comparator.EXACTLY_EQUALS, RECIPIENT_1))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addBccRecipient(RECIPIENT_1)
                            .addHeader("bcc", RECIPIENT_1))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isNull();
            }
        }

        @Nested
        class RecipientFilteringWithNotExactlyEqualsCommandRule implements WithNotExactlyEqualsCommandRule, WithRecipientFiltering {

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenDoNotExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.RECIPIENT, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, FRED_MARTIN))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addToRecipient(SENDER_1))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenAllDoNotExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.RECIPIENT, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, SENDER_2))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addToRecipient(SENDER_1)
                            .addCcRecipient(SENDER_2))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isNull();
            }

            @Test
            @Override
            public void mailDirectiveShouldNotBeSetWhenExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.RECIPIENT, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, SENDER_1))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addCcRecipient(SENDER_1))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isNull();
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenUnscrambledContentDoesntExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.RECIPIENT, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, "Frédéric MARTIN"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("to", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>, sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenFoldedContentDoesntExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.RECIPIENT, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, "Frédéric MARTIN"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("to", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldBeSetWhenMultipleHeaders(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.RECIPIENT, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, "Frédéric MARTIN"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addHeader("cc", "sender2 <sender2@james.org>")
                            .addHeader("to", "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>,\r\n" +
                                " sender1 <sender1@james.org>"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            @Override
            public void mailDirectiveShouldIgnoreBccHeaders(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.RECIPIENT, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, RECIPIENT_1))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .addBccRecipient(RECIPIENT_1)
                            .addToRecipient(RECIPIENT_2))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }
        }
    }

    @Nested
    class SubjectFiltering {

        @Nested
        class SubjectFilteringWithContainsCommandRule {

            @Test
            public void mailDirectiveShouldBeSetWhenContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.SUBJECT, Rule.Condition.Comparator.CONTAINS, "subject"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .setSubject("this is the subject of the mail"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }

            @Test
            public void mailDirectiveShouldBeSetWhenUnscrambledContentContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.SUBJECT, Rule.Condition.Comparator.CONTAINS, "Frédéric MAR"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .setSubject("=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= is in the subject of this mail"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            public void mailDirectiveShouldBeSetWhenFoldedContentContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.SUBJECT, Rule.Condition.Comparator.CONTAINS, "contains a folding and the matcher should work"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .setSubject( "this subject contains a folding\r\n" +
                                " and the matcher should work"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }
        }

        @Nested
        class SubjectFilteringWithNotContainsCommandRule {

            @Test
            public void mailDirectiveShouldBeSetWhenDoesntContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.SUBJECT, Rule.Condition.Comparator.NOT_CONTAINS, "james"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .setSubject("this subject is about java enterprise mail server"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }

            @Test
            public void mailDirectiveShouldBeSetWhenUnscrambledContentDoenstContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.SUBJECT, Rule.Condition.Comparator.NOT_CONTAINS, "Frédéric_MARTIN_alternative_name"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .setSubject("=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= is the subject"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            public void mailDirectiveShouldBeSetWhenFoldedContentDoesntContainsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.SUBJECT, Rule.Condition.Comparator.NOT_CONTAINS, "subject without \r\n"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .setSubject("This is subject without \r\n"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }
        }

        @Nested
        class SubjectFilteringWithExactlyEqualsCommandRule {

            @Test
            public void mailDirectiveShouldBeSetWhenExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.SUBJECT, Rule.Condition.Comparator.EXACTLY_EQUALS, "Subject content should be equals exaclty"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .setSubject("Subject content should be equals exaclty")
                            .build())
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }

            @Test
            public void mailDirectiveShouldBeSetWhenUnscrambledContentExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.SUBJECT, Rule.Condition.Comparator.EXACTLY_EQUALS, "Frédéric MARTIN is the subject"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .setSubject( "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= is the subject")
                            .build())
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            public void mailDirectiveShouldBeSetWhenFoldedContentExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.SUBJECT, Rule.Condition.Comparator.EXACTLY_EQUALS, "Frédéric MARTIN is the subject of this mail"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .setSubject("=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= is the subject\r\n" +
                                " of this mail")
                            .build())
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }
        }

        @Nested
        class SubjectFilteringWithNotExactlyEqualsCommandRule {

            @Test
            public void mailDirectiveShouldBeSetWhenDoNotExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.SUBJECT, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, "a rule is expected not be equals to subject"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .setSubject("this subject is not be equal to the rule"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isEqualTo(RECIPIENT_1_MAILBOX_1);
            }

            @Test
            public void mailDirectiveShouldNotBeSetWhenExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.SUBJECT, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, "Java Mail Subject"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getRecipient1MailboxId().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_2)
                        .recipients(RECIPIENT_1, RECIPIENT_2)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .setSubject("Java Mail Subject"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                        .isNull();
            }

            @Test
            public void mailDirectiveShouldBeSetWhenUnscrambledContentDoesntExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.SUBJECT, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, "Frédéric MARTIN"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .setSubject("=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= unscrambled"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }

            @Test
            public void mailDirectiveShouldBeSetWhenFoldedContentDoesntExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
                testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(FRED_MARTIN_USERNAME),
                        ImmutableList.of(
                            Rule.builder()
                                .id(RULE_ID_1)
                                .name(RULE_NAME_1)
                                .condition(Rule.Condition.of(Rule.Condition.Field.SUBJECT, Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, "Frédéric MARTIN \r\n unscrambled"))
                                .action(Rule.Action.ofMailboxIds(ImmutableList.of(testSystem.getFredMartinInbox().serialize())))
                                .build()));

                FakeMail mail = FakeMail.builder()
                        .sender(SENDER_1)
                        .recipient(FRED_MARTIN)
                        .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                            .setSubject("=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= \r\n unscrambled"))
                        .build();

                testSystem.getJmapFiltering().service(mail);

                assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + FRED_MARTIN_USERNAME))
                        .isEqualTo(FRED_MARTIN_INBOX);
            }
        }
    }
}