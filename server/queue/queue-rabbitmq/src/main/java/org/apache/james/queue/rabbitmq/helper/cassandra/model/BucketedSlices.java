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

package org.apache.james.queue.rabbitmq.helper.cassandra.model;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.apache.james.util.StreamUtils;

import com.google.common.base.Preconditions;

public class BucketedSlices {
    // todo review and test
    // strong typing for slice and buckets
    // cascading lambdas

    public static class Builder {
        private Instant startAt;
        private Optional<Instant> endAt;
        private long sliceWindowSideInSecond;
        private int bucketCount;

        public Builder() {
            endAt = Optional.empty();
        }

        public BucketedSlices.Builder startAt(Instant startSlice) {
            this.startAt = startSlice;
            return this;
        }

        public BucketedSlices.Builder endAtOptional(Optional<Instant> endAt) {
            Preconditions.checkNotNull(endAt);

            this.endAt = endAt;
            return this;
        }

        public BucketedSlices.Builder endAt(Instant endAt) {
            this.endAt = Optional.ofNullable(endAt);
            return this;
        }

        public BucketedSlices.Builder sliceWindowSideInSecond(long sliceWindowSideInSecond) {
            this.sliceWindowSideInSecond = sliceWindowSideInSecond;
            return this;
        }

        public BucketedSlices.Builder bucketCount(int bucketCount) {
            this.bucketCount = bucketCount;
            return this;
        }

        public BucketedSlices build() {
            return new BucketedSlices(bucketAndSliceStream());
        }

        private Stream<BucketedSlices.BucketAndSlice> bucketAndSliceStream() {
            Stream<Instant> allInterestingSlices = allInterestingSlices();
            Stream<Integer> allBuckets = IntStream.range(0, bucketCount).boxed();

            return StreamUtils
                .cartesianProduct(allBuckets, allInterestingSlices, BucketedSlices.BucketAndSlice::of);
        }

        private Stream<Instant> allInterestingSlices() {
            Instant firstSliceStart = sliceStart(startAt, sliceWindowSideInSecond);
            long sliceCount = calculateSliceCount();

            return LongStream.range(0, sliceCount)
                .map(slicePosition -> firstSliceStart.getEpochSecond() + sliceWindowSideInSecond * slicePosition)
                .mapToObj(Instant::ofEpochSecond);
        }

        private long calculateSliceCount() {
            return endAt
                .map(this::calculateSliceCount)
                .orElse(1L);
        }

        private long calculateSliceCount(Instant lastSliceStart) {
            Instant firstSliceStart = sliceStart(startAt, sliceWindowSideInSecond);
            return (lastSliceStart.getEpochSecond() - firstSliceStart.getEpochSecond()) / sliceWindowSideInSecond;
        }

        private Instant sliceStart(Instant timeRangeStartFrom, long sliceWindowSideInSecond) {
            long sliceCount = timeRangeStartFrom.getEpochSecond() / sliceWindowSideInSecond;
            long sliceStartTime = sliceCount * sliceWindowSideInSecond;
            return Instant.ofEpochSecond(sliceStartTime);
        }
    }

    public static class BucketAndSlice {
        static BucketedSlices.BucketAndSlice of(Integer bucketId, Instant sliceTime) {
            return new BucketedSlices.BucketAndSlice(bucketId, sliceTime);
        }

        private final int bucketId;
        private final Instant sliceStartInstant;

        private BucketAndSlice(Integer bucketId, Instant sliceStartInstant) {
            Preconditions.checkNotNull(bucketId);
            Preconditions.checkNotNull(sliceStartInstant);

            this.bucketId = bucketId;
            this.sliceStartInstant = sliceStartInstant;
        }

        public int getBucketId() {
            return bucketId;
        }

        public Instant getSliceStartInstant() {
            return sliceStartInstant;
        }
    }

    public static BucketedSlices.Builder builder() {
        return new BucketedSlices.Builder();
    }

    private final Stream<BucketedSlices.BucketAndSlice> interestingBucketSlices;

    BucketedSlices(Stream<BucketedSlices.BucketAndSlice> interestingBucketSlices) {
        this.interestingBucketSlices = interestingBucketSlices;
    }

    public Stream<BucketedSlices.BucketAndSlice> getBucketSlices() {
        return interestingBucketSlices;
    }
}
