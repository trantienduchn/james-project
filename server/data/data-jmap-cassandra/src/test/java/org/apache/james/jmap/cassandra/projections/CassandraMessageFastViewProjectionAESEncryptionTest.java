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

package org.apache.james.jmap.cassandra.projections;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.encryption.AesEncryptionCodec;
import org.apache.james.backends.cassandra.encryption.EncryptionConfiguration;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraMessageFastViewProjectionAESEncryptionTest implements CassandraMessageFastViewProjectionContract {

    @RegisterExtension
    static CassandraClusterExtension cassandra = new CassandraClusterExtension(CassandraMessageFastViewProjectionModule.MODULE);

    private CassandraMessageFastViewProjection testee;
    private CassandraMessageId.Factory cassandraMessageIdFactory;
    private RecordingMetricFactory metricFactory;

    @BeforeEach
    void setUp() {
        metricFactory = new RecordingMetricFactory();
        cassandraMessageIdFactory = new CassandraMessageId.Factory();
        AesEncryptionCodec aesEncryptionCodec = new AesEncryptionCodec(EncryptionConfiguration.builder()
            .salt("c603a7327ee3dcbc031d8d34b1096c605feca5e1")
            .password("my password".toCharArray()));

        testee = new CassandraMessageFastViewProjection(metricFactory, cassandra.getCassandraCluster().getConf(), aesEncryptionCodec);
    }

    @Override
    public MessageFastViewProjection testee() {
        return testee;
    }

    @Override
    public MessageId newMessageId() {
        return cassandraMessageIdFactory.generate();
    }

    @Override
    public RecordingMetricFactory metricFactory() {
        return metricFactory;
    }
}