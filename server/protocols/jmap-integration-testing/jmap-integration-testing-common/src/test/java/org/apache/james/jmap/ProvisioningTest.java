/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.with;
import static org.apache.james.jmap.HttpJmapAuthentication.authenticateJamesUser;
import static org.apache.james.jmap.JmapURIBuilder.baseUri;
import static org.apache.james.jmap.TestingConstants.jmapRequestSpecBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;

import java.util.concurrent.TimeUnit;

import org.apache.james.GuiceJamesServer;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.JmapGuiceProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;

public abstract class ProvisioningTest {
    private static final String NAME = "[0][0]";
    private static final String ARGUMENTS = "[0][1]";
    private static final String DOMAIN = "mydomain.tld";
    private static final String USER = "myuser@" + DOMAIN;
    private static final String PASSWORD = "secret";

    @BeforeEach
    void setup(GuiceJamesServer jmapServer) throws Throwable {
        RestAssured.requestSpecification = jmapRequestSpecBuilder
            .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort())
            .build();

        jmapServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DOMAIN)
            .addUser(USER, PASSWORD);
    }

    @Test
    public void provisionMailboxesShouldNotDuplicateMailboxByName(GuiceJamesServer jmapServer) throws Exception {
        String token = authenticateJamesUser(baseUri(jmapServer), USER, PASSWORD).serialize();

        boolean termination = ConcurrentTestRunner.builder()
            .threadCount(10)
            .build((a, b) -> with()
                .header("Authorization", token)
                .body("[[\"getMailboxes\", {}, \"#0\"]]")
                .post("/jmap"))
            .run()
            .awaitTermination(1, TimeUnit.MINUTES);

        assertThat(termination).isTrue();

        given()
            .header("Authorization", token)
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(6))
            .body(ARGUMENTS + ".list.name", hasItems(DefaultMailboxes.DEFAULT_MAILBOXES.toArray()));
    }

    @Test
    public void provisionMailboxesShouldSubscribeToThem(GuiceJamesServer jmapServer) throws Exception {
        String token = authenticateJamesUser(baseUri(jmapServer), USER, PASSWORD).serialize();

        with()
            .header("Authorization", token)
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
            .post("/jmap");

        assertThat(jmapServer.getProbe(MailboxProbeImpl.class)
            .listSubscriptions(USER))
            .containsAll(DefaultMailboxes.DEFAULT_MAILBOXES);
    }
}
