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

package org.apache.james.vault.search;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.vault.DeletedMessage;

public interface DeletedMessageField<T> {
    interface ValueExtractor<T> {
        Optional<T> extract(DeletedMessage deletedMessage);

        ValueExtractor<ZonedDateTime> DELETION_DATE_EXTRACTOR = deletedMessage -> Optional.ofNullable(deletedMessage.getDeletionDate());
        ValueExtractor<ZonedDateTime> DELIVERY_DATE_EXTRACTOR = deletedMessage -> Optional.ofNullable(deletedMessage.getDeliveryDate());
        ValueExtractor<List<MailAddress>> RECIPIENTS_EXTRACTOR = deletedMessage -> Optional.ofNullable(deletedMessage.getRecipients());
        ValueExtractor<MailAddress> SENDER_EXTRACTOR = deletedMessage -> deletedMessage.getSender().asOptional();
        ValueExtractor<Boolean> HAS_ATTACHMENTS_EXTRACTOR = deletedMessage -> Optional.of(deletedMessage.hasAttachment());
        ValueExtractor<List<MailboxId>> ORIGIN_MAILBOXES_EXTRACTOR = deletedMessage -> Optional.ofNullable(deletedMessage.getOriginMailboxes());
        ValueExtractor<String> SUBJECT_EXTRACTOR = DeletedMessage::getSubject;

    }

    DeletedMessageField<ZonedDateTime> DELETION_DATE = () -> ValueExtractor.DELETION_DATE_EXTRACTOR;
    DeletedMessageField<ZonedDateTime> DELIVERY_DATE = () -> ValueExtractor.DELIVERY_DATE_EXTRACTOR;
    DeletedMessageField<List<MailAddress>> RECIPIENTS = () -> ValueExtractor.RECIPIENTS_EXTRACTOR;
    DeletedMessageField<MailAddress> SENDER = () -> ValueExtractor.SENDER_EXTRACTOR;
    DeletedMessageField<Boolean> HAS_ATTACHMENTS = () -> ValueExtractor.HAS_ATTACHMENTS_EXTRACTOR;
    DeletedMessageField<List<MailboxId>> ORIGIN_MAILBOXES = () -> ValueExtractor.ORIGIN_MAILBOXES_EXTRACTOR;
    DeletedMessageField<String> SUBJECT = () -> ValueExtractor.SUBJECT_EXTRACTOR;

    ValueExtractor<T> valueExtractor();
}
