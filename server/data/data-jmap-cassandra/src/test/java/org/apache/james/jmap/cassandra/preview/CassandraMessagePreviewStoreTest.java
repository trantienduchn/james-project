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

package org.apache.james.jmap.cassandra.preview;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.jmap.api.preview.MessagePreviewStore;
import org.apache.james.jmap.api.preview.MessagePreviewStoreContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraMessagePreviewStoreTest implements MessagePreviewStoreContract {

    @RegisterExtension
    static CassandraClusterExtension cassandra = new CassandraClusterExtension(CassandraMessagePreviewModule.MODULE);

    private CassandraMessagePreviewStore testee;

    @BeforeEach
    void setUp() {
        testee = new CassandraMessagePreviewStore(cassandra.getCassandraCluster().getConf());
    }

    @Override
    public MessagePreviewStore testee() {
        return testee;
    }
}