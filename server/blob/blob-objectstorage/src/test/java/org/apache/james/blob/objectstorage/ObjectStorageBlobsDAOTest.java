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
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.MetricableBlobStore;
import org.apache.james.blob.api.MetricableBlobStoreContract;
import org.apache.james.blob.objectstorage.crypto.CryptoConfig;
import org.apache.james.blob.objectstorage.swift.Credentials;
import org.apache.james.blob.objectstorage.swift.Identity;
import org.apache.james.blob.objectstorage.swift.PassHeaderName;
import org.apache.james.blob.objectstorage.swift.SwiftTempAuthObjectStorage;
import org.apache.james.blob.objectstorage.swift.TenantName;
import org.apache.james.blob.objectstorage.swift.UserHeaderName;
import org.apache.james.blob.objectstorage.swift.UserName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@ExtendWith(DockerSwiftExtension.class)
public class ObjectStorageBlobsDAOTest implements MetricableBlobStoreContract {
    private static final String BIG_STRING = Strings.repeat("big blob content", 10 * 1024);
    private static final TenantName TENANT_NAME = TenantName.of("test");
    private static final UserName USER_NAME = UserName.of("tester");
    private static final Credentials PASSWORD = Credentials.of("testing");
    private static final Identity SWIFT_IDENTITY = Identity.of(TENANT_NAME, USER_NAME);
    private static final String SAMPLE_SALT = "c603a7327ee3dcbc031d8d34b1096c605feca5e1";
    private static final CryptoConfig CRYPTO_CONFIG = CryptoConfig.builder()
        .salt(SAMPLE_SALT)
        .password(PASSWORD.value().toCharArray())
        .build();

    private BucketName bucketName;
    private org.jclouds.blobstore.BlobStore blobStore;
    private SwiftTempAuthObjectStorage.Configuration testConfig;
    private ObjectStorageBlobsDAO objectStorageBlobsDAO;
    private BlobStore testee;

    @BeforeEach
    void setUp(DockerSwift dockerSwift) {
        bucketName = BucketName.DEFAULT;
        testConfig = SwiftTempAuthObjectStorage.configBuilder()
            .endpoint(dockerSwift.swiftEndpoint())
            .identity(SWIFT_IDENTITY)
            .credentials(PASSWORD)
            .tempAuthHeaderUserName(UserHeaderName.of("X-Storage-User"))
            .tempAuthHeaderPassName(PassHeaderName.of("X-Storage-Pass"))
            .build();
        BlobId.Factory blobIdFactory = blobIdFactory();
        ObjectStorageBlobsDAOBuilder.ReadyToBuild daoBuilder = ObjectStorageBlobsDAO
            .builder(testConfig)
            .defaultBucketName(bucketName)
            .blobIdFactory(blobIdFactory);
        blobStore = daoBuilder.getSupplier().get();
        objectStorageBlobsDAO = daoBuilder.build();
        objectStorageBlobsDAO.createContainer(bucketName).block();
        testee = new MetricableBlobStore(metricsTestExtension.getMetricFactory(), objectStorageBlobsDAO);
    }

    @AfterEach
    void tearDown() {
        blobStore.deleteContainer(bucketName.asString());
        blobStore.getContext().close();
    }

    @Override
    public BlobStore testee() {
        return testee;
    }

    @Override
    public BlobId.Factory blobIdFactory() {
        return new HashBlobId.Factory();
    }

    @Test
    void createContainerShouldMakeTheContainerToExist() {
        BucketName bucketName = BucketName.of(UUID.randomUUID().toString());
        objectStorageBlobsDAO.createContainer(bucketName).block();
        assertThat(blobStore.containerExists(bucketName.asString())).isTrue();
    }

