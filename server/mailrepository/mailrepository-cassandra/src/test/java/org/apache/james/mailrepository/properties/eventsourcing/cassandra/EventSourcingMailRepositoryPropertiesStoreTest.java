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

package org.apache.james.mailrepository.properties.eventsourcing.cassandra;

import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStoreExtension;
import org.apache.james.mailrepository.api.properties.MailRepositoryPropertiesStore;
import org.apache.james.mailrepository.api.properties.MailRepositoryPropertiesStoreContract;
import org.apache.james.mailrepository.api.properties.eventsourcing.EventSourcingMailRepositoryPropertiesStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class EventSourcingMailRepositoryPropertiesStoreTest implements MailRepositoryPropertiesStoreContract {

    @RegisterExtension
    static CassandraEventStoreExtension eventStoreExtension = new CassandraEventStoreExtension(
        EventSourcingMailRepositoryPropertiesStoreModules.MAIL_REPOSITORY_PROPERTIES_UPDATE);

    private EventSourcingMailRepositoryPropertiesStore mailRepositoryPropertiesStore;

    @BeforeEach
    void beforeEach() {
         mailRepositoryPropertiesStore = new EventSourcingMailRepositoryPropertiesStore(eventStoreExtension.getEventStore());
    }

    @Override
    public MailRepositoryPropertiesStore testee() {
        return mailRepositoryPropertiesStore;
    }
}