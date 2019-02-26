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

package org.apache.james.webadmin.vault.routes;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE_2;
import static org.apache.james.vault.DeletedMessageFixture.USER;
import static org.apache.james.vault.DeletedMessageFixture.USER_2;
import static org.apache.james.webadmin.WebAdminServer.NO_CONFIGURATION;
import static org.apache.james.webadmin.vault.routes.DeletedMessageVaultRestoreTask.RESTORE_MAILBOX_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import java.io.InputStream;

import org.apache.james.core.User;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.vault.memory.MemoryDeletedMessagesVault;
import org.apache.james.vault.Query;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import io.restassured.filter.log.LogDetail;
import reactor.core.publisher.Flux;

class DeletedMessagesVaultRoutesTest {

    private WebAdminServer webAdminServer;
    private MemoryDeletedMessagesVault vault;
    private InMemoryMailboxManager mailboxManager;

    @BeforeEach
    void beforeEach() throws Exception {
        vault = spy(new MemoryDeletedMessagesVault());
        InMemoryIntegrationResources inMemoryIntegrationResources = new InMemoryIntegrationResources();
        InMemoryIntegrationResources.Resources inMemoryResource = inMemoryIntegrationResources.createResources(new SimpleGroupMembershipResolver());
        mailboxManager = spy(inMemoryResource.getMailboxManager());

        MemoryTaskManager taskManager = new MemoryTaskManager();
        JsonTransformer jsonTransformer = new JsonTransformer();

        webAdminServer = WebAdminUtils.createWebAdminServer(
            new DefaultMetricFactory(),
            new TasksRoutes(taskManager, jsonTransformer),
            new DeletedMessagesVaultRoutes(vault, mailboxManager, jsonTransformer, taskManager));

        webAdminServer.configure(NO_CONFIGURATION);
        webAdminServer.await();
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(DeletedMessagesVaultRoutes.ROOT_PATH)
            .log(LogDetail.METHOD)
            .build();
    }

    @AfterEach
    void afterEach() {
        webAdminServer.destroy();
    }

