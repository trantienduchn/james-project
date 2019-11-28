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

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;
import javax.mail.internet.SharedInputStream;

import org.apache.james.jmap.api.preview.Preview;
import org.apache.james.jmap.draft.model.Attachment;
import org.apache.james.jmap.draft.model.BlobId;
import org.apache.james.jmap.draft.model.Keywords;
import org.apache.james.jmap.draft.utils.HtmlTextExtractor;
import org.apache.james.mailbox.BlobManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.util.mime.MessageContentExtractor;
import org.apache.james.util.mime.MessageContentExtractor.MessageContent;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;

public class MessageFullViewFactory implements MessageViewFactory<MessageFullView> {
    private final BlobManager blobManager;
    private final MessageContentExtractor messageContentExtractor;
    private final HtmlTextExtractor htmlTextExtractor;

    @Inject
    public MessageFullViewFactory(BlobManager blobManager, MessageContentExtractor messageContentExtractor,
                                  HtmlTextExtractor htmlTextExtractor) {
        this.blobManager = blobManager;
        this.messageContentExtractor = messageContentExtractor;
        this.htmlTextExtractor = htmlTextExtractor;
    }

    @Override
    public MessageFullView fromMessageResults(Collection<MessageResult> messageResults) throws MailboxException {
        return fromMetaDataWithContent(toMetaDataWithContent(messageResults));
    }

    public MessageFullView fromMetaDataWithContent(MetaDataWithContent message) throws MailboxException {
        Message mimeMessage = parse(message);
        MessageContent messageContent = extractContent(mimeMessage);
        Optional<String> htmlBody = messageContent.getHtmlBody();
        Optional<String> mainTextContent = mainTextContent(messageContent);
        Optional<String> textBody = computeTextBodyIfNeeded(messageContent, mainTextContent);

        Optional<Preview> preview = mainTextContent.map(Preview::compute);

        return MessageFullView.builder()
                .id(message.getMessageId())
                .blobId(BlobId.of(blobManager.toBlobId(message.getMessageId())))
                .threadId(message.getMessageId().serialize())
                .mailboxIds(message.getMailboxIds())
                .inReplyToMessageId(getHeader(mimeMessage, "in-reply-to"))
                .keywords(message.getKeywords())
                .subject(Strings.nullToEmpty(mimeMessage.getSubject()).trim())
                .headers(toMap(mimeMessage.getHeader().getFields()))
                .from(firstFromMailboxList(mimeMessage.getFrom()))
                .to(fromAddressList(mimeMessage.getTo()))
                .cc(fromAddressList(mimeMessage.getCc()))
                .bcc(fromAddressList(mimeMessage.getBcc()))
                .replyTo(fromAddressList(mimeMessage.getReplyTo()))
                .size(message.getSize())
                .date(getDateFromHeaderOrInternalDateOtherwise(mimeMessage, message))
                .textBody(textBody)
                .htmlBody(htmlBody)
                .preview(preview)
                .attachments(getAttachments(message.getAttachments()))
                .build();
    }

    private MetaDataWithContent toMetaDataWithContent(Collection<MessageResult> messageResults) throws MailboxException {
        assertOneMessageId(messageResults);

        MessageResult firstMessageResult = messageResults.iterator().next();
        List<MailboxId> mailboxIds = getMailboxIds(messageResults);
        Keywords keywords = getKeywords(messageResults);

        return MetaDataWithContent.builderFromMessageResult(firstMessageResult)
            .messageId(firstMessageResult.getMessageId())
            .mailboxIds(mailboxIds)
            .keywords(keywords)
            .build();
    }

    private Instant getDateFromHeaderOrInternalDateOtherwise(Message mimeMessage, MetaDataWithContent message) {
        return Optional.ofNullable(mimeMessage.getDate())
            .map(Date::toInstant)
            .orElse(message.getInternalDate());
    }

    private Optional<String> computeTextBodyIfNeeded(MessageContent messageContent, Optional<String> mainTextContent) {
        return messageContent.getTextBody()
            .map(Optional::of)
            .orElse(mainTextContent);
    }

    private Optional<String> mainTextContent(MessageContent messageContent) {
        return messageContent.getHtmlBody()
            .map(htmlTextExtractor::toPlainText)
            .filter(s -> !Strings.isNullOrEmpty(s))
            .map(Optional::of)
            .orElse(messageContent.getTextBody());
    }

    private Message parse(MetaDataWithContent message) throws MailboxException {
        try {
            return Message.Builder
                    .of()
                    .use(MimeConfig.PERMISSIVE)
                    .parse(message.getContent())
                    .build();
        } catch (IOException e) {
            throw new MailboxException("Unable to parse message: " + e.getMessage(), e);
        }
    }

    private MessageContent extractContent(Message mimeMessage) throws MailboxException {
        try {
            return messageContentExtractor.extract(mimeMessage);
        } catch (IOException e) {
            throw new MailboxException("Unable to extract content: " + e.getMessage(), e);
        }
    }
    
