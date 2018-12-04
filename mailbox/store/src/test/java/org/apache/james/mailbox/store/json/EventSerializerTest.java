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

package org.apache.james.mailbox.store.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.TreeMap;

import javax.mail.Flags;

import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.SimpleMessageMetaData;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.event.EventSerializer;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

public abstract class EventSerializerTest {

    public static final MessageUid UID = MessageUid.of(42);
    public static final long MOD_SEQ = 24L;
    public static final Flags FLAGS = new Flags();
    public static final UpdatedFlags UPDATED_FLAGS = UpdatedFlags.builder()
        .uid(UID)
        .modSeq(MOD_SEQ)
        .oldFlags(FLAGS)
        .newFlags(new Flags(Flags.Flag.SEEN))
        .build();
    public static final long SIZE = 45L;
    private static final MessageId MESSAGE_ID = new TestMessageId.Factory().generate();
    public static final SimpleMessageMetaData MESSAGE_META_DATA = new SimpleMessageMetaData(UID, MOD_SEQ, FLAGS, SIZE, null, MESSAGE_ID);
    public static final MailboxPath FROM = new MailboxPath("namespace", "user", "name");

    private EventSerializer serializer;
    private EventFactory eventFactory;
    private MailboxSession mailboxSession;
    private SimpleMailbox mailbox;

    abstract EventSerializer createSerializer();

    @Before
    public void setUp() {
        eventFactory = new EventFactory();
        serializer = createSerializer();
        mailboxSession = new MockMailboxSession("benwa");
        mailbox = new SimpleMailbox(MailboxPath.forUser("benwa", "name"), 42);
        mailbox.setMailboxId(TestId.of(28L));
    }

    @Test
    public void addedEventShouldBeWellConverted() throws Exception {
        TreeMap<MessageUid, MessageMetaData> treeMap = new TreeMap<>();
        treeMap.put(UID, MESSAGE_META_DATA);
        MailboxListener.MailboxEvent event = eventFactory.added(mailboxSession, treeMap, mailbox, ImmutableMap.<MessageUid, MailboxMessage>of());
        byte[] serializedEvent = serializer.serializeEvent(event);
        MailboxListener.MailboxEvent deserializedEvent = serializer.deSerializeEvent(serializedEvent);
        assertThat(deserializedEvent.getMailboxPath()).isEqualTo(event.getMailboxPath());
        assertThat(deserializedEvent.getSessionId()).isEqualTo(event.getSessionId());
        assertThat(deserializedEvent.getUser()).isEqualTo(event.getUser());
        assertThat(deserializedEvent).isInstanceOf(MailboxListener.Added.class);
        assertThat(((MailboxListener.Added)deserializedEvent).getUids()).containsOnly(UID);
        MessageMetaData messageMetaData = ((MailboxListener.Added)deserializedEvent).getMetaData(UID);
        assertThat(messageMetaData).isEqualTo(MESSAGE_META_DATA);
        assertThat(messageMetaData.getMessageId()).isEqualTo(MESSAGE_ID);
    }

    @Test
    public void expungedEventShouldBeWellConverted() throws Exception {
        TreeMap<MessageUid, MessageMetaData> treeMap = new TreeMap<>();
        treeMap.put(UID, MESSAGE_META_DATA);
        MailboxListener.MailboxEvent event = eventFactory.expunged(mailboxSession, treeMap, mailbox);
        byte[] serializedEvent = serializer.serializeEvent(event);
        MailboxListener.MailboxEvent deserializedEvent = serializer.deSerializeEvent(serializedEvent);
        assertThat(deserializedEvent.getMailboxPath()).isEqualTo(event.getMailboxPath());
        assertThat(deserializedEvent.getSessionId()).isEqualTo(event.getSessionId());
        assertThat(deserializedEvent.getUser()).isEqualTo(event.getUser());
        assertThat(deserializedEvent).isInstanceOf(MailboxListener.Expunged.class);
        assertThat(((MailboxListener.Expunged)deserializedEvent).getUids()).containsOnly(UID);
        MessageMetaData messageMetaData = ((MailboxListener.Expunged)deserializedEvent).getMetaData(UID);
        assertThat(messageMetaData).isEqualTo(MESSAGE_META_DATA);
        assertThat(messageMetaData.getMessageId()).isEqualTo(MESSAGE_ID);
    }

