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

package org.apache.james.queue.rabbitmq;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

import javax.mail.MessagingException;
import java.time.Instant;

class EnqueuedItemTest {

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(EnqueuedItem.class)
            .verify();
    }

    @Test
    void buildShouldThrowWhenMailQueueNameIsNull() throws MessagingException {
        MailQueueName mailQueueName = null;
        Mail mail = FakeMail.defaultFakeMail();
        Instant enqueuedTime = Instant.now();
        MimeMessagePartsId partsId = MimeMessagePartsId.builder()
                .headerBlobId(new HashBlobId.Factory().from("headerBlobId"))
                .bodyBlobId(new HashBlobId.Factory().from("bodyBlobId"))
                .build();
        assertThatThrownBy(() -> EnqueuedItem.builder()
                .mailQueueName(mailQueueName)
                .mail(mail)
                .enqueuedTime(enqueuedTime)
                .mimeMessagePartsId(partsId)
                .build());
    }

    @Test
    void buildShouldThrowWhenMailIsNull() throws MessagingException {
        MailQueueName mailQueueName = MailQueueName.fromString("mailQueueName");
        Mail mail = null;
        Instant enqueuedTime = Instant.now();
        MimeMessagePartsId partsId = MimeMessagePartsId.builder()
                .headerBlobId(new HashBlobId.Factory().from("headerBlobId"))
                .bodyBlobId(new HashBlobId.Factory().from("bodyBlobId"))
                .build();
        assertThatThrownBy(() -> EnqueuedItem.builder()
                .mailQueueName(mailQueueName)
                .mail(mail)
                .enqueuedTime(enqueuedTime)
                .mimeMessagePartsId(partsId)
                .build());
    }

    @Test
    void buildShouldThrowWhenEnqueuedTimeIsNull() throws MessagingException {
        MailQueueName mailQueueName = MailQueueName.fromString("mailQueueName");
        Mail mail = FakeMail.defaultFakeMail();
        Instant enqueuedTime = null;
        MimeMessagePartsId partsId = MimeMessagePartsId.builder()
                .headerBlobId(new HashBlobId.Factory().from("headerBlobId"))
                .bodyBlobId(new HashBlobId.Factory().from("bodyBlobId"))
                .build();
        assertThatThrownBy(() -> EnqueuedItem.builder()
                .mailQueueName(mailQueueName)
                .mail(mail)
                .enqueuedTime(enqueuedTime)
                .mimeMessagePartsId(partsId)
                .build());
    }

    @Test
    void buildShouldThrowWhenMimeMessagePartsIdIsNull() throws MessagingException {
        MailQueueName mailQueueName = MailQueueName.fromString("mailQueueName");
        Mail mail = FakeMail.defaultFakeMail();
        Instant enqueuedTime = Instant.now();
        MimeMessagePartsId partsId = null;
        assertThatThrownBy(() -> EnqueuedItem.builder()
                .mailQueueName(mailQueueName)
                .mail(mail)
                .enqueuedTime(enqueuedTime)
                .mimeMessagePartsId(partsId)
                .build());
    }
}