    @Test
    void createContainerShouldNotFailWithRuntimeExceptionWhenCreateContainerTwice() {
        BucketName bucketName = BucketName.of(UUID.randomUUID().toString());

        objectStorageBlobsDAO.createContainer(bucketName).block();
        assertThatCode(() -> objectStorageBlobsDAO.createContainer(bucketName).block())
            .doesNotThrowAnyException();
    }

    @Test
    void supportsEncryptionWithCustomPayloadCodec() throws IOException {
        ObjectStorageBlobsDAO encryptedDao = ObjectStorageBlobsDAO
            .builder(testConfig)
            .defaultBucketName(bucketName)
            .blobIdFactory(blobIdFactory())
            .payloadCodec(new AESPayloadCodec(CRYPTO_CONFIG))
            .build();
        String content = "James is the best!";
        BlobId blobId = encryptedDao.save(BucketName.DEFAULT, content).block();

        InputStream read = encryptedDao.read(BucketName.DEFAULT, blobId);
        String expectedContent = IOUtils.toString(read, Charsets.UTF_8);
        assertThat(content).isEqualTo(expectedContent);
    }

    @Test
    void encryptionWithCustomPayloadCodeCannotBeReadFromUnencryptedDAO() throws Exception {
        ObjectStorageBlobsDAO encryptedDao = ObjectStorageBlobsDAO
            .builder(testConfig)
            .defaultBucketName(bucketName)
            .blobIdFactory(blobIdFactory())
            .payloadCodec(new AESPayloadCodec(CRYPTO_CONFIG))
            .build();
        String content = "James is the best!";
        BlobId blobId = encryptedDao.save(BucketName.DEFAULT, content).block();

        InputStream encryptedIs = testee.read(BucketName.DEFAULT, blobId);
        assertThat(encryptedIs).isNotNull();
        String encryptedString = IOUtils.toString(encryptedIs, Charsets.UTF_8);
        assertThat(encryptedString).isNotEqualTo(content);

        InputStream clearTextIs = encryptedDao.read(BucketName.DEFAULT, blobId);
        String expectedContent = IOUtils.toString(clearTextIs, Charsets.UTF_8);
        assertThat(content).isEqualTo(expectedContent);
    }

    @Test
    void deleteContainerShouldDeleteSwiftContainer() {
        objectStorageBlobsDAO.deleteContainer();
        assertThat(blobStore.containerExists(bucketName.asString()))
            .isFalse();
    }

    @Test
    void saveBytesShouldNotCompleteWhenDoesNotAwait() {
        // String need to be big enough to get async thread busy hence could not return result instantly
        Mono<BlobId> blobIdFuture = testee
            .save(BucketName.DEFAULT, BIG_STRING.getBytes(StandardCharsets.UTF_8))
            .subscribeOn(Schedulers.elastic());
        assertThat(blobIdFuture.toFuture()).isNotCompleted();
    }

    @Test
    void saveStringShouldNotCompleteWhenDoesNotAwait() {
        Mono<BlobId> blobIdFuture = testee
            .save(BucketName.DEFAULT, BIG_STRING)
            .subscribeOn(Schedulers.elastic());
        assertThat(blobIdFuture.toFuture()).isNotCompleted();
    }

    @Test
    void saveInputStreamShouldNotCompleteWhenDoesNotAwait() {
        Mono<BlobId> blobIdFuture = testee
            .save(BucketName.DEFAULT, new ByteArrayInputStream(BIG_STRING.getBytes(StandardCharsets.UTF_8)))
            .subscribeOn(Schedulers.elastic());
        assertThat(blobIdFuture.toFuture()).isNotCompleted();
    }

    @Test
    void readBytesShouldNotCompleteWhenDoesNotAwait() {
        BlobId blobId = testee().save(BucketName.DEFAULT, BIG_STRING).block();
        Mono<byte[]> resultFuture = testee.readBytes(BucketName.DEFAULT, blobId).subscribeOn(Schedulers.elastic());
        assertThat(resultFuture.toFuture()).isNotCompleted();
    }
}

