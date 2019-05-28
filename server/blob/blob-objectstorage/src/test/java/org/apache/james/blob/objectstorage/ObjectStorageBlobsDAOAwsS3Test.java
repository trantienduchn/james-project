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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.MetricableBlobStore;
import org.apache.james.blob.api.MetricableBlobStoreContract;
import org.apache.james.blob.objectstorage.aws.AwsS3AuthConfiguration;
import org.apache.james.blob.objectstorage.aws.AwsS3ObjectStorage;
import org.apache.james.blob.objectstorage.aws.DockerAwsS3Container;
import org.apache.james.blob.objectstorage.aws.DockerAwsS3Extension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DockerAwsS3Extension.class)
public class ObjectStorageBlobsDAOAwsS3Test implements MetricableBlobStoreContract {


    private ContainerName containerName;
    private org.jclouds.blobstore.BlobStore blobStore;
    private ObjectStorageBlobsDAO objectStorageBlobsDAO;
    private BlobStore testee;
    private AwsS3AuthConfiguration configuration;
    private AwsS3ObjectStorage awsS3ObjectStorage;

    @BeforeEach
    void setUp(DockerAwsS3Container awsS3Container) {
        awsS3ObjectStorage = new AwsS3ObjectStorage();
        containerName = ContainerName.of(UUID.randomUUID().toString());
        configuration = AwsS3AuthConfiguration.builder()
            .endpoint(awsS3Container.getEndpoint())
            .accessKeyId(DockerAwsS3Container.ACCESS_KEY_ID)
            .secretKey(DockerAwsS3Container.SECRET_ACCESS_KEY)
            .build();

        containerName = ContainerName.of(UUID.randomUUID().toString());
        BlobId.Factory blobIdFactory = blobIdFactory();
        ObjectStorageBlobsDAOBuilder.ReadyToBuild daoBuilder = ObjectStorageBlobsDAO
            .builder(configuration)
            .container(containerName)
            .blobIdFactory(blobIdFactory)
            .putBlob(awsS3ObjectStorage.putBlob(containerName, configuration));
        blobStore = daoBuilder.getSupplier().get();
        objectStorageBlobsDAO = daoBuilder.build();
        objectStorageBlobsDAO.createContainer(containerName).block();
        testee = new MetricableBlobStore(metricsTestExtension.getMetricFactory(), objectStorageBlobsDAO);
    }

    @AfterEach
    void tearDown() {
        blobStore.deleteContainer(containerName.value());
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
    void saveByPathShouldSaveBlob() {
        BlobId file1Id = objectStorageBlobsDAO.save("file 1 content", "root/blob/file1")
            .block();

        assertThat(objectStorageBlobsDAO.read(file1Id))
            .hasSameContentAs(new ByteArrayInputStream("file 1 content".getBytes(StandardCharsets.UTF_8)));
    }

    // Currently there is only HashBlobId.Factory
    // we may need another BlobId Factory to generate BlobIds which have value are file paths
    @Test
    void saveByPathShouldReturnFilePatternBlobId() {
        BlobId file1Id = objectStorageBlobsDAO.save("file 1 content", "root/blob/file1")
            .block();

        assertThat(file1Id.asString())
            .isEqualTo("root/blob/file1");
    }

    @Test
    void saveByPathShouldSaveManyBlobsHavingSamePrefix() {
        BlobId file1Id = objectStorageBlobsDAO.save("file 1 content", "root/blob/file1")
            .block();
        BlobId file2Id = objectStorageBlobsDAO.save("file 2 content", "root/blob/file2")
            .block();

        assertThat(objectStorageBlobsDAO.read(file1Id))
            .hasSameContentAs(new ByteArrayInputStream("file 1 content".getBytes(StandardCharsets.UTF_8)));
        assertThat(objectStorageBlobsDAO.read(file2Id))
            .hasSameContentAs(new ByteArrayInputStream("file 2 content".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void listShouldListOnlyBlobsHavingSamePrefix() {
        BlobId file1Id = objectStorageBlobsDAO.save("file 1 content", "root/blob/file1")
            .block();
        BlobId file2Id = objectStorageBlobsDAO.save("file 2 content", "root/blob/file2")
            .block();
        BlobId file3Id = objectStorageBlobsDAO.save("file 3 content", "root/anotherFolder/file3")
            .block();

        assertThat(objectStorageBlobsDAO.list("root/blob/").toStream())
            .containsOnly(file1Id, file2Id);
    }

    @Test
    void listShouldSkipBlobsAtNestedLevels() {
        BlobId file1Id = objectStorageBlobsDAO.save("file 1 content", "root/blob/file1")
            .block();
        BlobId file2Id = objectStorageBlobsDAO.save("file 2 content", "root/blob/file2")
            .block();
        BlobId file3Id = objectStorageBlobsDAO.save("file 3 content", "root/blob/nested/file3")
            .block();
        BlobId file4Id = objectStorageBlobsDAO.save("file 4 content", "root/blob/nested/more-nested/file4")
            .block();
        BlobId topNestedFolder = blobIdFactory().from("root/blob/nested/");

        assertThat(objectStorageBlobsDAO.list("root/blob/").toStream())
            .containsOnly(file1Id, file2Id, topNestedFolder);
    }

    @Test
    void jcouldBlobStoreDeleteDirectoryDoesntWork() {
        BlobId file1Id = objectStorageBlobsDAO.save("file 1 content", "root/blob/file1")
            .block();
        BlobId file2Id = objectStorageBlobsDAO.save("file 2 content", "root/blob/file2")
            .block();

        // deleteDirectory() doesn't work
        blobStore.deleteDirectory(containerName.value(), "root/blob/");
        assertThat(objectStorageBlobsDAO.list("root/blob/").toStream())
            .containsOnly(file1Id, file2Id);

        // removeBlob() doesn't work
        blobStore.removeBlob(containerName.value(), "root/blob/");
        assertThat(objectStorageBlobsDAO.list("root/blob/").toStream())
            .containsOnly(file1Id, file2Id);
    }

    @Test
    void jcouldBlobStoreDeleteBlobStillWorks() {
        BlobId file1Id = objectStorageBlobsDAO.save("file 1 content", "root/blob/file1")
            .block();
        BlobId file2Id = objectStorageBlobsDAO.save("file 2 content", "root/blob/file2")
            .block();

        blobStore.removeBlob(containerName.value(), "root/blob/file1");
        blobStore.removeBlob(containerName.value(), "root/blob/file2");
        assertThat(objectStorageBlobsDAO.list("root/blob/").toStream())
            .isEmpty();
    }
}

