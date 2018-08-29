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
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.BOU;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.DELIVERY_PATH_PREFIX;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.EMPTY;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.FRED_MARTIN_FULLNAME;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.FRED_MARTIN_FULL_SCRAMBLED_ADDRESS;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.GA_BOU_ZO_MEU_FULL_ADDRESS;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.RECIPIENT_1;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.RECIPIENT_1_MAILBOX_1;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.RECIPIENT_1_USERNAME;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.SCRAMBLED_SUBJECT;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.SHOULD_NOT_MATCH;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.UNFOLDED_USERNAME;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.UNSCRAMBLED_SUBJECT;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_1_ADDRESS;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_1_AND_UNFOLDED_USER_FULL_ADDRESS;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_1_FULL_ADDRESS;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_1_USERNAME;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_2_ADDRESS;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_2_FULL_ADDRESS;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_2_USERNAME;
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
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

@ExtendWith(JMAPFilteringExtension.class)
class JMAPFilteringTest {

    static class FilteringArgumentBuilder {
        private Optional<String> description;
        private Optional<Rule.Condition.Field> field;
        private Optional<Rule.Condition.Comparator> comparator;
        private Optional<MimeMessageBuilder> mimeMessageBuilder;
        private Optional<String> valueToMatch;

        public FilteringArgumentBuilder() {
            description = Optional.empty();
            field = Optional.empty();
            comparator = Optional.empty();
            mimeMessageBuilder = Optional.empty();
            valueToMatch = Optional.empty();
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
            ArrayList<Object> availableArgs = new ArrayList<>();

            description.ifPresent(availableArgs::add);
            field.ifPresent(availableArgs::add);
            comparator.ifPresent(availableArgs::add);
            mimeMessageBuilder.ifPresent(availableArgs::add);
            valueToMatch.ifPresent(availableArgs::add);

            return Arguments.of(availableArgs.toArray(new Object[availableArgs.size()]));
        }

        private void initMessageBuilder() {
            if (!mimeMessageBuilder.isPresent()) {
                mimeMessageBuilder = Optional.of(MimeMessageBuilder.mimeMessageBuilder());
            }
        }
    }

    static class FilteringArgumentsProvider {

        private List<FilteringArgumentBuilder> argumentsBuilders;

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

    static Stream<Arguments> mailDirectiveShouldBeSetWhenContainsRuleValueParamsProvider() {
        return Stream.concat(
            Stream.of(FROM, TO, CC)
                .flatMap(headerField -> argumentsProvider()
                    .argument(argumentBuilder().description("normal content").field(headerField).header(headerField.asString(), USER_1_FULL_ADDRESS).valueToMatch(USER_1_USERNAME))
                    .argument(argumentBuilder().description("multiple headers").field(headerField).header(headerField.asString(), USER_1_FULL_ADDRESS).from(USER_2_FULL_ADDRESS).valueToMatch(USER_1_USERNAME))
                    .argument(argumentBuilder().description("scrambled content").field(headerField).header(headerField.asString(), FRED_MARTIN_FULL_SCRAMBLED_ADDRESS).valueToMatch(FRED_MARTIN_FULLNAME))
                    .argument(argumentBuilder().description("folded content").field(headerField).header(headerField.asString(), USER_1_AND_UNFOLDED_USER_FULL_ADDRESS).valueToMatch(UNFOLDED_USERNAME))
                    .argument(argumentBuilder().description("multiple spaces content").field(headerField).header(headerField.asString(), GA_BOU_ZO_MEU_FULL_ADDRESS).valueToMatch(BOU))
                    .toStream()),

            argumentsProvider()
                .argument(argumentBuilder().description("normal content to header").field(RECIPIENT).header("to", USER_3_FULL_ADDRESS).valueToMatch(USER_3_USERNAME))
                .argument(argumentBuilder().description("scrambled content in to header").field(RECIPIENT).header("to", FRED_MARTIN_FULL_SCRAMBLED_ADDRESS).valueToMatch(FRED_MARTIN_FULLNAME))
                .argument(argumentBuilder().description("folded content in to header").field(RECIPIENT).header("to", USER_1_AND_UNFOLDED_USER_FULL_ADDRESS).valueToMatch(UNFOLDED_USERNAME))

                .argument(argumentBuilder().description("normal content cc header").field(RECIPIENT).header("cc", USER_3_FULL_ADDRESS).valueToMatch(USER_3_USERNAME))
                .argument(argumentBuilder().description("scrambled content in cc header").field(RECIPIENT).header("cc", FRED_MARTIN_FULL_SCRAMBLED_ADDRESS).valueToMatch(FRED_MARTIN_FULLNAME))
                .argument(argumentBuilder().description("folded content in cc header").field(RECIPIENT).header("cc", USER_1_AND_UNFOLDED_USER_FULL_ADDRESS).valueToMatch(UNFOLDED_USERNAME))

                .argument(argumentBuilder().description("multiple to and cc headers").field(RECIPIENT)
                        .ccRecipient(USER_1_FULL_ADDRESS)
                        .ccRecipient(USER_2_FULL_ADDRESS)
                        .toRecipient(USER_3_FULL_ADDRESS)
                        .toRecipient(USER_4_FULL_ADDRESS)
                        .valueToMatch(USER_1_USERNAME))

                .argument(argumentBuilder().scrambledSubjectToMatch(FRED_MARTIN_FULLNAME))
                .argument(argumentBuilder().unscrambledSubjectToMatch(FRED_MARTIN_FULLNAME))

                .toStream()
        );
    }

