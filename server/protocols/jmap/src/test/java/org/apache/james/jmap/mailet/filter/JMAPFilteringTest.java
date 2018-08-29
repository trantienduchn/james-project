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

import static org.apache.james.core.builder.MimeMessageBuilder.mimeMessageBuilder;
import static org.apache.james.jmap.api.filtering.Rule.Condition.Comparator.CONTAINS;
import static org.apache.james.jmap.api.filtering.Rule.Condition.Comparator.EXACTLY_EQUALS;
import static org.apache.james.jmap.api.filtering.Rule.Condition.Comparator.NOT_CONTAINS;
import static org.apache.james.jmap.api.filtering.Rule.Condition.Comparator.NOT_EXACTLY_EQUALS;
import static org.apache.james.jmap.api.filtering.Rule.Condition.Field.CC;
import static org.apache.james.jmap.api.filtering.Rule.Condition.Field.FROM;
import static org.apache.james.jmap.api.filtering.Rule.Condition.Field.RECIPIENT;
import static org.apache.james.jmap.api.filtering.Rule.Condition.Field.SUBJECT;
import static org.apache.james.jmap.api.filtering.Rule.Condition.Field.TO;
import static org.apache.james.jmap.mailet.filter.ActionApplier.DELIVERY_PATH_PREFIX;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.BOU;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.CC_HEADER;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.EMPTY;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.FRED_MARTIN_FULLNAME;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.FRED_MARTIN_FULL_SCRAMBLED_ADDRESS;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.GA_BOU_ZO_MEU_FULL_ADDRESS;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.RECIPIENT_1;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.RECIPIENT_1_MAILBOX_1;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.RECIPIENT_1_USERNAME;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.SCRAMBLED_SUBJECT;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.SHOULD_NOT_MATCH;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.TO_HEADER;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.UNFOLDED_USERNAME;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.UNSCRAMBLED_SUBJECT;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_1_ADDRESS;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_1_AND_UNFOLDED_USER_FULL_ADDRESS;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_1_FULL_ADDRESS;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_1_USERNAME;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_2_ADDRESS;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_2_FULL_ADDRESS;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_3_ADDRESS;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_3_FULL_ADDRESS;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_3_USERNAME;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_4_FULL_ADDRESS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.core.User;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.jmap.mailet.filter.JMAPFilteringExtension.JMAPFilteringTestSystem;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.util.OptionalUtils;
import org.apache.james.util.StreamUtils;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

@ExtendWith(JMAPFilteringExtension.class)
class JMAPFilteringTest {

    static class FilteringArgumentBuilder {
        private Optional<String> description;
        private Optional<Rule.Condition.Field> field;
        private Optional<Rule.Condition.Comparator> comparator;
        private Optional<MimeMessageBuilder> mimeMessageBuilder;
        private Optional<String> valueToMatch;

        public FilteringArgumentBuilder(Optional<String> description, Optional<Rule.Condition.Field> field,
                                        Optional<Rule.Condition.Comparator> comparator,
                                        Optional<MimeMessageBuilder> mimeMessageBuilder, Optional<String> valueToMatch) {
            this.description = description;
            this.field = field;
            this.comparator = comparator;
            this.mimeMessageBuilder = mimeMessageBuilder;
            this.valueToMatch = valueToMatch;
        }