    private List<Attachment> getAttachments(List<MessageAttachment> attachments) {
        return attachments.stream()
                .map(this::fromMailboxAttachment)
                .collect(Guavate.toImmutableList());
    }

    private Attachment fromMailboxAttachment(MessageAttachment attachment) {
        return Attachment.builder()
                    .blobId(BlobId.of(attachment.getAttachmentId().getId()))
                    .type(attachment.getAttachment().getType())
                    .size(attachment.getAttachment().getSize())
                    .name(attachment.getName())
                    .cid(attachment.getCid().map(Cid::getValue))
                    .isInline(attachment.isInline())
                    .build();
    }

    public static class MetaDataWithContent {
        public static Builder builder() {
            return new Builder();
        }
        
        public static Builder builderFromMessageResult(MessageResult messageResult) throws MailboxException {
            Builder builder = builder()
                .uid(messageResult.getUid())
                .size(messageResult.getSize())
                .internalDate(messageResult.getInternalDate().toInstant())
                .attachments(messageResult.getLoadedAttachments())
                .mailboxId(messageResult.getMailboxId());
            try {
                return builder.content(messageResult.getFullContent().getInputStream());
            } catch (IOException e) {
                throw new MailboxException("Can't get message full content: " + e.getMessage(), e);
            }
        }
        
        public static class Builder {
            private MessageUid uid;
            private Keywords keywords;
            private Long size;
            private Instant internalDate;
            private InputStream content;
            private SharedInputStream sharedContent;
            private List<MessageAttachment> attachments;
            private Set<MailboxId> mailboxIds = Sets.newHashSet();
            private MessageId messageId;

            public Builder uid(MessageUid uid) {
                this.uid = uid;
                return this;
            }

            public Builder keywords(Keywords keywords) {
                this.keywords = keywords;
                return this;
            }

            public Builder size(long size) {
                this.size = size;
                return this;
            }
            
            public Builder internalDate(Instant internalDate) {
                this.internalDate = internalDate;
                return this;
            }
            
            public Builder content(InputStream content) {
                this.content = content;
                return this;
            }
            
            public Builder sharedContent(SharedInputStream sharedContent) {
                this.sharedContent = sharedContent;
                return this;
            }
            
            public Builder attachments(List<MessageAttachment> attachments) {
                this.attachments = attachments;
                return this;
            }
            
            public Builder mailboxId(MailboxId mailboxId) {
                this.mailboxIds.add(mailboxId);
                return this;
            }

            public Builder mailboxIds(List<MailboxId> mailboxIds) {
                this.mailboxIds.addAll(mailboxIds);
                return this;
            }
            
            public Builder messageId(MessageId messageId) {
                this.messageId = messageId;
                return this;
            }
            
            public MetaDataWithContent build() {
                Preconditions.checkArgument(uid != null);
                Preconditions.checkArgument(keywords != null);
                Preconditions.checkArgument(size != null);
                Preconditions.checkArgument(internalDate != null);
                Preconditions.checkArgument(content != null ^ sharedContent != null);
                Preconditions.checkArgument(attachments != null);
                Preconditions.checkArgument(mailboxIds != null);
                Preconditions.checkArgument(messageId != null);
                return new MetaDataWithContent(uid, keywords, size, internalDate, content, sharedContent, attachments, mailboxIds, messageId);
            }
        }

        private final MessageUid uid;
        private final Keywords keywords;
        private final long size;
        private final Instant internalDate;
        private final InputStream content;
        private final SharedInputStream sharedContent;
        private final List<MessageAttachment> attachments;
        private final Set<MailboxId> mailboxIds;
        private final MessageId messageId;

        private MetaDataWithContent(MessageUid uid,
                                    Keywords keywords,
                                    long size,
                                    Instant internalDate,
                                    InputStream content,
                                    SharedInputStream sharedContent,
                                    List<MessageAttachment> attachments,
                                    Set<MailboxId> mailboxIds,
                                    MessageId messageId) {
            this.uid = uid;
            this.keywords = keywords;
            this.size = size;
            this.internalDate = internalDate;
            this.content = content;
            this.sharedContent = sharedContent;
            this.attachments = attachments;
            this.mailboxIds = mailboxIds;
            this.messageId = messageId;
        }

        public MessageUid getUid() {
            return uid;
        }

        public Keywords getKeywords() {
            return keywords;
        }

        public long getSize() {
            return size;
        }

        public Instant getInternalDate() {
            return internalDate;
        }

        public InputStream getContent() {
            if (sharedContent != null) {
                long begin = 0;
                long allContent = -1;
                return sharedContent.newStream(begin, allContent);
            }
            return content;
        }

        public List<MessageAttachment> getAttachments() {
            return attachments;
        }

        public Set<MailboxId> getMailboxIds() {
            return mailboxIds;
        }

        public MessageId getMessageId() {
            return messageId;
        }

    }
}