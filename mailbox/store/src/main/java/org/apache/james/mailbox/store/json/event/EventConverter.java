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

package org.apache.james.mailbox.store.json.event;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.james.core.User;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.json.event.dto.EventDataTransferObject;
import org.apache.james.mailbox.store.json.event.dto.EventType;
import org.apache.james.mailbox.store.json.event.dto.MailboxDataTransferObject;
import org.apache.james.mailbox.store.json.event.dto.MailboxPathDataTransferObject;
import org.apache.james.mailbox.store.json.event.dto.MailboxSessionIdDataTransferObject;
import org.apache.james.mailbox.store.json.event.dto.MessageMetaDataDataTransferObject;
import org.apache.james.mailbox.store.json.event.dto.UpdatedFlagsDataTransferObject;
import org.apache.james.mailbox.store.json.event.dto.UserDataTransferObject;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableMap;

public class EventConverter {

    private static final Logger LOG = LoggerFactory.getLogger(EventConverter.class);

    private final EventFactory eventFactory;
    private final MailboxConverter mailboxConverter;

    public EventConverter(MailboxConverter mailboxConverter) {
        this.eventFactory = new EventFactory();
        this.mailboxConverter = mailboxConverter;
    }

    public EventDataTransferObject convertToDataTransferObject(MailboxListener.MailboxEvent event) throws Exception {
        MailboxDataTransferObject mailboxDataTransferObject = mailboxConverter.extractMailboxDataTransferObject(event);
        if (event instanceof MailboxListener.Added) {
            return constructMeteDataHoldingEventProxy(EventType.ADDED,
                event.getSessionId(),
                event.getUser(),
                mailboxDataTransferObject,
                ((MailboxListener.Added) event).getUids(),
                (MailboxListener.Added) event);
        } else if (event instanceof MailboxListener.Expunged) {
            return constructMeteDataHoldingEventProxy(EventType.DELETED,
                event.getSessionId(),
                event.getUser(),
                mailboxDataTransferObject,
                ((MailboxListener.Expunged) event).getUids(),
                (MailboxListener.Expunged) event);
        } else if (event instanceof MailboxListener.FlagsUpdated) {
            return constructFalgsUpdatedProxy(event.getSessionId(),
                event.getUser(),
                mailboxDataTransferObject,
                ((MailboxListener.FlagsUpdated) event).getUids(),
                ((MailboxListener.FlagsUpdated) event).getUpdatedFlags());
        } else if (event instanceof MailboxListener.MailboxRenamed) {
            return constructMailboxRenamedProxy(event.getSessionId(),
                event.getUser(),
                mailboxDataTransferObject,
                event.getMailboxPath());
        } else if (event instanceof MailboxListener.MailboxDeletion) {
            MailboxListener.MailboxDeletion deletionEvent = (MailboxListener.MailboxDeletion) event;
            return constructMailboxDeletionProxy(EventType.MAILBOX_DELETED,
                event.getSessionId(),
                event.getUser(),
                mailboxDataTransferObject,
                deletionEvent.getQuotaRoot(),
                deletionEvent.getDeletedMessageCount(),
                deletionEvent.getTotalDeletedSize());
        } else if (event instanceof MailboxListener.MailboxAdded) {
            return constructMailboxAddedProxy(EventType.MAILBOX_ADDED,
                event.getSessionId(),
                event.getUser(),
                mailboxDataTransferObject);
        } else {
            throw new Exception("You are trying to serialize an event that can't be serialized");
        }
    }

    public MailboxListener.MailboxEvent retrieveEvent(EventDataTransferObject eventDataTransferObject) throws Exception {
        Mailbox mailbox = mailboxConverter.retrieveMailbox(eventDataTransferObject.getMailbox());
        User user = eventDataTransferObject.getUserDTO().getUser();
        Optional<MailboxSession.SessionId> sessionId = eventDataTransferObject.getSessionIdDTO()
            .map(MailboxSessionIdDataTransferObject::toSessionId);
        switch (eventDataTransferObject.getType()) {
            case ADDED:
                return eventFactory.added(sessionId,
                    user,
                    retrieveMetadata(eventDataTransferObject.getMetaDataProxyMap()),
                    mailbox,
                    ImmutableMap.<MessageUid, MailboxMessage>of());
            case DELETED:
                return eventFactory.expunged(sessionId,
                    user,
                    retrieveMetadata(eventDataTransferObject.getMetaDataProxyMap()),
                    mailbox);
            case FLAGS:
                return eventFactory.flagsUpdated(sessionId,
                    user,
                    eventDataTransferObject.getUids(),
                    mailbox,
                    retrieveUpdatedFlags(eventDataTransferObject.getUpdatedFlags()));
            case MAILBOX_ADDED:
                return eventFactory.mailboxAdded(sessionId,
                    user,
                    mailbox);
            case MAILBOX_DELETED:
                return eventFactory.mailboxDeleted(sessionId,
                    user,
                    mailbox,
                    eventDataTransferObject.getQuotaRoot().orElseThrow(() -> new EventNotValidException("Not a Deletion event, missing quotaRoot")),
                    eventDataTransferObject.getDeletedMessageCount().orElseThrow(() -> new EventNotValidException("Not a Deletion event, missing quotaCount")),
                    eventDataTransferObject.getTotalDeletedSize().orElseThrow(() -> new EventNotValidException("Not a Deletion event, missing quotaSize")));
            case MAILBOX_RENAMED:
                return eventFactory.mailboxRenamed(sessionId,
                    user,
                    eventDataTransferObject.getFrom().getPath(),
                    mailbox);
            default:
                throw new Exception("Can not deserialize unknown event");
        }
    }

