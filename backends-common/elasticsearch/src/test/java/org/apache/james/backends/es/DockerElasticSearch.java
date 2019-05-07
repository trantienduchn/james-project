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

package org.apache.james.backends.es;

import static io.restassured.RestAssured.given;

import java.util.Optional;

import org.apache.http.HttpStatus;
import org.apache.james.util.Host;
import org.apache.james.util.docker.DockerGenericContainer;
import org.apache.james.util.docker.Images;
import org.apache.james.util.docker.RateLimiters;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;

import com.google.common.collect.ImmutableList;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

public class DockerElasticSearch {

    private static final int ES_HTTP_PORT = 9200;
    private static final int ES_TCP_PORT = 9300;

    private final DockerGenericContainer eSContainer;

    public DockerElasticSearch() {
        this.eSContainer = new DockerGenericContainer(Images.ELASTICSEARCH_2)
            .withExposedPorts(ES_HTTP_PORT, ES_TCP_PORT)
            .waitingFor(new HostPortWaitStrategy().withRateLimiter(RateLimiters.TWENTIES_PER_SECOND));
    }

    public void start() {
        if (!eSContainer.isRunning()) {
            eSContainer.start();
        }
    }

    public void stop() {
        eSContainer.stop();
    }

    public int getHttpPort() {
        return eSContainer.getMappedPort(ES_HTTP_PORT);
    }

    public int getTcpPort() {
        return eSContainer.getMappedPort(ES_TCP_PORT);
    }

    public String getIp() {
        return eSContainer.getHostIp();
    }

    public Host getTcpHost() {
        return Host.from(getIp(), getTcpPort());
    }

    public Host getHttpHost() {
        return Host.from(getIp(), getHttpPort());
    }

    public void pause() {
        eSContainer.pause();
    }

    public void unpause() {
        eSContainer.unpause();
    }

    public void cleanUpData() {
        given(esAPI()).when()
            .delete("_all")
        .then()
            .statusCode(HttpStatus.SC_OK);
    }

    public void awaitForElasticSearch() {
        given(esAPI()).when()
            .post("_flush")
        .then()
            .statusCode(HttpStatus.SC_OK);
    }

    public ClientProvider clientProvider() {
        Optional<String> noClusterName = Optional.empty();
        return ClientProviderImpl.fromHosts(ImmutableList.of(getTcpHost()), noClusterName);
    }

    private RequestSpecification esAPI() {
        return new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setBasePath("/")
            .setPort(getHttpPort())
            .build();
    }
}
