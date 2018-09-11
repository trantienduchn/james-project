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
import java.util.concurrent.CompletionStage;
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
    private final CassandraRabbitMQHelperConfiguration configuration;
    private final Random random;

    @Inject
    public DeleteMailHelper(DeletedMailsDAO deletedMailsDao,
                                      EnqueuedMailsDAO enqueuedMailsDao,
                                      FirstEnqueuedMailDAO firstEnqueuedMailDao,
                                      CassandraRabbitMQHelperConfiguration configuration) {
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
            return firstEnqueuedMailDao.findFirstEnqueuedInstant(mailQueueName)
                .thenCompose(maybeStartSliceInstant -> findMailInEnqueuedTable(mail, mailQueueName, maybeStartSliceInstant))
                .thenCompose(maybeEnqueuedMail -> setMailSliceToFirstEnqueued(mailQueueName, maybeEnqueuedMail));
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    private CompletionStage<Void> setMailSliceToFirstEnqueued(MailQueueName mailQueueName, Optional<EnqueuedMail> maybeEnqueuedMail) {
        if (maybeEnqueuedMail.isPresent()) {
            EnqueuedMail endQueuedMail = maybeEnqueuedMail.get();
            return firstEnqueuedMailDao.updateFirstEnqueuedTime(mailQueueName, endQueuedMail.getTimeRangeStart());
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    private CompletionStage<Optional<EnqueuedMail>> findMailInEnqueuedTable(
        Mail mail, MailQueueName mailQueueName, Optional<Instant> startInstantOptional) {

        if (startInstantOptional.isPresent()) {
            Instant sliceStart = startInstantOptional.get();
            Stream<BucketedSlices.BucketAndSlice> allBucketSlices = calculateBucketedSlices(sliceStart, Optional.empty());
            return enqueuedMailsDao.findEnqueuedMail(mailQueueName, allBucketSlices, MailKey.fromMail(mail));
        } else {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    private boolean shouldUpdateFirstEnqueued() {
        int threshold = configuration.getUpdateFirstEnqueuedPace();
        return Math.abs(random.nextInt()) % threshold == 0;
    }

    private Stream<BucketedSlices.BucketAndSlice> calculateBucketedSlices(Instant startingPoint, Optional<Instant> endAtOptional) {
        return BucketedSlices.builder()
            .startAt(startingPoint)
            .endAtOptional(endAtOptional)
            .bucketCount(configuration.getBucketCount())
            .sliceWindowSideInSecond(configuration.getSliceWindow().getSeconds())
            .build()
            .getBucketSlices();
    }
}
