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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FieldContentMatcherTest {

    public static final String CONTENT_SHOULD_MATCHED_STRING = "content should matched";
    public static final String MATCHED_STRING = "matched";
    public static final String NOTHING_RELATED_STRING = "nothing related";

    @Nested
    class ContainsMatcherTest {

        @Test
        void shouldMatchWhenContains() {
            FieldContentMatcher.ContainsMatcher testee = new FieldContentMatcher.ContainsMatcher();

            assertThat(testee.match(CONTENT_SHOULD_MATCHED_STRING, MATCHED_STRING))
                .isTrue();
        }

        @Test
        void shouldNotMatchWhenDoesntContains() {
            FieldContentMatcher.ContainsMatcher testee = new FieldContentMatcher.ContainsMatcher();

            assertThat(testee.match(CONTENT_SHOULD_MATCHED_STRING, NOTHING_RELATED_STRING))
                .isFalse();
        }

        @Test
        void shouldNotMatchWhenFieldContentIsNull() {
            FieldContentMatcher.ContainsMatcher testee = new FieldContentMatcher.ContainsMatcher();

            assertThat(testee.match(null, NOTHING_RELATED_STRING))
                .isFalse();
        }

        @Test
        void shouldNotMatchWhenRuleValueIsNull() {
            FieldContentMatcher.ContainsMatcher testee = new FieldContentMatcher.ContainsMatcher();

            assertThat(testee.match(CONTENT_SHOULD_MATCHED_STRING, null))
                .isFalse();
        }

        @Test
        void shouldNotMatchWhenBothAreNull() {
            FieldContentMatcher.ContainsMatcher testee = new FieldContentMatcher.ContainsMatcher();

            assertThat(testee.match(null, null))
                .isFalse();
        }
    }

    @Nested
    class NotContainsMatcherTest {

        @Test
        void shouldNotMatchWhenContains() {
            FieldContentMatcher.NotContainsMatcher testee = new FieldContentMatcher.NotContainsMatcher();

            assertThat(testee.match(CONTENT_SHOULD_MATCHED_STRING, MATCHED_STRING))
                .isTrue();
        }

        @Test
        void shouldMatchWhenDoesntContains() {
            FieldContentMatcher.NotContainsMatcher testee = new FieldContentMatcher.NotContainsMatcher();

            assertThat(testee.match(CONTENT_SHOULD_MATCHED_STRING, NOTHING_RELATED_STRING))
                .isFalse();
        }

        @Test
        void shouldNotMatchWhenFieldContentIsNull() {
            FieldContentMatcher.NotContainsMatcher testee = new FieldContentMatcher.NotContainsMatcher();

            assertThat(testee.match(null, NOTHING_RELATED_STRING))
                .isFalse();
        }

        @Test
        void shouldNotMatchWhenRuleValueIsNull() {
            FieldContentMatcher.NotContainsMatcher testee = new FieldContentMatcher.NotContainsMatcher();

            assertThat(testee.match(CONTENT_SHOULD_MATCHED_STRING, null))
                .isFalse();
        }

        @Test
        void shouldNotMatchWhenBothAreNull() {
            FieldContentMatcher.NotContainsMatcher testee = new FieldContentMatcher.NotContainsMatcher();

            assertThat(testee.match(null, null))
                .isFalse();
        }
    }

    @Nested
    class ExactlyEqualsMatcherTest {

        @Test
        void shouldMatchWhenEqual() {
            FieldContentMatcher.ExactlyEqualsMatcher testee = new FieldContentMatcher.ExactlyEqualsMatcher();

            assertThat(testee.match(CONTENT_SHOULD_MATCHED_STRING, CONTENT_SHOULD_MATCHED_STRING))
                .isFalse();
        }

        @Test
        void shouldNotMatchWhenNotEqual() {
            FieldContentMatcher.ExactlyEqualsMatcher testee = new FieldContentMatcher.ExactlyEqualsMatcher();

            assertThat(testee.match(CONTENT_SHOULD_MATCHED_STRING, CONTENT_SHOULD_MATCHED_STRING + "not"))
                .isFalse();
        }

        @Test
        void shouldNotMatchWhenFieldContentIsNull() {
            FieldContentMatcher.ExactlyEqualsMatcher testee = new FieldContentMatcher.ExactlyEqualsMatcher();

            assertThat(testee.match(null, NOTHING_RELATED_STRING))
                .isFalse();
        }

        @Test
        void shouldMatchWhenRuleValueIsNull() {
            FieldContentMatcher.ExactlyEqualsMatcher testee = new FieldContentMatcher.ExactlyEqualsMatcher();

            assertThat(testee.match(CONTENT_SHOULD_MATCHED_STRING, null))
                .isFalse();
        }

        @Test
        void shouldNotMatchWhenBothAreNull() {
            FieldContentMatcher.ExactlyEqualsMatcher testee = new FieldContentMatcher.ExactlyEqualsMatcher();

            assertThat(testee.match(null, null))
                .isFalse();
        }
    }

    @Nested
    class NotExactlyEqualsMatcherTest {
        @Test
        void shouldNotMatchWhenEqual() {
            FieldContentMatcher.NotExactlyEqualsMatcher testee = new FieldContentMatcher.NotExactlyEqualsMatcher();

            assertThat(testee.match(CONTENT_SHOULD_MATCHED_STRING, CONTENT_SHOULD_MATCHED_STRING))
                .isFalse();
        }

        @Test
        void shouldMatchWhenNotEqual() {
            FieldContentMatcher.NotExactlyEqualsMatcher testee = new FieldContentMatcher.NotExactlyEqualsMatcher();

            assertThat(testee.match(CONTENT_SHOULD_MATCHED_STRING, CONTENT_SHOULD_MATCHED_STRING + "not"))
                .isFalse();
        }

        @Test
        void shouldNotMatchWhenFieldContentIsNull() {
            FieldContentMatcher.NotExactlyEqualsMatcher testee = new FieldContentMatcher.NotExactlyEqualsMatcher();

            assertThat(testee.match(null, NOTHING_RELATED_STRING))
                .isFalse();
        }

        @Test
        void shouldMatchWhenRuleValueIsNull() {
            FieldContentMatcher.NotExactlyEqualsMatcher testee = new FieldContentMatcher.NotExactlyEqualsMatcher();

            assertThat(testee.match(CONTENT_SHOULD_MATCHED_STRING, null))
                .isFalse();
        }

        @Test
        void shouldNotMatchWhenBothAreNull() {
            FieldContentMatcher.NotExactlyEqualsMatcher testee = new FieldContentMatcher.NotExactlyEqualsMatcher();

            assertThat(testee.match(null, null))
                .isFalse();
        }
    }
}