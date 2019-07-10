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

package org.apache.james.vault.blob;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.apache.james.blob.api.BucketName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BucketNameGeneratorTest {
    private static final Instant NOW = Instant.parse("2007-12-03T10:15:30.00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("UTC"));

    private static final BucketNameGenerator DEFAULT_GENERATOR = new BucketNameGenerator(CLOCK);

    @Test
    void currentBucketShouldReturnBucketFormattedOnFirstDayOfMonth() {
        assertThat(new BucketNameGenerator(CLOCK).currentBucket())
            .isEqualTo(BucketName.of("deleted-messages-2007-12-01"));
    }

    @Nested
    class IsBucketRangeBefore {

        @ParameterizedTest
        @ValueSource(strings = {
            "deleted-Messages-2018-07-01",
            "deleted-Messages-2018-07-02",
            "deletedMessages-2018-07-01",
            "deletedMessages-2018-007-01",
            "deletedMessages-2018-07-001",
            "2018-07-01",
            "2018-01-07",
            "01-07-2018",
        })
        void shouldBeFalseWhenPassingNonWellFormattedBucketNames(String bucketNameAsString) {
            BucketName bucketName = BucketName.of(bucketNameAsString);
            ZonedDateTime beforeThisTime = ZonedDateTime.parse("2019-01-09T10:15:30.00Z");

            assertThat(DEFAULT_GENERATOR.isBucketRangeBefore(bucketName, beforeThisTime))
                .isFalse();
        }

        @Test
        void shouldBeFalseWhenBucketNameIsInSameMonthWithPassedTime() {
            BucketName bucketName = BucketName.of("deleted-messages-2019-07-01");
            ZonedDateTime beforeThisTime = ZonedDateTime.parse("2019-07-09T10:15:30.00Z");

            assertThat(DEFAULT_GENERATOR.isBucketRangeBefore(bucketName, beforeThisTime))
                .isFalse();
        }

        @Test
        void shouldBeFalseWhenBucketNameIsInSameDayWithPassedTime() {
            BucketName bucketName = BucketName.of("deleted-messages-2019-07-01");
            ZonedDateTime beforeThisTime = ZonedDateTime.parse("2019-07-01T10:15:30.00Z");

            assertThat(DEFAULT_GENERATOR.isBucketRangeBefore(bucketName, beforeThisTime))
                .isFalse();
        }

        @Test
        void shouldBeFalseWhenBucketNameIsInSameTimeWithPassedTime() {
            BucketName bucketName = BucketName.of("deleted-messages-2019-07-01");
            ZonedDateTime beforeThisTime = ZonedDateTime.parse("2019-07-01T00:00:00.00Z");

            assertThat(DEFAULT_GENERATOR.isBucketRangeBefore(bucketName, beforeThisTime))
                .isFalse();
        }

        @Test
        void shouldBeTrueWhenBucketNameIsInPreviousMonthThanPassedTime() {
            BucketName bucketName = BucketName.of("deleted-messages-2019-06-01");
            ZonedDateTime beforeThisTime = ZonedDateTime.parse("2019-07-09T10:15:30.00Z");

            assertThat(DEFAULT_GENERATOR.isBucketRangeBefore(bucketName, beforeThisTime))
                .isTrue();
        }

        @Test
        void shouldBeTrueWhenBucketNameIsInLongTimeBeforePassedTime() {
            BucketName bucketName = BucketName.of("deleted-messages-2000-06-01");
            ZonedDateTime beforeThisTime = ZonedDateTime.parse("2019-07-09T10:15:30.00Z");

            assertThat(DEFAULT_GENERATOR.isBucketRangeBefore(bucketName, beforeThisTime))
                .isTrue();
        }

        @Test
        void shouldBeFalseWhenBucketNameIsInNearFuture() {
            BucketName bucketName = BucketName.of("deleted-messages-2019-08-01");
            ZonedDateTime beforeThisTime = ZonedDateTime.parse("2019-07-09T10:15:30.00Z");

            assertThat(DEFAULT_GENERATOR.isBucketRangeBefore(bucketName, beforeThisTime))
                .isFalse();
        }

        @Test
        void shouldBeFalseWhenBucketNameIsInFarFuture() {
            BucketName bucketName = BucketName.of("deleted-messages-2222-08-01");
            ZonedDateTime beforeThisTime = ZonedDateTime.parse("2019-07-09T10:15:30.00Z");

            assertThat(DEFAULT_GENERATOR.isBucketRangeBefore(bucketName, beforeThisTime))
                .isFalse();
        }
    }
}