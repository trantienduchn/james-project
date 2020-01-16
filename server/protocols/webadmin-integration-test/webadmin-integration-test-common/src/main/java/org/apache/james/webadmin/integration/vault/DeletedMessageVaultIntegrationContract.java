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

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.with;
import static io.restassured.config.ParamConfig.UpdateStrategy.REPLACE;
import static org.apache.james.jmap.HttpJmapAuthentication.authenticateJamesUser;
import static org.apache.james.jmap.JMAPTestingConstants.ARGUMENTS;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.jmap.JMAPTestingConstants.LOCALHOST_IP;
import static org.apache.james.jmap.JMAPTestingConstants.calmlyAwait;
import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
import static org.apache.james.jmap.JmapCommonRequests.deleteMessages;
import static org.apache.james.jmap.JmapCommonRequests.getAllMailboxesIds;
import static org.apache.james.jmap.JmapCommonRequests.getLastMessageId;
import static org.apache.james.jmap.JmapCommonRequests.getOutboxId;
import static org.apache.james.jmap.JmapCommonRequests.listMessageIdsForAccount;
import static org.apache.james.jmap.LocalHostURIBuilder.baseUri;
import static org.apache.james.mailbox.backup.ZipAssert.EntryChecks.hasName;
import static org.apache.james.mailbox.backup.ZipAssert.assertThatZip;
import static org.apache.james.webadmin.integration.vault.DeletedMessagesVaultRequests.deleteFromVault;
import static org.apache.james.webadmin.integration.vault.DeletedMessagesVaultRequests.exportVaultContent;
import static org.apache.james.webadmin.integration.vault.DeletedMessagesVaultRequests.purgeVault;
import static org.apache.james.webadmin.integration.vault.DeletedMessagesVaultRequests.restoreMessagesForUserWithQuery;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;

import java.io.FileInputStream;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;

import org.apache.james.GuiceJamesServer;
import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.core.Username;
import org.apache.james.jmap.AccessToken;
import org.apache.james.jmap.draft.JmapGuiceProbe;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.backup.ZipAssert;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.probe.MailboxProbe;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.UpdatableTickingClock;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.inject.Module;

import io.restassured.RestAssured;
import io.restassured.config.ParamConfig;
import io.restassured.parsing.Parser;
import io.restassured.specification.RequestSpecification;

public interface DeletedMessageVaultIntegrationContract {

    class ClockExtension implements GuiceModuleTestExtension {
        private UpdatableTickingClock clock;

        @Override
        public void beforeEach(ExtensionContext extensionContext) throws Exception {
            clock = new UpdatableTickingClock(NOW.toInstant());
        }

        @Override
        public Module getModule() {
            return binder -> binder.bind(Clock.class).toInstance(clock);
        }

