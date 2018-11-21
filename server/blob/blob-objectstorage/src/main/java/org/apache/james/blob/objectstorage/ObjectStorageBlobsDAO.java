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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.io.IOUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.ObjectStoreException;
import org.apache.james.blob.api.WithMetric;
import org.apache.james.blob.objectstorage.swift.SwiftKeystone2ObjectStorage;
import org.apache.james.blob.objectstorage.swift.SwiftKeystone3ObjectStorage;
import org.apache.james.blob.objectstorage.swift.SwiftTempAuthObjectStorage;
import org.apache.james.metrics.api.MetricFactory;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.blobstore.options.CopyOptions;
import org.jclouds.domain.Location;
import org.jclouds.io.Payload;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingInputStream;

public class ObjectStorageBlobsDAO implements BlobStore, WithMetric {
    private static final InputStream EMPTY_STREAM = new ByteArrayInputStream(new byte[0]);
    public static final Location DEFAULT_LOCATION = null;


    private final BlobId.Factory blobIdFactory;

    private final ContainerName containerName;
    private final org.jclouds.blobstore.BlobStore blobStore;
    private final PayloadCodec payloadCodec;
    private final MetricFactory metricFactory;

    ObjectStorageBlobsDAO(ContainerName containerName, BlobId.Factory blobIdFactory,
                          org.jclouds.blobstore.BlobStore blobStore, PayloadCodec payloadCodec, MetricFactory metricFactory) {
        this.blobIdFactory = blobIdFactory;
        this.containerName = containerName;
        this.blobStore = blobStore;
        this.payloadCodec = payloadCodec;
        this.metricFactory = metricFactory;
    }

    public static ObjectStorageBlobsDAOBuilder.RequireContainerName builder(SwiftTempAuthObjectStorage.Configuration testConfig) {
        return SwiftTempAuthObjectStorage.daoBuilder(testConfig);
    }

    public static ObjectStorageBlobsDAOBuilder.RequireContainerName builder(SwiftKeystone2ObjectStorage.Configuration testConfig) {
        return SwiftKeystone2ObjectStorage.daoBuilder(testConfig);
    }

    public static ObjectStorageBlobsDAOBuilder.RequireContainerName builder(SwiftKeystone3ObjectStorage.Configuration testConfig) {
        return SwiftKeystone3ObjectStorage.daoBuilder(testConfig);
    }

    public CompletableFuture<ContainerName> createContainer(ContainerName name) {
        return CompletableFuture.supplyAsync(() -> blobStore.createContainerInLocation(DEFAULT_LOCATION, name.value()))
            .thenApply(created -> {
                if (created) {
                    return name;
                } else {
                    throw new ObjectStoreException("Unable to create container " + name.value());
                }
            });
    }

    @Override
    public CompletableFuture<BlobId> save(byte[] data) {
        return metricFactory.runPublishingTimerMetric(metricNamePrefix() + SAVE_BYTES_TIMER_NAME,
            saveInputStream(new ByteArrayInputStream(data)));
    }

    @Override
    public CompletableFuture<BlobId> save(InputStream data) {
        return metricFactory.runPublishingTimerMetric(metricNamePrefix() + SAVE_INPUT_STREAM_TIMER_NAME,
            saveInputStream(data));
    }

    private CompletableFuture<BlobId> saveInputStream(InputStream data) {
        Preconditions.checkNotNull(data);

        BlobId tmpId = blobIdFactory.randomId();
        BlobId id = save(data, tmpId);
        updateBlobId(tmpId, id);

        return CompletableFuture.completedFuture(id);
    }

    private void updateBlobId(BlobId from, BlobId to) {
        String containerName = this.containerName.value();
        blobStore.copyBlob(containerName, from.asString(), containerName, to.asString(),
            CopyOptions.NONE);
        blobStore.removeBlob(containerName, from.asString());
    }

    private BlobId save(InputStream data, BlobId id) {
        String containerName = this.containerName.value();
        HashingInputStream hashingInputStream = new HashingInputStream(Hashing.sha256(), data);
        Payload payload = payloadCodec.write(hashingInputStream);
        Blob blob = blobStore.blobBuilder(id.asString()).payload(payload).build();
        blobStore.putBlob(containerName, blob);
        return blobIdFactory.from(hashingInputStream.hash().toString());
    }

    @Override
    public CompletableFuture<byte[]> readBytes(BlobId blobId) {
        return metricFactory.runPublishingTimerMetric(metricNamePrefix() + READ_BYTES_TIMER_NAME,
            readBytesFromBlobId(blobId));
    }

    private CompletableFuture<byte[]> readBytesFromBlobId(BlobId blobId) {
        return CompletableFuture
            .supplyAsync(Throwing.supplier(() -> IOUtils.toByteArray(readFromBlobId(blobId))).sneakyThrow());
    }

    @Override
    public InputStream read(BlobId blobId) throws ObjectStoreException {
        return metricFactory.runPublishingTimerMetric(metricNamePrefix() + READ_TIMER_NAME,
            () -> readFromBlobId(blobId));
    }

    private InputStream readFromBlobId(BlobId blobId) throws ObjectStoreException {
        Blob blob = blobStore.getBlob(containerName.value(), blobId.asString());

        try {
            if (blob != null) {
                return payloadCodec.read(blob.getPayload());
            } else {
                return EMPTY_STREAM;
            }
        } catch (IOException cause) {
            throw new ObjectStoreException(
                "Failed to readBytes blob " + blobId.asString(),
                cause);
        }

    }

    @Override
    public String metricNamePrefix() {
        return "ObjectStorageBlobsDAO:";
    }
}