    private EventDataTransferObject constructMailboxAddedProxy(EventType eventType,
                                                               Optional<MailboxSession.SessionId> sessionId,
                                                               User user,
                                                               MailboxDataTransferObject mailboxIntermediate) {
        return EventDataTransferObject.builder()
            .type(eventType)
            .sessionId(sessionId.map(MailboxSessionIdDataTransferObject::new))
            .user(new UserDataTransferObject(user))
            .mailbox(mailboxIntermediate)
            .build();
    }

    private EventDataTransferObject constructMailboxDeletionProxy(EventType eventType,
                                                                  Optional<MailboxSession.SessionId> sessionId,
                                                                  User user,
                                                                  MailboxDataTransferObject mailboxIntermediate,
                                                                  QuotaRoot quotaRoot,
                                                                  QuotaCount deletedMessageCount,
                                                                  QuotaSize totalDeletedSize) {
        return EventDataTransferObject.builder()
            .type(eventType)
            .sessionId(sessionId.map(MailboxSessionIdDataTransferObject::new))
            .user(new UserDataTransferObject(user))
            .mailbox(mailboxIntermediate)
            .quotaRoot(Optional.of(quotaRoot))
            .deletedMessageCount(Optional.of(deletedMessageCount))
            .totalDeletedSize(Optional.of(totalDeletedSize))
            .build();
    }

    private EventDataTransferObject constructMailboxRenamedProxy(Optional<MailboxSession.SessionId> sessionId,
                                                                 User user,
                                                                 MailboxDataTransferObject mailboxIntermediate,
                                                                 MailboxPath from) {
        return EventDataTransferObject.builder()
            .type(EventType.MAILBOX_RENAMED)
            .sessionId(sessionId.map(MailboxSessionIdDataTransferObject::new))
            .user(new UserDataTransferObject(user))
            .mailbox(mailboxIntermediate)
            .from(new MailboxPathDataTransferObject(from))
            .build();
    }

    private EventDataTransferObject constructFalgsUpdatedProxy(Optional<MailboxSession.SessionId> sessionId,
                                                               User user,
                                                               MailboxDataTransferObject mailboxIntermediate,
                                                               List<MessageUid> uids,
                                                               List<UpdatedFlags> updatedFlagsList) {
        List<UpdatedFlagsDataTransferObject> updatedFlagsDataTransferObjects = updatedFlagsList.stream()
            .map(UpdatedFlagsDataTransferObject::new)
            .collect(Guavate.toImmutableList());
        return EventDataTransferObject.builder()
            .type(EventType.FLAGS)
            .sessionId(sessionId.map(MailboxSessionIdDataTransferObject::new))
            .user(new UserDataTransferObject(user))
            .mailbox(mailboxIntermediate)
            .uids(uids)
            .updatedFlags(updatedFlagsDataTransferObjects)
            .build();
    }

    private EventDataTransferObject constructMeteDataHoldingEventProxy(EventType eventType,
                                                                       Optional<MailboxSession.SessionId> sessionId,
                                                                       User user,
                                                                       MailboxDataTransferObject mailboxIntermediate,
                                                                       List<MessageUid> uids,
                                                                       MailboxListener.MetaDataHoldingEvent event) {
        HashMap<MessageUid, MessageMetaDataDataTransferObject> metaDataProxyMap = new HashMap<>();
        for (MessageUid uid : uids) {
            metaDataProxyMap.put(uid, new MessageMetaDataDataTransferObject(
                event.getMetaData(uid)
            ));
        }
        return EventDataTransferObject.builder()
            .type(eventType)
            .sessionId(sessionId.map(MailboxSessionIdDataTransferObject::new))
            .user(new UserDataTransferObject(user))
            .mailbox(mailboxIntermediate)
            .uids(uids)
            .metaData(metaDataProxyMap)
            .build();
    }

    private SortedMap<MessageUid, MessageMetaData> retrieveMetadata(Map<MessageUid, MessageMetaDataDataTransferObject> metaDataProxyMap) {
        if (metaDataProxyMap != null) {
            TreeMap<MessageUid, MessageMetaData> result = new TreeMap<>();
            Set<Map.Entry<MessageUid, MessageMetaDataDataTransferObject>> entrySet = metaDataProxyMap.entrySet();
            for (Map.Entry<MessageUid, MessageMetaDataDataTransferObject> entry : entrySet) {
                result.put(entry.getKey(), entry.getValue().getMetadata());
            }
            return result;
        } else {
            LOG.warn("Event serialization problem : No metadata");
            return null;
        }
    }

    private List<UpdatedFlags> retrieveUpdatedFlags(List<UpdatedFlagsDataTransferObject> updatedFlagsDataTransferObject) {
        return updatedFlagsDataTransferObject.stream()
            .map(UpdatedFlagsDataTransferObject::retrieveUpdatedFlags)
            .collect(Guavate.toImmutableList());
    }

}
