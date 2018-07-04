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

package org.apache.james.utils.mountebank;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;

import java.nio.charset.StandardCharsets;

import org.apache.http.HttpStatus;
import org.apache.james.util.docker.Images;
import org.apache.james.util.docker.SwarmGenericContainer;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.jayway.awaitility.core.ConditionFactory;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.builder.ResponseSpecBuilder;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.specification.RequestSpecification;
import com.jayway.restassured.specification.ResponseSpecification;

public class MounteBankDocker implements TestRule {

    public static final int IMPOSER_SMTP_PORT = 25;

    private static final int MOUTEBANK_API_PORT = 2525;
    private static final ResponseSpecification RESPONSE_SPECIFICATION = new ResponseSpecBuilder().build();
    private final SwarmGenericContainer container;

    public MounteBankDocker() {
        container = new SwarmGenericContainer(Images.MOUNTE_BANK)
            .withAffinityToContainer()
            .waitingFor(new HostPortWaitStrategy());
    }

    @Override
    public Statement apply(Statement statement, Description description) {
        return container.apply(statement, description);
    }

    public void awaitStarted(ConditionFactory calmyAwait) {
        calmyAwait.until(() -> container.tryConnect(MOUTEBANK_API_PORT));
    }

    public SwarmGenericContainer getContainer() {
        return container;
    }

    public void createImposer(Contract contract) throws JsonProcessingException {
        String contractJson = ContractSerializer.write(contract);

        given(requestSpecification(contractJson), RESPONSE_SPECIFICATION)
            .post("/imposters")
        .then()
            .statusCode(HttpStatus.SC_CREATED);
    }

    private RequestSpecification requestSpecification(String requestBody) {
        return new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
            .setPort(MOUTEBANK_API_PORT)
            .setBaseUri("http://" + container.getContainerIp())
            .setBody(requestBody)
            .build();
    }
}
