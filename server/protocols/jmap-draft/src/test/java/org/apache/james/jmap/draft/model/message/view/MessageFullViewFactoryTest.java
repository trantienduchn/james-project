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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.core.Username;
import org.apache.james.jmap.draft.model.Attachment;
import org.apache.james.jmap.draft.model.BlobId;
import org.apache.james.jmap.draft.model.Emailer;
import org.apache.james.jmap.draft.model.Keyword;
import org.apache.james.jmap.draft.model.Keywords;
import org.apache.james.jmap.draft.model.MessagePreviewGenerator;
import org.apache.james.jmap.draft.model.Number;
import org.apache.james.jmap.draft.model.message.view.MessageFullViewFactory.MetaDataWithContent;
import org.apache.james.jmap.draft.utils.HtmlTextExtractor;
import org.apache.james.jmap.draft.utils.JsoupHtmlTextExtractor;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.util.mime.MessageContentExtractor;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

class MessageFullViewFactoryTest {
    private static final String FORWARDED = "forwarded";
    private static final InMemoryId MAILBOX_ID = InMemoryId.of(18L);
    private static final Instant INTERNAL_DATE = Instant.parse("2012-02-03T14:30:42Z");
    private static final Username BOB = Username.of("bob@local");
    private static final Username ALICE = Username.of("alice@local");
    private static final String DELIVERY_DATE = "2010-10-30T15:12:00Z";
    private static final ZonedDateTime ZONED_DELIVERY_DATE = ZonedDateTime.parse(DELIVERY_DATE);
    private static final Date MESSAGE_DATE = Date.from(ZONED_DELIVERY_DATE.toInstant());

    private MessageIdManager messageIdManager;
    private InMemoryMailboxManager mailboxManager;
    private MailboxSession session;
    private MessageManager bobInbox;
    private MessageManager bobMailbox;
    private ComposedMessageId message1;
    private MessageFullViewFactory messageFullViewFactory;