        public FilteringArgumentBuilder() {
            this(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }

        public FilteringArgumentBuilder description(String description) {
            this.description = Optional.ofNullable(description);
            return this;
        }

        public FilteringArgumentBuilder field(Rule.Condition.Field field) {
            this.field = Optional.ofNullable(field);
            return this;
        }

        public FilteringArgumentBuilder comparator(Rule.Condition.Comparator comparator) {
            this.comparator = Optional.ofNullable(comparator);
            return this;
        }

        public FilteringArgumentBuilder from(String from) {
            initMessageBuilder();
            mimeMessageBuilder.ifPresent(Throwing.consumer(mimeBuilder -> mimeBuilder.addFrom(from)));
            return this;
        }

        public FilteringArgumentBuilder noHeader() {
            initMessageBuilder();
            return this;
        }

        public FilteringArgumentBuilder toRecipient(String toRecipient) {
            initMessageBuilder();
            mimeMessageBuilder.ifPresent(Throwing.consumer(mimeBuilder -> mimeBuilder.addToRecipient(toRecipient)));
            return this;
        }

        public FilteringArgumentBuilder ccRecipient(String ccRecipient) {
            initMessageBuilder();
            mimeMessageBuilder.ifPresent(Throwing.consumer(mimeBuilder -> mimeBuilder.addCcRecipient(ccRecipient)));
            return this;
        }

        public FilteringArgumentBuilder bccRecipient(String bccRecipient) {
            initMessageBuilder();
            mimeMessageBuilder.ifPresent(Throwing.consumer(mimeBuilder -> mimeBuilder.addBccRecipient(bccRecipient)));
            return this;
        }

        public FilteringArgumentBuilder header(String headerName, String headerValue) {
            initMessageBuilder();
            mimeMessageBuilder.ifPresent(Throwing.consumer(mimeBuilder -> mimeBuilder.addHeader(headerName, headerValue)));
            return this;
        }

        public FilteringArgumentBuilder headerForField(String headerValue) {
            Preconditions.checkState(field.isPresent(), "field should be set first");

            initMessageBuilder();

            mimeMessageBuilder.ifPresent(Throwing.consumer(mimeBuilder -> mimeBuilder.addHeader(field.get().asString(), headerValue)));
            return this;
        }

        public FilteringArgumentBuilder subject(String subject) {
            initMessageBuilder();
            mimeMessageBuilder.ifPresent(Throwing.consumer(mimeBuilder -> mimeBuilder.setSubject(subject)));
            return this;
        }

        public FilteringArgumentBuilder valueToMatch(String valueToMatch) {
            this.valueToMatch = Optional.ofNullable(valueToMatch);
            return this;
        }

        public FilteringArgumentBuilder scrambledSubjectToMatch(String valueToMatch) {
            return description("normal content")
                .field(SUBJECT)
                .subject(SCRAMBLED_SUBJECT)
                .valueToMatch(valueToMatch);
        }

        public FilteringArgumentBuilder unscrambledSubjectToMatch(String valueToMatch) {
            return description("unscrambled content")
                .field(SUBJECT)
                .subject(UNSCRAMBLED_SUBJECT)
                .valueToMatch(valueToMatch);
        }

        public Arguments build() {
            return Arguments.of(
                Stream.of(description, field, comparator, mimeMessageBuilder, valueToMatch)
                    .flatMap(OptionalUtils::toStream)
                    .toArray());
        }

        private void initMessageBuilder() {
            if (!mimeMessageBuilder.isPresent()) {
                mimeMessageBuilder = Optional.of(MimeMessageBuilder.mimeMessageBuilder());
            }
        }

        public FilteringArgumentBuilder copy() {
            return new FilteringArgumentBuilder(description, field, comparator, mimeMessageBuilder, valueToMatch);
        }
    }

    static class FilteringArgumentsProvider {
        private final List<FilteringArgumentBuilder> argumentsBuilders;

        public FilteringArgumentsProvider() {
            argumentsBuilders = new ArrayList<>();
        }

        public FilteringArgumentsProvider argument(FilteringArgumentBuilder builder) {
            argumentsBuilders.add(builder);
            return this;
        }

        public Stream<Arguments> toStream() {
            return argumentsBuilders.stream().map(FilteringArgumentBuilder::build);
        }
    }

    static FilteringArgumentBuilder argumentBuilder() {
        return new FilteringArgumentBuilder();
    }

    static FilteringArgumentsProvider argumentsProvider() {
        return new FilteringArgumentsProvider();
    }

