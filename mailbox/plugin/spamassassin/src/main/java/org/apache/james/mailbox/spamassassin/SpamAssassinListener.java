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
package org.apache.james.mailbox.spamassassin;

import java.io.InputStream;
import java.util.List;

import javax.inject.Inject;

import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.SystemMailboxesProvider;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.event.MessageMoveEvent;
import org.apache.james.mailbox.store.event.SpamEventListener;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class SpamAssassinListener implements SpamEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpamAssassinListener.class);

    private final SpamAssassin spamAssassin;
    private final SystemMailboxesProvider systemMailboxesProvider;
    private final ExecutionMode executionMode;

    @Inject
    SpamAssassinListener(SpamAssassin spamAssassin, SystemMailboxesProvider systemMailboxesProvider, ExecutionMode executionMode) {
        this.spamAssassin = spamAssassin;
        this.systemMailboxesProvider = systemMailboxesProvider;
        this.executionMode = executionMode;
    }

    @Override
    public ListenerType getType() {
        return ListenerType.ONCE;
    }

    @Override
    public ExecutionMode getExecutionMode() {
        return executionMode;
    }

    @Override
    public void event(Event event) {
        String username = event.getUser().asString();
        if (event instanceof MessageMoveEvent) {
            MessageMoveEvent messageMoveEvent = (MessageMoveEvent) event;
            if (isMessageMovedToSpamMailbox(messageMoveEvent)) {
                LOGGER.debug("Spam event detected");
                ImmutableList<InputStream> messages = retrieveMessages(messageMoveEvent);
                spamAssassin.learnSpam(messages, username);
            }
            if (isMessageMovedOutOfSpamMailbox(messageMoveEvent)) {
                ImmutableList<InputStream> messages = retrieveMessages(messageMoveEvent);
                spamAssassin.learnHam(messages, username);
            }
        }
        if (event instanceof EventFactory.AddedImpl) {
            EventFactory.AddedImpl addedEvent = (EventFactory.AddedImpl) event;
            if (isAppendedToInbox(addedEvent)) {
                List<InputStream> contents = addedEvent.getAvailableMessages()
                    .values()
                    .stream()
                    .map(Throwing.function(MailboxMessage::getFullContent))
                    .collect(Guavate.toImmutableList());
                spamAssassin.learnHam(contents, username);
            }
        }
    }

    private boolean isAppendedToInbox(EventFactory.AddedImpl addedEvent) {
        try {
            return systemMailboxesProvider.findMailbox(Role.INBOX, addedEvent.getUser())
                .getId().equals(addedEvent.getMailboxId());
        } catch (MailboxException e) {
            LOGGER.warn("Could not resolve Inbox mailbox", e);
            return false;
        }
    }

    public ImmutableList<InputStream> retrieveMessages(MessageMoveEvent messageMoveEvent) {
        return messageMoveEvent.getMessages()
            .values()
            .stream()
            .map(Throwing.function(Message::getFullContent))
            .collect(Guavate.toImmutableList());
    }

    @VisibleForTesting
    boolean isMessageMovedToSpamMailbox(MessageMoveEvent event) {
        try {
            MailboxId spamMailboxId = systemMailboxesProvider.findMailbox(Role.SPAM, event.getUser()).getId();

            return event.getMessageMoves().addedMailboxIds().contains(spamMailboxId);
        } catch (MailboxException e) {
            LOGGER.warn("Could not resolve Spam mailbox", e);
            return false;
        }
    }

    @VisibleForTesting
    boolean isMessageMovedOutOfSpamMailbox(MessageMoveEvent event) {
        try {
            MailboxId spamMailboxId = systemMailboxesProvider.findMailbox(Role.SPAM, event.getUser()).getId();
            MailboxId trashMailboxId = systemMailboxesProvider.findMailbox(Role.TRASH, event.getUser()).getId();

            return event.getMessageMoves().removedMailboxIds().contains(spamMailboxId)
                && !event.getMessageMoves().addedMailboxIds().contains(trashMailboxId);
        } catch (MailboxException e) {
            LOGGER.warn("Could not resolve Spam mailbox", e);
            return false;
        }
    }
}