    @Test
    public void flagsUpdatedEventShouldBeWellConverted() throws Exception {
        MailboxListener.MailboxEvent event = eventFactory.flagsUpdated(mailboxSession, Lists.newArrayList(UID), mailbox, Lists.newArrayList(UPDATED_FLAGS));
        byte[] serializedEvent = serializer.serializeEvent(event);
        MailboxListener.MailboxEvent deserializedEvent = serializer.deSerializeEvent(serializedEvent);
        assertThat(deserializedEvent.getMailboxPath()).isEqualTo(event.getMailboxPath());
        assertThat(deserializedEvent.getSessionId()).isEqualTo(event.getSessionId());
        assertThat(deserializedEvent.getUser()).isEqualTo(event.getUser());
        assertThat(deserializedEvent).isInstanceOf(MailboxListener.FlagsUpdated.class);
        assertThat(((MailboxListener.FlagsUpdated)event).getUpdatedFlags()).containsOnly(UPDATED_FLAGS);
    }

    @Test
    public void mailboxAddedShouldBeWellConverted() throws Exception {
        MailboxListener.MailboxEvent event = eventFactory.mailboxAdded(mailboxSession, mailbox);
        byte[] serializedEvent = serializer.serializeEvent(event);
        MailboxListener.MailboxEvent deserializedEvent = serializer.deSerializeEvent(serializedEvent);
        assertThat(deserializedEvent.getMailboxPath()).isEqualTo(event.getMailboxPath());
        assertThat(deserializedEvent.getSessionId()).isEqualTo(event.getSessionId());
        assertThat(deserializedEvent.getUser()).isEqualTo(event.getUser());
        assertThat(deserializedEvent).isInstanceOf(MailboxListener.MailboxAdded.class);
    }

    @Test
    public void mailboxDeletionShouldBeWellConverted() throws Exception {
        QuotaRoot quotaRoot = QuotaRoot.quotaRoot("root", Optional.empty());
        QuotaCount quotaCount = QuotaCount.count(123);
        QuotaSize quotaSize = QuotaSize.size(456);
        MailboxListener.MailboxDeletion event = eventFactory.mailboxDeleted(mailboxSession, mailbox, quotaRoot, quotaCount, quotaSize);
        byte[] serializedEvent = serializer.serializeEvent(event);
        MailboxListener.MailboxDeletion deserializedEvent = (MailboxListener.MailboxDeletion) serializer.deSerializeEvent(serializedEvent);
        assertThat(deserializedEvent.getMailboxPath()).isEqualTo(event.getMailboxPath());
        assertThat(deserializedEvent.getSessionId()).isEqualTo(event.getSessionId());
        assertThat(deserializedEvent.getUser()).isEqualTo(event.getUser());
        assertThat(deserializedEvent).isInstanceOf(MailboxListener.MailboxDeletion.class);
        assertThat(deserializedEvent.getQuotaRoot()).isEqualTo(quotaRoot);
        assertThat(deserializedEvent.getDeletedMessageCount()).isEqualTo(quotaCount);
        assertThat(deserializedEvent.getTotalDeletedSize()).isEqualTo(quotaSize);
    }

    @Test
    public void mailboxRenamedShouldBeWellConverted() throws Exception {
        MailboxListener.MailboxEvent event = eventFactory.mailboxRenamed(mailboxSession, FROM, mailbox);
        byte[] serializedEvent = serializer.serializeEvent(event);
        MailboxListener.MailboxEvent deserializedEvent = serializer.deSerializeEvent(serializedEvent);
        assertThat(deserializedEvent.getMailboxPath()).isEqualTo(event.getMailboxPath());
        assertThat(deserializedEvent.getSessionId()).isEqualTo(event.getSessionId());
        assertThat(deserializedEvent.getUser()).isEqualTo(event.getUser());
        assertThat(deserializedEvent).isInstanceOf(MailboxListener.MailboxRenamed.class);
        assertThat(deserializedEvent.getMailboxPath()).isEqualTo(event.getMailboxPath());
    }

}