    static Stream<Arguments> exactlyEqualsTestSuite() {
        return StreamUtils.flatten(
            Stream.of(FROM, TO, CC)
                .map(headerField -> argumentBuilder().field(headerField))
                .flatMap(argBuilder -> argumentsProvider()
                    .argument(argBuilder.copy()
                        .description("full address value")
                        .headerForField(USER_1_FULL_ADDRESS)
                        .valueToMatch(USER_1_USERNAME))
                    .argument(argBuilder.copy()
                        .description("address only value")
                        .headerForField(USER_1_FULL_ADDRESS)
                        .valueToMatch(USER_1_ADDRESS))
                    .argument(argBuilder.copy()
                        .description("personal only value")
                        .headerForField(USER_1_FULL_ADDRESS)
                        .valueToMatch(USER_1_FULL_ADDRESS))
                    .argument(argBuilder.copy()
                        .description("personal header should match personal")
                        .headerForField(USER_1_USERNAME)
                        .valueToMatch(USER_1_USERNAME))
                    .argument(argBuilder.copy()
                        .description("address header should match address")
                        .headerForField(USER_1_ADDRESS)
                        .valueToMatch(USER_1_ADDRESS))
                    .argument(argBuilder.copy()
                        .description("multiple headers")
                        .headerForField(USER_1_FULL_ADDRESS)
                        .headerForField(USER_2_FULL_ADDRESS)
                        .valueToMatch(USER_1_USERNAME))
                    .argument(argBuilder.copy()
                        .description("scrambled content")
                        .headerForField(FRED_MARTIN_FULL_SCRAMBLED_ADDRESS)
                        .valueToMatch(FRED_MARTIN_FULLNAME))
                    .argument(argBuilder.copy()
                        .description("folded content")
                        .headerForField(USER_1_AND_UNFOLDED_USER_FULL_ADDRESS)
                        .valueToMatch(UNFOLDED_USERNAME))
                    .toStream()),
            Stream.of(TO_HEADER, CC_HEADER)
                .flatMap(headerName -> Stream.of(
                    argumentBuilder()
                        .description("full address " + headerName + " header")
                        .field(RECIPIENT)
                        .header(headerName, USER_3_FULL_ADDRESS)
                        .valueToMatch(USER_3_FULL_ADDRESS)
                        .build(),
                    argumentBuilder()
                        .description("address only " + headerName + " header")
                        .field(RECIPIENT).header(headerName, USER_3_FULL_ADDRESS)
                        .valueToMatch(USER_3_ADDRESS)
                        .build(),
                    argumentBuilder()
                        .description("personal only " + headerName + " header")
                        .field(RECIPIENT)
                        .header(headerName, USER_3_FULL_ADDRESS)
                        .valueToMatch(USER_3_USERNAME)
                        .build(),
                    argumentBuilder()
                        .description("scrambled content in " + headerName + " header")
                        .field(RECIPIENT)
                        .header(headerName, FRED_MARTIN_FULL_SCRAMBLED_ADDRESS)
                        .valueToMatch(FRED_MARTIN_FULLNAME)
                        .build(),
                    argumentBuilder()
                        .description("folded content in " + headerName + " header")
                        .field(RECIPIENT)
                        .header(headerName, USER_1_AND_UNFOLDED_USER_FULL_ADDRESS)
                        .valueToMatch(UNFOLDED_USERNAME)
                        .build())),
            argumentsProvider()
                .argument(argumentBuilder().description("multiple to and cc headers").field(RECIPIENT)
                    .ccRecipient(USER_1_FULL_ADDRESS)
                    .ccRecipient(USER_2_FULL_ADDRESS)
                    .toRecipient(USER_3_FULL_ADDRESS)
                    .toRecipient(USER_4_FULL_ADDRESS)
                    .valueToMatch(USER_4_FULL_ADDRESS))
                .argument(argumentBuilder().scrambledSubjectToMatch(UNSCRAMBLED_SUBJECT))
                .argument(argumentBuilder().unscrambledSubjectToMatch(UNSCRAMBLED_SUBJECT))
                .toStream());
    }

    static Stream<Arguments> containsTestSuite() {
        return Stream.concat(
            exactlyEqualsTestSuite(),
            containsArguments());
    }

