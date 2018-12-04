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
package org.apache.james.mailbox.store.event;

import java.util.Map;

import org.apache.james.core.User;
import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageMoves;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

public class MessageMoveEvent implements Event {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private MailboxSession.SessionId sessionId;
        private User user;
        private MessageMoves messageMoves;
        private ImmutableMap.Builder<MessageUid, MailboxMessage> messagesBuilder;

        private Builder() {
            messagesBuilder = ImmutableMap.builder();
        }

        public Builder session(MailboxSession session) {
            this.sessionId = session.getSessionId();
            this.user = session.getUser().getCoreUser();
            return this;
        }

        public Builder sessionId(MailboxSession.SessionId sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder user(User user) {
            this.user = user;
            return this;
        }

        public Builder messageMoves(MessageMoves messageMoves) {
            this.messageMoves = messageMoves;
            return this;
        }

        public Builder messages(Map<MessageUid, MailboxMessage> messages) {
            this.messagesBuilder.putAll(messages);
            return this;
        }

        public MessageMoveEvent build() {
            Preconditions.checkNotNull(sessionId, "'sessionId' is mandatory");
            Preconditions.checkNotNull(user, "'user' is mandatory");
            Preconditions.checkNotNull(messageMoves, "'messageMoves' is mandatory");

            ImmutableMap<MessageUid, MailboxMessage> messages = messagesBuilder.build();

            return new MessageMoveEvent(sessionId, user, messageMoves, messages);
        }
    }

    private final MailboxSession.SessionId sessionId;
    private final User user;
    private final MessageMoves messageMoves;
    private final Map<MessageUid, MailboxMessage> messages;

    @VisibleForTesting
    MessageMoveEvent(MailboxSession.SessionId sessionId, User user, MessageMoves messageMoves, Map<MessageUid, MailboxMessage> messages) {
        this.sessionId = sessionId;
        this.user = user;
        this.messageMoves = messageMoves;
        this.messages = messages;
    }

    public boolean isNoop() {
        return messages.isEmpty();
    }

    @Override
    public MailboxSession getSession() {
        throw new UnsupportedOperationException("wiil be removed");
    }

    @Override
    public User getUser() {
        return user;
    }

    @Override
    public MailboxSession.SessionId getSessionId() {
        return sessionId;
    }

    public MessageMoves getMessageMoves() {
        return messageMoves;
    }

    public Map<MessageUid, MailboxMessage> getMessages() {
        return messages;
    }

    public boolean isMoveTo(MailboxId mailboxId) {
        return messageMoves.addedMailboxIds()
                .contains(mailboxId);
    }

    public boolean isMoveFrom(MailboxId mailboxId) {
        return messageMoves.removedMailboxIds()
                .contains(mailboxId);
    }
}