    @BeforeEach
    void setUp() throws Exception {
        HtmlTextExtractor htmlTextExtractor = new JsoupHtmlTextExtractor();

        MessagePreviewGenerator messagePreview = new MessagePreviewGenerator();
        MessageContentExtractor messageContentExtractor = new MessageContentExtractor();

        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        messageIdManager = resources.getMessageIdManager();
        mailboxManager = resources.getMailboxManager();

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

        messageFullViewFactory = new MessageFullViewFactory(resources.getBlobManager(), messagePreview, messageContentExtractor, htmlTextExtractor);
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

        MessageFullView actual = messageFullViewFactory.fromMessageResults(messages);
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
            softly.assertThat(actual.isHasAttachment()).isEqualTo(false);
            softly.assertThat(actual.getPreview()).isEqualTo("test content");
            softly.assertThat(actual.getTextBody()).isEqualTo(Optional.of("test content"));
            softly.assertThat(actual.getHtmlBody()).isEqualTo(Optional.empty());
            softly.assertThat(actual.getAttachments()).isEqualTo(ImmutableList.of());
            softly.assertThat(actual.getAttachedMessages()).isEqualTo(ImmutableMap.of());
        });
    }

    @Test
    void fromMessageResultsShouldCombineKeywords() throws Exception {
        messageIdManager.setInMailboxes(message1.getMessageId(), ImmutableList.of(bobInbox.getId(), bobMailbox.getId()), session);
        bobMailbox.setFlags(new Flags(Flags.Flag.FLAGGED), MessageManager.FlagsUpdateMode.REPLACE, MessageRange.all(), session);

        List<MessageResult> messages = messageIdManager
            .getMessages(ImmutableList.of(message1.getMessageId()), FetchGroupImpl.MINIMAL, session);

        MessageFullView actual = messageFullViewFactory.fromMessageResults(messages);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(actual.getId()).isEqualTo(message1.getMessageId());
            softly.assertThat(actual.getKeywords()).isEqualTo(Keywords.strictFactory().from(Keyword.SEEN, Keyword.FLAGGED).asMap());
        });
    }

    @Test
    void emptyMailShouldBeLoadedIntoMessage() throws Exception {
        MetaDataWithContent testMail = MetaDataWithContent.builder()
                .uid(MessageUid.of(2))
                .keywords(Keywords.strictFactory().from(Keyword.SEEN))
                .size(0)
                .internalDate(INTERNAL_DATE)
                .content(new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)))
                .attachments(ImmutableList.of())
                .mailboxId(MAILBOX_ID)
                .messageId(TestMessageId.of(2))
                .build();

        MessageFullView testee = messageFullViewFactory.fromMetaDataWithContent(testMail);
        assertThat(testee)
            .extracting(MessageFullView::getPreview, MessageFullView::getSize, MessageFullView::getSubject, MessageFullView::getHeaders, MessageFullView::getDate)
            .containsExactly("(Empty)", Number.ZERO, "", ImmutableMap.of("MIME-Version", "1.0"), INTERNAL_DATE);
    }

    @Test
    void flagsShouldBeSetIntoMessage() throws Exception {
        MetaDataWithContent testMail = MetaDataWithContent.builder()
                .uid(MessageUid.of(2))
                .keywords(Keywords.strictFactory().from(Keyword.ANSWERED, Keyword.FLAGGED, Keyword.DRAFT, Keyword.FORWARDED))
                .size(0)
                .internalDate(INTERNAL_DATE)
                .content(new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)))
                .attachments(ImmutableList.of())
                .mailboxId(MAILBOX_ID)
                .messageId(TestMessageId.of(2))
                .build();
        MessageFullView testee = messageFullViewFactory.fromMetaDataWithContent(testMail);
        assertThat(testee)
            .extracting(MessageFullView::isIsUnread, MessageFullView::isIsFlagged, MessageFullView::isIsAnswered, MessageFullView::isIsDraft, MessageFullView::isIsForwarded)
            .containsExactly(true, true, true, true, true);
    }

    @Test
    void headersShouldBeSetIntoMessage() throws Exception {
        Keywords keywords = Keywords.strictFactory().from(Keyword.SEEN);
        String headers = "From: user <user@domain>\n"
                + "Subject: test subject\n"
                + "To: user1 <user1@domain>, user2 <user2@domain>\n"
                + "Cc: usercc <usercc@domain>\n"
                + "Bcc: userbcc <userbcc@domain>\n"
                + "Reply-To: \"user to reply to\" <user.reply.to@domain>\n"
                + "In-Reply-To: <SNT124-W2664003139C1E520CF4F6787D30@phx.gbl>\n"
                + "Date: Tue, 14 Jul 2015 12:30:42 +0000\n"
                + "Other-header: other header value";
        MetaDataWithContent testMail = MetaDataWithContent.builder()
                .uid(MessageUid.of(2))
                .keywords(keywords)
                .size(headers.length())
                .internalDate(INTERNAL_DATE)
                .content(new ByteArrayInputStream(headers.getBytes(StandardCharsets.UTF_8)))
                .attachments(ImmutableList.of())
                .mailboxId(MAILBOX_ID)
                .messageId(TestMessageId.of(2))
                .build();

        Emailer user = Emailer.builder().name("user").email("user@domain").build();
        Emailer user1 = Emailer.builder().name("user1").email("user1@domain").build();
        Emailer user2 = Emailer.builder().name("user2").email("user2@domain").build();
        Emailer usercc = Emailer.builder().name("usercc").email("usercc@domain").build();
        Emailer userbcc = Emailer.builder().name("userbcc").email("userbcc@domain").build();
        Emailer userRT = Emailer.builder().name("user to reply to").email("user.reply.to@domain").build();
        ImmutableMap<String, String> headersMap = ImmutableMap.<String, String>builder()
                .put("Cc", "usercc <usercc@domain>")
                .put("Bcc", "userbcc <userbcc@domain>")
                .put("Subject", "test subject")
                .put("From", "user <user@domain>")
                .put("To", "user1 <user1@domain>, user2 <user2@domain>")
                .put("Reply-To", "\"user to reply to\" <user.reply.to@domain>")
                .put("In-Reply-To", "<SNT124-W2664003139C1E520CF4F6787D30@phx.gbl>")
                .put("Other-header", "other header value")
                .put("Date", "Tue, 14 Jul 2015 12:30:42 +0000")
                .put("MIME-Version", "1.0")
                .build();

        MessageFullView testee = messageFullViewFactory.fromMetaDataWithContent(testMail);
        MessageFullView expected = MessageFullView.builder()
                .id(TestMessageId.of(2))
                .blobId(BlobId.of("2"))
                .threadId("2")
                .mailboxId(MAILBOX_ID)
                .inReplyToMessageId("<SNT124-W2664003139C1E520CF4F6787D30@phx.gbl>")
                .headers(headersMap)
                .from(user)
                .to(ImmutableList.of(user1, user2))
                .cc(ImmutableList.of(usercc))
                .bcc(ImmutableList.of(userbcc))
                .replyTo(ImmutableList.of(userRT))
                .subject("test subject")
                .date(Instant.parse("2015-07-14T12:30:42.000Z"))
                .size(headers.length())
                .preview("(Empty)")
                .textBody(Optional.of(""))
                .htmlBody(Optional.empty())
                .keywords(keywords)
                .build();
        assertThat(testee).isEqualToComparingFieldByField(expected);
    }

    @Test
    void headersShouldBeUnfoldedAndDecoded() throws Exception {
        Keywords keywords = Keywords.strictFactory().from(Keyword.SEEN);
        String headers = "From: user <user@domain>\n"
            + "Subject: test subject\n"
            + "To: user1 <user1@domain>,\r\n"
            + " user2 <user2@domain>\n"
            + "Cc: =?UTF-8?Q?Beno=c3=aet_TELLIER?= <tellier@linagora.com>";
        MetaDataWithContent testMail = MetaDataWithContent.builder()
            .uid(MessageUid.of(2))
            .keywords(keywords)
            .size(headers.length())
            .internalDate(INTERNAL_DATE)
            .content(new ByteArrayInputStream(headers.getBytes(StandardCharsets.UTF_8)))
            .attachments(ImmutableList.of())
            .mailboxId(MAILBOX_ID)
            .messageId(TestMessageId.of(2))
            .build();

        Emailer user = Emailer.builder().name("user").email("user@domain").build();
        Emailer user1 = Emailer.builder().name("user1").email("user1@domain").build();
        Emailer user2 = Emailer.builder().name("user2").email("user2@domain").build();
        Emailer usercc = Emailer.builder().name("Benoît TELLIER").email("tellier@linagora.com").build();
        ImmutableMap<String, String> headersMap = ImmutableMap.<String, String>builder()
            .put("Cc", "Benoît TELLIER <tellier@linagora.com>")
            .put("Subject", "test subject")
            .put("From", "user <user@domain>")
            .put("To", "user1 <user1@domain>, user2 <user2@domain>")
            .put("MIME-Version", "1.0")
            .build();
        MessageFullView testee = messageFullViewFactory.fromMetaDataWithContent(testMail);
        MessageFullView expected = MessageFullView.builder()
            .id(TestMessageId.of(2))
            .blobId(BlobId.of("2"))
            .threadId("2")
            .mailboxId(MAILBOX_ID)
            .headers(headersMap)
            .from(user)
            .to(ImmutableList.of(user1, user2))
            .cc(ImmutableList.of(usercc))
            .subject("test subject")
            .date(Instant.parse("2012-02-03T14:30:42.000Z"))
            .size(headers.length())
            .preview("(Empty)")
            .textBody(Optional.of(""))
            .htmlBody(Optional.empty())
            .keywords(keywords)
            .build();

        assertThat(testee).isEqualToComparingFieldByField(expected);
    }

    @Test
    void multivaluedHeadersShouldBeSeparatedByLineFeed() throws Exception {
        Keywords keywords = Keywords.strictFactory().from(Keyword.SEEN);
        String headers = "From: user <user@domain>\n"
            + "Subject: test subject\n"
            + "Multi-header: first value\n"
            + "To: user1 <user1@domain>\n"
            + "Multi-header: second value\n";
        MetaDataWithContent testMail = MetaDataWithContent.builder()
            .uid(MessageUid.of(2))
            .keywords(keywords)
            .size(headers.length())
            .internalDate(INTERNAL_DATE)
            .content(new ByteArrayInputStream(headers.getBytes(StandardCharsets.UTF_8)))
            .attachments(ImmutableList.of())
            .mailboxId(MAILBOX_ID)
            .messageId(TestMessageId.of(2))
            .build();

        Emailer user = Emailer.builder().name("user").email("user@domain").build();
        Emailer user1 = Emailer.builder().name("user1").email("user1@domain").build();
        ImmutableMap<String, String> headersMap = ImmutableMap.<String, String>builder()
            .put("From", "user <user@domain>")
            .put("Subject", "test subject")
            .put("Multi-header", "first value\nsecond value")
            .put("To", "user1 <user1@domain>")
            .put("MIME-Version", "1.0")
            .build();
        MessageFullView testee = messageFullViewFactory.fromMetaDataWithContent(testMail);
        MessageFullView expected = MessageFullView.builder()
            .id(TestMessageId.of(2))
            .blobId(BlobId.of("2"))
            .threadId("2")
            .mailboxId(MAILBOX_ID)
            .headers(headersMap)
            .from(user)
            .to(ImmutableList.of(user1))
            .subject("test subject")
            .date(Instant.parse("2012-02-03T14:30:42.000Z"))
            .size(headers.length())
            .preview("(Empty)")
            .textBody(Optional.of(""))
            .htmlBody(Optional.empty())
            .keywords(keywords)
            .build();

        assertThat(testee).isEqualToComparingFieldByField(expected);
    }

    @Test
    void textBodyShouldBeSetIntoMessage() throws Exception {
        Keywords keywords = Keywords.strictFactory().from(Keyword.SEEN);
        String headers = "Subject: test subject\n";
        String body = "Mail body";
        String mail = headers + "\n" + body;
        MetaDataWithContent testMail = MetaDataWithContent.builder()
                .uid(MessageUid.of(2))
                .keywords(keywords)
                .size(mail.length())
                .internalDate(INTERNAL_DATE)
                .content(new ByteArrayInputStream(mail.getBytes(StandardCharsets.UTF_8)))
                .attachments(ImmutableList.of())
                .mailboxId(MAILBOX_ID)
                .messageId(TestMessageId.of(2))
                .build();
        MessageFullView testee = messageFullViewFactory.fromMetaDataWithContent(testMail);
        assertThat(testee.getTextBody()).hasValue("Mail body");
    }

    @Test
    void textBodyShouldNotOverrideWhenItIsThere() throws Exception {
        ByteArrayInputStream messageContent = new ByteArrayInputStream(("Subject\n"
            + "MIME-Version: 1.0\n"
            + "Content-Type: multipart/alternative;\n"
            + "\tboundary=\"----=_Part_370449_1340169331.1489506420401\"\n"
            + "\n"
            + "------=_Part_370449_1340169331.1489506420401\n"
            + "Content-Type: text/plain; charset=UTF-8\n"
            + "Content-Transfer-Encoding: 7bit\n"
            + "\n"
            + "My plain message\n"
            + "------=_Part_370449_1340169331.1489506420401\n"
            + "Content-Type: text/html; charset=UTF-8\n"
            + "Content-Transfer-Encoding: 7bit\n"
            + "\n"
            + "<a>The </a> <strong>HTML</strong> message"
        ).getBytes(StandardCharsets.UTF_8));

        MetaDataWithContent testMail = MetaDataWithContent.builder()
            .uid(MessageUid.of(2))
            .keywords(Keywords.strictFactory().from(Keyword.SEEN))
            .internalDate(INTERNAL_DATE)
            .size(1000)
            .content(messageContent)
            .attachments(ImmutableList.of())
            .mailboxId(MAILBOX_ID)
            .messageId(TestMessageId.of(2))
            .build();
        MessageFullView testee = messageFullViewFactory.fromMetaDataWithContent(testMail);

        assertThat(testee.getTextBody())
            .isPresent()
            .isEqualTo(Optional.of("My plain message"));
    }

    @Test
    void previewShouldBeLimitedTo256Length() throws Exception {
        String headers = "Subject: test subject\n";
        String body300 = "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999"
                + "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999"
                + "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999";
        String expectedPreview = "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999" 
                + "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999" 
                + "00000000001111111111222222222233333333334444444444555555";
        assertThat(body300.length()).isEqualTo(300);
        assertThat(expectedPreview.length()).isEqualTo(256);
        String mail = headers + "\n" + body300;
        MetaDataWithContent testMail = MetaDataWithContent.builder()
                .uid(MessageUid.of(2))
            .keywords(Keywords.strictFactory().from(Keyword.SEEN))
                .size(mail.length())
                .internalDate(INTERNAL_DATE)
                .content(new ByteArrayInputStream(mail.getBytes(StandardCharsets.UTF_8)))
                .attachments(ImmutableList.of())
                .mailboxId(MAILBOX_ID)
                .messageId(TestMessageId.of(2))
                .build();
        MessageFullView testee = messageFullViewFactory.fromMetaDataWithContent(testMail);
        assertThat(testee.getPreview()).isEqualTo(expectedPreview);
    }
    
    @Test
    void attachmentsShouldBeEmptyWhenNone() throws Exception {
        MetaDataWithContent testMail = MetaDataWithContent.builder()
                .uid(MessageUid.of(2))
            .keywords(Keywords.strictFactory().from(Keyword.SEEN))
                .size(0)
                .internalDate(INTERNAL_DATE)
                .content(ClassLoader.getSystemResourceAsStream("spamMail.eml"))
                .attachments(ImmutableList.of())
                .mailboxId(MAILBOX_ID)
                .messageId(TestMessageId.of(2))
                .build();
        MessageFullView testee = messageFullViewFactory.fromMetaDataWithContent(testMail);
        assertThat(testee.getAttachments()).isEmpty();
    }
    
    @Test
    void attachmentsShouldBeRetrievedWhenSome() throws Exception {
        String payload = "payload";
        BlobId blodId = BlobId.of("id1");
        String type = "content";
        Attachment expectedAttachment = Attachment.builder()
                .blobId(blodId)
                .size(payload.length())
                .type(type)
                .cid("cid")
                .isInline(true)
                .build();
        MetaDataWithContent testMail = MetaDataWithContent.builder()
                .uid(MessageUid.of(2))
            .keywords(Keywords.strictFactory().from(Keyword.SEEN))
                .size(0)
                .internalDate(INTERNAL_DATE)
                .content(ClassLoader.getSystemResourceAsStream("spamMail.eml"))
                .attachments(ImmutableList.of(MessageAttachment.builder()
                        .attachment(org.apache.james.mailbox.model.Attachment.builder()
                                .attachmentId(AttachmentId.from(blodId.getRawValue()))
                                .bytes(payload.getBytes())
                                .type(type)
                                .build())
                        .cid(Cid.from("cid"))
                        .isInline(true)
                        .build()))
                .mailboxId(MAILBOX_ID)
                .messageId(TestMessageId.of(2))
                .build();

        MessageFullView testee = messageFullViewFactory.fromMetaDataWithContent(testMail);

        assertThat(testee.getAttachments()).hasSize(1);
        assertThat(testee.getAttachments().get(0)).isEqualToComparingFieldByField(expectedAttachment);
    }

    @Test
    void invalidAddressesShouldBeAllowed() throws Exception {
        String headers = "From: user <userdomain>\n"
            + "To: user1 <user1domain>, user2 <user2domain>\n"
            + "Cc: usercc <userccdomain>\n"
            + "Bcc: userbcc <userbccdomain>\n"
            + "Subject: test subject\n";
        MetaDataWithContent testMail = MetaDataWithContent.builder()
            .uid(MessageUid.of(2))
            .keywords(Keywords.strictFactory().from(Keyword.SEEN))
            .size(headers.length())
            .internalDate(INTERNAL_DATE)
            .content(new ByteArrayInputStream(headers.getBytes(StandardCharsets.UTF_8)))
            .attachments(ImmutableList.of())
            .mailboxId(MAILBOX_ID)
            .messageId(new TestMessageId.Factory().generate())
            .build();

        MessageFullView testee = messageFullViewFactory.fromMetaDataWithContent(testMail);

        Emailer user = Emailer.builder().name("user").email("userdomain").allowInvalid().build();
        Emailer user1 = Emailer.builder().name("user1").email("user1domain").allowInvalid().build();
        Emailer user2 = Emailer.builder().name("user2").email("user2domain").allowInvalid().build();
        Emailer usercc = Emailer.builder().name("usercc").email("userccdomain").allowInvalid().build();
        Emailer userbcc = Emailer.builder().name("userbcc").email("userbccdomain").allowInvalid().build();

        assertThat(testee.getFrom()).contains(user);
        assertThat(testee.getTo()).contains(user1, user2);
        assertThat(testee.getCc()).contains(usercc);
        assertThat(testee.getBcc()).contains(userbcc);
    }

    @Test
    void messageWithoutFromShouldHaveEmptyFromField() throws Exception {
        String headers = "To: user <user@domain>\n"
            + "Subject: test subject\n";
        MetaDataWithContent testMail = MetaDataWithContent.builder()
            .uid(MessageUid.of(2))
            .keywords(Keywords.strictFactory().from(Keyword.SEEN))
            .size(headers.length())
            .internalDate(INTERNAL_DATE)
            .content(new ByteArrayInputStream(headers.getBytes(StandardCharsets.UTF_8)))
            .attachments(ImmutableList.of())
            .mailboxId(MAILBOX_ID)
            .messageId(new TestMessageId.Factory().generate())
            .build();

        MessageFullView testee = messageFullViewFactory.fromMetaDataWithContent(testMail);

        assertThat(testee.getFrom()).isEmpty();
    }

    @Test
    void dateFromHeaderShouldBeUsedIfPresent() throws Exception {
        String headers = "From: user <userdomain>\n"
            + "To: user1 <user1domain>, user2 <user2domain>\n"
            + "Cc: usercc <userccdomain>\n"
            + "Bcc: userbcc <userbccdomain>\n"
            + "Date: Wed, 17 May 2017 14:18:52 +0300\n"
            + "Subject: test subject\n";

        MetaDataWithContent testMail = MetaDataWithContent.builder()
            .uid(MessageUid.of(2))
            .keywords(Keywords.strictFactory().from(Keyword.SEEN))
            .size(headers.length())
            .internalDate(INTERNAL_DATE)
            .content(new ByteArrayInputStream(headers.getBytes(StandardCharsets.UTF_8)))
            .attachments(ImmutableList.of())
            .mailboxId(MAILBOX_ID)
            .messageId(new TestMessageId.Factory().generate())
            .build();

        MessageFullView testee = messageFullViewFactory.fromMetaDataWithContent(testMail);

        assertThat(testee.getDate())
            .isEqualTo(Instant.parse("2017-05-17T11:18:52.000Z"));
    }

    @Test
    void dateFromHeaderShouldUseCurrentCenturyWhenNone() throws Exception {
        String headers = "From: user <userdomain>\n"
            + "To: user1 <user1domain>, user2 <user2domain>\n"
            + "Cc: usercc <userccdomain>\n"
            + "Bcc: userbcc <userbccdomain>\n"
            + "Date: Wed, 17 May 17 14:18:52 +0300\n"
            + "Subject: test subject\n";

        MetaDataWithContent testMail = MetaDataWithContent.builder()
            .uid(MessageUid.of(2))
            .keywords(Keywords.strictFactory().from(Keyword.SEEN))
            .size(headers.length())
            .internalDate(INTERNAL_DATE)
            .content(new ByteArrayInputStream(headers.getBytes(StandardCharsets.UTF_8)))
            .attachments(ImmutableList.of())
            .mailboxId(MAILBOX_ID)
            .messageId(new TestMessageId.Factory().generate())
            .build();

        MessageFullView testee = messageFullViewFactory.fromMetaDataWithContent(testMail);

        assertThat(testee.getDate())
            .isEqualTo(Instant.parse("2017-05-17T11:18:52.000Z"));
    }

    @Test
    void internalDateShouldBeUsedIfNoDateInHeaders() throws Exception {
        String headers = "From: user <userdomain>\n"
            + "To: user1 <user1domain>, user2 <user2domain>\n"
            + "Cc: usercc <userccdomain>\n"
            + "Bcc: userbcc <userbccdomain>\n"
            + "Subject: test subject\n";

        MetaDataWithContent testMail = MetaDataWithContent.builder()
            .uid(MessageUid.of(2))
            .keywords(Keywords.strictFactory().from(Keyword.SEEN))
            .size(headers.length())
            .internalDate(INTERNAL_DATE)
            .content(new ByteArrayInputStream(headers.getBytes(StandardCharsets.UTF_8)))
            .attachments(ImmutableList.of())
            .mailboxId(MAILBOX_ID)
            .messageId(new TestMessageId.Factory().generate())
            .build();

        MessageFullView testee = messageFullViewFactory.fromMetaDataWithContent(testMail);

        assertThat(testee.getDate()).isEqualTo(INTERNAL_DATE);
    }

    @Test
    void mailWithBigLinesShouldBeLoadedIntoMessage() throws Exception {
        MetaDataWithContent testMail = MetaDataWithContent.builder()
                .uid(MessageUid.of(2))
            .keywords(Keywords.strictFactory().from(Keyword.SEEN))
                .size(1010)
                .internalDate(INTERNAL_DATE)
                .content(new ByteArrayInputStream((StringUtils.repeat("0123456789", 101).getBytes(StandardCharsets.UTF_8))))
                .attachments(ImmutableList.of())
                .mailboxId(MAILBOX_ID)
                .messageId(TestMessageId.of(2))
                .build();

        MessageFullView testee = messageFullViewFactory.fromMetaDataWithContent(testMail);
        assertThat(testee)
            .extracting(MessageFullView::getPreview, MessageFullView::getSize, MessageFullView::getSubject, MessageFullView::getHeaders, MessageFullView::getDate)
            .containsExactly("(Empty)", Number.fromLong(1010L), "", ImmutableMap.of("MIME-Version", "1.0"), INTERNAL_DATE);
    }

    @Test
    void textBodyShouldBeSetIntoMessageInCaseOfHtmlBody() throws Exception {
        ByteArrayInputStream messageContent = new ByteArrayInputStream(("CContent-Type: text/html\r\n"
            + "Subject: message 1 subject\r\n"
            + "\r\n"
            + "my <b>HTML</b> message").getBytes(StandardCharsets.UTF_8));
        MetaDataWithContent testMail = MetaDataWithContent.builder()
            .uid(MessageUid.of(2))
            .keywords(Keywords.strictFactory().from(Keyword.SEEN))
            .size(messageContent.read())
            .internalDate(INTERNAL_DATE)
            .content(messageContent)
            .attachments(ImmutableList.of())
            .mailboxId(MAILBOX_ID)
            .messageId(TestMessageId.of(2))
            .build();
        MessageFullView testee = messageFullViewFactory.fromMetaDataWithContent(testMail);

        assertThat(testee.getPreview()).isEqualTo("my HTML message");
        assertThat(testee.getTextBody()).hasValue("my HTML message");
        assertThat(testee.getHtmlBody()).hasValue("my <b>HTML</b> message");
    }

    @Test
    void textBodyShouldBeEmptyInCaseOfEmptyHtmlBodyAndEmptyTextBody() throws Exception {
        ByteArrayInputStream messageContent = new ByteArrayInputStream(("CContent-Type: text/html\r\n"
            + "Subject: message 1 subject\r\n").getBytes(StandardCharsets.UTF_8));
        MetaDataWithContent testMail = MetaDataWithContent.builder()
            .uid(MessageUid.of(2))
            .keywords(Keywords.strictFactory().from(Keyword.SEEN))
            .size(messageContent.read())
            .internalDate(INTERNAL_DATE)
            .content(messageContent)
            .attachments(ImmutableList.of())
            .mailboxId(MAILBOX_ID)
            .messageId(TestMessageId.of(2))
            .build();
        MessageFullView testee = messageFullViewFactory.fromMetaDataWithContent(testMail);

        assertThat(testee.getPreview()).isEqualTo(MessagePreviewGenerator.NO_BODY);
        assertThat(testee.getHtmlBody()).contains("");
        assertThat(testee.getTextBody()).isEmpty();
    }

    @Test
    void previewBodyShouldReturnTruncatedStringWithoutHtmlTagWhenHtmlBodyContainTags() throws Exception {
        String body = "This is a <b>HTML</b> mail containing <u>underlined part</u>, <i>italic part</i> and <u><i>underlined AND italic part</i></u>9999999999"
            + "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999"
            + "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999"
            + "000000000011111111112222222222333333333344444444445555555";
        String expected = "This is a HTML mail containing underlined part, italic part and underlined AND italic part9999999999"
            + "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999"
            + "00000000001111111111222222222233333333334444444444555555";

        ByteArrayInputStream messageContent = new ByteArrayInputStream(("CContent-Type: text/html\r\n"
            + "Subject: message 1 subject\r\n"
            + "\r\n"
            + body).getBytes(StandardCharsets.UTF_8));

        MetaDataWithContent testMail = MetaDataWithContent.builder()
            .uid(MessageUid.of(2))
            .keywords(Keywords.strictFactory().from(Keyword.SEEN))
            .size(messageContent.read())
            .internalDate(INTERNAL_DATE)
            .content(messageContent)
            .attachments(ImmutableList.of())
            .mailboxId(MAILBOX_ID)
            .messageId(TestMessageId.of(2))
            .build();
        MessageFullView testee = messageFullViewFactory.fromMetaDataWithContent(testMail);

        assertThat(testee.getPreview()).isEqualTo(expected);
    }

    @Test
    void previewBodyShouldReturnTextBodyWhenNoHtmlBody() throws Exception {
        ByteArrayInputStream messageContent = new ByteArrayInputStream(("CContent-Type: text/plain\r\n"
            + "Subject: message 1 subject\r\n"
            + "\r\n"
            + "My plain text").getBytes(StandardCharsets.UTF_8));

        MetaDataWithContent testMail = MetaDataWithContent.builder()
            .uid(MessageUid.of(2))
            .keywords(Keywords.strictFactory().from(Keyword.SEEN))
            .size(messageContent.read())
            .internalDate(INTERNAL_DATE)
            .content(messageContent)
            .attachments(ImmutableList.of())
            .mailboxId(MAILBOX_ID)
            .messageId(TestMessageId.of(2))
            .build();
        MessageFullView testee = messageFullViewFactory.fromMetaDataWithContent(testMail);

        assertThat(testee.getPreview()).isEqualTo("My plain text");
    }

    @Test
    void previewBodyShouldReturnStringEmptyWhenNoHtmlBodyAndNoTextBody() throws Exception {
        ByteArrayInputStream messageContent = new ByteArrayInputStream(("Subject: message 1 subject\r\n").getBytes(StandardCharsets.UTF_8));

        MetaDataWithContent testMail = MetaDataWithContent.builder()
            .uid(MessageUid.of(2))
            .keywords(Keywords.strictFactory().from(Keyword.SEEN))
            .size(messageContent.read())
            .internalDate(INTERNAL_DATE)
            .content(messageContent)
            .attachments(ImmutableList.of())
            .mailboxId(MAILBOX_ID)
            .messageId(TestMessageId.of(2))
            .build();
        MessageFullView testee = messageFullViewFactory.fromMetaDataWithContent(testMail);

        assertThat(testee)
            .extracting(MessageFullView::getPreview, MessageFullView::getHtmlBody, MessageFullView::getTextBody)
            .containsExactly(MessagePreviewGenerator.NO_BODY, Optional.empty(), Optional.of(""));
    }

    @Test
    void previewBodyShouldReturnStringEmptyWhenNoMeaningHtmlBodyAndNoTextBody() throws Exception {
        ByteArrayInputStream messageContent = new ByteArrayInputStream(("CContent-Type: text/html\r\n"
            + "Subject: message 1 subject\r\n"
            + "\r\n"
            + "<html><body></body></html>").getBytes(StandardCharsets.UTF_8));

        MetaDataWithContent testMail = MetaDataWithContent.builder()
            .uid(MessageUid.of(2))
            .keywords(Keywords.strictFactory().from(Keyword.SEEN))
            .size(messageContent.read())
            .internalDate(INTERNAL_DATE)
            .content(messageContent)
            .attachments(ImmutableList.of())
            .mailboxId(MAILBOX_ID)
            .messageId(TestMessageId.of(2))
            .build();
        MessageFullView testee = messageFullViewFactory.fromMetaDataWithContent(testMail);

        assertThat(testee)
            .extracting(MessageFullView::getPreview, MessageFullView::getHtmlBody, MessageFullView::getTextBody)
            .containsExactly(MessagePreviewGenerator.NO_BODY, Optional.of("<html><body></body></html>"), Optional.empty());
    }

    @Test
    void previewBodyShouldReturnTextBodyWhenNoMeaningHtmlBodyAndTextBody() throws Exception {
        ByteArrayInputStream messageContent = new ByteArrayInputStream(("Subject\n"
            + "MIME-Version: 1.0\n"
            + "Content-Type: multipart/alternative;\n"
            + "\tboundary=\"----=_Part_370449_1340169331.1489506420401\"\n"
            + "\n"
            + "------=_Part_370449_1340169331.1489506420401\n"
            + "Content-Type: text/plain; charset=UTF-8\n"
            + "Content-Transfer-Encoding: 7bit\n"
            + "\n"
            + "My plain message\n"
            + "------=_Part_370449_1340169331.1489506420401\n"
            + "Content-Type: text/html; charset=UTF-8\n"
            + "Content-Transfer-Encoding: 7bit\n"
            + "\n"
            + "<html></html>"
        ).getBytes(StandardCharsets.UTF_8));

        MetaDataWithContent testMail = MetaDataWithContent.builder()
            .uid(MessageUid.of(2))
            .keywords(Keywords.strictFactory().from(Keyword.SEEN))
            .size(messageContent.read())
            .internalDate(INTERNAL_DATE)
            .content(messageContent)
            .attachments(ImmutableList.of())
            .mailboxId(MAILBOX_ID)
            .messageId(TestMessageId.of(2))
            .build();
        MessageFullView testee = messageFullViewFactory.fromMetaDataWithContent(testMail);

        assertThat(testee)
            .extracting(MessageFullView::getPreview, MessageFullView::getHtmlBody, MessageFullView::getTextBody)
            .containsExactly("My plain message", Optional.of("<html></html>"), Optional.of("My plain message"));
    }

    @Test
    void keywordShouldBeSetIntoMessage() throws Exception {
        Keywords keywords = Keywords.strictFactory().from(Keyword.SEEN, Keyword.DRAFT);

        MetaDataWithContent testMail = MetaDataWithContent.builder()
            .uid(MessageUid.of(2))
            .keywords(keywords)
            .size(0)
            .internalDate(INTERNAL_DATE)
            .content(new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)))
            .attachments(ImmutableList.of())
            .mailboxId(MAILBOX_ID)
            .messageId(TestMessageId.of(2))
            .build();
        MessageFullView testee = messageFullViewFactory.fromMetaDataWithContent(testMail);
        assertThat(testee.getKeywords()).containsAllEntriesOf(keywords.asMap());
    }

    @Test
    void keywordWithUserFlagShouldBeSetIntoMessage() throws Exception {
        Keywords keywords = Keywords.strictFactory().from(Keyword.ANSWERED, Keyword.of(FORWARDED));

        MetaDataWithContent testMail = MetaDataWithContent.builder()
            .uid(MessageUid.of(2))
            .keywords(keywords)
            .size(0)
            .internalDate(INTERNAL_DATE)
            .content(new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)))
            .attachments(ImmutableList.of())
            .mailboxId(MAILBOX_ID)
            .messageId(TestMessageId.of(2))
            .build();
        MessageFullView testee = messageFullViewFactory.fromMetaDataWithContent(testMail);
        assertThat(testee.getKeywords()).containsAllEntriesOf(keywords.asMap());
    }
}