    private static Stream<Arguments> containsArguments() {
        return StreamUtils.flatten(
            Stream.of(FROM, TO, CC)
                .map(headerField -> argumentBuilder().field(headerField))
                .flatMap(argBuilder -> argumentsProvider()
                    .argument(argBuilder.copy()
                        .description("full address value (partial matching)")
                        .headerForField(USER_1_FULL_ADDRESS)
                        .valueToMatch("ser1 <"))
                    .argument(argBuilder.copy()
                        .description("address only value (partial matching)")
                        .headerForField(USER_1_FULL_ADDRESS)
                        .valueToMatch("ser1@jam"))
                    .argument(argBuilder.copy()
                        .description("personal only value (partial matching)")
                        .headerForField(USER_1_FULL_ADDRESS)
                        .valueToMatch("ser1"))
                    .argument(argBuilder.copy()
                        .description("address header & match in the address (partial matching)")
                        .headerForField(USER_1_ADDRESS)
                        .valueToMatch("ser1@jam"))
                    .argument(argBuilder.copy()
                        .description("raw value matching (partial matching)")
                        .headerForField(GA_BOU_ZO_MEU_FULL_ADDRESS)
                        .valueToMatch(BOU))
                    .argument(argBuilder.copy()
                        .description("multiple headers (partial matching)")
                        .headerForField(USER_1_FULL_ADDRESS)
                        .headerForField(USER_2_FULL_ADDRESS)
                        .valueToMatch("ser1@jam"))
                    .argument(argBuilder.copy()
                        .description("scrambled content (partial matching)")
                        .headerForField(FRED_MARTIN_FULL_SCRAMBLED_ADDRESS)
                        .valueToMatch("déric MAR"))
                    .argument(argBuilder.copy()
                        .description("folded content (partial matching)")
                        .headerForField(USER_1_AND_UNFOLDED_USER_FULL_ADDRESS)
                        .valueToMatch("ded_us"))
                    .toStream()),
            Stream.of(TO_HEADER, CC_HEADER)
                .flatMap(headerName -> Stream.of(
                    argumentBuilder()
                        .description("full address " + headerName + " header (partial matching)")
                        .field(RECIPIENT)
                        .header(headerName, USER_3_FULL_ADDRESS)
                        .valueToMatch("ser3 <us")
                        .build(),
                    argumentBuilder()
                        .description("address only " + headerName + " header (partial matching)")
                        .field(RECIPIENT)
                        .header(headerName, USER_3_FULL_ADDRESS)
                        .valueToMatch("ser3@jam")
                        .build(),
                    argumentBuilder()
                        .description("personal only " + headerName + " header (partial matching)")
                        .field(RECIPIENT)
                        .header(headerName, USER_3_FULL_ADDRESS)
                        .valueToMatch("ser3")
                        .build(),
                    argumentBuilder()
                        .description("scrambled content in " + headerName + " header (partial matching)")
                        .field(RECIPIENT)
                        .header(headerName, FRED_MARTIN_FULL_SCRAMBLED_ADDRESS)
                        .valueToMatch("déric MAR")
                        .build(),
                    argumentBuilder()
                        .description("folded content in " + headerName + " header (partial matching)")
                        .field(RECIPIENT)
                        .header(headerName, USER_1_AND_UNFOLDED_USER_FULL_ADDRESS)
                        .valueToMatch("folded_us")
                        .build())),
            argumentsProvider()
                .argument(argumentBuilder().description("multiple to and cc headers (partial matching)").field(RECIPIENT)
                    .ccRecipient(USER_1_FULL_ADDRESS)
                    .ccRecipient(USER_2_FULL_ADDRESS)
                    .toRecipient(USER_3_FULL_ADDRESS)
                    .toRecipient(USER_4_FULL_ADDRESS)
                    .valueToMatch("user4@jam"))
                .argument(argumentBuilder().scrambledSubjectToMatch("is the subject"))
                .argument(argumentBuilder().unscrambledSubjectToMatch("rédéric MART"))
                .toStream());
    }

    static Stream<Arguments> notEqualsTestSuite() {
        return Stream.concat(
            notContainsTestSuite(),
            containsArguments());
    }

