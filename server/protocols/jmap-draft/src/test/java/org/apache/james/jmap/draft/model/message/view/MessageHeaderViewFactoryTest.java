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

package org.apache.james.jmap.draft.model.message.view;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.jmap.draft.model.BlobId;
import org.apache.james.jmap.draft.model.Emailer;
import org.apache.james.jmap.draft.model.Keyword;
import org.apache.james.jmap.draft.model.Keywords;
import org.apache.james.jmap.draft.model.Number;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mime4j.dom.Message;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

class MessageHeaderViewFactoryTest {
    private static final Username BOB = Username.of("bob@local");
    private static final Username ALICE = Username.of("alice@local");
    private static final String DELIVERY_DATE = "2010-10-30T15:12:00Z";
    private static final ZonedDateTime ZONED_DELIVERY_DATE = ZonedDateTime.parse(DELIVERY_DATE);
    private static final Date MESSAGE_DATE = Date.from(ZONED_DELIVERY_DATE.toInstant());

    private MessageIdManager messageIdManager;
    private MessageHeaderViewFactory testee;
    private MailboxSession session;
    private MessageManager bobInbox;
    private MessageManager bobMailbox;
    private ComposedMessageId message1;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        messageIdManager = resources.getMessageIdManager();
        InMemoryMailboxManager mailboxManager = resources.getMailboxManager();

        session = mailboxManager.createSystemSession(BOB);
        MailboxId bobInboxId = mailboxManager.createMailbox(MailboxPath.inbox(session), session).get();
        MailboxId bobMailboxId = mailboxManager.createMailbox(MailboxPath.forUser(BOB, "anotherMailbox"), session).get();

        bobInbox = mailboxManager.getMailbox(bobInboxId, session);
        bobMailbox = mailboxManager.getMailbox(bobMailboxId, session);

        Message headerMessage = Message.Builder.of()
            .setSubject("test")
            .setFrom(ALICE.asString())
            .setTo(BOB.asString())
            .setDate(MESSAGE_DATE)
            .setBody("test content", StandardCharsets.UTF_8)
            .build();

        message1 = bobInbox.appendMessage(MessageManager.AppendCommand.builder()
                .withFlags(new Flags(Flags.Flag.SEEN))
                .build(headerMessage),
            session);

        testee = new MessageHeaderViewFactory(resources.getBlobManager());
    }

    @Test
    void fromMessageResultsShouldReturnCorrectView() throws Exception {
        List<MessageResult> messages = messageIdManager
            .getMessages(ImmutableList.of(message1.getMessageId()), FetchGroupImpl.MINIMAL, session);

        Emailer aliceEmail = Emailer.builder().name(ALICE.asString()).email(ALICE.asString()).build();
        Emailer bobEmail = Emailer.builder().name(BOB.asString()).email(BOB.asString()).build();

        ImmutableMap<String, String> headersMap = ImmutableMap.<String, String>builder()
            .put("Content-Type", "text/plain; charset=UTF-8")
            .put("Date", "Sat, 30 Oct 2010 22:12:00 +0700")
            .put("From", "alice@local")
            .put("To", "bob@local")
            .put("Subject", "test")
            .put("MIME-Version", "1.0")
            .build();

        MessageHeaderView actual = testee.fromMessageResults(messages);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(actual.getId()).isEqualTo(message1.getMessageId());
            softly.assertThat(actual.getMailboxIds()).containsExactly(bobInbox.getId());
            softly.assertThat(actual.getThreadId()).isEqualTo(message1.getMessageId().serialize());
            softly.assertThat(actual.getSize()).isEqualTo(Number.fromLong(162));
            softly.assertThat(actual.getKeywords()).isEqualTo(Keywords.strictFactory().from(Keyword.SEEN).asMap());
            softly.assertThat(actual.getBlobId()).isEqualTo(BlobId.of(message1.getMessageId().serialize()));
            softly.assertThat(actual.getInReplyToMessageId()).isEqualTo(Optional.empty());
            softly.assertThat(actual.getHeaders()).isEqualTo(headersMap);
            softly.assertThat(actual.getFrom()).isEqualTo(Optional.of(aliceEmail));
            softly.assertThat(actual.getTo()).isEqualTo(ImmutableList.of(bobEmail));
            softly.assertThat(actual.getCc()).isEqualTo(ImmutableList.of());
            softly.assertThat(actual.getBcc()).isEqualTo(ImmutableList.of());
            softly.assertThat(actual.getReplyTo()).isEqualTo(ImmutableList.of());
            softly.assertThat(actual.getSubject()).isEqualTo("test");
            softly.assertThat(actual.getDate()).isEqualTo(DELIVERY_DATE);
        });
    }

    @Test
    void fromMessageResultsShouldCombineKeywords() throws Exception {
        messageIdManager.setInMailboxes(message1.getMessageId(), ImmutableList.of(bobInbox.getId(), bobMailbox.getId()), session);
        bobMailbox.setFlags(new Flags(Flags.Flag.FLAGGED), MessageManager.FlagsUpdateMode.REPLACE, MessageRange.all(), session);

        List<MessageResult> messages = messageIdManager
            .getMessages(ImmutableList.of(message1.getMessageId()), FetchGroupImpl.MINIMAL, session);

        MessageHeaderView actual = testee.fromMessageResults(messages);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(actual.getId()).isEqualTo(message1.getMessageId());
            softly.assertThat(actual.getKeywords()).isEqualTo(Keywords.strictFactory().from(Keyword.SEEN, Keyword.FLAGGED).asMap());
        });
    }
}
