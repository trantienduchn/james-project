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

import static org.apache.james.jmap.api.filtering.Rule.Condition;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.mail.Address;
import javax.mail.Message;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.javax.AddressHelper;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.jmap.api.filtering.Rule.Condition.Field;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.functions.ThrowingFunction;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

public interface MailMatcher {

    interface HeaderExtractor extends ThrowingFunction<Mail, Stream<String>> {}

    class HeaderMatcher implements MailMatcher {

        private final Logger logger = LoggerFactory.getLogger(HeaderMatcher.class);

        private final ContentMatcher contentMatcher;
        private final String ruleValue;
        private final HeaderExtractor headerExtractor;

        HeaderMatcher(Optional<ContentMatcher> contentMatcherOptional, String ruleValue,
                      Optional<HeaderExtractor> headerExtractorOptional) {
            Preconditions.checkArgument(contentMatcherOptional.isPresent());
            Preconditions.checkArgument(headerExtractorOptional.isPresent());

            this.contentMatcher = contentMatcherOptional.get();
            this.ruleValue = ruleValue;
            this.headerExtractor = headerExtractorOptional.get();
        }

        @Override
        public boolean match(Mail mail) {
            try {
                return headerExtractor
                        .apply(mail)
                        .anyMatch(headerLine -> contentMatcher.match(headerLine, ruleValue));
            } catch (Exception e) {
                logger.error("error while extracting mail header", e);
                return false;
            }
        }
    }

    interface ContentMatcher {
        ContentMatcher CONTAINS_MATCHER = StringUtils::contains;
        ContentMatcher NOT_CONTAINS_MATCHER = negate(StringUtils::contains);
        ContentMatcher EXACTLY_EQUALS_MATCHER = StringUtils::equals;
        ContentMatcher NOT_EXACTLY_EQUALS_MATCHER = negate(StringUtils::equals);

        Map<Rule.Condition.Comparator, ContentMatcher> CONTENT_MATCHER_REGISTRY = ImmutableMap.<Rule.Condition.Comparator, ContentMatcher>builder()
                .put(Condition.Comparator.CONTAINS, CONTAINS_MATCHER)
                .put(Condition.Comparator.NOT_CONTAINS, NOT_CONTAINS_MATCHER)
                .put(Condition.Comparator.EXACTLY_EQUALS, EXACTLY_EQUALS_MATCHER)
                .put(Condition.Comparator.NOT_EXACTLY_EQUALS, NOT_EXACTLY_EQUALS_MATCHER)
                .build();

        static ContentMatcher negate(ContentMatcher contentMatcher) {
            return (String fullContent, String matchingValue) ->
                    !contentMatcher.match(fullContent, matchingValue);
        }

        static Optional<ContentMatcher> asContentMatcher(Condition.Comparator comparator) {
            return Optional
                .ofNullable(CONTENT_MATCHER_REGISTRY.get(comparator));
        }

        boolean match(String fullContent, String matchingValue);
    }

    HeaderExtractor SUBJECT_EXTRACTOR = mail ->
            Optional.ofNullable(mail.getMessage().getSubject())
                    .map(Stream::of)
                    .orElse(Stream.empty());
    HeaderExtractor RECIPIENT_EXTRACTOR =  mail -> addressExtractor(mail.getMessage().getAllRecipients());
    HeaderExtractor FROM_EXTRACTOR = mail -> addressExtractor(mail.getMessage().getFrom());
    HeaderExtractor CC_EXTRACTOR = recipientExtractor(Message.RecipientType.CC);
    HeaderExtractor TO_EXTRACTOR = recipientExtractor(Message.RecipientType.TO);

    Map<Field, HeaderExtractor> HEADER_EXTRACTOR_REGISTRY = ImmutableMap.<Field, HeaderExtractor>builder()
            .put(Field.SUBJECT, SUBJECT_EXTRACTOR)
            .put(Field.RECIPIENT, RECIPIENT_EXTRACTOR)
            .put(Field.FROM, FROM_EXTRACTOR)
            .put(Field.CC, CC_EXTRACTOR)
            .put(Field.TO, TO_EXTRACTOR)
            .build();

    static MailMatcher from(Rule rule) {
        Optional<ContentMatcher> contentMatcherOptional = ContentMatcher.asContentMatcher(rule.getCondition().getComparator());
        Optional<HeaderExtractor> headerExtractorOptional = getHeaderExtractor(rule.getCondition().getField());

        return new HeaderMatcher(contentMatcherOptional, rule.getCondition().getValue(), headerExtractorOptional);
    }

    static HeaderExtractor recipientExtractor(Message.RecipientType type) {
        return mail -> addressExtractor(mail.getMessage().getRecipients(type));
    }

    static Stream<String> addressExtractor(Address[] addresses) {
        return Optional.ofNullable(addresses)
                .map(AddressHelper::asStringStream)
                .orElse(Stream.empty());
    }

    static Optional<HeaderExtractor> getHeaderExtractor(Field field) {
        return Optional
            .ofNullable(HEADER_EXTRACTOR_REGISTRY.get(field));
    }

    boolean match(Mail mail);
}
