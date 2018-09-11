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
import java.util.Objects;
import java.util.Optional;

import org.apache.james.queue.rabbitmq.MailQueueName;
import org.apache.mailet.Mail;

public class EnqueuedMail {

    public static class Builder {
        private Optional<Mail> mail;
        private Optional<Integer> bucketId;
        private Optional<Instant> timeRangeStart;
        private Optional<MailKey> mailKey;
        private Optional<MailQueueName> mailQueueName;

        private Builder() {
            mail = Optional.empty();
            bucketId = Optional.empty();
            timeRangeStart = Optional.empty();
            mailKey = Optional.empty();
            mailQueueName = Optional.empty();
        }

        public Builder mail(Mail mail) {
            this.mail = Optional.ofNullable(mail);
            return this;
        }

        public Builder bucketId(Integer bucketId) {
            this.bucketId = Optional.ofNullable(bucketId);
            return this;
        }

        public Builder timeRangeStart(Instant timeRangeStart) {
            this.timeRangeStart = Optional.ofNullable(timeRangeStart);
            return this;
        }

        public Builder mailKey(MailKey mailKey) {
            this.mailKey = Optional.ofNullable(mailKey);
            return this;
        }

        public Builder mailQueueName(MailQueueName mailQueueName) {
            this.mailQueueName = Optional.ofNullable(mailQueueName);
            return this;
        }

        public EnqueuedMail build() {
            return new EnqueuedMail(
                mail.orElse(null),
                bucketId.orElse(0),
                timeRangeStart.orElse(null),
                mailKey.orElse(null),
                mailQueueName.orElse(null));
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final Mail mail;
    private final int bucketId;
    private final Instant timeRangeStart;
    private final MailKey mailKey;
    private final MailQueueName mailQueueName;

    private EnqueuedMail(Mail mail, int bucketId, Instant timeRangeStart, MailKey mailKey, MailQueueName mailQueueName) {
        this.mail = mail;
        this.bucketId = bucketId;
        this.timeRangeStart = timeRangeStart;
        this.mailKey = mailKey;
        this.mailQueueName = mailQueueName;
    }

    public Mail getMail() {
        return mail;
    }

    public int getBucketId() {
        return bucketId;
    }

    public MailKey getMailKey() {
        return mailKey;
    }

    public MailQueueName getMailQueueName() {
        return mailQueueName;
    }

    public Instant getTimeRangeStart() {
        return timeRangeStart;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof EnqueuedMail) {
            EnqueuedMail that = (EnqueuedMail) o;

            return Objects.equals(this.bucketId, that.bucketId)
                    && Objects.equals(this.mail, that.mail)
                    && Objects.equals(this.timeRangeStart, that.timeRangeStart)
                    && Objects.equals(this.mailKey, that.mailKey)
                    && Objects.equals(this.mailQueueName, that.mailQueueName);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(mail, bucketId, timeRangeStart, mailKey, mailQueueName);
    }
}