    @Test
    void restoreShouldReturnInvalidWhenUserIsInvalid() {
        when()
            .post("not@valid@user.com")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("The username should not contain multiple domain delimiter."));
    }

    @Test
    void postShouldReturnNotFoundWhenNoUserPathParameter() {
        when()
            .post()
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .body("statusCode", is(404))
            .body("type", is(ErrorResponder.ErrorType.NOT_FOUND.getType()))
            .body("message", is("POST /deletedMessages/user can not be found"));
    }

    @Test
    void restoreShouldReturnATaskCreated() {
        when()
            .post(USER.asString())
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue());
    }

    @Test
    void restoreShouldProduceAnSuccessfulTaskWithAdditionalInformation() {
        vault.append(USER, DELETED_MESSAGE).block();
        vault.append(USER, DELETED_MESSAGE_2).block();

        String taskId = given()
            .when()
                .post(USER.asString())
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("type", is(DeletedMessageVaultRestoreTask.TYPE))
            .body("additionalInformation.successfulRestoreCount", is(2))
            .body("additionalInformation.errorRestoreCount", is(0))
            .body("additionalInformation.failRemovingCount", is(0))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    void restoreShouldProduceFailedTaskWhenTheVaultGetsError() {
        vault.append(USER, DELETED_MESSAGE).block();
        vault.append(USER, DELETED_MESSAGE_2).block();

        doThrow(new RuntimeException("mock exception"))
            .when(vault)
            .search(any(), any());

        String taskId = given()
            .when()
                .post(USER.asString())
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("failed"))
            .body("taskId", is(taskId))
            .body("type", is(DeletedMessageVaultRestoreTask.TYPE))
            .body("additionalInformation.successfulRestoreCount", is(0))
            .body("additionalInformation.errorRestoreCount", is(0))
            .body("additionalInformation.failRemovingCount", is(0))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()));
    }

    @Test
    void restoreShouldProduceFailedTaskWithFailsRemovingCountWhenTheVaultDeleteGetsError() {
        vault.append(USER, DELETED_MESSAGE).block();
        vault.append(USER, DELETED_MESSAGE_2).block();

        doThrow(new RuntimeException("mock exception"))
            .when(vault)
            .delete(any(), any());

        String taskId = given()
            .when()
                .post(USER.asString())
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("failed"))
            .body("taskId", is(taskId))
            .body("type", is(DeletedMessageVaultRestoreTask.TYPE))
            .body("additionalInformation.successfulRestoreCount", is(2))
            .body("additionalInformation.errorRestoreCount", is(0))
            .body("additionalInformation.failRemovingCount", is(2))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()));
    }

    @Test
    void restoreShouldProduceFailedTaskWhenMailboxMangerGetsError() throws Exception {
        vault.append(USER, DELETED_MESSAGE).block();
        vault.append(USER, DELETED_MESSAGE_2).block();

        doThrow(new RuntimeException("mock exception"))
            .when(mailboxManager)
            .createMailbox(any(MailboxPath.class), any(MailboxSession.class));

        String taskId = given()
            .when()
                .post(USER.asString())
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("failed"))
            .body("taskId", is(taskId))
            .body("type", is(DeletedMessageVaultRestoreTask.TYPE))
            .body("additionalInformation.successfulRestoreCount", is(0))
            .body("additionalInformation.errorRestoreCount", is(0))
            .body("additionalInformation.failRemovingCount", is(0))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()));
    }

    @Test
    void restoreShouldRemoveAllMessageInTheVaultOfCorrespondingUser() {
        vault.append(USER, DELETED_MESSAGE).block();
        vault.append(USER, DELETED_MESSAGE_2).block();

        String taskId = given()
            .when()
                .post(USER.asString())
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
            .then()
            .body("status", is("completed"));

        assertThat(Flux.from(vault.search(USER, Query.ALL)).toStream())
            .isEmpty();
    }

    @Test
    void restoreShouldNotDeleteExistingMessagesInTheUserMailbox() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(USER.asString());
        MailboxPath restoreMailboxPath = MailboxPath.forUser(USER.asString(), RESTORE_MAILBOX_NAME);
        mailboxManager.createMailbox(restoreMailboxPath, session);
        MessageManager messageManager = mailboxManager.getMailbox(restoreMailboxPath, session);
        messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(DELETED_MESSAGE.getContent().get()),
            session);

        vault.append(USER, DELETED_MESSAGE).block();
        vault.append(USER, DELETED_MESSAGE_2).block();

        String taskId = given()
            .when()
                .post(USER.asString())
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
            .then()
            .body("status", is("completed"));

        assertThat(messageManager.getMessages(MessageRange.all(), FetchGroupImpl.MINIMAL, session))
            .hasSize(3);
    }

    @Test
    void restoreShouldMoveAllMessageFromVaultToRestoreMailboxOfCorrespondingUser() throws Exception {
        vault.append(USER, DELETED_MESSAGE).block();
        vault.append(USER, DELETED_MESSAGE_2).block();

        String taskId = given()
            .when()
                .post(USER.asString())
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"));

        MailboxSession session = mailboxManager.createSystemSession(USER.asString());
        MessageManager messageManager = mailboxManager.getMailbox(MailboxPath.forUser(USER.asString(), RESTORE_MAILBOX_NAME), session);
        messageManager.getMessages(MessageRange.all(), FetchGroupImpl.MINIMAL, session);

        assertThat(messageManager.getMessages(MessageRange.all(), FetchGroupImpl.MINIMAL, session))
            .hasSize(2)
            .anySatisfy(messageResult -> assertThat(fullContent(messageResult)).hasSameContentAs(DELETED_MESSAGE.getContent().get()))
            .anySatisfy(messageResult -> assertThat(fullContent(messageResult)).hasSameContentAs(DELETED_MESSAGE_2.getContent().get()));
    }

    @Test
    void restoreShouldNotMoveMessagesToAnOtherUserMailbox() throws Exception {
        vault.append(USER, DELETED_MESSAGE).block();
        vault.append(USER, DELETED_MESSAGE_2).block();

        String taskId = given()
            .when()
                .post(USER.asString())
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"));

        assertThat(hasAnyMail(USER_2))
            .isFalse();
    }

    @Test
    void restoreShouldNotImpactToOtherVault() {
        vault.append(USER, DELETED_MESSAGE).block();
        vault.append(USER, DELETED_MESSAGE_2).block();
        vault.append(USER_2, DELETED_MESSAGE).block();

        String taskId = given()
            .when()
                .post(USER.asString())
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"));

        assertThat(Flux.from(vault.search(USER_2, Query.ALL)).toStream())
            .containsOnly(DELETED_MESSAGE);
    }

    private boolean hasAnyMail(User user) throws MailboxException {
        MailboxSession session = mailboxManager.createSystemSession(user.asString());
        int limitToOneMessage = 1;

        return !mailboxManager.search(MultimailboxesSearchQuery.from(new SearchQuery()).build(), session, limitToOneMessage)
            .isEmpty();
    }

    private InputStream fullContent(MessageResult messageResult) {
        try {
            return messageResult.getFullContent().getInputStream();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}