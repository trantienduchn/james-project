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

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketBlobStoreContract;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.MetricableBlobStore;
import org.apache.james.blob.api.MetricableBlobStoreContract;
import org.apache.james.blob.objectstorage.aws.AwsS3AuthConfiguration;
import org.apache.james.blob.objectstorage.aws.AwsS3ObjectStorage;
import org.apache.james.blob.objectstorage.aws.DockerAwsS3Container;
import org.apache.james.blob.objectstorage.aws.DockerAwsS3Extension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DockerAwsS3Extension.class)
public class ObjectStorageBlobsDAOAWSTest implements MetricableBlobStoreContract, BucketBlobStoreContract {

    private static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();

    private BucketName defaultBucketName;
    private org.jclouds.blobstore.BlobStore blobStore;
    private ObjectStorageBlobsDAO objectStorageBlobsDAO;
    private AwsS3ObjectStorage awsS3ObjectStorage;
    private AwsS3AuthConfiguration configuration;
    private BlobStore testee;

    @BeforeEach
    void setUp(DockerAwsS3Container dockerAwsS3) {
        awsS3ObjectStorage = new AwsS3ObjectStorage();
        defaultBucketName = BucketName.of("e7a929fe-2003-48d4-aaa9-a65d4954379d");
        configuration = AwsS3AuthConfiguration.builder()
            .endpoint(dockerAwsS3.getEndpoint())
            .accessKeyId(DockerAwsS3Container.ACCESS_KEY_ID)
            .secretKey(DockerAwsS3Container.SECRET_ACCESS_KEY)
            .build();

        ObjectStorageBlobsDAOBuilder.ReadyToBuild builder = ObjectStorageBlobsDAO
            .builder(configuration)
            .blobIdFactory(BLOB_ID_FACTORY)
            .namespace(defaultBucketName)
            .blobPutter(awsS3ObjectStorage.putBlob(configuration));

        blobStore = builder.getSupplier().get();
        objectStorageBlobsDAO = builder.build();
        testee = new MetricableBlobStore(metricsTestExtension.getMetricFactory(), objectStorageBlobsDAO);
    }

    @AfterEach
    void tearDown() {
        blobStore.deleteContainer(defaultBucketName.asString());
        blobStore.deleteContainer(CUSTOM.asString());
        blobStore.getContext().close();
        awsS3ObjectStorage.tearDown();
    }

    @Override
    public BlobStore testee() {
        return testee;
    }

    @Override
    public BlobId.Factory blobIdFactory() {
        return BLOB_ID_FACTORY;
    }
}
