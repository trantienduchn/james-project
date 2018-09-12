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

package org.apache.james.queue.rabbitmq.helper.cassandra;

import java.time.Duration;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;

public class StoreMailHelperTest {

    private static final int ENQUEUED_PACE_UPDATE = 5;

    @ClassRule
    public static DockerCassandraRule cassandraServer = new DockerCassandraRule();
    private static CassandraCluster cassandra;

    private StoreMailHelper testee;

    @BeforeClass
    public static void setUpClass() {
        cassandra = CassandraCluster.create(CassandraRabbitMQHelperModule.MODULE, cassandraServer.getHost());
    }

    @Before
    public void setUp() {
        CassandraRabbitMQConfiguration configuration = new CassandraRabbitMQConfiguration(1,
            ENQUEUED_PACE_UPDATE, Duration.ofHours(1));

        EnqueuedMailsDAO enqueuedMailDao = new EnqueuedMailsDAO(
            cassandra.getConf(),
            CassandraUtils.WITH_DEFAULT_CONFIGURATION,
            cassandra.getTypesProvider(),
            configuration);

        FirstEnqueuedMailDAO firstEnqueuedMailDao = new FirstEnqueuedMailDAO(cassandra.getConf());

        testee = new StoreMailHelper(enqueuedMailDao, firstEnqueuedMailDao, configuration);
    }

    @After
    public void tearDown() {
        cassandra.clearTables();
    }

    @AfterClass
    public static void tearDownClass() {
        cassandra.closeCluster();
    }


}