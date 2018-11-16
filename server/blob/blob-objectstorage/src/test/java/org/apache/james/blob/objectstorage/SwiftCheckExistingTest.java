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

package org.apache.james.blob.objectstorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.testcontainers.containers.wait.strategy.Wait.forHttp;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;

import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.io.payloads.ByteArrayPayload;
import org.jclouds.openstack.keystone.config.KeystoneProperties;
import org.jclouds.openstack.swift.v1.blobstore.RegionScopedBlobStoreContext;
import org.jclouds.openstack.swift.v1.reference.TempAuthHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;

import com.github.fge.lambdas.Throwing;

class SwiftCheckExistingTest {

    static class SwiftContainer {
        private static final String SWIFT_DOCKER_IMAGE = "jeantil/openstack-keystone-swift:pike";
        private static final int SWIFT_PORT = 8080;
        private static final int KEYSTONE_ADMIN_PORT = 35357;
        private final GenericContainer<?> swiftContainer;

        public SwiftContainer() {
            this.swiftContainer = new GenericContainer<>(SWIFT_DOCKER_IMAGE);
            this.swiftContainer
                .withExposedPorts(KEYSTONE_ADMIN_PORT)
                .withExposedPorts(SWIFT_PORT)
                .waitingFor(
                    new WaitAllStrategy()
                        .withStrategy(
                            forHttp("/v3")
                                .forPort(KEYSTONE_ADMIN_PORT)
                                .forStatusCode(200)
                                .withStartupTimeout(Duration.ofMinutes(10))
                        ).withStrategy(
                            forHttp("/info")
                                .forPort(SWIFT_PORT)
                                .forStatusCode(200)
                                .withStartupTimeout(Duration.ofMinutes(10))));

        }

        public void start() {
            swiftContainer.start();
            Integer swiftPort = swiftContainer.getMappedPort(SWIFT_PORT);
            String containerIpAddress = swiftContainer.getContainerIpAddress();
            Container.ExecResult execResult =
                Throwing.supplier(() ->
                    swiftContainer.execInContainer(
                        "/swift/bin/register-swift-endpoint.sh",
                        "http://" + containerIpAddress + ":" + swiftPort))
                    .sneakyThrow()
                    .get();
        }

        String getAuth10Host() {
            return String.format("%s:%d", swiftContainer.getContainerIpAddress(),
                swiftContainer.getMappedPort(SWIFT_PORT));
        }
    }

    private static final String BLOB_CONTENT = "content";
    @RegisterExtension
    static SwiftContainer swiftContainer = new SwiftContainer();

    @BeforeAll
    static void setupAll() {
        swiftContainer.start();
    }

    private RegionScopedBlobStoreContext blobStoreContext;
    private BlobStore blobStore;
    private String container;

    @BeforeEach
    void setUp() {
        Properties properties = new Properties();
        properties.setProperty(KeystoneProperties.CREDENTIAL_TYPE, "tempAuthCredentials");
        properties.setProperty(TempAuthHeaders.TEMP_AUTH_HEADER_USER, "X-Storage-User");
        properties.setProperty(TempAuthHeaders.TEMP_AUTH_HEADER_PASS, "X-Storage-Pass");

        blobStoreContext = ContextBuilder.newBuilder("openstack-swift")
            .endpoint("http://" + swiftContainer.getAuth10Host() + "/auth/v1.0")
            .credentials("test:tester", "testing")
            .overrides(properties)
            .buildView(RegionScopedBlobStoreContext.class);
        container = "container";
        blobStore = blobStoreContext.getBlobStore();
    }

    @AfterEach
    void tearDown() {
        blobStore.deleteContainer(container);
        blobStoreContext.close();
    }

    @Test
    void getBlobIsGonnaSuccessWhenContainerExistsIsNotInvoked() throws Exception {
        assertThat(blobStore.createContainerInLocation(null, container))
            .isTrue();

        String blobId = putBlob();

        InputStream blobStream = blobStore.getBlob(container, blobId)
            .getPayload()
            .openStream();
        assertThat(blobStream)
            .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void getBlobIsGonnaFailWhenContainerExistsIsInvoked() {
        assertThat(blobStore.containerExists(container))
            .isFalse();
        assertThat(blobStore.createContainerInLocation(null, container))
            .isTrue();

        String blobId = putBlob();

        assertThatThrownBy(() -> blobStore.getBlob(container, blobId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Optional.get() cannot be called on an absent value");
    }

    private String putBlob() {
        String blobId = UUID.randomUUID().toString();
        Blob blob = blobStore.blobBuilder(blobId)
            .payload(new ByteArrayPayload(BLOB_CONTENT.getBytes(StandardCharsets.UTF_8)))
            .build();
        blobStore.putBlob(container, blob);
        return blobId;
    }
}

