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

package org.apache.james.queue.rabbitmq.view.cassandra.configuration;

import java.time.Duration;
import java.util.Objects;

import org.apache.commons.configuration.Configuration;
import org.apache.james.util.TimeConverter;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class CassandraMailQueueViewConfiguration {
    interface Builder {
        @FunctionalInterface
        interface RequireBucketCount {
            RequireUpdateBrowseStartPace bucketCount(int bucketCount);
        }

        @FunctionalInterface
        interface RequireUpdateBrowseStartPace {
            RequireSliceWindow updateBrowseStartPace(int updateBrowseStartPace);
        }

        @FunctionalInterface
        interface RequireSliceWindow {
            ReadyToBuild sliceWindow(Duration sliceWindow);
        }

        class ReadyToBuild {
            private final int bucketCount;
            private final int updateBrowseStartPace;
            private final Duration sliceWindow;

            private ReadyToBuild(int bucketCount, int updateBrowseStartPace, Duration sliceWindow) {
                this.bucketCount = bucketCount;
                this.updateBrowseStartPace = updateBrowseStartPace;
                this.sliceWindow = sliceWindow;
            }

            public CassandraMailQueueViewConfiguration build() {
                Preconditions.checkNotNull(sliceWindow, "'sliceWindow' is compulsory");
                Preconditions.checkState(!(sliceWindow.isZero() || sliceWindow.isNegative()), "'sliceWindow' need to be strictly positive");
                Preconditions.checkState(bucketCount > 0, "'bucketCount' needs to be a strictly positive integer");
                Preconditions.checkState(updateBrowseStartPace > 0, "'updateBrowseStartPace' needs to be a strictly positive integer");

                return new CassandraMailQueueViewConfiguration(bucketCount, updateBrowseStartPace, sliceWindow);
            }
        }
    }

    public static Builder.RequireBucketCount builder() {
        return bucketCount -> updateBrowseStartPace -> sliceWindow -> new Builder.ReadyToBuild(bucketCount, updateBrowseStartPace, sliceWindow);
    }

    public static CassandraMailQueueViewConfiguration from(Configuration configuration) {
        Integer bucketCount = configuration.getInteger(BUCKET_COUNT_PROPERTY, null);
        Preconditions.checkState(bucketCount != null, "You need to specify the " +
            BUCKET_COUNT_PROPERTY + " property. The higher the bucket count is, the better the data will be spread in the cluster." +
            " You might want the bucket count to exceed your Cassandra node count.");

        Integer updateBrowseStartPace = configuration.getInteger(UPDATE_BROWSE_START_PACE_PROPERTY, null);
        Preconditions.checkState(updateBrowseStartPace != null, "You need to specify the " +
            UPDATE_BROWSE_START_PACE_PROPERTY + " property. The mailQueue will use this number to randomly update the browsing start point of the mail queue." +
            " The lower that number is, the more the start point will be up to date (and thus browse quick) but the more unnecessary work will be performed upon dequeues/deletes.");

        String sliceWindowString = configuration.getString(SLICE_WINDOW_PROPERTY);
        Preconditions.checkState(sliceWindowString != null, "You need to specify the " +
            SLICE_WINDOW_PROPERTY + " property as the seconds long of each enqueued window");

        return builder()
            .bucketCount(bucketCount)
            .updateBrowseStartPace(updateBrowseStartPace)
            .sliceWindow(Duration.ofMillis(TimeConverter.getMilliSeconds(sliceWindowString)))
            .build();
    }

    static final String BUCKET_COUNT_PROPERTY = "mailqueueview.cassandra.bucketCount";
    static final String UPDATE_BROWSE_START_PACE_PROPERTY = "mailqueueview.cassandra.updateBrowseStartPace";
    static final String SLICE_WINDOW_PROPERTY = "mailqueueview.cassandra.sliceWindow";

    private final int bucketCount;
    private final int updateBrowseStartPace;
    private final Duration sliceWindow;

    private CassandraMailQueueViewConfiguration(int bucketCount,
                                                int updateBrowseStartPace,
                                                Duration sliceWindow) {
        this.bucketCount = bucketCount;
        this.updateBrowseStartPace = updateBrowseStartPace;
        this.sliceWindow = sliceWindow;
    }

    public int getUpdateBrowseStartPace() {
        return updateBrowseStartPace;
    }

    public int getBucketCount() {
        return bucketCount;
    }

    public Duration getSliceWindow() {
        return sliceWindow;
    }

    void validateConfigurationChange(CassandraMailQueueViewConfiguration configurationUpdate) {
        validateConfigurationChangeForSlice(configurationUpdate);
        validateConfigurationChangeForBuckets(configurationUpdate);
    }

    private void validateConfigurationChangeForSlice(CassandraMailQueueViewConfiguration configurationUpdate) {
        long updateSliceWindowInSecond = configurationUpdate.getSliceWindow().getSeconds();
        long currentSliceWindowInSecond = getSliceWindow().getSeconds();
        Preconditions.checkArgument(
            configurationUpdate.getSliceWindow().compareTo(getSliceWindow()) <= 0
                && currentSliceWindowInSecond % updateSliceWindowInSecond == 0,
            "update 'sliceWindow'(" + configurationUpdate.getSliceWindow() + ") have to be less than and divide the previous sliceWindow: "
                + getSliceWindow());
    }

    private void validateConfigurationChangeForBuckets(CassandraMailQueueViewConfiguration configurationUpdate) {
        Preconditions.checkArgument(configurationUpdate.getBucketCount() >= getBucketCount(),
            "can not set 'bucketCount'(" + configurationUpdate.getBucketCount() + ") to be less than the current one: " + getBucketCount());
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof CassandraMailQueueViewConfiguration) {
            CassandraMailQueueViewConfiguration that = (CassandraMailQueueViewConfiguration) o;

            return Objects.equals(this.bucketCount, that.bucketCount)
                && Objects.equals(this.updateBrowseStartPace, that.updateBrowseStartPace)
                && Objects.equals(this.sliceWindow, that.sliceWindow);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(bucketCount, updateBrowseStartPace, sliceWindow);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("bucketCount", bucketCount)
            .add("updateBrowseStartPace", updateBrowseStartPace)
            .add("sliceWindow", sliceWindow)
            .toString();
    }
}