    static Stream<Arguments> notContainsTestSuite() {
        return StreamUtils.flatten(
            Stream.of(FROM, TO, CC)
                .map(headerField -> argumentBuilder().field(headerField))
                .flatMap(argBuilder -> argumentsProvider()
                    .argument(argBuilder.copy()
                        .description("normal content")
                        .headerForField(USER_1_FULL_ADDRESS)
                        .valueToMatch(SHOULD_NOT_MATCH))
                    .argument(argBuilder.copy()
                        .description("multiple headers")
                        .headerForField(USER_1_FULL_ADDRESS)
                        .from(USER_2_FULL_ADDRESS)
                        .valueToMatch(SHOULD_NOT_MATCH))
                    .argument(argBuilder.copy()
                        .description("scrambled content")
                        .headerForField(FRED_MARTIN_FULL_SCRAMBLED_ADDRESS)
                        .valueToMatch(SHOULD_NOT_MATCH))
                    .argument(argBuilder.copy()
                        .description("folded content")
                        .headerForField(USER_1_AND_UNFOLDED_USER_FULL_ADDRESS)
                        .valueToMatch(SHOULD_NOT_MATCH))
                    .argument(argBuilder.copy()
                        .description("empty content")
                        .headerForField(EMPTY)
                        .valueToMatch(SHOULD_NOT_MATCH))
                    .argument(argBuilder.copy()
                        .description("case sensitive content")
                        .headerForField(GA_BOU_ZO_MEU_FULL_ADDRESS)
                        .valueToMatch(BOU.toLowerCase()))
                    .toStream()),
            Stream.of(TO_HEADER, CC_HEADER)
                .flatMap(headerName -> Stream.of(
                    argumentBuilder()
                        .description("normal content " + headerName + " header")
                        .field(RECIPIENT).header(headerName, USER_3_FULL_ADDRESS)
                        .valueToMatch(SHOULD_NOT_MATCH)
                        .build(),
                    argumentBuilder()
                        .description("scrambled content in " + headerName + " header")
                        .field(RECIPIENT).header(headerName, FRED_MARTIN_FULL_SCRAMBLED_ADDRESS)
                        .valueToMatch(SHOULD_NOT_MATCH)
                        .build(),
                    argumentBuilder()
                        .description("folded content in " + headerName + " header")
                        .field(RECIPIENT)
                        .header(headerName, USER_1_AND_UNFOLDED_USER_FULL_ADDRESS)
                        .valueToMatch(SHOULD_NOT_MATCH)
                        .build(),
                    argumentBuilder()
                        .description("bcc header")
                        .field(RECIPIENT)
                        .header(headerName, USER_1_AND_UNFOLDED_USER_FULL_ADDRESS)
                        .valueToMatch(SHOULD_NOT_MATCH)
                        .build())),
            argumentsProvider()
                .argument(argumentBuilder().description("multiple to and cc headers").field(RECIPIENT)
                    .ccRecipient(USER_1_FULL_ADDRESS)
                    .ccRecipient(USER_2_FULL_ADDRESS)
                    .toRecipient(USER_3_FULL_ADDRESS)
                    .toRecipient(USER_4_FULL_ADDRESS)
                    .valueToMatch(SHOULD_NOT_MATCH))
                .argument(argumentBuilder().description("matching bcc headers").field(RECIPIENT)
                    .bccRecipient(USER_1_FULL_ADDRESS)
                    .valueToMatch(USER_1_FULL_ADDRESS))
                .argument(argumentBuilder().scrambledSubjectToMatch(SHOULD_NOT_MATCH))
                .argument(argumentBuilder().unscrambledSubjectToMatch(SHOULD_NOT_MATCH))
                .toStream(),
            Stream.of(Rule.Condition.Field.values())
                .map(field -> argumentBuilder()
                    .description("no header")
                    .field(field)
                    .noHeader()
                    .valueToMatch(USER_1_USERNAME)
                    .build()));
    }

