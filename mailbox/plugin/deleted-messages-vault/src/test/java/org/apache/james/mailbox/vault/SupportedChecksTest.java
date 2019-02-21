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

package org.apache.james.mailbox.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.mailbox.vault.GenericComponents.FieldName;
import org.apache.james.mailbox.vault.GenericComponents.OperatorSign;
import org.apache.james.mailbox.vault.Query.QueryElement;
import org.apache.james.mailbox.vault.Query.QueryElementValue;
import org.apache.james.mailbox.vault.SupportedChecks.ComparedValue;
import org.apache.james.mailbox.vault.SupportedChecks.ComparedValues;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class SupportedChecksTest {

    @Test
    void queryShouldMatchWhenContainsMatchedFieldNameElements() {
        Query query = Query.of(ImmutableList.of(
            QueryElement.from(new QueryElementValue("hello", FieldName.withName("subject")), OperatorSign.CONTAINS),
            QueryElement.from(new QueryElementValue("hi", FieldName.withName("content")), OperatorSign.CONTAINS)
        ));

        ComparedValues comparedValue = () -> ImmutableList.of(new ComparedValue("hello this is the subject", FieldName.withName("subject")));

        assertThat(SupportedChecks.matches(query, comparedValue))
            .isTrue();
    }

    @Test
    void queryShouldMatchWhenNotContainsMatchedFieldNameElements() { // because there is no field has name `subject with a tail` or `content`
        Query query = Query.of(ImmutableList.of(
            QueryElement.from(new QueryElementValue("hello", FieldName.withName("subject with a tail")), OperatorSign.CONTAINS),
            QueryElement.from(new QueryElementValue("hi", FieldName.withName("content")), OperatorSign.CONTAINS)
        ));

        ComparedValues comparedValue = () -> ImmutableList.of(new ComparedValue("hello this is the subject", FieldName.withName("subject")));

        assertThat(SupportedChecks.matches(query, comparedValue))
            .isTrue();
    }

    @Test
    void queryShouldNotMatchWhenContainsMatchedFieldNameElementsButDoesntMatchFieldValue() {
        Query query = Query.of(ImmutableList.of(
            QueryElement.from(new QueryElementValue("hello", FieldName.withName("subject")), OperatorSign.CONTAINS),
            QueryElement.from(new QueryElementValue("hi", FieldName.withName("content")), OperatorSign.CONTAINS)
        ));

        ComparedValues comparedValue = () -> ImmutableList.of(new ComparedValue("hi this is the subject", FieldName.withName("subject")));

        assertThat(SupportedChecks.matches(query, comparedValue))
            .isFalse();
    }

    @Test
    void queryShouldMatchWhenAllComparedValuesAreMatched() {
        Query query = Query.of(ImmutableList.of(
            QueryElement.from(new QueryElementValue("hello", FieldName.withName("subject")), OperatorSign.CONTAINS),
            QueryElement.from(new QueryElementValue("hi", FieldName.withName("content")), OperatorSign.CONTAINS)
        ));

        ComparedValues comparedValue = () -> ImmutableList.of(
            new ComparedValue("hello this is the subject", FieldName.withName("subject")),
            new ComparedValue("hi this is the subject", FieldName.withName("content")));

        assertThat(SupportedChecks.matches(query, comparedValue))
            .isTrue();
    }

    @Test
    void queryShouldNotMatchWhenNotAllComparedValuesAreMatched() {
        Query query = Query.of(ImmutableList.of(
            QueryElement.from(new QueryElementValue("hello", FieldName.withName("subject")), OperatorSign.CONTAINS),
            QueryElement.from(new QueryElementValue("hi", FieldName.withName("content")), OperatorSign.CONTAINS)
        ));

        ComparedValues comparedValue = () -> ImmutableList.of(
            new ComparedValue("hello subject", FieldName.withName("subject")),
            new ComparedValue("hello", FieldName.withName("content")));

        assertThat(SupportedChecks.matches(query, comparedValue))
            .isFalse();
    }

    @Test
    void queryShouldNotMatchWithDifferentValueTypeWhenNotAllComparedValuesAreMatched() {
        Query query = Query.of(ImmutableList.of(
            QueryElement.from(new QueryElementValue("hello", FieldName.withName("subject")), OperatorSign.CONTAINS),
            QueryElement.from(new QueryElementValue(new Integer(5), FieldName.withName("numberOfAttachments")), OperatorSign.GREATER_OR_EQUAL)
        ));

        ComparedValues comparedValue = () -> ImmutableList.of(
            new ComparedValue("hello subject", FieldName.withName("subject")),
            new ComparedValue(new Integer(4), FieldName.withName("numberOfAttachments")));

        assertThat(SupportedChecks.matches(query, comparedValue))
            .isFalse();
    }

    @Test
    void queryShouldMatchWithDifferentValueType() {
        Query query = Query.of(ImmutableList.of(
            QueryElement.from(new QueryElementValue("hello", FieldName.withName("subject")), OperatorSign.CONTAINS),
            QueryElement.from(new QueryElementValue(new Integer(5), FieldName.withName("numberOfAttachments")), OperatorSign.GREATER_OR_EQUAL)
        ));

        ComparedValues comparedValue = () -> ImmutableList.of(
            new ComparedValue("hello subject", FieldName.withName("subject")),
            new ComparedValue(new Integer(10), FieldName.withName("numberOfAttachments")));

        assertThat(SupportedChecks.matches(query, comparedValue))
            .isTrue();
    }

    @Test
    void queryShouldThrowWithUnSupportedTypes() {
        Query query = Query.of(ImmutableList.of(
            QueryElement.from(new QueryElementValue("hello", FieldName.withName("subject")), OperatorSign.CONTAINS),
            QueryElement.from(new QueryElementValue(new Integer(5), FieldName.withName("numberOfAttachments")), OperatorSign.GREATER_OR_EQUAL)
        ));

        ComparedValues comparedValue = () -> ImmutableList.of(
            new ComparedValue("hello subject", FieldName.withName("subject")),
            new ComparedValue(new Long(10), FieldName.withName("numberOfAttachments"))); // Long type is not registered

        assertThatThrownBy(() -> SupportedChecks.matches(query, comparedValue))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("java.lang.Long is not supported");
    }
}