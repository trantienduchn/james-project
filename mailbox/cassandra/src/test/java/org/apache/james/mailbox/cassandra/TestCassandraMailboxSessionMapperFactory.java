/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailbox.cassandra;

import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.blob.cassandra.CassandraBlobId;
import org.apache.james.blob.cassandra.CassandraBlobsDAO;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraACLMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraApplicableFlagDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAOV2;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentOwnerDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraDeletedMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraFirstUnseenDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxCounterDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathDAOImpl;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathV2DAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxRecentsDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraModSeqProvider;
import org.apache.james.mailbox.cassandra.mail.CassandraUidProvider;
import org.apache.james.mailbox.cassandra.mail.CassandraUserMailboxRightsDAO;

import com.datastax.driver.core.Session;

public class TestCassandraMailboxSessionMapperFactory {

    public static CassandraMailboxSessionMapperFactory forTests(Session session, CassandraTypesProvider typesProvider,
                                                                CassandraMessageId.Factory factory) {
        return forTests(session, typesProvider, factory, CassandraConfiguration.DEFAULT_CONFIGURATION);
    }

    public static CassandraMailboxSessionMapperFactory forTests(Session session, CassandraTypesProvider typesProvider,
                                                                CassandraMessageId.Factory factory, CassandraConfiguration cassandraConfiguration) {

        CassandraUtils utils = new CassandraUtils(cassandraConfiguration);
        CassandraBlobsDAO cassandraBlobsDAO = new CassandraBlobsDAO(session);
        CassandraBlobId.Factory blobIdFactory = new CassandraBlobId.Factory();

        return new CassandraMailboxSessionMapperFactory(
            new CassandraUidProvider(session),
            new CassandraModSeqProvider(session),
            session,
            new CassandraMessageDAO(session, typesProvider, cassandraBlobsDAO, blobIdFactory, utils, factory),
            new CassandraMessageIdDAO(session, factory),
            new CassandraMessageIdToImapUidDAO(session, factory),
            new CassandraMailboxCounterDAO(session),
            new CassandraMailboxRecentsDAO(session),
            new CassandraMailboxDAO(session, typesProvider),
            new CassandraMailboxPathDAOImpl(session, typesProvider),
            new CassandraMailboxPathV2DAO(session, CassandraUtils.WITH_DEFAULT_CONFIGURATION),
            new CassandraFirstUnseenDAO(session),
            new CassandraApplicableFlagDAO(session),
            new CassandraAttachmentDAO(session, utils, cassandraConfiguration),
            new CassandraAttachmentDAOV2(blobIdFactory, session),
            new CassandraDeletedMessageDAO(session),
            cassandraBlobsDAO, new CassandraAttachmentMessageIdDAO(session, factory, utils),
            new CassandraAttachmentOwnerDAO(session, utils),
            new CassandraACLMapper(session,
                new CassandraUserMailboxRightsDAO(session, utils),
                cassandraConfiguration),
            new CassandraUserMailboxRightsDAO(session, utils),
            utils,
            cassandraConfiguration);
    }

}