    @ParameterizedTest(name = "CONTAINS should match for header field {1}, with {0}")
    @MethodSource("containsTestSuite")
    void matchingContainsTest(String testDescription,
                              Rule.Condition.Field fieldToMatch,
                              MimeMessageBuilder mimeMessageBuilder,
                              String valueToMatch,
                              JMAPFilteringTestSystem testSystem) throws Exception {

        testSystem.defineRulesForRecipient1(Rule.Condition.of(fieldToMatch, CONTAINS, valueToMatch));
        FakeMail mail = testSystem.asMail(mimeMessageBuilder);
        testSystem.getJmapFiltering().service(mail);

        assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                .isEqualTo(RECIPIENT_1_MAILBOX_1);
    }

    @ParameterizedTest(name = "CONTAINS should not match for header field {1}, with {0}")
    @MethodSource("notContainsTestSuite")
    void notMatchingContainsTest(String testDescription,
                              Rule.Condition.Field fieldToMatch,
                              MimeMessageBuilder mimeMessageBuilder,
                              String valueToMatch,
                              JMAPFilteringTestSystem testSystem) throws Exception {

        testSystem.defineRulesForRecipient1(Rule.Condition.of(fieldToMatch, CONTAINS, valueToMatch));
        FakeMail mail = testSystem.asMail(mimeMessageBuilder);
        testSystem.getJmapFiltering().service(mail);

        assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                .isNull();
    }

    @ParameterizedTest(name = "NOT-CONTAINS should be matching for field {1}, with {0}")
    @MethodSource("notContainsTestSuite")
    void matchingNotContainsTest(String testDescription,
                                 Rule.Condition.Field fieldToMatch,
                                 MimeMessageBuilder mimeMessageBuilder,
                                 String valueToMatch,
                                 JMAPFilteringTestSystem testSystem) throws Exception {
        testSystem.defineRulesForRecipient1(Rule.Condition.of(fieldToMatch, NOT_CONTAINS, valueToMatch));
        FakeMail mail = testSystem.asMail(mimeMessageBuilder);
        testSystem.getJmapFiltering().service(mail);

        assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
            .isEqualTo(RECIPIENT_1_MAILBOX_1);
    }


    @ParameterizedTest(name = "NOT-CONTAINS should not be matching for field {1}, with {0}")
    @MethodSource("containsTestSuite")
    void notContainsNotMatchingTest(String testDescription,
                                    Rule.Condition.Field fieldToMatch,
                                    MimeMessageBuilder mimeMessageBuilder,
                                    String valueToMatch,
                                    JMAPFilteringTestSystem testSystem) throws Exception {

        testSystem.defineRulesForRecipient1(Rule.Condition.of(fieldToMatch, NOT_CONTAINS, valueToMatch));
        FakeMail mail = testSystem.asMail(mimeMessageBuilder);
        testSystem.getJmapFiltering().service(mail);

        assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
            .isNull();
    }

    @ParameterizedTest(name = "EXACTLY-EQUALS should match for header field {1}, with {0}")
    @MethodSource("exactlyEqualsTestSuite")
    void equalsMatchingTest(String testDescription,
                            Rule.Condition.Field fieldToMatch,
                            MimeMessageBuilder mimeMessageBuilder,
                            String valueToMatch,
                            JMAPFilteringTestSystem testSystem) throws Exception {

        testSystem.defineRulesForRecipient1(Rule.Condition.of(fieldToMatch, EXACTLY_EQUALS, valueToMatch));
        FakeMail mail = testSystem.asMail(mimeMessageBuilder);
        testSystem.getJmapFiltering().service(mail);

        assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
            .isEqualTo(RECIPIENT_1_MAILBOX_1);
    }

    @ParameterizedTest(name = "EXACTLY-EQUALS should not match for header field {1}, with {0}")
    @MethodSource("notEqualsTestSuite")
    void equalsNotMatchingTest(String testDescription,
                            Rule.Condition.Field fieldToMatch,
                            MimeMessageBuilder mimeMessageBuilder,
                            String valueToMatch,
                            JMAPFilteringTestSystem testSystem) throws Exception {
        testSystem.defineRulesForRecipient1(Rule.Condition.of(fieldToMatch, EXACTLY_EQUALS, valueToMatch));
        FakeMail mail = testSystem.asMail(mimeMessageBuilder);
        testSystem.getJmapFiltering().service(mail);

        assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
            .isNull();
    }

