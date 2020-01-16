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

package org.apache.james.webadmin.integration.vault;

import static io.restassured.RestAssured.with;
import static org.apache.james.jmap.HttpJmapAuthentication.authenticateJamesUser;
import static org.apache.james.jmap.JMAPTestingConstants.ARGUMENTS;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.jmap.JMAPTestingConstants.LOCALHOST_IP;
import static org.apache.james.jmap.JMAPTestingConstants.calmlyAwait;
import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
import static org.apache.james.jmap.JmapCommonRequests.deleteMessages;
import static org.apache.james.jmap.JmapCommonRequests.getOutboxId;
import static org.apache.james.jmap.JmapCommonRequests.listMessageIdsForAccount;
import static org.apache.james.jmap.LocalHostURIBuilder.baseUri;
import static org.apache.james.linshare.LinshareFixture.MATCH_ALL_QUERY;
import static org.apache.james.linshare.LinshareFixture.USER_1;
import static org.apache.james.mailbox.backup.ZipAssert.assertThatZip;
import static org.apache.james.webadmin.integration.vault.DeletedMessagesVaultRequests.exportVaultContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.Username;
import org.apache.james.jmap.AccessToken;
import org.apache.james.jmap.draft.JmapGuiceProbe;
import org.apache.james.linshare.LinshareExtension;
import org.apache.james.linshare.client.Document;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.backup.ZipAssert;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.probe.MailboxProbe;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import io.restassured.parsing.Parser;
import io.restassured.specification.RequestSpecification;

public interface LinshareBlobExportMechanismIntegrationContract {

    class TestSystem {
        private final AccessToken homerAccessToken;
        private final AccessToken bartAccessToken;
        private final RequestSpecification webAdminApi;
        private final ImapGuiceProbe imapProbe;

        public TestSystem(GuiceJamesServer jmapServer) {
            webAdminApi = WebAdminUtils.spec(jmapServer.getProbe(WebAdminGuiceProbe.class).getWebAdminPort());

            Port jmapPort = jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort();
            homerAccessToken = authenticateJamesUser(baseUri(jmapPort), Username.of(HOMER), HOMER_PASSWORD);
            bartAccessToken = authenticateJamesUser(baseUri(jmapPort), Username.of(BART), BART_PASSWORD);

            imapProbe = jmapServer.getProbe(ImapGuiceProbe.class);
        }

        private void bartSendMessageToHomer() {
            String messageCreationId = "creationId";
            String outboxId = getOutboxId(bartAccessToken);
            String textBody = "You got mail!";
            String requestBody = "[" +
                "  [" +
                "    \"setMessages\"," +
                "    {" +
                "      \"create\": { \"" + messageCreationId  + "\" : {" +
                "        \"from\": { \"name\": \"user2\", \"email\": \"" + BART + "\"}," +
                "        \"to\": [{ \"name\": \"user1\", \"email\": \"" + HOMER + "\"}]," +
                "        \"subject\": \"" + SUBJECT + "\"," +
                "        \"textBody\": \"" + textBody + "\"," +
                "        \"htmlBody\": \"Test <b>body</b>, HTML version\"," +
                "        \"mailboxIds\": [\"" + outboxId + "\"] " +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

            with()
                .header("Authorization", bartAccessToken.asString())
                .body(requestBody)
                .post("/jmap")
            .then()
                .extract()
                .body()
                .path(ARGUMENTS + ".created." + messageCreationId + ".id");
        }

        private void homerDeletesMessages(List<String> idsToDestroy) {
            deleteMessages(homerAccessToken, idsToDestroy);
        }
    }

    String HOMER = "homer@" + DOMAIN;
    String BART = "bart@" + DOMAIN;
    String HOMER_PASSWORD = "homerPassword";
    String BART_PASSWORD = "bartPassword";
    ConditionFactory WAIT_TEN_SECONDS = calmlyAwait.atMost(Duration.TEN_SECONDS);
    String SUBJECT = "This mail will be restored from the vault!!";
    ExportRequest EXPORT_ALL_HOMER_MESSAGES_TO_USER_1 = ExportRequest
        .userExportFrom(HOMER)
        .exportTo(USER_1.getUsername())
        .query(MATCH_ALL_QUERY);

    @BeforeEach
    default void setup(GuiceJamesServer jmapServer) throws Throwable {
        jmapServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DOMAIN)
            .addUser(HOMER, HOMER_PASSWORD)
            .addUser(BART, BART_PASSWORD);

        Port jmapPort = jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort();
        RestAssured.requestSpecification = jmapRequestSpecBuilder
            .setPort(jmapPort.getValue())
            .build();
        RestAssured.defaultParser = Parser.JSON;

