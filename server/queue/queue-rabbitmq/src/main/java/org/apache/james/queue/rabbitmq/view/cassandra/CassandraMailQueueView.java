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

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;

import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.rabbitmq.MailQueueName;
import org.apache.james.queue.rabbitmq.view.api.MailQueueView;
import org.apache.mailet.Mail;

import com.google.common.collect.Iterators;

public class CassandraMailQueueView implements MailQueueView {

    public static class Factory {
        private final CassandraMailQueueMailStore storeHelper;
        private final CassandraMailQueueBrowser cassandraMailQueueBrowser;
        private final CassandraMailQueueMailDelete cassandraMailQueueMailDelete;

        @Inject
        public Factory(CassandraMailQueueMailStore storeHelper, CassandraMailQueueBrowser cassandraMailQueueBrowser, CassandraMailQueueMailDelete cassandraMailQueueMailDelete) {
            this.storeHelper = storeHelper;
            this.cassandraMailQueueBrowser = cassandraMailQueueBrowser;
            this.cassandraMailQueueMailDelete = cassandraMailQueueMailDelete;
        }

        public MailQueueView create(MailQueueName mailQueueName) {
            return new CassandraMailQueueView(storeHelper, mailQueueName, cassandraMailQueueBrowser, cassandraMailQueueMailDelete);
        }
    }

    private final CassandraMailQueueMailStore storeHelper;
    private final CassandraMailQueueBrowser cassandraMailQueueBrowser;
    private final CassandraMailQueueMailDelete cassandraMailQueueMailDelete;

    private final MailQueueName mailQueueName;

    CassandraMailQueueView(CassandraMailQueueMailStore storeHelper,
                                  MailQueueName mailQueueName,
                                  CassandraMailQueueBrowser cassandraMailQueueBrowser,
                                  CassandraMailQueueMailDelete cassandraMailQueueMailDelete) {
        this.mailQueueName = mailQueueName;
        this.storeHelper = storeHelper;
        this.cassandraMailQueueBrowser = cassandraMailQueueBrowser;
        this.cassandraMailQueueMailDelete = cassandraMailQueueMailDelete;
    }

    @Override
    public CompletableFuture<Void> storeMail(Instant enqueuedTime, Mail mail) {
        return storeHelper.storeMailInEnqueueTable(mail, mailQueueName, enqueuedTime);
    }

    @Override
    public CompletableFuture<Void> deleteMail(Mail mail) {
        return cassandraMailQueueMailDelete.considerDeleted(mail, mailQueueName);
    }

    @Override
    public ManageableMailQueue.MailQueueIterator browse() {
        return new CassandraMailQueueBrowser.CassandraMailQueueIterator(
            cassandraMailQueueBrowser.browse(mailQueueName)
                .join()
                .iterator());
    }

    @Override
    public long getSize() {
        return Iterators.size(browse());
    }

}
