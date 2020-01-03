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

package org.apache.james.webadmin.data.jmap;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static io.restassured.RestAssured.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.jmap.api.model.Preview;
import org.apache.james.jmap.api.projections.MessageFastViewPrecomputedProperties;
import org.apache.james.jmap.draft.utils.JsoupHtmlTextExtractor;
import org.apache.james.jmap.memory.projections.MemoryMessageFastViewProjection;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.task.TaskManager;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.util.html.HtmlTextExtractor;
import org.apache.james.util.mime.MessageContentExtractor;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import io.restassured.filter.log.LogDetail;
import spark.Service;

class RecomputeUserFastViewProjectionItemsRequestToTaskTest {
    private final class JMAPRoutes implements Routes {
        private final MessageFastViewProjectionCorrector corrector;
        private final TaskManager taskManager;
        private final UsersRepository usersRepository;

        private JMAPRoutes(MessageFastViewProjectionCorrector corrector, TaskManager taskManager, UsersRepository usersRepository) {
            this.corrector = corrector;
            this.taskManager = taskManager;
            this.usersRepository = usersRepository;
        }

        @Override
        public String getBasePath() {
            return BASE_PATH;
        }

        @Override
        public void define(Service service) {
            service.post(BASE_PATH,
                TaskFromRequestRegistry.builder()
                    .registrations(new RecomputeUserFastViewProjectionItemsRequestToTask(corrector, usersRepository))
                    .buildAsRoute(taskManager),
                new JsonTransformer());
        }
    }

    static final String BASE_PATH = "/:username/mailboxes";


    static final MessageFastViewPrecomputedProperties PROJECTION_ITEM = MessageFastViewPrecomputedProperties.builder()
        .preview(Preview.from("body"))
        .hasAttachment(false)
        .build();

    static final DomainList NO_DOMAIN_LIST = null;
    static final Username BOB = Username.of("bob");
    static final Username CEDRIC = Username.of("cedric");

    private WebAdminServer webAdminServer;
    private MemoryTaskManager taskManager;
    private MemoryMessageFastViewProjection messageFastViewProjection;
    private InMemoryMailboxManager mailboxManager;
    private MemoryUsersRepository usersRepository;

    @BeforeEach
    void setUp() {
        JsonTransformer jsonTransformer = new JsonTransformer();
        taskManager = new MemoryTaskManager(new Hostname("foo"));

        messageFastViewProjection = new MemoryMessageFastViewProjection(new RecordingMetricFactory());
        mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        usersRepository = MemoryUsersRepository.withoutVirtualHosting(NO_DOMAIN_LIST);
        MessageContentExtractor messageContentExtractor = new MessageContentExtractor();
        HtmlTextExtractor htmlTextExtractor = new JsoupHtmlTextExtractor();
        Preview.Factory previewFactory = new Preview.Factory(messageContentExtractor, htmlTextExtractor);
        MessageFastViewPrecomputedProperties.Factory projectionItemFactory = new MessageFastViewPrecomputedProperties.Factory(previewFactory);
        webAdminServer = WebAdminUtils.createWebAdminServer(
            new TasksRoutes(taskManager, jsonTransformer),
            new JMAPRoutes(
                new MessageFastViewProjectionCorrector(usersRepository, mailboxManager, messageFastViewProjection, projectionItemFactory),
                taskManager, usersRepository))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath("/bob/mailboxes")
            .log(LogDetail.URI)
            .build();
    }

    @AfterEach
    void afterEach() {
        webAdminServer.destroy();
        taskManager.stop();
    }

