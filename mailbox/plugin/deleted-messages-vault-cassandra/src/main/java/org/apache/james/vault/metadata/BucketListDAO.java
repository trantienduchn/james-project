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

package org.apache.james.vault.metadata;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.vault.metadata.DeletedMessageMetadataModule.BucketListTable.BUCKET_NAME;
import static org.apache.james.vault.metadata.DeletedMessageMetadataModule.BucketListTable.TABLE;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BucketName;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class BucketListDAO {
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement addStatement;
    private final PreparedStatement removeStatement;
    private final PreparedStatement listStatement;

    BucketListDAO(Session session) {
        cassandraAsyncExecutor = new CassandraAsyncExecutor(session);

        addStatement = prepareAddStatement(session);
        removeStatement = prepareRemoveStatement(session);
        listStatement = prepareListStatement(session);
    }

    private PreparedStatement prepareAddStatement(Session session) {
        return session.prepare(insertInto(TABLE)
            .value(BUCKET_NAME, bindMarker(BUCKET_NAME)));
    }

    private PreparedStatement prepareRemoveStatement(Session session) {
        return session.prepare(delete().from(TABLE)
            .where(eq(BUCKET_NAME, bindMarker(BUCKET_NAME))));
    }

    private PreparedStatement prepareListStatement(Session session) {
        return session.prepare(select(BUCKET_NAME).from(TABLE));
    }

    Mono<Void> addBucket(BucketName bucketName) {
        return cassandraAsyncExecutor.executeVoid(addStatement.bind()
            .setString(BUCKET_NAME, bucketName.asString()));
    }

    Mono<Void> removeBucket(BucketName bucketName) {
        return cassandraAsyncExecutor.executeVoid(removeStatement.bind()
            .setString(BUCKET_NAME, bucketName.asString()));
    }

    Flux<BucketName> listBuckets() {
        return cassandraAsyncExecutor.executeRows(listStatement.bind())
            .map(row -> row.getString(BUCKET_NAME))
            .map(BucketName::of);
    }
}
