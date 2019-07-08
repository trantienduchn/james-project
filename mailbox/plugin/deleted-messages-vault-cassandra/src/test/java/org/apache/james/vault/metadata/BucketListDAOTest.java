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

import static org.apache.james.vault.metadata.DeletedMessageMetadataModule.MODULE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.blob.api.BucketName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class BucketListDAOTest {
    private static final BucketName BUCKET_NAME = BucketName.of("deletedMessages-2019-06-01");
    private static final BucketName BUCKET_NAME_2 = BucketName.of("deletedMessages-2019-07-01");

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULE);

    private BucketListDAO testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new BucketListDAO(cassandra.getConf());
    }

    @Test
    void listBucketsShouldBeEmptyWhenNoBucketReferenced() {
        assertThat(testee.listBuckets().toStream()).isEmpty();
    }

    @Test
    void listBucketsShouldReturnAddedValue() {
        testee.addBucket(BUCKET_NAME).block();

        assertThat(testee.listBuckets().toStream()).containsExactly(BUCKET_NAME);
    }

    @Test
    void listBucketsShouldReturnAllAddedValues() {
        testee.addBucket(BUCKET_NAME).block();
        testee.addBucket(BUCKET_NAME_2).block();

        assertThat(testee.listBuckets().toStream()).containsExactlyInAnyOrder(BUCKET_NAME, BUCKET_NAME_2);
    }

    @Test
    void addShouldBeIdempotent() {
        testee.addBucket(BUCKET_NAME).block();
        testee.addBucket(BUCKET_NAME).block();

        assertThat(testee.listBuckets().toStream()).containsExactly(BUCKET_NAME);
    }

    @Test
    void listBucketsShouldNotReturnDeletedValues() {
        testee.addBucket(BUCKET_NAME).block();

        testee.removeBucket(BUCKET_NAME).block();

        assertThat(testee.listBuckets().toStream()).isEmpty();
    }

    @Test
    void addBucketShouldAllowReAddingDeletedValue() {
        testee.addBucket(BUCKET_NAME).block();
        testee.removeBucket(BUCKET_NAME).block();

        testee.addBucket(BUCKET_NAME).block();

        assertThat(testee.listBuckets().toStream()).containsExactly(BUCKET_NAME);
    }

    @Test
    void removeBucketShouldNotThrowOnNonExistingValue() {
        assertThatCode(() -> testee.removeBucket(BUCKET_NAME).block())
            .doesNotThrowAnyException();
    }

}