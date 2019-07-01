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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.TestBlobId;
import org.apache.james.blob.objectstorage.aws.AwsS3AuthConfiguration;
import org.apache.james.blob.objectstorage.aws.AwsS3ObjectStorage;
import org.apache.james.blob.objectstorage.aws.DockerAwsS3Container;
import org.apache.james.blob.objectstorage.aws.DockerAwsS3Extension;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DockerAwsS3Extension.class)
public class ObjectStorageBlobsDAOAWSTest {

    private BucketName defaultBucketName;
    private org.jclouds.blobstore.BlobStore blobStore;
    private ObjectStorageBlobsDAO objectStorageBlobsDAO;
    private AwsS3ObjectStorage awsS3ObjectStorage;
    private AwsS3AuthConfiguration configuration;

    @BeforeEach
    void setUp(DockerAwsS3Container dockerAwsS3) {
        awsS3ObjectStorage = new AwsS3ObjectStorage();
        defaultBucketName = BucketName.of(UUID.randomUUID().toString());
        configuration = AwsS3AuthConfiguration.builder()
            .endpoint(dockerAwsS3.getEndpoint())
            .accessKeyId(DockerAwsS3Container.ACCESS_KEY_ID)
            .secretKey(DockerAwsS3Container.SECRET_ACCESS_KEY)
            .build();

        ObjectStorageBlobsDAOBuilder.ReadyToBuild builder = ObjectStorageBlobsDAO
            .builder(configuration)
            .defaultBucketName(defaultBucketName)
            .blobIdFactory(new TestBlobId.Factory())
            .putBlob(awsS3ObjectStorage.putBlob(configuration));

        blobStore = builder.getSupplier().get();
        objectStorageBlobsDAO = builder.build();
        objectStorageBlobsDAO.createBucket(defaultBucketName).block();
    }

    @AfterEach
    void tearDown() {
        blobStore.deleteContainer(defaultBucketName.asString());
        blobStore.getContext().close();
        awsS3ObjectStorage.tearDown();
    }

    @Test
    void createBucketShouldCreateBucket() throws Exception {
        BucketName bucketName = BucketName.of("bucket");

        objectStorageBlobsDAO.createBucket(bucketName).block();

        assertThat(blobStore.containerExists(bucketName.asString()))
            .isTrue();
    }

    @Test
    void createBucketMultipleTimesShouldNotThrow() throws Exception {
        BucketName bucketName = BucketName.of("bucket");

        assertThatCode(() -> {
                objectStorageBlobsDAO.createBucket(bucketName).block();
                objectStorageBlobsDAO.createBucket(bucketName).block();
                objectStorageBlobsDAO.createBucket(bucketName).block();
            })
            .doesNotThrowAnyException();
    }

    @Test
    void deleteBucketShouldDeleteBucket() throws Exception {
        BucketName bucketName = BucketName.of("bucket");
        objectStorageBlobsDAO.createBucket(bucketName).block();

        objectStorageBlobsDAO.deleteBucket(bucketName).block();

        assertThat(blobStore.containerExists(bucketName.asString()))
            .isFalse();
    }

    @Test
    void deleteBucketMultipleTimeShouldNotThrow() throws Exception {
        BucketName bucketName = BucketName.of("bucket");
        objectStorageBlobsDAO.createBucket(bucketName).block();

        assertThatCode(() -> {
                objectStorageBlobsDAO.deleteBucket(bucketName).block();
                objectStorageBlobsDAO.deleteBucket(bucketName).block();
                objectStorageBlobsDAO.deleteBucket(bucketName).block();
            })
            .doesNotThrowAnyException();
    }


    @Test
    void concurrentCreateBucketCannotResilientCreate() throws Exception {
        BucketName bucketName = BucketName.of("concurrentCreating");

        assertThatThrownBy(() ->
            ConcurrentTestRunner.builder()
                .operation((threadNumber, step) -> objectStorageBlobsDAO.createBucket(bucketName).block())
                .threadCount(20)
                .operationCount(20)
                .runSuccessfullyWithin(Duration.ofSeconds(30)))
            .isInstanceOf(ExecutionException.class)
            .hasMessageContaining("org.jclouds.aws.AWSResponseException: request PUT")
            .hasMessageContaining("failed with code 400")
            .hasMessageContaining("The specified bucket is not valid.");
    }

    @Test
    void concurrentDeleteBucketCannotResilientDelete() throws Exception {
        BucketName bucketName = BucketName.of("concurrentDeleting");
        objectStorageBlobsDAO.createBucket(bucketName)
            .block();

        assertThatThrownBy(() ->
            ConcurrentTestRunner.builder()
                .operation((threadNumber, step) -> objectStorageBlobsDAO.deleteBucket(bucketName).block())
                .threadCount(20)
                .operationCount(20)
                .runSuccessfullyWithin(Duration.ofSeconds(30)))
            .isInstanceOf(ExecutionException.class)
            .hasMessageContaining("org.jclouds.aws.AWSResponseException: request PUT")
            .hasMessageContaining("failed with code 400")
            .hasMessageContaining("The specified bucket is not valid.");

    }

}

