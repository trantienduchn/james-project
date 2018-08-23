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

import java.util.List;

import org.apache.james.core.MailAddress;
import org.apache.james.core.User;
import org.apache.james.jmap.api.filtering.FilteringManagement;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.mailet.Mail;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

class FilterRules {

    static class FilterRulesMatcher {

        private Rule rule;
        private FieldContentExtractor fieldContentExtractor;
        private FieldContentMatcher fieldContentMatcher;

        private FilterRulesMatcher(Rule rule) {
            this.rule = rule;
            initProperties(rule);
        }

        private void initProperties(Rule rule) {
            Rule.Condition ruleCondition = rule.getCondition();

            String fieldName = ruleCondition.getField().asString();
            this.fieldContentExtractor = FieldContentExtractorProvider.getFieldContentExtractor(fieldName);

            String comparatorString = ruleCondition.getComparator().asString();
            this.fieldContentMatcher = FieldContentMatcherProvider.getComparator(comparatorString);
        }

        public boolean match(Mail mail) {
            return fieldContentExtractor
                .getContent(mail)
                .anyMatch(fieldContent -> fieldContentMatcher.match(fieldContent, rule.getCondition().getValue()));
        }

        public Rule getRule() {
            return rule;
        }
    }

    private final FilteringManagement filteringManagement;
    private final MailAddress recipient;

    FilterRules(MailAddress recipient, FilteringManagement filteringManagement) {
        Preconditions.checkNotNull(filteringManagement);
        Preconditions.checkNotNull(recipient);

        this.recipient = recipient;
        this.filteringManagement = filteringManagement;
    }

    List<String> matchedMailboxIds(Mail mail) {
        List<Rule> filterRules = filteringManagement
                .listRulesForUser(User.fromMailAddress(recipient));

        return filterRules.stream()
            .map(FilterRulesMatcher::new)
            .filter(rulesMatcher -> rulesMatcher.match(mail))
            .flatMap(rulesMatcher -> rulesMatcher.getRule().getAction().getMailboxIds().stream())
            .collect(ImmutableList.toImmutableList());
    }

    MailAddress getRecipient() {
        return recipient;
    }
}
