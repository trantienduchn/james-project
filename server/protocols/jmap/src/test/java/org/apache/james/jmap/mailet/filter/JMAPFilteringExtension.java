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

import org.apache.james.eventsourcing.eventstore.memory.InMemoryEventStore;
import org.apache.james.jmap.api.filtering.FilteringManagement;
import org.apache.james.jmap.api.filtering.impl.EventSourcingFilteringManagement;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class JMAPFilteringExtension implements BeforeEachCallback, ParameterResolver {

    class JMAPFilteringTestSystem {
        private final JMAPFiltering jmapFiltering;
        private final FilteringManagement filteringManagement;
        private final UsersRepository usersRepository;
        private final InMemoryMailboxManager mailboxManager;

        private MailboxId fredMartinInbox;
        private MailboxId recipient1Mailbox;

        JMAPFilteringTestSystem(JMAPFiltering jmapFiltering, FilteringManagement filteringManagement, UsersRepository usersRepository,
                                InMemoryMailboxManager mailboxManager) {
            this.jmapFiltering = jmapFiltering;
            this.filteringManagement = filteringManagement;
            this.usersRepository = usersRepository;
            this.mailboxManager = mailboxManager;
        }

        public JMAPFiltering getJmapFiltering() {
            return jmapFiltering;
        }

        public FilteringManagement getFilteringManagement() {
            return filteringManagement;
        }

        public UsersRepository getUsersRepository() {
            return usersRepository;
        }

        public InMemoryMailboxManager getMailboxManager() {
            return mailboxManager;
        }


        public MailboxId createMailbox(InMemoryMailboxManager mailboxManager, String username, String mailboxName) throws Exception {
            MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
            return mailboxManager
                    .createMailbox(MailboxPath.forUser(username, mailboxName), mailboxSession)
                    .get();
        }

        public MailboxId getRecipient1MailboxId() {
            return recipient1Mailbox;
        }

        public MailboxId getFredMartinInbox() {
            return fredMartinInbox;
        }
    }

    private JMAPFilteringTestSystem testSystem;

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        FilteringManagement filteringManagement = new EventSourcingFilteringManagement(new InMemoryEventStore());
        MemoryUsersRepository usersRepository = MemoryUsersRepository.withoutVirtualHosting();
        InMemoryMailboxManager mailboxManager = new InMemoryIntegrationResources().createMailboxManager(new SimpleGroupMembershipResolver());
        ActionApplier.Factory actionApplierFactory = ActionApplier.factory(mailboxManager, new InMemoryId.Factory());

        JMAPFiltering jmapFiltering = new JMAPFiltering(filteringManagement, usersRepository, actionApplierFactory);

        testSystem = new JMAPFilteringTestSystem(jmapFiltering, filteringManagement, usersRepository, mailboxManager);

        initMailboxes();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == JMAPFilteringTestSystem.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return testSystem;
    }

    private void initMailboxes() throws Exception {
        InMemoryMailboxManager mailboxManager = testSystem.getMailboxManager();
        MailboxId mailbox1Id = testSystem.createMailbox(mailboxManager, JMAPFilteringTest.RECIPIENT_1_USERNAME, JMAPFilteringTest.RECIPIENT_1_MAILBOX_1);

        testSystem.recipient1Mailbox = mailbox1Id;

        MailboxId fredMartinInbox = testSystem.createMailbox(mailboxManager, JMAPFilteringTest.FRED_MARTIN_USERNAME, JMAPFilteringTest.FRED_MARTIN_INBOX);
        testSystem.fredMartinInbox = fredMartinInbox;
    }
}