        @Override
        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
            return parameterContext.getParameter().getType() == UpdatableTickingClock.class;
        }

        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
            return clock;
        }
    }

    class TestSystem {
        private final AccessToken homerAccessToken;
        private final AccessToken bartAccessToken;
        private final AccessToken jackAccessToken;
        private final RequestSpecification webAdminApi;
        private final MailboxId otherMailboxId;


        public TestSystem(GuiceJamesServer jamesServer) {
            MailboxProbe mailboxProbe = jamesServer.getProbe(MailboxProbeImpl.class);
            otherMailboxId = mailboxProbe.createMailbox("#private", HOMER, MAILBOX_NAME);

            Port jmapPort = jamesServer.getProbe(JmapGuiceProbe.class).getJmapPort();
            homerAccessToken = authenticateJamesUser(baseUri(jmapPort), Username.of(HOMER), PASSWORD);
            bartAccessToken = authenticateJamesUser(baseUri(jmapPort), Username.of(BART), BOB_PASSWORD);
            jackAccessToken = authenticateJamesUser(baseUri(jmapPort), Username.of(JACK), PASSWORD);


            webAdminApi = WebAdminUtils.spec(jamesServer.getProbe(WebAdminGuiceProbe.class).getWebAdminPort())
                .config(WebAdminUtils.defaultConfig()
                    .paramConfig(new ParamConfig(REPLACE, REPLACE, REPLACE)));
        }

        private String exportAndGetFileLocationFromLastMail(ExportRequest exportRequest, AccessToken shareeAccessToken) {
            int currentNumberOfMessages = listMessageIdsForAccount(shareeAccessToken).size();
            exportVaultContent(webAdminApi, exportRequest);

            WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(shareeAccessToken).size() == currentNumberOfMessages + 1);
            String exportingMessageId = getLastMessageId(shareeAccessToken);

            return exportedFileLocationFromMailHeader(exportingMessageId, shareeAccessToken);
        }

        private String exportedFileLocationFromMailHeader(String messageId, AccessToken accessToken) {
            return with()
                    .header("Authorization", accessToken.asString())
                    .body("[[\"getMessages\", {\"ids\": [\"" + messageId + "\"]}, \"#0\"]]")
                    .post("/jmap")
                .jsonPath()
                    .getList(ARGUMENTS + ".list.headers.corresponding-file", String.class)
                    .get(0);
        }

        private void homerSharesHisMailboxWithBart() {
            with()
                .header("Authorization", homerAccessToken.asString())
                .body("[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + otherMailboxId.serialize() + "\" : {" +
                    "          \"sharedWith\" : {\"" + BART + "\": [\"l\", \"w\", \"r\"]}" +
                    "        }" +
                    "      }" +
                    "    }," +
                    "    \"#0\"" +
                    "  ]" +
                    "]")
                .post("/jmap");
        }

        private void bartSendMessageToHomer() {
            bartSendMessageToHomerWithSubject(SUBJECT);
        }

        private void bartSendMessageToHomerAndJack() {
            String messageCreationId = "creationId";
            String outboxId = getOutboxId(bartAccessToken);
            String bigEnoughBody = Strings.repeat("123456789\n", 12 * 100);
            String requestBody = "[" +
                "  [" +
                "    \"setMessages\"," +
                "    {" +
                "      \"create\": { \"" + messageCreationId  + "\" : {" +
                "        \"headers\":{\"Disposition-Notification-To\":\"" + BART + "\"}," +
                "        \"from\": { \"name\": \"Bob\", \"email\": \"" + BART + "\"}," +
                "        \"to\": [{ \"name\": \"Homer\", \"email\": \"" + HOMER + "\"}, { \"name\": \"Jack\", \"email\": \"" + JACK + "\"}]," +
                "        \"subject\": \"" + SUBJECT + "\"," +
                "        \"textBody\": \"" + bigEnoughBody + "\"," +
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

        private void bartSendMessageToHomerWithSubject(String subject) {
            String messageCreationId = "creationId";
            String outboxId = getOutboxId(bartAccessToken);
            String bigEnoughBody = Strings.repeat("123456789\n", 12 * 100);
            String requestBody = "[" +
                "  [" +
                "    \"setMessages\"," +
                "    {" +
                "      \"create\": { \"" + messageCreationId  + "\" : {" +
                "        \"headers\":{\"Disposition-Notification-To\":\"" + BART + "\"}," +
                "        \"from\": { \"name\": \"Bob\", \"email\": \"" + BART + "\"}," +
                "        \"to\": [{ \"name\": \"User\", \"email\": \"" + HOMER + "\"}]," +
                "        \"subject\": \"" + subject + "\"," +
                "        \"textBody\": \"" + bigEnoughBody + "\"," +
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

        private void bartDeletesMessages(List<String> idsToDestroy) {
            deleteMessages(bartAccessToken, idsToDestroy);
        }

        private void jackDeletesMessages(List<String> idsToDestroy) {
            deleteMessages(jackAccessToken, idsToDestroy);
        }

        private void restoreAllMessagesOfHomer() {
            restoreMessagesFor(HOMER);
        }

        private void restoreMessagesFor(String user) {
            restoreMessagesForUserWithQuery(webAdminApi, user, MATCH_ALL_QUERY);
        }

        private void homerMovesTheMailInAnotherMailbox(String messageId) {
            String updateRequestBody = "[" +
                "  [" +
                "    \"setMessages\"," +
                "    {" +
                "      \"update\": { \"" + messageId  + "\" : {" +
                "        \"mailboxIds\": [\"" + otherMailboxId.serialize() + "\"]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

            given()
                .header("Authorization", homerAccessToken.asString())
                .body(updateRequestBody)
                .when()
                .post("/jmap");
        }

        private boolean homerHasMailboxWithRole(Role role) {
            return getAllMailboxesIds(homerAccessToken).stream()
                .filter(mailbox -> mailbox.get("role") != null)
                .anyMatch(mailbox -> mailbox.get("role").equals(role.serialize())
                    && mailbox.get("name").equals(role.getDefaultMailbox()));
        }

        private void waitTillHomerHasAMessage() {
            WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);
        }

    }

    ZonedDateTime NOW = ZonedDateTime.now();
    ZonedDateTime TWO_MONTH_AFTER_ONE_YEAR_EXPIRATION = NOW.plusYears(1).plusMonths(2);
    String FIRST_SUBJECT = "first subject";
    String SECOND_SUBJECT = "second subject";
    String HOMER = "homer@" + DOMAIN;
    String BART = "bart@" + DOMAIN;
    String JACK = "jack@" + DOMAIN;
    String PASSWORD = "password";
    String BOB_PASSWORD = "bobPassword";
    ConditionFactory WAIT_TWO_MINUTES = calmlyAwait.atMost(Duration.TWO_MINUTES);
    String SUBJECT = "This mail will be restored from the vault!!";
    String MAILBOX_NAME = "toBeDeleted";
    String MATCH_ALL_QUERY = "{" +
        "\"combinator\": \"and\"," +
        "\"criteria\": []" +
        "}";
    ExportRequest EXPORT_ALL_HOMER_MESSAGES_TO_BART = ExportRequest
        .userExportFrom(HOMER)
        .exportTo(BART)
        .query(MATCH_ALL_QUERY);
    ExportRequest EXPORT_ALL_JACK_MESSAGES_TO_HOMER = ExportRequest
        .userExportFrom(JACK)
        .exportTo(HOMER)
        .query(MATCH_ALL_QUERY);

    @BeforeEach
    default void setup(GuiceJamesServer jmapServer) throws Throwable {
        MailboxProbe mailboxProbe = jmapServer.getProbe(MailboxProbeImpl.class);
        DataProbe dataProbe = jmapServer.getProbe(DataProbeImpl.class);

        Port jmapPort = jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort();
        RestAssured.requestSpecification = jmapRequestSpecBuilder
            .setPort(jmapPort.getValue())
            .build();
        RestAssured.defaultParser = Parser.JSON;

        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(HOMER, PASSWORD);
        dataProbe.addUser(BART, BOB_PASSWORD);
        dataProbe.addUser(JACK, PASSWORD);
        mailboxProbe.createMailbox("#private", HOMER, DefaultMailboxes.INBOX);

    }

    void awaitSearchUpToDate();

    @Tag(BasicFeature.TAG)
    @Test
    default void vaultEndpointShouldRestoreJmapDeletedEmail(TestSystem testSystem) {
        testSystem.bartSendMessageToHomer();
        testSystem.waitTillHomerHasAMessage();

        testSystem.homerDeletesMessages(listMessageIdsForAccount(testSystem.homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 0);

        testSystem.restoreAllMessagesOfHomer();
        testSystem.waitTillHomerHasAMessage();

        String messageId = listMessageIdsForAccount(testSystem.homerAccessToken).get(0);
        given()
            .header("Authorization", testSystem.homerAccessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(ARGUMENTS + ".list.subject", hasItem(SUBJECT));
    }

    @Tag(BasicFeature.TAG)
    @Test
    default void vaultEndpointShouldRestoreImapDeletedEmail(GuiceJamesServer jmapServer, TestSystem testSystem,
                                                            IMAPMessageReader imapMessageReader) throws Exception {
        testSystem.bartSendMessageToHomer();
        testSystem.waitTillHomerHasAMessage();

        imapMessageReader.connect(LOCALHOST_IP, jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(HOMER, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .setFlagsForAllMessagesInMailbox("\\Deleted");
        imapMessageReader.expunge();

        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 0);

        testSystem.restoreAllMessagesOfHomer();
        testSystem.waitTillHomerHasAMessage();

        String messageId = listMessageIdsForAccount(testSystem.homerAccessToken).get(0);
        given()
            .header("Authorization", testSystem.homerAccessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(ARGUMENTS + ".list.subject", hasItem(SUBJECT));
    }

    @Tag(BasicFeature.TAG)
    @Test
    default void vaultEndpointShouldRestoreImapDeletedMailbox(GuiceJamesServer jmapServer, TestSystem testSystem,
                                                              IMAPMessageReader imapMessageReader) throws Exception {
        testSystem.bartSendMessageToHomer();
        testSystem.waitTillHomerHasAMessage();

        imapMessageReader.connect(LOCALHOST_IP, jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(HOMER, PASSWORD)
            .select(IMAPMessageReader.INBOX);

        imapMessageReader.moveFirstMessage(MAILBOX_NAME);

        imapMessageReader.delete(MAILBOX_NAME);

        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 0);

        testSystem.restoreAllMessagesOfHomer();
        testSystem.waitTillHomerHasAMessage();

        String messageId = listMessageIdsForAccount(testSystem.homerAccessToken).get(0);
        given()
            .header("Authorization", testSystem.homerAccessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(ARGUMENTS + ".list.subject", hasItem(SUBJECT));
    }

    @Test
    default void restoreShouldCreateRestoreMessagesMailbox(TestSystem testSystem) {
        testSystem.bartSendMessageToHomer();
        testSystem.waitTillHomerHasAMessage();

        testSystem.homerDeletesMessages(listMessageIdsForAccount(testSystem.homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 0);

        testSystem.restoreAllMessagesOfHomer();
        testSystem.waitTillHomerHasAMessage();

        assertThat(testSystem.homerHasMailboxWithRole(Role.RESTORED_MESSAGES)).isTrue();
    }

    @Test
    default void postShouldRestoreMatchingMessages(TestSystem testSystem) {
        testSystem.bartSendMessageToHomerWithSubject("aaaaa");
        testSystem.bartSendMessageToHomerWithSubject("bbbbb");
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 2);

        testSystem.homerDeletesMessages(listMessageIdsForAccount(testSystem.homerAccessToken));

        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 0);

        String query = "{" +
            "  \"combinator\": \"and\"," +
            "  \"criteria\": [" +
            "    {" +
            "      \"fieldName\": \"subject\"," +
            "      \"operator\": \"equals\"," +
            "      \"value\": \"aaaaa\"" +
            "    }" +
            "  ]" +
            "}";
        restoreMessagesForUserWithQuery(testSystem.webAdminApi, HOMER, query);

        testSystem.waitTillHomerHasAMessage();

        String messageId = listMessageIdsForAccount(testSystem.homerAccessToken).get(0);
        given()
            .header("Authorization", testSystem.homerAccessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
            .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(ARGUMENTS + ".list.subject", hasItem("aaaaa"));
    }

    @Test
    default void postShouldNotRestoreWhenNoMatchingMessages(TestSystem testSystem) throws Exception {
        testSystem.bartSendMessageToHomerWithSubject("aaaaa");
        testSystem.bartSendMessageToHomerWithSubject("bbbbb");
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 2);

        testSystem.homerDeletesMessages(listMessageIdsForAccount(testSystem.homerAccessToken));

        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 0);

        String query = "{" +
            "  \"combinator\": \"and\"," +
            "  \"criteria\": [" +
            "    {" +
            "      \"fieldName\": \"subject\"," +
            "      \"operator\": \"equals\"," +
            "      \"value\": \"ccccc\"" +
            "    }" +
            "  ]" +
            "}";
        restoreMessagesForUserWithQuery(testSystem.webAdminApi, HOMER, query);


        Thread.sleep(Duration.FIVE_SECONDS.getValueInMS());

        // No additional had been restored for Bart as the vault is empty
        assertThat(listMessageIdsForAccount(testSystem.homerAccessToken).size())
            .isEqualTo(0);
    }

    @Test
    default void imapMovedMessageShouldNotEndUpInTheVault(GuiceJamesServer jmapServer, TestSystem testSystem,
                                                          IMAPMessageReader imapMessageReader) throws Exception {
        testSystem.bartSendMessageToHomer();
        testSystem.waitTillHomerHasAMessage();

        imapMessageReader.connect(LOCALHOST_IP, jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(HOMER, PASSWORD)
            .select(IMAPMessageReader.INBOX);

        imapMessageReader.moveFirstMessage(MAILBOX_NAME);

        //Moved messages should not be moved to the vault
        testSystem.restoreAllMessagesOfHomer();
        testSystem.waitTillHomerHasAMessage();


        // No messages restored for bart
        assertThat(listMessageIdsForAccount(testSystem.bartAccessToken).size()).isEqualTo(1);
    }

    @Test
    default void jmapMovedMessageShouldNotEndUpInTheVault(TestSystem testSystem) {
        testSystem.bartSendMessageToHomer();
        testSystem.waitTillHomerHasAMessage();
        String messageId = listMessageIdsForAccount(testSystem.homerAccessToken).get(0);

        testSystem.homerMovesTheMailInAnotherMailbox(messageId);

        //Moved messages should not be moved to the vault
        testSystem.restoreAllMessagesOfHomer();
        testSystem.waitTillHomerHasAMessage();


        // No messages restored for bart
        assertThat(listMessageIdsForAccount(testSystem.bartAccessToken).size()).isEqualTo(1);
    }

    @Test
    default void restoreShouldNotImpactOtherUsers(TestSystem testSystem) {
        testSystem.bartSendMessageToHomer();
        testSystem.waitTillHomerHasAMessage();

        testSystem.homerDeletesMessages(listMessageIdsForAccount(testSystem.homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 0);

        testSystem.bartDeletesMessages(listMessageIdsForAccount(testSystem.bartAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.bartAccessToken).size() == 0);

        testSystem.restoreAllMessagesOfHomer();
        testSystem.waitTillHomerHasAMessage();

        // No messages restored for bart
        assertThat(listMessageIdsForAccount(testSystem.bartAccessToken).size()).isEqualTo(0);
    }

    @Test
    default void restoredMessagesShouldNotBeRemovedFromTheVault(TestSystem testSystem) {
        testSystem.bartSendMessageToHomer();
        testSystem.waitTillHomerHasAMessage();

        testSystem.homerDeletesMessages(listMessageIdsForAccount(testSystem.homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 0);

        testSystem.restoreAllMessagesOfHomer();
        testSystem.waitTillHomerHasAMessage();

        testSystem.restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 2);
    }

    @Test
    default void vaultEndpointShouldNotRestoreItemsWhenTheVaultIsEmpty(TestSystem testSystem) {
        testSystem.bartSendMessageToHomer();
        testSystem.waitTillHomerHasAMessage();

        testSystem.restoreAllMessagesOfHomer();
        awaitSearchUpToDate();

        // No additional had been restored as the vault is empty
        assertThat(listMessageIdsForAccount(testSystem.homerAccessToken).size())
            .isEqualTo(1);
    }

    @Test
    default void vaultEndpointShouldNotRestoreMessageForSharee(TestSystem testSystem) {
        testSystem.bartSendMessageToHomer();
        testSystem.waitTillHomerHasAMessage();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.bartAccessToken).size() == 1);

        String messageId = listMessageIdsForAccount(testSystem.homerAccessToken).get(0);
        testSystem.homerMovesTheMailInAnotherMailbox(messageId);

        testSystem.homerSharesHisMailboxWithBart();

        testSystem.bartDeletesMessages(ImmutableList.of(messageId));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 0);

        testSystem.restoreMessagesFor(BART);
        awaitSearchUpToDate();

        // No additional had been restored for Bart as the vault is empty
        assertThat(listMessageIdsForAccount(testSystem.bartAccessToken).size())
            .isEqualTo(1);
    }

    @Test
    default void vaultEndpointShouldRestoreMessageForSharer(TestSystem testSystem) {
        testSystem.bartSendMessageToHomer();
        testSystem.waitTillHomerHasAMessage();

        String messageId = listMessageIdsForAccount(testSystem.homerAccessToken).get(0);
        testSystem.homerMovesTheMailInAnotherMailbox(messageId);

        testSystem.homerSharesHisMailboxWithBart();

        testSystem.bartDeletesMessages(ImmutableList.of(messageId));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 0);

        testSystem.restoreAllMessagesOfHomer();
        testSystem.waitTillHomerHasAMessage();

        String newMessageId = listMessageIdsForAccount(testSystem.homerAccessToken).get(0);
        given()
            .header("Authorization", testSystem.homerAccessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + newMessageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
            .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(ARGUMENTS + ".list.subject", hasItem(SUBJECT));
    }

    @Tag(BasicFeature.TAG)
    @Test
    default void vaultExportShouldExportZipContainsVaultMessagesToShareeWhenJmapDeleteMessage(TestSystem testSystem) throws Exception {
        testSystem.bartSendMessageToHomer();
        testSystem.waitTillHomerHasAMessage();
        String messageIdOfHomer = listMessageIdsForAccount(testSystem.homerAccessToken).get(0);

        testSystem.homerDeletesMessages(listMessageIdsForAccount(testSystem.homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 0);

        String fileLocation = testSystem.exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, testSystem.bartAccessToken);

        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasEntriesSize(1)
                .allSatisfies(entry -> hasName(messageIdOfHomer + ".eml"));
        }
    }

    @Tag(BasicFeature.TAG)
    @Test
    default void vaultExportShouldExportZipContainsVaultMessagesToShareeWhenImapDeleteMessage(GuiceJamesServer jmapServer,
                                                                                              TestSystem testSystem,
                                                                                              IMAPMessageReader imapMessageReader) throws Exception {
        testSystem.bartSendMessageToHomer();
        testSystem.waitTillHomerHasAMessage();
        String messageIdOfHomer = listMessageIdsForAccount(testSystem.homerAccessToken).get(0);

        imapMessageReader.connect(LOCALHOST_IP, jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(HOMER, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .setFlagsForAllMessagesInMailbox("\\Deleted");
        imapMessageReader.expunge();

        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 0);

        String fileLocation = testSystem.exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, testSystem.bartAccessToken);

        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasEntriesSize(1)
                .allSatisfies(entry -> hasName(messageIdOfHomer + ".eml"));
        }
    }

    @Tag(BasicFeature.TAG)
    @Test
    default void vaultExportShouldExportZipContainsVaultMessagesToShareeWhenImapDeletedMailbox(GuiceJamesServer jmapServer,
                                                                                               TestSystem testSystem,
                                                                                               IMAPMessageReader imapMessageReader) throws Exception {
        testSystem.bartSendMessageToHomer();
        testSystem.waitTillHomerHasAMessage();
        String messageIdOfHomer = listMessageIdsForAccount(testSystem.homerAccessToken).get(0);

        imapMessageReader.connect(LOCALHOST_IP, jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(HOMER, PASSWORD)
            .select(IMAPMessageReader.INBOX);

        imapMessageReader.moveFirstMessage(MAILBOX_NAME);

        imapMessageReader.delete(MAILBOX_NAME);

        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 0);

        String fileLocation = testSystem.exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, testSystem.bartAccessToken);

        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasEntriesSize(1)
                .allSatisfies(entry -> hasName(messageIdOfHomer + ".eml"));
        }
    }

    @Test
    default void vaultExportShouldExportZipContainsOnlyMatchedMessages(TestSystem testSystem) throws Exception {
        testSystem.bartSendMessageToHomerWithSubject(FIRST_SUBJECT);
        testSystem.waitTillHomerHasAMessage();
        String firstMessageIdOfHomer = listMessageIdsForAccount(testSystem.homerAccessToken).get(0);

        testSystem.bartSendMessageToHomerWithSubject(SECOND_SUBJECT);
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 2);

        testSystem.homerDeletesMessages(listMessageIdsForAccount(testSystem.homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 0);

        ExportRequest exportRequest = ExportRequest
            .userExportFrom(HOMER)
            .exportTo(BART)
            .query("{" +
                "  \"fieldName\": \"subject\"," +
                "  \"operator\": \"equals\"," +
                "  \"value\": \"" + FIRST_SUBJECT + "\"" +
                "}");
        String fileLocation = testSystem.exportAndGetFileLocationFromLastMail(exportRequest, testSystem.bartAccessToken);

        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.containsOnlyEntriesMatching(hasName(firstMessageIdOfHomer + ".eml"));
        }
    }

    @Test
    default void vaultExportShouldExportEmptyZipWhenQueryDoesntMatch(TestSystem testSystem) throws Exception {
        testSystem.bartSendMessageToHomerWithSubject(FIRST_SUBJECT);
        testSystem.bartSendMessageToHomerWithSubject(SECOND_SUBJECT);
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 2);

        testSystem.homerDeletesMessages(listMessageIdsForAccount(testSystem.homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 0);

        ExportRequest exportRequest = ExportRequest
            .userExportFrom(HOMER)
            .exportTo(BART)
            .query("{" +
                "  \"fieldName\": \"subject\"," +
                "  \"operator\": \"equals\"," +
                "  \"value\": \"non matching\"" +
                "}");
        String fileLocation = testSystem.exportAndGetFileLocationFromLastMail(exportRequest, testSystem.bartAccessToken);

        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasNoEntry();
        }
    }

    @Test
    default void vaultExportShouldExportEmptyZipWhenVaultIsEmpty(TestSystem testSystem) throws Exception {
        String fileLocation = testSystem.exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, testSystem.bartAccessToken);

        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasNoEntry();
        }
    }

    @Test
    default void vaultExportShouldResponseIdempotentSideEffect(TestSystem testSystem) throws Exception {
        testSystem.bartSendMessageToHomer();
        testSystem.waitTillHomerHasAMessage();

        testSystem.homerDeletesMessages(listMessageIdsForAccount(testSystem.homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 0);

        String fileLocationFirstExport = testSystem.exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, testSystem.bartAccessToken);
        String fileLocationSecondExport = testSystem.exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, testSystem.bartAccessToken);

        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocationFirstExport))) {
            zipAssert.hasSameContentWith(new FileInputStream(fileLocationSecondExport));
        }
    }

    @Test
    default void vaultPurgeShouldMakeExportProduceEmptyZipWhenAllMessagesAreExpired(UpdatableTickingClock clock,
                                                                                    TestSystem testSystem) throws Exception {
        testSystem.bartSendMessageToHomer();
        testSystem.bartSendMessageToHomer();
        testSystem.bartSendMessageToHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 3);

        testSystem.homerDeletesMessages(listMessageIdsForAccount(testSystem.homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 0);

        clock.setInstant(TWO_MONTH_AFTER_ONE_YEAR_EXPIRATION.toInstant());
        purgeVault(testSystem.webAdminApi);

        String fileLocation = testSystem.exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, testSystem.bartAccessToken);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasNoEntry();
        }
    }

    @Test
    default void vaultPurgeShouldMakeExportProduceAZipWhenOneMessageIsNotExpired(UpdatableTickingClock clock,
                                                                                 TestSystem testSystem) throws Exception {
        testSystem.bartSendMessageToHomer();
        testSystem.waitTillHomerHasAMessage();

        testSystem.homerDeletesMessages(listMessageIdsForAccount(testSystem.homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 0);

        testSystem.bartSendMessageToHomer();
        testSystem.waitTillHomerHasAMessage();

        String messageIdOfNotExpiredMessage = listMessageIdsForAccount(testSystem.homerAccessToken).get(0);

        clock.setInstant(TWO_MONTH_AFTER_ONE_YEAR_EXPIRATION.toInstant());
        testSystem.homerDeletesMessages(listMessageIdsForAccount(testSystem.homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 0);

        purgeVault(testSystem.webAdminApi);

        String fileLocation = testSystem.exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, testSystem.bartAccessToken);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasEntriesSize(1)
                .allSatisfies(entry -> hasName(messageIdOfNotExpiredMessage + ".eml"));
        }
    }

    @Test
    default void vaultPurgeShouldMakeExportProduceZipWhenAllMessagesAreNotExpired(TestSystem testSystem) throws Exception {
        testSystem.bartSendMessageToHomer();
        testSystem.bartSendMessageToHomer();
        testSystem.bartSendMessageToHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 3);

        testSystem.homerDeletesMessages(listMessageIdsForAccount(testSystem.homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 0);

        purgeVault(testSystem.webAdminApi);

        String fileLocation = testSystem.exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, testSystem.bartAccessToken);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasEntriesSize(3);
        }
    }

    @Test
    default void vaultPurgeShouldNotAppendMessageToTheUserMailbox(UpdatableTickingClock clock,
                                                                  TestSystem testSystem) {
        testSystem.bartSendMessageToHomer();
        testSystem.waitTillHomerHasAMessage();

        testSystem.homerDeletesMessages(listMessageIdsForAccount(testSystem.homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 0);

        clock.setInstant(TWO_MONTH_AFTER_ONE_YEAR_EXPIRATION.toInstant());
        purgeVault(testSystem.webAdminApi);

        assertThat(listMessageIdsForAccount(testSystem.homerAccessToken))
            .hasSize(0);
    }

    @Test
    default void vaultDeleteShouldDeleteMessageThenExportWithNoEntry(TestSystem testSystem) throws Exception {
        testSystem.bartSendMessageToHomer();
        testSystem.waitTillHomerHasAMessage();

        String messageIdOfHomer = listMessageIdsForAccount(testSystem.homerAccessToken).get(0);

        testSystem.homerDeletesMessages(listMessageIdsForAccount(testSystem.homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 0);

        deleteFromVault(testSystem.webAdminApi, HOMER, messageIdOfHomer);

        String fileLocation = testSystem.exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, testSystem.bartAccessToken);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasNoEntry();
        }
    }

    @Test
    default void vaultDeleteShouldNotDeleteEmptyVaultThenExportNoEntry(TestSystem testSystem) throws Exception {
        testSystem.bartSendMessageToHomer();
        testSystem.waitTillHomerHasAMessage();

        String messageIdOfHomer = listMessageIdsForAccount(testSystem.homerAccessToken).get(0);

        deleteFromVault(testSystem.webAdminApi, HOMER, messageIdOfHomer);

        String fileLocation = testSystem.exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, testSystem.bartAccessToken);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasNoEntry();
        }
    }

    @Test
    default void vaultDeleteShouldNotDeleteNotMatchedMessageInVaultThenExportAnEntry(TestSystem testSystem) throws Exception {
        testSystem.bartSendMessageToHomer();
        testSystem.waitTillHomerHasAMessage();
        String messageIdOfHomer = listMessageIdsForAccount(testSystem.homerAccessToken).get(0);

        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.bartAccessToken).size() == 1);
        String messageIdOfBart = listMessageIdsForAccount(testSystem.bartAccessToken).get(0);

        testSystem.homerDeletesMessages(listMessageIdsForAccount(testSystem.homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 0);

        deleteFromVault(testSystem.webAdminApi, HOMER, messageIdOfBart);

        String fileLocation = testSystem.exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, testSystem.bartAccessToken);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasEntriesSize(1)
                .allSatisfies(entry -> hasName(messageIdOfHomer + ".eml"));
        }
    }

    @Test
    default void vaultDeleteShouldNotAppendMessageToTheUserMailbox(TestSystem testSystem) {
        testSystem.bartSendMessageToHomer();
        testSystem.waitTillHomerHasAMessage();

        String messageIdOfHomer = listMessageIdsForAccount(testSystem.homerAccessToken).get(0);

        testSystem.homerDeletesMessages(listMessageIdsForAccount(testSystem.homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 0);

        deleteFromVault(testSystem.webAdminApi, HOMER, messageIdOfHomer);

        assertThat(listMessageIdsForAccount(testSystem.homerAccessToken))
            .hasSize(0);
    }

    @Test
    default void vaultDeleteShouldDeleteAllMessagesHavingSameBlobContent(TestSystem testSystem) throws Exception {
        testSystem.bartSendMessageToHomerAndJack();
        testSystem.waitTillHomerHasAMessage();

        String homerInboxMessageId = listMessageIdsForAccount(testSystem.homerAccessToken).get(0);
        testSystem.homerDeletesMessages(ImmutableList.of(homerInboxMessageId));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 0);

        // the message same with homer's one in inbox
        String jackInboxMessageId = listMessageIdsForAccount(testSystem.jackAccessToken).get(0);
        testSystem.jackDeletesMessages(ImmutableList.of(jackInboxMessageId));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.jackAccessToken).size() == 0);

        // delete from homer's vault, expecting the message contains the same blob in jack's vault will be deleted
        deleteFromVault(testSystem.webAdminApi, HOMER, homerInboxMessageId);

        String fileLocationOfBartMessages = testSystem.exportAndGetFileLocationFromLastMail(EXPORT_ALL_JACK_MESSAGES_TO_HOMER, testSystem.homerAccessToken);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocationOfBartMessages))) {
            zipAssert.hasNoEntry();
        }
    }

    @Test
    default void vaultDeleteShouldNotDeleteAllMessagesHavingSameBlobContentWhenMessageNotDeletedWithinTheSameMonth(UpdatableTickingClock clock,
                                                                                                                   TestSystem testSystem) throws Exception {
        testSystem.bartSendMessageToHomerAndJack();
        testSystem.waitTillHomerHasAMessage();

        String homerInboxMessageId = listMessageIdsForAccount(testSystem.homerAccessToken).get(0);
        testSystem.homerDeletesMessages(ImmutableList.of(homerInboxMessageId));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.homerAccessToken).size() == 0);

        // one year later, delete jack's message
        clock.setInstant(NOW.plusYears(1).toInstant());
        // the message same with homer's one in inbox
        String jackInboxMessageId = listMessageIdsForAccount(testSystem.jackAccessToken).get(0);
        testSystem.jackDeletesMessages(ImmutableList.of(jackInboxMessageId));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(testSystem.jackAccessToken).size() == 0);

        // delete from homer's vault, expecting jack's vault still be intact
        deleteFromVault(testSystem.webAdminApi, HOMER, homerInboxMessageId);

        String fileLocationOfBartMessages = testSystem.exportAndGetFileLocationFromLastMail(EXPORT_ALL_JACK_MESSAGES_TO_HOMER, testSystem.homerAccessToken);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocationOfBartMessages))) {
            zipAssert.hasEntriesSize(1)
                .allSatisfies(entry -> hasName(jackInboxMessageId + ".eml"));
        }
    }
}