    @Test
    void actionRequestParameterShouldBeCompulsory() {
        when()
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("'action' query parameter is compulsory. Supported values are [recomputeFastViewProjectionItems]"));
    }

    @Test
    void postShouldFailUponEmptyAction() {
        given()
            .queryParam("action", "")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("'action' query parameter cannot be empty or blank. Supported values are [recomputeFastViewProjectionItems]"));
    }

    @Test
    void postShouldFailUponInvalidAction() {
        given()
            .queryParam("action", "invalid")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("Invalid value supplied for query parameter 'action': invalid. Supported values are [recomputeFastViewProjectionItems]"));
    }

    @Test
    void postShouldFailUponBadUsername() {
        given()
            .basePath("/bad@bad@bad/mailboxes")
            .queryParam("action", "recomputeFastViewProjectionItems")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("The username should not contain multiple domain delimiter."));
    }

    @Test
    void recomputeUserShouldFailUponUnknownUser() {
        given()
            .basePath("/notFound/mailboxes")
            .queryParam("action", "recomputeFastViewProjectionItems")
            .post()
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .body("statusCode", is(404))
            .body("type", is(ErrorResponder.ErrorType.NOT_FOUND.getType()))
            .body("message", is("User 'notfound' does not exists"));
    }

    @Test
    void postShouldCreateANewTask() throws Exception {
        usersRepository.addUser(BOB, "pass");

        given()
            .queryParam("action", "recomputeFastViewProjectionItems")
            .post()
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue());
    }

    @Test
    void recomputeUserShouldCompleteWhenUserWithNoMailbox() throws Exception {
        usersRepository.addUser(BOB, "pass");

        String taskId = with()
            .queryParam("action", "recomputeFastViewProjectionItems")
            .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("type", is("RecomputeUserFastViewProjectionItemsTask"))
            .body("additionalInformation.username", is(BOB.asString()))
            .body("additionalInformation.processedMessageCount", is(0))
            .body("additionalInformation.failedMessageCount", is(0))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    void recomputeUserShouldCompleteWhenUserWithNoMessage() throws Exception {
        usersRepository.addUser(BOB, "pass");
        mailboxManager.createMailbox(MailboxPath.inbox(BOB), mailboxManager.createSystemSession(BOB));

        String taskId = with()
            .queryParam("action", "recomputeFastViewProjectionItems")
            .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("type", is("RecomputeUserFastViewProjectionItemsTask"))
            .body("additionalInformation.username", is(BOB.asString()))
            .body("additionalInformation.processedMessageCount", is(0))
            .body("additionalInformation.failedMessageCount", is(0))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    void recomputeUserShouldCompleteWhenOneMessage() throws Exception {
        usersRepository.addUser(BOB, "pass");
        MailboxSession session = mailboxManager.createSystemSession(BOB);
        Optional<MailboxId> mailboxId = mailboxManager.createMailbox(MailboxPath.inbox(BOB), session);
        mailboxManager.getMailbox(mailboxId.get(), session).appendMessage(
            MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
            session);

        String taskId = with()
            .queryParam("action", "recomputeFastViewProjectionItems")
            .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("type", is("RecomputeUserFastViewProjectionItemsTask"))
            .body("additionalInformation.username", is(BOB.asString()))
            .body("additionalInformation.processedMessageCount", is(1))
            .body("additionalInformation.failedMessageCount", is(0))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    void recomputeUserShouldBeUserBound() throws Exception {
        usersRepository.addUser(BOB, "pass");
        MailboxSession bobSession = mailboxManager.createSystemSession(BOB);
        Optional<MailboxId> mailboxIdBob = mailboxManager.createMailbox(MailboxPath.inbox(BOB), bobSession);
        mailboxManager.getMailbox(mailboxIdBob.get(), bobSession).appendMessage(
            MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
            bobSession);

        usersRepository.addUser(CEDRIC, "pass");
        MailboxSession cedricSession = mailboxManager.createSystemSession(CEDRIC);
        Optional<MailboxId> mailboxIdCedric = mailboxManager.createMailbox(MailboxPath.inbox(CEDRIC), cedricSession);
        mailboxManager.getMailbox(mailboxIdCedric.get(), cedricSession).appendMessage(
            MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
            cedricSession);

        String taskId = with()
            .queryParam("action", "recomputeFastViewProjectionItems")
        .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("type", is("RecomputeUserFastViewProjectionItemsTask"))
            .body("additionalInformation.username", is(BOB.asString()))
            .body("additionalInformation.processedMessageCount", is(1))
            .body("additionalInformation.failedMessageCount", is(0))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    void recomputeUserShouldUpdateProjection() throws Exception {
        usersRepository.addUser(BOB, "pass");
        MailboxSession session = mailboxManager.createSystemSession(BOB);
        Optional<MailboxId> mailboxId = mailboxManager.createMailbox(MailboxPath.inbox(BOB), session);
        ComposedMessageId messageId = mailboxManager.getMailbox(mailboxId.get(), session).appendMessage(
            MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
            session);

        String taskId = with()
            .queryParam("action", "recomputeFastViewProjectionItems")
            .post()
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(messageFastViewProjection.retrieve(messageId.getMessageId()).block())
            .isEqualTo(PROJECTION_ITEM);
    }

    @Test
    void recomputeUserShouldBeIdempotent() throws Exception {
        usersRepository.addUser(BOB, "pass");
        MailboxSession session = mailboxManager.createSystemSession(BOB);
        Optional<MailboxId> mailboxId = mailboxManager.createMailbox(MailboxPath.inbox(BOB), session);
        ComposedMessageId messageId = mailboxManager.getMailbox(mailboxId.get(), session).appendMessage(
            MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
            session);

        String taskId1 = with()
            .queryParam("action", "recomputeFastViewProjectionItems")
            .post()
            .jsonPath()
            .get("taskId");
        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId1 + "/await");

        String taskId2 = with()
            .queryParam("action", "recomputeFastViewProjectionItems")
            .post()
            .jsonPath()
            .get("taskId");
        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId2 + "/await");

        assertThat(messageFastViewProjection.retrieve(messageId.getMessageId()).block())
            .isEqualTo(PROJECTION_ITEM);
    }
}