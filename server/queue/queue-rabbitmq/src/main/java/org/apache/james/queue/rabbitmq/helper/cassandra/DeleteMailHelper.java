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

import java.time.Instant;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.queue.rabbitmq.MailQueueName;
import org.apache.james.queue.rabbitmq.helper.cassandra.model.BucketedSlices;
import org.apache.james.queue.rabbitmq.helper.cassandra.model.EnqueuedMail;
import org.apache.james.queue.rabbitmq.helper.cassandra.model.MailKey;
import org.apache.mailet.Mail;

class DeleteMailHelper {

    private final DeletedMailsDAO deletedMailsDao;
    private final EnqueuedMailsDAO enqueuedMailsDao;
    private final FirstEnqueuedMailDAO firstEnqueuedMailDao;
    private final CassandraRabbitMQConfiguration configuration;
    private final Random random;

    @Inject
    public DeleteMailHelper(DeletedMailsDAO deletedMailsDao,
                                      EnqueuedMailsDAO enqueuedMailsDao,
                                      FirstEnqueuedMailDAO firstEnqueuedMailDao,
                                      CassandraRabbitMQConfiguration configuration) {
        this.deletedMailsDao = deletedMailsDao;
        this.enqueuedMailsDao = enqueuedMailsDao;
        this.firstEnqueuedMailDao = firstEnqueuedMailDao;

        this.configuration = configuration;
        random = new Random();
    }

    CompletableFuture<Void> updateDeleteTable(Mail mail, MailQueueName mailQueueName) {
        return deletedMailsDao
            .insertOne(mailQueueName, MailKey.fromMail(mail))
            .thenAccept(avoid -> updateEnqueuedMail(mail, mailQueueName));
    }

    private CompletableFuture<Void> updateEnqueuedMail(Mail mail, MailQueueName mailQueueName) {
        if (shouldUpdateFirstEnqueued()) {
            return firstEnqueuedMailDao
                .findFirstEnqueuedInstant(mailQueueName)
                .thenCompose(findMailInEnqueued(mail, mailQueueName))
                .thenCompose(setFirstEnqueued(mailQueueName));
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    private Function<Optional<EnqueuedMail>, CompletableFuture<Void>> setFirstEnqueued(MailQueueName mailQueueName) {
        return maybeEnqueuedMail ->

            maybeEnqueuedMail
                .map(endQueuedMail -> firstEnqueuedMailDao
                    .updateFirstEnqueuedTime(mailQueueName, endQueuedMail.getTimeRangeStart()))
            .orElse(CompletableFuture.completedFuture(null));
    }

    private Function<Optional<Instant>, CompletableFuture<Optional<EnqueuedMail>>> findMailInEnqueued(
        Mail mail, MailQueueName mailQueueName) {

        return startInstantOptional ->

            startInstantOptional
                .map(sliceStart -> findEnqueuedWithMailKey(mailQueueName, sliceStart, mail))
                .orElse(CompletableFuture.completedFuture(Optional.empty()));
    }

    private CompletableFuture<Optional<EnqueuedMail>> findEnqueuedWithMailKey(
        MailQueueName mailQueueName, Instant sliceStart, Mail mail) {

        Stream<BucketedSlices.BucketAndSlice> allBucketSlices = calculateBucketedSlicesNoEndPoint(sliceStart);
        return enqueuedMailsDao.findEnqueuedMail(mailQueueName, allBucketSlices, MailKey.fromMail(mail));
    }

    private boolean shouldUpdateFirstEnqueued() {
        int threshold = configuration.getUpdateFirstEnqueuedPace();
        return Math.abs(random.nextInt()) % threshold == 0;
    }

    private Stream<BucketedSlices.BucketAndSlice> calculateBucketedSlicesNoEndPoint(Instant startingPoint) {
        return BucketedSlices.builder()
            .startAt(startingPoint)
            .bucketCount(configuration.getBucketCount())
            .sliceWindowSideInSecond(configuration.getSliceWindow().getSeconds())
            .build()
            .getBucketSlices();
    }
}
