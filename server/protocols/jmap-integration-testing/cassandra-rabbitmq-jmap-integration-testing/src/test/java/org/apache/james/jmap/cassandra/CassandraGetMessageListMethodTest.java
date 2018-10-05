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

package org.apache.james.jmap.cassandra;

import org.apache.james.CassandraRabbitMQJmapTestRule;
import org.apache.james.DockerCassandraRule;
import org.apache.james.DockerRabbitMQRule;
import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.methods.integration.GetMessageListMethodTest;
import org.apache.james.modules.TestJMAPServerModule;
import org.junit.ClassRule;
import org.junit.Rule;

import java.io.IOException;

public class CassandraGetMessageListMethodTest extends GetMessageListMethodTest {

    @ClassRule
    public static DockerCassandraRule cassandra = new DockerCassandraRule();

    @ClassRule
    public static DockerRabbitMQRule rabbitMQTestRule = new DockerRabbitMQRule();

    @Rule
    public CassandraRabbitMQJmapTestRule rule = CassandraRabbitMQJmapTestRule.defaultTestRule();

    @Override
    protected GuiceJamesServer createJmapServer() throws IOException {
        return rule.jmapServer(rabbitMQTestRule.getModule(),
                cassandra.getModule(),
                new TestJMAPServerModule(LIMIT_TO_3_MESSAGES));
    }

    @Override
    protected void await() {
        rule.await();
    }
}
