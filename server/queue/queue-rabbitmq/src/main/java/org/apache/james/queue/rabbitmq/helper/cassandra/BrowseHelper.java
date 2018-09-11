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

package org.apache.james.queue.rabbitmq.helper.cassandra;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.rabbitmq.MailQueueName;
import org.apache.james.queue.rabbitmq.helper.cassandra.model.BucketedSlices;
import org.apache.james.queue.rabbitmq.helper.cassandra.model.EnqueuedMail;
import org.apache.james.util.CompletableFutureUtil;

class BrowseHelper {

    private final DeletedMailsDAO deletedMailsDao;
    private final EnqueuedMailsDAO enqueuedMailsDao;
    private final CassandraRabbitMQHelperConfiguration configuration;
    private final Clock clock;

    @Inject
    public BrowseHelper(DeletedMailsDAO deletedMailsDao,
                        EnqueuedMailsDAO enqueuedMailsDao,
                        CassandraRabbitMQHelperConfiguration configuration) {
        this.deletedMailsDao = deletedMailsDao;
        this.enqueuedMailsDao = enqueuedMailsDao;
        this.configuration = configuration;
        clock = Clock.systemUTC();
    }

    public CompletableFuture<Stream<ManageableMailQueue.MailQueueItemView>> browse(
        MailQueueName queueName, Instant startingPoint) {

        Stream<BucketedSlices.BucketAndSlice> allBucketSlices = BucketedSlices.builder()
            .startAt(startingPoint)
            .endAt(clock.instant())
            .bucketCount(configuration.getBucketCount())
            .sliceWindowSideInSecond(configuration.getSliceWindow().getSeconds())
            .build()
            .getBucketSlices();

        return CompletableFutureUtil.allOf(
            allBucketSlices
                .map(bucketAndSlice -> enqueuedMailsDao
                    .selectEnqueuedMails(queueName, bucketAndSlice.getBucketId(), bucketAndSlice.getSliceStartInstant())
                    .thenCompose(this::filterNonDeletedItem)))
            .thenApply(stream -> stream.flatMap(Function.identity()));
    }

    private CompletableFuture<Stream<ManageableMailQueue.MailQueueItemView>> filterNonDeletedItem(Stream<EnqueuedMail> enqueuedMailStream) {

        return CompletableFutureUtil.allOf(
            enqueuedMailStream
                .map(this::filterNotDeleted))
            .thenApply(this::filterPresentItem)
            .thenApply(this::mapToMailQueueItem);
    }

    private CompletableFuture<Optional<EnqueuedMail>> filterNotDeleted(EnqueuedMail enqueuedMail) {
        return deletedMailsDao
            .checkDeleted(enqueuedMail.getMailQueueName(), enqueuedMail.getMailKey())
            .thenApply(mailIsDeleted -> fromDeletedToManageableQueueItem(mailIsDeleted, enqueuedMail));
    }

    private Optional<EnqueuedMail> fromDeletedToManageableQueueItem(Boolean mailIsDeleted, EnqueuedMail enqueuedMail) {
        return Optional.of(mailIsDeleted)
                .filter(isDeleted -> !isDeleted)
                .map(isNotDeleted -> enqueuedMail);
    }

    private Stream<EnqueuedMail> filterPresentItem(
        Stream<Optional<EnqueuedMail>> afterCheckingDeletionStream) {

        return afterCheckingDeletionStream
            .filter(Optional::isPresent)
            .map(Optional::get);
    }

    private Stream<ManageableMailQueue.MailQueueItemView> mapToMailQueueItem(
        Stream<EnqueuedMail> afterCheckingDeletionStream) {

        return afterCheckingDeletionStream
            .map(EnqueuedMail::getMail)
            .map(ManageableMailQueue.MailQueueItemView::new);
    }
}