    @ParameterizedTest(name = "NOT_EXACTLY_EQUALS should match for header field {1}, with {0}")
    @MethodSource("notEqualsTestSuite")
    void notEqualsMatchingTest(String testDescription,
                               Rule.Condition.Field fieldToMatch,
                               MimeMessageBuilder mimeMessageBuilder,
                               String valueToMatch,
                               JMAPFilteringTestSystem testSystem) throws Exception {

        testSystem.defineRulesForRecipient1(Rule.Condition.of(fieldToMatch, NOT_EXACTLY_EQUALS, valueToMatch));
        FakeMail mail = testSystem.asMail(mimeMessageBuilder);
        testSystem.getJmapFiltering().service(mail);

        assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
            .isEqualTo(RECIPIENT_1_MAILBOX_1);
    }

    @ParameterizedTest(name = "NOT_EXACTLY_EQUALS should not match for header field {1}, with {0}")
    @MethodSource("exactlyEqualsTestSuite")
    void notMatchingNotEqualsTests(String testDescription,
                                   Rule.Condition.Field fieldToMatch,
                                   MimeMessageBuilder mimeMessageBuilder,
                                   String valueToMatch,
                                   JMAPFilteringTestSystem testSystem) throws Exception {
        testSystem.defineRulesForRecipient1(Rule.Condition.of(fieldToMatch, NOT_EXACTLY_EQUALS, valueToMatch));
        FakeMail mail = testSystem.asMail(mimeMessageBuilder);
        testSystem.getJmapFiltering().service(mail);

        assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
            .isNull();
    }

