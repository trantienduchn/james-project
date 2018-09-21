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

package org.apache.james.queue.rabbitmq.view.cassandra;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;

import org.apache.james.queue.rabbitmq.MailQueueName;
import org.apache.james.queue.rabbitmq.view.cassandra.configuration.CassandraMailQueueViewConfiguration;
import org.apache.james.queue.rabbitmq.view.cassandra.model.BucketedSlices.BucketId;
import org.apache.james.queue.rabbitmq.view.cassandra.model.EnqueuedMail;
import org.apache.james.queue.rabbitmq.view.cassandra.model.MailKey;
import org.apache.mailet.Mail;

import com.google.common.annotations.VisibleForTesting;

public class CassandraMailQueueMailStore {

    private final EnqueuedMailsDAO enqueuedMailsDao;
    private final BrowseStartDAO browseStartDao;
    private final CassandraMailQueueViewConfiguration configuration;
    private final Clock clock;
    private final Set<MailQueueName> initialInserted;

    @Inject
    CassandraMailQueueMailStore(EnqueuedMailsDAO enqueuedMailsDao,
                                BrowseStartDAO browseStartDao,
                                CassandraMailQueueViewConfiguration configuration) {
        this(enqueuedMailsDao, browseStartDao, configuration, Clock.systemUTC());
    }

    @VisibleForTesting
    CassandraMailQueueMailStore(EnqueuedMailsDAO enqueuedMailsDao,
                                BrowseStartDAO browseStartDao,
                                CassandraMailQueueViewConfiguration configuration,
                                Clock clock) {
        this.enqueuedMailsDao = enqueuedMailsDao;
        this.browseStartDao = browseStartDao;
        this.configuration = configuration;
        this.clock = clock;
        this.initialInserted = ConcurrentHashMap.newKeySet();
    }

    CompletableFuture<Void> storeMailInEnqueueTable(Mail mail, MailQueueName mailQueueName, Instant enqueuedTime) {
        EnqueuedMail enqueuedMail = convertToEnqueuedMail(mail, mailQueueName, enqueuedTime);

        return enqueuedMailsDao.insert(enqueuedMail)
            .thenCompose(any -> initBrowseStartIfNeeded(mailQueueName, enqueuedMail.getTimeRangeStart()));
    }

    private CompletableFuture<Void> initBrowseStartIfNeeded(MailQueueName mailQueueName, Instant sliceStartAt) {
        if (!initialInserted.contains(mailQueueName)) {
            return tryInsertBrowseStart(mailQueueName, sliceStartAt);
        }
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> tryInsertBrowseStart(MailQueueName mailQueueName, Instant sliceStartAt) {
        return browseStartDao
            .insertInitialBrowseStart(mailQueueName, sliceStartAt)
            .thenAccept(any -> initialInserted.add(mailQueueName));
    }

    private EnqueuedMail convertToEnqueuedMail(Mail mail, MailQueueName mailQueueName, Instant enqueuedTime) {
        return EnqueuedMail.builder()
            .mail(mail)
            .bucketId(computedBucketId(mail))
            .timeRangeStart(currentSliceStartInstant())
            .enqueuedTime(enqueuedTime)
            .mailKey(MailKey.fromMail(mail))
            .mailQueueName(mailQueueName)
            .build();
    }

    private Instant currentSliceStartInstant() {
        long sliceSize = configuration.getSliceWindow().getSeconds();
        long sliceId = clock.instant().getEpochSecond() / sliceSize;
        return Instant.ofEpochSecond(sliceId * sliceSize);
    }

    private BucketId computedBucketId(Mail mail) {
        int mailKeyHashCode = mail.getName().hashCode();
        int bucketIdValue = mailKeyHashCode % configuration.getBucketCount();
        return BucketId.of(bucketIdValue);
    }
}
