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

package org.apache.james;

import static org.awaitility.Duration.FIVE_HUNDRED_MILLISECONDS;

import java.util.concurrent.TimeUnit;

import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.MailRepositoryProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class CassandraMailRepositoryIntegrationTest {

    private static final MailRepositoryUrl SENDER_DENIED_URL = MailRepositoryUrl.from("cassandra://var/mail/sender-denied/");
    private static final Duration ONE_MILLISECOND = new Duration(1, TimeUnit.MILLISECONDS);

    @ClassRule
    public static DockerCassandraRule cassandra = new DockerCassandraRule();
    
    @Rule
    public CassandraJmapTestRule cassandraJmap = CassandraJmapTestRule.defaultTestRule();
    @Rule
    public SMTPMessageSender smtpMessageSender = new SMTPMessageSender("other.com");

    private GuiceJamesServer server;
    private ConditionFactory await;

    @Before
    public void setup() throws Exception {
        server = cassandraJmap.jmapServer(cassandra.getModule());
        server.start();
        await = Awaitility.with()
            .pollInterval(FIVE_HUNDRED_MILLISECONDS)
            .and()
            .with()
            .pollDelay(ONE_MILLISECOND)
            .await();
    }

    @After
    public void tearDown() {
        server.stop();
    }

    @Test
    public void deniedSenderMailShouldBeStoredInCassandraMailRepositoryWhenConfigured() throws Exception {
        server.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain("domain.com")
            .addUser("user@domain.com", "secret");

        smtpMessageSender.connect("127.0.0.1", server.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage("denied@other.com", "user@domain.com");

        MailRepositoryProbeImpl repositoryProbe = server.getProbe(MailRepositoryProbeImpl.class);
        await.until(() -> repositoryProbe.getRepositoryMailCount(SENDER_DENIED_URL) == 1);
    }


}