    @Nested
    class MultiRuleBehaviourTest {
        @Test
        void mailDirectiveShouldSetFirstMatchedRuleWhenMultipleRules(JMAPFilteringTestSystem testSystem) throws Exception {
            InMemoryMailboxManager mailboxManager = testSystem.getMailboxManager();
            MailboxId mailbox1Id = testSystem.createMailbox(mailboxManager, RECIPIENT_1_USERNAME, "RECIPIENT_1_MAILBOX_1");
            MailboxId mailbox2Id = testSystem.createMailbox(mailboxManager, RECIPIENT_1_USERNAME, "RECIPIENT_1_MAILBOX_2");
            MailboxId mailbox3Id = testSystem.createMailbox(mailboxManager, RECIPIENT_1_USERNAME, "RECIPIENT_1_MAILBOX_3");

            testSystem.defineRulesForRecipient1();
            testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                ImmutableList.of(
                    Rule.builder()
                        .id(Rule.Id.of("1"))
                        .name("rule 1")
                        .condition(Rule.Condition.of(SUBJECT, CONTAINS, UNSCRAMBLED_SUBJECT))
                        .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(mailbox1Id.serialize())))
                        .build(),
                    Rule.builder()
                        .id(Rule.Id.of("2"))
                        .name("rule 2")
                        .condition(Rule.Condition.of(FROM, NOT_CONTAINS, USER_1_USERNAME))
                        .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(mailbox2Id.serialize())))
                        .build(),
                    Rule.builder()
                        .id(Rule.Id.of("3"))
                        .name("rule 3")
                        .condition(Rule.Condition.of(TO, EXACTLY_EQUALS, USER_3_ADDRESS))
                        .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(mailbox3Id.serialize())))
                        .build()));

            FakeMail mail = FakeMail.builder()
                .sender(USER_1_ADDRESS)
                .recipients(RECIPIENT_1)
                .mimeMessage(mimeMessageBuilder()
                    .addFrom(USER_2_ADDRESS)
                    .addToRecipient(USER_3_ADDRESS)
                    .setSubject(UNSCRAMBLED_SUBJECT))
                .build();

            testSystem.getJmapFiltering().service(mail);

            assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                .isEqualTo("RECIPIENT_1_MAILBOX_1");
        }

        @Test
        void mailDirectiveShouldSetFirstMatchedMailboxWhenMultipleMailboxes(JMAPFilteringTestSystem testSystem) throws Exception {
            InMemoryMailboxManager mailboxManager = testSystem.getMailboxManager();
            MailboxId mailbox1Id = testSystem.createMailbox(mailboxManager, RECIPIENT_1_USERNAME, "RECIPIENT_1_MAILBOX_1");
            MailboxId mailbox2Id = testSystem.createMailbox(mailboxManager, RECIPIENT_1_USERNAME, "RECIPIENT_1_MAILBOX_2");
            MailboxId mailbox3Id = testSystem.createMailbox(mailboxManager, RECIPIENT_1_USERNAME, "RECIPIENT_1_MAILBOX_3");

            testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                ImmutableList.of(
                    Rule.builder()
                        .id(Rule.Id.of("1"))
                        .name("rule 1")
                        .condition(Rule.Condition.of(SUBJECT, CONTAINS, UNSCRAMBLED_SUBJECT))
                        .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(ImmutableList.of(
                            mailbox3Id.serialize(),
                            mailbox2Id.serialize(),
                            mailbox1Id.serialize()))))
                        .build()));

            FakeMail mail = FakeMail.builder()
                .sender(USER_1_ADDRESS)
                .recipients(RECIPIENT_1)
                .mimeMessage(mimeMessageBuilder()
                    .setSubject(UNSCRAMBLED_SUBJECT))
                .build();

            testSystem.getJmapFiltering().service(mail);

            assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                .isEqualTo("RECIPIENT_1_MAILBOX_3");
        }

        @Test
        void mailDirectiveShouldNotBeSetWhenAllDoNotExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
            testSystem.defineRulesForRecipient1(
                Rule.Condition.of(FROM, CONTAINS, USER_1_FULL_ADDRESS),
                Rule.Condition.of(FROM, EXACTLY_EQUALS, USER_1_FULL_ADDRESS),
                Rule.Condition.of(TO, CONTAINS, USER_1_FULL_ADDRESS),
                Rule.Condition.of(TO, EXACTLY_EQUALS, USER_1_FULL_ADDRESS),
                Rule.Condition.of(CC, CONTAINS, USER_1_FULL_ADDRESS),
                Rule.Condition.of(CC, EXACTLY_EQUALS, USER_1_FULL_ADDRESS),
                Rule.Condition.of(RECIPIENT, EXACTLY_EQUALS, USER_1_FULL_ADDRESS),
                Rule.Condition.of(RECIPIENT, EXACTLY_EQUALS, USER_1_FULL_ADDRESS),
                Rule.Condition.of(SUBJECT, CONTAINS, USER_1_FULL_ADDRESS),
                Rule.Condition.of(SUBJECT, EXACTLY_EQUALS, USER_1_FULL_ADDRESS)
            );

            FakeMail mail = testSystem.asMail(mimeMessageBuilder());
            testSystem.getJmapFiltering().service(mail);

            assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                .isNull();
        }

        @Test
        void mailDirectiveShouldNotBeSetWhenNoneRulesValueIsContained(JMAPFilteringTestSystem testSystem) throws Exception {

            testSystem.defineRulesForRecipient1(
                Rule.Condition.of(FROM, CONTAINS, SHOULD_NOT_MATCH),
                Rule.Condition.of(TO, CONTAINS, SHOULD_NOT_MATCH),
                Rule.Condition.of(CC, CONTAINS, SHOULD_NOT_MATCH));

            FakeMail mail = FakeMail.builder()
                .sender(USER_1_ADDRESS)
                .recipients(RECIPIENT_1)
                .mimeMessage(mimeMessageBuilder()
                    .addFrom(USER_1_FULL_ADDRESS)
                    .addToRecipient(USER_2_FULL_ADDRESS)
                    .addCcRecipient(USER_3_FULL_ADDRESS))
                .build();

            testSystem.getJmapFiltering().service(mail);

            assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                .isNull();
        }
    }
}