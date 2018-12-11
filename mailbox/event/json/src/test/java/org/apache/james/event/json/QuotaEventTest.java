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

package org.apache.james.event.json;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.core.User;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class QuotaEventTest {

    private static final User USER = User.fromUsername("user");
    private static final QuotaRoot QUOTA_ROOT = QuotaRoot.quotaRoot("foo", Optional.empty());
    private static final Quota<QuotaCount> QUOTA_COUNT = Quota.<QuotaCount>builder()
        .used(QuotaCount.count(12))
        .computedLimit(QuotaCount.count(100))
        .build();
    private static final Quota<QuotaSize> QUOTA_SIZE = Quota.<QuotaSize>builder()
        .used(QuotaSize.size(1234))
        .computedLimit(QuotaSize.size(10000))
        .build();
    private static final Instant INSTANT = Instant.parse("2018-11-13T12:00:55Z");
    private static final MailboxListener.QuotaUsageUpdatedEvent DEFAULT_QUOTA_EVENT =
        new MailboxListener.QuotaUsageUpdatedEvent(USER, QUOTA_ROOT, QUOTA_COUNT, QUOTA_SIZE, INSTANT);

    private static final String DEFAULT_QUOTA_EVENT_JSON =
        "{" +
            "\"QuotaUsageUpdatedEvent\":{" +
            "\"quotaRoot\":\"foo\"," +
            "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
            "\"time\":\"2018-11-13T12:00:55Z\"," +
            "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
            "\"user\":\"user\"" +
            "}" +
        "}";

    @Nested
    class WithUser {

        @Nested
        class WithValidUser {

            @Nested
            class WithUserContainsOnlyUsername {

                private final MailboxListener.QuotaUsageUpdatedEvent eventWithUserContainsUsername = new MailboxListener.QuotaUsageUpdatedEvent(
                    User.fromUsername("onlyUsername"),
                    QUOTA_ROOT,
                    QUOTA_COUNT,
                    QUOTA_SIZE,
                    INSTANT);
                private final String quotaUsageUpdatedEvent =
                    "{" +
                        "\"QuotaUsageUpdatedEvent\":{" +
                        "\"quotaRoot\":\"foo\"," +
                        "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                        "\"time\":\"2018-11-13T12:00:55Z\"," +
                        "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                        "\"user\":\"onlyUsername\"" +
                        "}" +
                    "}";

                @Test
                void fromJsonShouldReturnQuotaEvent() {
                    assertThat(QuotaEvent$.MODULE$.fromJson(quotaUsageUpdatedEvent).get())
                        .isEqualTo(eventWithUserContainsUsername);
                }

                @Test
                void toJsonShouldReturnQuotaEventJson() {
                    assertThatJson(QuotaEvent$.MODULE$.toJson(eventWithUserContainsUsername))
                        .isEqualTo(quotaUsageUpdatedEvent);
                }
            }

            @Nested
            class WithUserContainsUsernameAndDomain {

                private final MailboxListener.QuotaUsageUpdatedEvent eventWithUserContainsUsernameAndDomain = new MailboxListener.QuotaUsageUpdatedEvent(
                    User.fromUsername("user@domain"),
                    QUOTA_ROOT,
                    QUOTA_COUNT,
                    QUOTA_SIZE,
                    INSTANT);
                private final String quotaUsageUpdatedEvent =
                    "{" +
                        "\"QuotaUsageUpdatedEvent\":{" +
                        "\"quotaRoot\":\"foo\"," +
                        "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                        "\"time\":\"2018-11-13T12:00:55Z\"," +
                        "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                        "\"user\":\"user@domain\"" +
                        "}" +
                    "}";

                @Test
                void fromJsonShouldReturnQuotaEvent() {
                    assertThat(QuotaEvent$.MODULE$.fromJson(quotaUsageUpdatedEvent).get())
                        .isEqualTo(eventWithUserContainsUsernameAndDomain);
                }

                @Test
                void toJsonShouldReturnQuotaEventJson() {
                    assertThatJson(QuotaEvent$.MODULE$.toJson(eventWithUserContainsUsernameAndDomain))
                        .isEqualTo(quotaUsageUpdatedEvent);
                }
            }
        }

        @Nested
        class WithInvalidUser {

            @Test
            void fromJsonShouldThrowWhenEmptyUser() {
                String quotaUsageUpdatedEvent =
                    "{" +
                        "\"QuotaUsageUpdatedEvent\":{" +
                        "\"quotaRoot\":\"foo\"," +
                        "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                        "\"time\":\"2018-11-13T12:00:55Z\"," +
                        "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                        "\"user\":\"\"" +
                        "}" +
                    "}";
                assertThatThrownBy(() -> QuotaEvent$.MODULE$.fromJson(quotaUsageUpdatedEvent))
                    .isInstanceOf(IllegalArgumentException.class);
            }


            @Test
            void fromJsonShouldReturnErrorResultWhenUserIsNull() {
                String quotaUsageUpdatedEvent =
                    "{" +
                        "\"QuotaUsageUpdatedEvent\":{" +
                        "\"quotaRoot\":\"foo\"," +
                        "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                        "\"time\":\"2018-11-13T12:00:55Z\"," +
                        "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}" +
                        "}" +
                    "}";

                assertThat(QuotaEvent$.MODULE$.fromJson(quotaUsageUpdatedEvent).isSuccess())
                    .isFalse();
            }

            @Test
            void fromJsonShouldThrowWhenUserIsInvalid() {
                String quotaUsageUpdatedEvent =
                    "{" +
                        "\"QuotaUsageUpdatedEvent\":{" +
                        "\"quotaRoot\":\"foo\"," +
                        "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                        "\"time\":\"2018-11-13T12:00:55Z\"," +
                        "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                        "\"user\":\"@domain\"" +
                        "}" +
                    "}";
                assertThatThrownBy(() -> QuotaEvent$.MODULE$.fromJson(quotaUsageUpdatedEvent))
                    .isInstanceOf(IllegalArgumentException.class);
            }
        }

    }

    @Nested
    class WitQuotaRoot {

        @Nested
        class WithNormalQuotaRoot {

            @Test
            void toJsonShouldReturnSerializedJsonQuotaRoot() {
                assertThatJson(QuotaEvent$.MODULE$.toJson(DEFAULT_QUOTA_EVENT))
                    .isEqualTo(DEFAULT_QUOTA_EVENT_JSON);
            }

            @Test
            void fromJsonShouldDeserializeQuotaRootJson() {
                assertThat(QuotaEvent$.MODULE$.fromJson(DEFAULT_QUOTA_EVENT_JSON).get())
                    .isEqualTo(DEFAULT_QUOTA_EVENT);
            }
        }

        @Nested
        class WithEmptyQuotaRoot {
            private final QuotaRoot emptyQuotaRoot = QuotaRoot.quotaRoot("", Optional.empty());
            private final MailboxListener.QuotaUsageUpdatedEvent eventWithEmptyQuotaRoot =
                new MailboxListener.QuotaUsageUpdatedEvent(
                    USER,
                    emptyQuotaRoot,
                    QUOTA_COUNT,
                    QUOTA_SIZE,
                    INSTANT);
            private final String quotaUsageUpdatedEvent =
                "{" +
                    "\"QuotaUsageUpdatedEvent\":{" +
                    "\"quotaRoot\":\"\"," +
                    "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                    "\"time\":\"2018-11-13T12:00:55Z\"," +
                    "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                    "\"user\":\"user\"" +
                    "}" +
                "}";

            @Test
            void toJsonShouldSerializeWithEmptyQuotaRoot() {
                assertThatJson(QuotaEvent$.MODULE$.toJson(eventWithEmptyQuotaRoot))
                    .isEqualTo(quotaUsageUpdatedEvent);
            }

            @Test
            void fromJsonShouldDeserializeWithEmptyQuotaRoot() {
                assertThat(QuotaEvent$.MODULE$.fromJson(quotaUsageUpdatedEvent).get())
                    .isEqualTo(eventWithEmptyQuotaRoot);
            }
        }

        @Nested
        class WithNullQuotaRoot {
            private final MailboxListener.QuotaUsageUpdatedEvent eventWithNullQuotaRoot =
                new MailboxListener.QuotaUsageUpdatedEvent(
                    USER,
                    null,
                    QUOTA_COUNT,
                    QUOTA_SIZE,
                    INSTANT);

            private final String quotaUsageUpdatedEvent =
                "{" +
                    "\"QuotaUsageUpdatedEvent\":{" +
                    "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                    "\"time\":\"2018-11-13T12:00:55Z\"," +
                    "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                    "\"user\":\"user\"" +
                    "}" +
                "}";

            @Test
            void toJsonShouldThrowWithNullQuotaRoot() {
                assertThatThrownBy(() -> QuotaEvent$.MODULE$.toJson(eventWithNullQuotaRoot))
                    .isInstanceOf(NullPointerException.class);
            }

            @Test
            void fromJsonShouldReturnErrorWithNullQuotaRoot() {
                assertThat(QuotaEvent$.MODULE$.fromJson(quotaUsageUpdatedEvent).isSuccess())
                    .isFalse();
            }
        }
    }

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Nested
    class WithQuotaCount {
        private final String limitedQuotaCountEventFormattedJson =
            "{" +
                "\"QuotaUsageUpdatedEvent\":{" +
                "\"quotaRoot\":\"foo\"," +
                "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{\"%s\":100}}," +
                "\"time\":\"2018-11-13T12:00:55Z\"," +
                "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                "\"user\":\"user\"" +
                "}" +
            "}";

        private final String unLimitedQuotaCountEventFormattedJson =
            "{" +
                "\"QuotaUsageUpdatedEvent\":{" +
                "\"quotaRoot\":\"foo\"," +
                "\"countQuota\":{\"used\":12,\"limit\":null,\"limits\":{\"%s\":null}}," +
                "\"time\":\"2018-11-13T12:00:55Z\"," +
                "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                "\"user\":\"user\"" +
                "}" +
            "}";

        private Quota<QuotaCount> limitedQuotaCountByScopes(Quota.Scope scope) {
            return Quota.<QuotaCount>builder()
                .used(QuotaCount.count(12))
                .computedLimit(QuotaCount.count(100))
                .limitForScope(QuotaCount.count(100), scope)
                .build();
        }

        private Quota<QuotaCount> unLimitedQuotaCountByScopes(Quota.Scope scope) {
            return Quota.<QuotaCount>builder()
                .used(QuotaCount.count(12))
                .computedLimit(QuotaCount.unlimited())
                .limitForScope(QuotaCount.unlimited(), scope)
                .build();
        }

        private MailboxListener.QuotaUsageUpdatedEvent quotaEventByQuotaCount(Quota<QuotaCount> countQuota) {
            return new MailboxListener.QuotaUsageUpdatedEvent(USER, QUOTA_ROOT, countQuota, QUOTA_SIZE, INSTANT);
        }

        private String quotaUsageUpdatedEventJsonFactory(String formattedString, Quota.Scope scope) {
            return String.format(formattedString, scope.name());
        }

        private Stream<Arguments> argumentByScope(Quota.Scope scope) {
            MailboxListener.QuotaUsageUpdatedEvent limitedQuotaEvent = quotaEventByQuotaCount(limitedQuotaCountByScopes(scope));
            String limitedQuotaJsonEvent = quotaUsageUpdatedEventJsonFactory(limitedQuotaCountEventFormattedJson, scope);

            MailboxListener.QuotaUsageUpdatedEvent unLimitedQuotaEvent = quotaEventByQuotaCount(unLimitedQuotaCountByScopes(scope));
            String unLimitedQuotaJsonEvent = quotaUsageUpdatedEventJsonFactory(unLimitedQuotaCountEventFormattedJson, scope);

            return Stream.of(
                Arguments.of(limitedQuotaEvent, limitedQuotaJsonEvent),
                Arguments.of(unLimitedQuotaEvent, unLimitedQuotaJsonEvent));
        }

        private Stream<Arguments> quotaUpdateEventFactory() {
            return Stream.of(Quota.Scope.values())
                .flatMap(this::argumentByScope);
        }

        @ParameterizedTest
        @MethodSource("quotaUpdateEventFactory")
        void toJsonShouldSerializeWithVariousQuotaCount(MailboxListener.QuotaUsageUpdatedEvent serializedQuotaEvent,
                                                        String jsonQuotaEvent) {
            assertThatJson(QuotaEvent$.MODULE$.toJson(serializedQuotaEvent))
                .isEqualTo(jsonQuotaEvent);
        }

        @ParameterizedTest
        @MethodSource("quotaUpdateEventFactory")
        void fromJsonShouldDeserializeWithVariousQuotaCount(MailboxListener.QuotaUsageUpdatedEvent quotaEvent,
                                                            String deserializedJsonQuotaEvent) {
            assertThat(QuotaEvent$.MODULE$.fromJson(deserializedJsonQuotaEvent).get())
                .isEqualTo(quotaEvent);
        }
    }

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Nested
    class WithQuotaSize {
        private final String limitedQuotaSizeEventFormattedJson =
            "{" +
                "\"QuotaUsageUpdatedEvent\":{" +
                "\"quotaRoot\":\"foo\"," +
                "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                "\"time\":\"2018-11-13T12:00:55Z\"," +
                "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{\"%s\":10000}}," +
                "\"user\":\"user\"" +
                "}" +
            "}";

        private final String unLimitedQuotaSizeEventFormattedJson =
            "{" +
                "\"QuotaUsageUpdatedEvent\":{" +
                "\"quotaRoot\":\"foo\"," +
                "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                "\"time\":\"2018-11-13T12:00:55Z\"," +
                "\"sizeQuota\":{\"used\":1234,\"limit\":null,\"limits\":{\"%s\":null}}," +
                "\"user\":\"user\"" +
                "}" +
            "}";

        private Quota<QuotaSize> limitedQuotaSizeByScopes(Quota.Scope scope) {
            return Quota.<QuotaSize>builder()
                .used(QuotaSize.size(1234))
                .computedLimit(QuotaSize.size(10000))
                .limitForScope(QuotaSize.size(10000), scope)
                .build();
        }

        private Quota<QuotaSize> unLimitedQuotaSizeByScopes(Quota.Scope scope) {
            return Quota.<QuotaSize>builder()
                .used(QuotaSize.size(1234))
                .computedLimit(QuotaSize.unlimited())
                .limitForScope(QuotaSize.unlimited(), scope)
                .build();
        }

        private MailboxListener.QuotaUsageUpdatedEvent quotaEventByQuotaSize(Quota<QuotaSize> quotaSize) {
            return new MailboxListener.QuotaUsageUpdatedEvent(USER, QUOTA_ROOT, QUOTA_COUNT, quotaSize, INSTANT);
        }

        private String quotaUsageUpdatedEventJsonFactory(String formattedString, Quota.Scope scope) {
            return String.format(formattedString, scope.name());
        }

        private Stream<Arguments> argumentByScope(Quota.Scope scope) {
            MailboxListener.QuotaUsageUpdatedEvent limitedQuotaEvent = quotaEventByQuotaSize(limitedQuotaSizeByScopes(scope));
            String limitedQuotaJsonEvent = quotaUsageUpdatedEventJsonFactory(limitedQuotaSizeEventFormattedJson, scope);

            MailboxListener.QuotaUsageUpdatedEvent unLimitedQuotaEvent = quotaEventByQuotaSize(unLimitedQuotaSizeByScopes(scope));
            String unLimitedQuotaJsonEvent = quotaUsageUpdatedEventJsonFactory(unLimitedQuotaSizeEventFormattedJson, scope);

            return Stream.of(
                Arguments.of(limitedQuotaEvent, limitedQuotaJsonEvent),
                Arguments.of(unLimitedQuotaEvent, unLimitedQuotaJsonEvent));
        }

        private Stream<Arguments> quotaUpdateEventFactory() {
            return Stream.of(Quota.Scope.values())
                .flatMap(this::argumentByScope);
        }

        @ParameterizedTest
        @MethodSource("quotaUpdateEventFactory")
        void toJsonShouldSerializeWithVariousQuotaSize(MailboxListener.QuotaUsageUpdatedEvent serializedQuotaEvent,
                                                String jsonQuotaEvent) {
            assertThatJson(QuotaEvent$.MODULE$.toJson(serializedQuotaEvent))
                .isEqualTo(jsonQuotaEvent);
        }

        @ParameterizedTest
        @MethodSource("quotaUpdateEventFactory")
        void fromJsonShouldDeserializeWithVariousQuotaSize(MailboxListener.QuotaUsageUpdatedEvent quotaEvent,
                                                    String deserializedJsonQuotaEvent) {
            assertThat(QuotaEvent$.MODULE$.fromJson(deserializedJsonQuotaEvent).get())
                .isEqualTo(quotaEvent);
        }
    }

    @Nested
    class WithTime {

        @Test
        void toJsonShouldReturnSerializedJsonEventWhenTimeIsValid() {
            assertThatJson(QuotaEvent$.MODULE$.toJson(DEFAULT_QUOTA_EVENT))
                .isEqualTo(DEFAULT_QUOTA_EVENT_JSON);
        }

        @Test
        void fromJsonShouldReturnDeSerializedEventWhenTimeIsValid() {
            assertThat(QuotaEvent$.MODULE$.fromJson(DEFAULT_QUOTA_EVENT_JSON).get())
                .isEqualTo(DEFAULT_QUOTA_EVENT);
        }

        @Test
        void fromJsonShouldReturnErrorResultWhenTimeIsNull() {
            String quotaUsageUpdatedEvent =
                "{" +
                    "\"QuotaUsageUpdatedEvent\":{" +
                    "\"quotaRoot\":\"foo\"," +
                    "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{\"Domain\":100}}," +
                    "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                    "\"user\":\"user\"" +
                    "}" +
                "}";

            assertThat(QuotaEvent$.MODULE$.fromJson(quotaUsageUpdatedEvent).isSuccess())
                .isFalse();
        }

        @Test
        void fromJsonShouldReturnErrorResultWhenTimeIsEmpty() {
            String quotaUsageUpdatedEvent =
                "{" +
                    "\"QuotaUsageUpdatedEvent\":{" +
                    "\"quotaRoot\":\"foo\"," +
                    "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{\"Domain\":100}}," +
                    "\"time\":\"\"," +
                    "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                    "\"user\":\"user\"" +
                    "}" +
                "}";

            assertThat(QuotaEvent$.MODULE$.fromJson(quotaUsageUpdatedEvent).isSuccess())
                .isFalse();
        }
    }
}
