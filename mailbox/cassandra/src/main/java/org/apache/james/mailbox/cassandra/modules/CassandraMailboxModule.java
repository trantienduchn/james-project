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

package org.apache.james.mailbox.cassandra.modules;

import static com.datastax.driver.core.DataType.bigint;
import static com.datastax.driver.core.DataType.text;
import static com.datastax.driver.core.DataType.timeuuid;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.utils.CassandraConstants;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxPathTable;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxPathV2Table;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxTable;

import com.datastax.driver.core.schemabuilder.SchemaBuilder;

public interface CassandraMailboxModule {
    CassandraModule MODULE = CassandraModule.builder()
        .type(CassandraMailboxTable.MAILBOX_BASE)
        .statement(statement -> statement
            .ifNotExists()
            .addColumn(CassandraMailboxTable.MailboxBase.NAMESPACE, text())
            .addColumn(CassandraMailboxTable.MailboxBase.USER, text()))
        .table(CassandraMailboxTable.TABLE_NAME)
        .statement(statement -> statement
            .ifNotExists()
            .addPartitionKey(CassandraMailboxTable.ID, timeuuid())
            .addUDTColumn(CassandraMailboxTable.MAILBOX_BASE, SchemaBuilder.frozen(CassandraMailboxTable.MAILBOX_BASE))
            .addColumn(CassandraMailboxTable.NAME, text())
            .addColumn(CassandraMailboxTable.UIDVALIDITY, bigint())
            .withOptions()
            .comment("Holds the mailboxes information.")
            .caching(SchemaBuilder.KeyCaching.ALL,
                SchemaBuilder.rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .table(CassandraMailboxPathTable.TABLE_NAME)
        .statement(statement -> statement
            .ifNotExists()
            .addUDTPartitionKey(CassandraMailboxPathTable.NAMESPACE_AND_USER, SchemaBuilder.frozen(CassandraMailboxTable.MAILBOX_BASE))
            .addClusteringColumn(CassandraMailboxPathTable.MAILBOX_NAME, text())
            .addColumn(CassandraMailboxPathTable.MAILBOX_ID, timeuuid())
            .withOptions()
            .comment("Denormalisation table. Allow to retrieve mailboxes belonging to a certain user. This is a " +
                "LIST optimisation.")
            .caching(SchemaBuilder.KeyCaching.ALL,
                SchemaBuilder.rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .table(CassandraMailboxPathV2Table.TABLE_NAME)
        .statement(statement -> statement
            .ifNotExists()
            .addPartitionKey(CassandraMailboxPathV2Table.NAMESPACE, text())
            .addPartitionKey(CassandraMailboxPathV2Table.USER, text())
            .addClusteringColumn(CassandraMailboxPathV2Table.MAILBOX_NAME, text())
            .addColumn(CassandraMailboxPathV2Table.MAILBOX_ID, timeuuid())
            .withOptions()
            .comment("Denormalisation table. Allow to retrieve mailboxes belonging to a certain user. This is a " +
                "LIST optimisation.")
            .caching(SchemaBuilder.KeyCaching.ALL,
                SchemaBuilder.rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .build();
}