    @ParameterizedTest(name = "mailDirectiveShouldBeSetWhenContainsRuleValue when matching header field {1}, with {0}")
    @MethodSource("mailDirectiveShouldBeSetWhenContainsRuleValueParamsProvider")
    void mailDirectiveShouldBeSetWhenContainsRuleValue(String testDescription,
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

    static Stream<Arguments> mailDirectiveShouldBeSetWhenDoesntContainsRuleValueParamsProvider() throws Exception {
        return Stream.concat(
            Stream.of(FROM, TO, CC)
                .flatMap(headerField -> argumentsProvider()
                    .argument(argumentBuilder().description("normal content").field(headerField).header(headerField.asString(), USER_1_FULL_ADDRESS).valueToMatch(SHOULD_NOT_MATCH))
                    .argument(argumentBuilder().description("multiple headers").field(headerField).header(headerField.asString(), USER_1_FULL_ADDRESS).from(USER_2_FULL_ADDRESS).valueToMatch(SHOULD_NOT_MATCH))
                    .argument(argumentBuilder().description("scrambled content").field(headerField).header(headerField.asString(), FRED_MARTIN_FULL_SCRAMBLED_ADDRESS).valueToMatch(SHOULD_NOT_MATCH))
                    .argument(argumentBuilder().description("folded content").field(headerField).header(headerField.asString(), USER_1_AND_UNFOLDED_USER_FULL_ADDRESS).valueToMatch(SHOULD_NOT_MATCH))
                    .argument(argumentBuilder().description("empty content").field(headerField).header(headerField.asString(), EMPTY).valueToMatch(SHOULD_NOT_MATCH))
                    .argument(argumentBuilder().description("case sensitive content").field(headerField).header(headerField.asString(), GA_BOU_ZO_MEU_FULL_ADDRESS).valueToMatch(BOU.toLowerCase()))
                    .toStream()),

            argumentsProvider()
                .argument(argumentBuilder().description("normal content to header").field(RECIPIENT).header("to", USER_3_FULL_ADDRESS).valueToMatch(SHOULD_NOT_MATCH))
                .argument(argumentBuilder().description("scrambled content in to header").field(RECIPIENT).header("to", FRED_MARTIN_FULL_SCRAMBLED_ADDRESS).valueToMatch(SHOULD_NOT_MATCH))
                .argument(argumentBuilder().description("folded content in to header").field(RECIPIENT).header("to", USER_1_AND_UNFOLDED_USER_FULL_ADDRESS).valueToMatch(SHOULD_NOT_MATCH))

                .argument(argumentBuilder().description("normal content cc header").field(RECIPIENT).header("cc", USER_3_FULL_ADDRESS).valueToMatch(SHOULD_NOT_MATCH))
                .argument(argumentBuilder().description("scrambled content in cc header").field(RECIPIENT).header("cc", FRED_MARTIN_FULL_SCRAMBLED_ADDRESS).valueToMatch(SHOULD_NOT_MATCH))
                .argument(argumentBuilder().description("folded content in cc header").field(RECIPIENT).header("cc", USER_1_AND_UNFOLDED_USER_FULL_ADDRESS).valueToMatch(SHOULD_NOT_MATCH))

                .argument(argumentBuilder().description("multiple to and cc headers").field(RECIPIENT)
                        .ccRecipient(USER_1_FULL_ADDRESS)
                        .ccRecipient(USER_2_FULL_ADDRESS)
                        .toRecipient(USER_3_FULL_ADDRESS)
                        .toRecipient(USER_4_FULL_ADDRESS)
                        .valueToMatch(SHOULD_NOT_MATCH))

                .argument(argumentBuilder().scrambledSubjectToMatch(SHOULD_NOT_MATCH))
                .argument(argumentBuilder().unscrambledSubjectToMatch(SHOULD_NOT_MATCH))

                .toStream()
        );
    }

    @ParameterizedTest(name = "mailDirectiveShouldBeSetWhenDoesntContainsRuleValue when matching header field {1}, with {0}")
    @MethodSource("mailDirectiveShouldBeSetWhenDoesntContainsRuleValueParamsProvider")
    void mailDirectiveShouldBeSetWhenDoesntContainsRuleValue(String testDescription,
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

    static Stream<Arguments> mailDirectiveShouldNotBeSetWhenAtleastOneContentContainsRuleValueParamsProvider() throws Exception {
        return argumentsProvider()
            .argument(argumentBuilder().description("one match").field(FROM).from(USER_1_FULL_ADDRESS).from(USER_2_FULL_ADDRESS).valueToMatch(USER_2_USERNAME))
            .argument(argumentBuilder().description("two matches").field(TO)
                    .toRecipient(USER_1_FULL_ADDRESS)
                    .toRecipient(USER_2_FULL_ADDRESS)
                    .toRecipient(FRED_MARTIN_FULL_SCRAMBLED_ADDRESS)
                .valueToMatch("user"))
            .argument(argumentBuilder().description("all matches").field(CC)
                    .ccRecipient(USER_1_FULL_ADDRESS)
                    .ccRecipient(USER_2_FULL_ADDRESS)
                    .ccRecipient(USER_3_FULL_ADDRESS)
                    .ccRecipient(USER_4_FULL_ADDRESS)
                .valueToMatch("user"))
            .toStream();
    }

    @ParameterizedTest(name = "mailDirectiveShouldNotBeSetWhenAtleastOneContentContainsRuleValue when {0}")
    @MethodSource("mailDirectiveShouldNotBeSetWhenAtleastOneContentContainsRuleValueParamsProvider")
    void mailDirectiveShouldNotBeSetWhenAtleastOneContentContainsRuleValue(
            String testDescription,
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

    static Stream<Arguments> mailDirectiveShouldBeSetWhenExactlyEqualsRuleValueParamsProvider() throws Exception {
        return Stream.concat(
            Stream.of(FROM, TO, CC)
                .flatMap(headerField -> argumentsProvider()
                    .argument(argumentBuilder().description("full address value").field(headerField).header(headerField.asString(), USER_1_FULL_ADDRESS).valueToMatch(USER_1_USERNAME))
                    .argument(argumentBuilder().description("address only value").field(headerField).header(headerField.asString(), USER_1_FULL_ADDRESS).valueToMatch(USER_1_USERNAME))
                    .argument(argumentBuilder().description("personal only value").field(headerField).header(headerField.asString(), USER_1_FULL_ADDRESS).valueToMatch(USER_1_USERNAME))
                    .argument(argumentBuilder().description("personal header should match personal").field(headerField).header(headerField.asString(), USER_1_ADDRESS).valueToMatch(USER_1_ADDRESS))
                    .argument(argumentBuilder().description("address header should match address").field(headerField).header(headerField.asString(), USER_1_USERNAME).valueToMatch(USER_1_USERNAME))
                    .argument(argumentBuilder().description("multiple headers").field(headerField).header(headerField.asString(), USER_1_FULL_ADDRESS).header(headerField.asString(), USER_2_FULL_ADDRESS).valueToMatch(USER_1_USERNAME))
                    .argument(argumentBuilder().description("scrambled content").field(headerField).header(headerField.asString(), FRED_MARTIN_FULL_SCRAMBLED_ADDRESS).valueToMatch(FRED_MARTIN_FULLNAME))
                    .argument(argumentBuilder().description("folded content").field(headerField).header(headerField.asString(), USER_1_AND_UNFOLDED_USER_FULL_ADDRESS).valueToMatch(UNFOLDED_USERNAME))
                    .toStream()),

            argumentsProvider()
                .argument(argumentBuilder().description("full address to header").field(RECIPIENT).header("to", USER_3_FULL_ADDRESS).valueToMatch(USER_3_FULL_ADDRESS))
                .argument(argumentBuilder().description("address only to header").field(RECIPIENT).header("to", USER_3_FULL_ADDRESS).valueToMatch(USER_3_ADDRESS))
                .argument(argumentBuilder().description("personal only to header").field(RECIPIENT).header("to", USER_3_FULL_ADDRESS).valueToMatch(USER_3_USERNAME))
                .argument(argumentBuilder().description("scrambled content in to header").field(RECIPIENT).header("to", FRED_MARTIN_FULL_SCRAMBLED_ADDRESS).valueToMatch(FRED_MARTIN_FULLNAME))
                .argument(argumentBuilder().description("folded content in to header").field(RECIPIENT).header("to", USER_1_AND_UNFOLDED_USER_FULL_ADDRESS).valueToMatch(UNFOLDED_USERNAME))

                .argument(argumentBuilder().description("full address cc header").field(RECIPIENT).header("cc", USER_3_FULL_ADDRESS).valueToMatch(USER_3_FULL_ADDRESS))
                .argument(argumentBuilder().description("address only cc header").field(RECIPIENT).header("cc", USER_3_FULL_ADDRESS).valueToMatch(USER_3_ADDRESS))
                .argument(argumentBuilder().description("personal only cc header").field(RECIPIENT).header("cc", USER_3_FULL_ADDRESS).valueToMatch(USER_3_USERNAME))
                .argument(argumentBuilder().description("scrambled content in cc header").field(RECIPIENT).header("cc", FRED_MARTIN_FULL_SCRAMBLED_ADDRESS).valueToMatch(FRED_MARTIN_FULLNAME))
                .argument(argumentBuilder().description("folded content in cc header").field(RECIPIENT).header("cc", USER_1_AND_UNFOLDED_USER_FULL_ADDRESS).valueToMatch(UNFOLDED_USERNAME))

                .argument(argumentBuilder().description("multiple to and cc headers").field(RECIPIENT)
                        .ccRecipient(USER_1_FULL_ADDRESS)
                        .ccRecipient(USER_2_FULL_ADDRESS)
                        .toRecipient(USER_3_FULL_ADDRESS)
                        .toRecipient(USER_4_FULL_ADDRESS)
                        .valueToMatch(USER_4_FULL_ADDRESS))

                .argument(argumentBuilder().scrambledSubjectToMatch(UNSCRAMBLED_SUBJECT))
                .argument(argumentBuilder().unscrambledSubjectToMatch(UNSCRAMBLED_SUBJECT))

                .toStream()
        );
    }

    @ParameterizedTest(name = "mailDirectiveShouldBeSetWhenExactlyEqualsRuleValue when matching header field {1}, with {0}")
    @MethodSource("mailDirectiveShouldBeSetWhenExactlyEqualsRuleValueParamsProvider")
    void mailDirectiveShouldBeSetWhenExactlyEqualsRuleValue(
            String testDescription,
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

    static Stream<Arguments> mailDirectiveShouldBeSetWhenNotExactlyEqualsRuleValueParamsProvider() throws Exception {
        return Stream.concat(
            Stream.of(FROM, TO, CC)
                .flatMap(headerField -> argumentsProvider()
                    .argument(argumentBuilder().description("full address value should not match").field(headerField).header(headerField.asString(), USER_1_FULL_ADDRESS).valueToMatch(SHOULD_NOT_MATCH))
                    .argument(argumentBuilder().description("only address value should not match").field(headerField).header(headerField.asString(), USER_1_FULL_ADDRESS).valueToMatch(SHOULD_NOT_MATCH))
                    .argument(argumentBuilder().description("only personal value should not match").field(headerField).header(headerField.asString(), USER_1_FULL_ADDRESS).valueToMatch(SHOULD_NOT_MATCH))
                    .argument(argumentBuilder().description("personal value should not match address").field(headerField).header(headerField.asString(), USER_1_ADDRESS).valueToMatch(USER_1_USERNAME))
                    .argument(argumentBuilder().description("address value should not match personal").field(headerField).header(headerField.asString(), USER_1_USERNAME).valueToMatch(USER_1_ADDRESS))
                    .argument(argumentBuilder().description("multiple headers").field(headerField).header(headerField.asString(), USER_1_FULL_ADDRESS).header(headerField.asString(), USER_2_FULL_ADDRESS).valueToMatch(SHOULD_NOT_MATCH))
                    .argument(argumentBuilder().description("scrambled content").field(headerField).header(headerField.asString(), FRED_MARTIN_FULL_SCRAMBLED_ADDRESS).valueToMatch(SHOULD_NOT_MATCH))
                    .argument(argumentBuilder().description("folded content").field(headerField).header(headerField.asString(), USER_1_AND_UNFOLDED_USER_FULL_ADDRESS).valueToMatch(SHOULD_NOT_MATCH))
                    .argument(argumentBuilder().description("empty content").field(headerField).header(headerField.asString(), EMPTY).valueToMatch(USER_1_USERNAME))
                    .toStream()),

            argumentsProvider()
                .argument(argumentBuilder().description("full address to header").field(RECIPIENT).header("to", USER_3_FULL_ADDRESS).valueToMatch(SHOULD_NOT_MATCH))
                .argument(argumentBuilder().description("address only to header").field(RECIPIENT).header("to", USER_3_FULL_ADDRESS).valueToMatch(SHOULD_NOT_MATCH))
                .argument(argumentBuilder().description("personal only to header").field(RECIPIENT).header("to", USER_3_FULL_ADDRESS).valueToMatch(SHOULD_NOT_MATCH))
                .argument(argumentBuilder().description("scrambled content in to header").field(RECIPIENT).header("to", FRED_MARTIN_FULL_SCRAMBLED_ADDRESS).valueToMatch(SHOULD_NOT_MATCH))
                .argument(argumentBuilder().description("folded content in to header").field(RECIPIENT).header("to", USER_1_AND_UNFOLDED_USER_FULL_ADDRESS).valueToMatch(SHOULD_NOT_MATCH))

                .argument(argumentBuilder().description("full address cc header").field(RECIPIENT).header("cc", USER_3_FULL_ADDRESS).valueToMatch(SHOULD_NOT_MATCH))
                .argument(argumentBuilder().description("address only cc header").field(RECIPIENT).header("cc", USER_3_FULL_ADDRESS).valueToMatch(SHOULD_NOT_MATCH))
                .argument(argumentBuilder().description("personal only cc header").field(RECIPIENT).header("cc", USER_3_FULL_ADDRESS).valueToMatch(SHOULD_NOT_MATCH))
                .argument(argumentBuilder().description("scrambled content in cc header").field(RECIPIENT).header("cc", FRED_MARTIN_FULL_SCRAMBLED_ADDRESS).valueToMatch(SHOULD_NOT_MATCH))
                .argument(argumentBuilder().description("folded content in cc header").field(RECIPIENT).header("cc", USER_1_AND_UNFOLDED_USER_FULL_ADDRESS).valueToMatch(SHOULD_NOT_MATCH))

                .argument(argumentBuilder().description("multiple to and cc headers").field(RECIPIENT)
                        .ccRecipient(USER_1_FULL_ADDRESS)
                        .ccRecipient(USER_2_FULL_ADDRESS)
                        .toRecipient(USER_3_FULL_ADDRESS)
                        .toRecipient(USER_4_FULL_ADDRESS)
                        .valueToMatch(SHOULD_NOT_MATCH))

                .argument(argumentBuilder().scrambledSubjectToMatch(SHOULD_NOT_MATCH))
                .argument(argumentBuilder().unscrambledSubjectToMatch(SHOULD_NOT_MATCH))

                .toStream()
        );
    }

    @ParameterizedTest(name = "mailDirectiveShouldBeSetWhenNotExactlyEqualsRuleValue when matching header field {1}, with {0}")
    @MethodSource("mailDirectiveShouldBeSetWhenNotExactlyEqualsRuleValueParamsProvider")
    void mailDirectiveShouldBeSetWhenNotExactlyEqualsRuleValue(
            String testDescription,
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

    static Stream<Arguments> mailDirectiveShouldNotBeSetWhenAtLeastExactlyEqualsRuleValueParamsProvider() throws Exception {
        return argumentsProvider()
            .argument(argumentBuilder().description("from headers").field(FROM).from(USER_1_FULL_ADDRESS).from(USER_2_FULL_ADDRESS).valueToMatch(USER_1_USERNAME))
            .argument(argumentBuilder().description("to headers").field(TO).toRecipient(USER_1_FULL_ADDRESS).toRecipient(USER_2_FULL_ADDRESS).valueToMatch(USER_1_USERNAME))
            .argument(argumentBuilder().description("cc headers").field(CC).ccRecipient(USER_1_FULL_ADDRESS).ccRecipient(USER_2_FULL_ADDRESS).valueToMatch(USER_1_USERNAME))
            .argument(argumentBuilder().description("recipient headers").field(RECIPIENT).toRecipient(USER_1_FULL_ADDRESS).ccRecipient(USER_2_FULL_ADDRESS).valueToMatch(USER_1_USERNAME))
            .toStream();
    }

    @ParameterizedTest(name = "mailDirectiveShouldNotBeSetWhenAtLeastExactlyEqualsRuleValue when {0}")
    @MethodSource("mailDirectiveShouldNotBeSetWhenAtLeastExactlyEqualsRuleValueParamsProvider")
    void mailDirectiveShouldNotBeSetWhenAtLeastExactlyEqualsRuleValue(
            String testDescription,
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

    static Stream<Arguments> mailDirectiveShouldIgnoreBccHeadersParamsProvider() throws Exception {
        return argumentsProvider()
            .argument(argumentBuilder().comparator(CONTAINS).bccRecipient(USER_3_FULL_ADDRESS).valueToMatch(USER_3_FULL_ADDRESS))
            .argument(argumentBuilder().comparator(EXACTLY_EQUALS).bccRecipient(USER_3_FULL_ADDRESS).valueToMatch(USER_3_FULL_ADDRESS))
            .toStream();
    }

    @ParameterizedTest(name = "mailDirectiveShouldIgnoreBccHeaders with comparator {0}")
    @MethodSource("mailDirectiveShouldIgnoreBccHeadersParamsProvider")
    void mailDirectiveShouldIgnoreBccHeaders(
            Rule.Condition.Comparator comparator,
            MimeMessageBuilder mimeMessageBuilder,
            String valueToMatch,
            JMAPFilteringTestSystem testSystem) throws Exception {

        testSystem.defineRulesForRecipient1(Rule.Condition.of(RECIPIENT, comparator, valueToMatch));
        FakeMail mail = testSystem.asMail(mimeMessageBuilder);
        testSystem.getJmapFiltering().service(mail);

        assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                .isNull();
    }


    static Stream<Arguments> mailDirectiveShouldNotBeSetWhenHeaderContentIsNullParamsProvider() throws Exception {
        return Stream.of(Rule.Condition.Field.values())
            .flatMap(field -> Stream.of(CONTAINS, EXACTLY_EQUALS).map(
                    comparator -> argumentBuilder().field(field).comparator(comparator).valueToMatch(USER_1_USERNAME)
                            .build()));
    }

    @ParameterizedTest(name = "mailDirectiveShouldNotBeSetWhenHeaderContentIsNull when matching header field {1}, with {0}")
    @MethodSource("mailDirectiveShouldNotBeSetWhenHeaderContentIsNullParamsProvider")
    void mailDirectiveShouldNotBeSetWhenHeaderContentIsNull(
            Rule.Condition.Field fieldToMatch,
            Rule.Condition.Comparator comparator,
            String valueToMatch,
            JMAPFilteringTestSystem testSystem) throws Exception {
        testSystem.defineRulesForRecipient1(Rule.Condition.of(fieldToMatch, comparator, valueToMatch));
        FakeMail mail = testSystem.asMail(mimeMessageBuilder());
        testSystem.getJmapFiltering().service(mail);

        assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                .isNull();
    }

    static Stream<Arguments> mailDirectiveShouldBeSetWhenHeaderContentIsNullParamsProvider() throws Exception {
        return Stream.of(Rule.Condition.Field.values())
            .flatMap(field -> Stream.of(NOT_CONTAINS, NOT_EXACTLY_EQUALS).map(
                    comparator -> argumentBuilder().field(field).comparator(comparator).valueToMatch(USER_1_USERNAME)
                            .build()));
    }

    @ParameterizedTest(name = "mailDirectiveShouldBeSetWhenHeaderContentIsNull when matching header field {1}, with {0}")
    @MethodSource("mailDirectiveShouldBeSetWhenHeaderContentIsNullParamsProvider")
    void mailDirectiveShouldBeSetWhenHeaderContentIsNull(
            Rule.Condition.Field fieldToMatch,
            Rule.Condition.Comparator comparator,
            String valueToMatch,
            JMAPFilteringTestSystem testSystem) throws Exception {

        testSystem.defineRulesForRecipient1(Rule.Condition.of(fieldToMatch, comparator, valueToMatch));

        FakeMail mail = testSystem.asMail(mimeMessageBuilder());

        testSystem.getJmapFiltering().service(mail);

        assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                .isEqualTo(RECIPIENT_1_MAILBOX_1);
    }

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
}