        MailboxProbe mailboxProbe = jmapServer.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, HOMER, DefaultMailboxes.INBOX);
    }

    @Test
    default void exportShouldShareTheDocumentViaLinshareWhenJmapDelete(TestSystem testSystem, LinshareExtension linshare) throws Exception {
        testSystem.bartSendMessageToHomer();

        WAIT_TEN_SECONDS.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 1);

        testSystem.homerDeletesMessages(listMessageIdsForAccount(testSystem.homerAccessToken));
        WAIT_TEN_SECONDS.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).isEmpty());

        exportVaultContent(testSystem.webAdminApi, EXPORT_ALL_HOMER_MESSAGES_TO_USER_1);

        assertThat(linshare.getAPIFor(USER_1).receivedShares())
            .hasSize(1)
            .allSatisfy(receivedShare -> assertThat(receivedShare.getDocument().getName()).endsWith(".zip"))
            .allSatisfy(receivedShare -> assertThat(receivedShare.getSender().getMail()).isEqualTo(USER_1.getUsername()));
    }

    @Test
    default void exportShouldShareTheDocumentViaLinshareWhenImapDelete(IMAPMessageReader imapMessageReader,
                                                                       TestSystem testSystem,
                                                                       LinshareExtension linshare) throws Exception {
        testSystem.bartSendMessageToHomer();

        WAIT_TEN_SECONDS.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 1);

        imapMessageReader.connect(LOCALHOST_IP, testSystem.imapProbe.getImapPort())
            .login(HOMER, HOMER_PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .setFlagsForAllMessagesInMailbox("\\Deleted");
        imapMessageReader.expunge();

        WAIT_TEN_SECONDS.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).isEmpty());

        exportVaultContent(testSystem.webAdminApi, EXPORT_ALL_HOMER_MESSAGES_TO_USER_1);

        assertThat(linshare.getAPIFor(USER_1).receivedShares())
            .hasSize(1)
            .allSatisfy(receivedShare -> assertThat(receivedShare.getDocument().getName()).endsWith(".zip"))
            .allSatisfy(receivedShare -> assertThat(receivedShare.getSender().getMail()).isEqualTo(USER_1.getUsername()));
    }

    @Test
    default void exportShouldSendAnEmailToShareeWhenJmapDelete(TestSystem testSystem,
                                                               LinshareExtension linshare) {
        testSystem.bartSendMessageToHomer();

        WAIT_TEN_SECONDS.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 1);

        testSystem.homerDeletesMessages(listMessageIdsForAccount(testSystem.homerAccessToken));
        WAIT_TEN_SECONDS.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).isEmpty());

        exportVaultContent(testSystem.webAdminApi, EXPORT_ALL_HOMER_MESSAGES_TO_USER_1);

        WAIT_TEN_SECONDS.untilAsserted(
            () -> linshare.getLinshare().fakeSmtpRequestSpecification()
                .get("/api/email")
            .then()
                .body("", hasSize(2)));

        linshare.getLinshare().fakeSmtpRequestSpecification()
            .get("/api/email")
        .then()
            .body("[1].subject", containsString("John Doe has shared a file with you"))
            .body("[1].to", hasItem(USER_1.getUsername()));
    }

    @Test
    default void exportShouldSendAnEmailToShareeWhenImapDelete(IMAPMessageReader imapMessageReader,
                                                               TestSystem testSystem,
                                                               LinshareExtension linshare) throws Exception {
        testSystem.bartSendMessageToHomer();

        WAIT_TEN_SECONDS.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 1);

        imapMessageReader.connect(LOCALHOST_IP, testSystem.imapProbe.getImapPort())
            .login(HOMER, HOMER_PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .setFlagsForAllMessagesInMailbox("\\Deleted");
        imapMessageReader.expunge();

        WAIT_TEN_SECONDS.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).isEmpty());

        exportVaultContent(testSystem.webAdminApi, EXPORT_ALL_HOMER_MESSAGES_TO_USER_1);

        WAIT_TEN_SECONDS.untilAsserted(
            () -> linshare.getLinshare().fakeSmtpRequestSpecification()
                .get("/api/email")
            .then()
                .body("", hasSize(2)));

        linshare.getLinshare().fakeSmtpRequestSpecification()
            .get("/api/email")
        .then()
            .body("[1].subject", containsString("John Doe has shared a file with you"))
            .body("[1].to", hasItem(USER_1.getUsername()));
    }

    @Test
    default void exportShouldShareNonEmptyZipViaLinshareWhenJmapDelete(TestSystem testSystem,
                                                                       LinshareExtension linshare) throws Exception {
        testSystem.bartSendMessageToHomer();

        WAIT_TEN_SECONDS.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 1);

        testSystem.homerDeletesMessages(listMessageIdsForAccount(testSystem.homerAccessToken));
        WAIT_TEN_SECONDS.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).isEmpty());

        exportVaultContent(testSystem.webAdminApi, EXPORT_ALL_HOMER_MESSAGES_TO_USER_1);

        Document sharedDoc = linshare.getAPIFor(USER_1).receivedShares().get(0).getDocument();
        byte[] sharedFile =  linshare.downloadSharedFile(USER_1, sharedDoc.getId(), sharedDoc.getName());

        try (ZipAssert zipAssert = assertThatZip(new ByteArrayInputStream(sharedFile))) {
            zipAssert.hasEntriesSize(1);
        }
    }

    @Test
    default void exportShouldShareNonEmptyZipViaLinshareWhenImapDelete(IMAPMessageReader imapMessageReader,
                                                                       TestSystem testSystem,
                                                                       LinshareExtension linshare) throws Exception {
        testSystem.bartSendMessageToHomer();

        WAIT_TEN_SECONDS.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 1);

        imapMessageReader.connect(LOCALHOST_IP, testSystem.imapProbe.getImapPort())
            .login(HOMER, HOMER_PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .setFlagsForAllMessagesInMailbox("\\Deleted");
        imapMessageReader.expunge();

        WAIT_TEN_SECONDS.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).isEmpty());

        exportVaultContent(testSystem.webAdminApi, EXPORT_ALL_HOMER_MESSAGES_TO_USER_1);

        Document sharedDoc = linshare.getAPIFor(USER_1).receivedShares().get(0).getDocument();
        byte[] sharedFile =  linshare.downloadSharedFile(USER_1, sharedDoc.getId(), sharedDoc.getName());

        try (ZipAssert zipAssert = assertThatZip(new ByteArrayInputStream(sharedFile))) {
            zipAssert.hasEntriesSize(1);
        }
    }
}
