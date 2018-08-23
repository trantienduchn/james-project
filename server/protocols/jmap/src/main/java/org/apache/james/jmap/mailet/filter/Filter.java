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

package org.apache.james.jmap.mailet.filter;

import java.util.List;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.MailAddress;
import org.apache.james.jmap.api.filtering.FilteringManagement;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.transport.mailets.delivery.MailStore;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

import com.github.fge.lambdas.consumers.ThrowingConsumer;

/**
 *
 */
public class Filter extends GenericMailet {

    private final FilteringManagement filteringManagement;
    private final UsersRepository usersRepository;
    private final MailboxMapper mailboxMapper;
    private final MailboxId.Factory mailboxIdFactory;

    @Inject
    public Filter(FilteringManagement filteringManagement,
                  UsersRepository usersRepository,
                  MailboxMapper mailboxMapper,
                  MailboxId.Factory mailboxIdFactory) {

        this.filteringManagement = filteringManagement;
        this.usersRepository = usersRepository;
        this.mailboxMapper = mailboxMapper;
        this.mailboxIdFactory = mailboxIdFactory;
    }

    @Override
    public void service(Mail mail) {
        mail.getRecipients()
            .parallelStream()
            .map(recipient -> new FilterRules(recipient, filteringManagement))
            .map(filterRules -> ImmutablePair.of(filterRules.getRecipient(), filterRules.matchedMailboxIds(mail)))
            .filter(pair -> !pair.getRight().isEmpty())
            .forEach(pair -> moveByMailbox(mail));
    }

    ThrowingConsumer<Pair<MailAddress, List<String>>> moveByMailbox(Mail mail) {
        return userMailboxIdsToStore -> {
            String user = usersRepository.getUser(userMailboxIdsToStore.getLeft());

            userMailboxIdsToStore.getRight()
                .forEach(addStorageDirective(user, mail));
        };
    }

    ThrowingConsumer<String> addStorageDirective(String user, Mail mail) {
        return mailboxIdString -> {
            MailboxId mailboxId = mailboxIdFactory.fromString(mailboxIdString);
            Mailbox mailbox = mailboxMapper.findMailboxById(mailboxId);
            String attributeNameForUser = MailStore.DELIVERY_PATH_PREFIX + user;
            mail.setAttribute(attributeNameForUser, mailbox.getName());
        };
    }
}
