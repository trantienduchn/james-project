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

package org.apache.james.blob.memory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.IOUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.WithMetric;
import org.apache.james.metrics.api.MetricFactory;

import com.google.common.base.Preconditions;

public class MemoryBlobStore implements BlobStore, WithMetric {
    private final ConcurrentHashMap<BlobId, byte[]> blobs;
    private final BlobId.Factory factory;
    private final MetricFactory metricFactory;

    public MemoryBlobStore(MetricFactory metricFactory, BlobId.Factory factory) {
        this.metricFactory = metricFactory;
        this.factory = factory;
        blobs = new ConcurrentHashMap<>();
    }

    @Override
    public CompletableFuture<BlobId> save(byte[] data) {
        return metricFactory
            .runPublishingTimerMetric(metricNamePrefix() + SAVE_BYTES_TIMER_NAME, saveBytes(data));
    }

    private CompletableFuture<BlobId> saveBytes(byte[] data) {
        Preconditions.checkNotNull(data);
        BlobId blobId = factory.forPayload(data);

        blobs.put(blobId, data);

        return CompletableFuture.completedFuture(blobId);
    }

    @Override
    public CompletableFuture<BlobId> save(InputStream data) {
        return metricFactory
            .runPublishingTimerMetric(metricNamePrefix() + SAVE_INPUT_STREAM_TIMER_NAME, saveInputStream(data));
    }

    private CompletableFuture<BlobId> saveInputStream(InputStream data) {
        Preconditions.checkNotNull(data);
        try {
            byte[] bytes = IOUtils.toByteArray(data);
            return save(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CompletableFuture<byte[]> readBytes(BlobId blobId) {
        return metricFactory
            .runPublishingTimerMetric(metricNamePrefix() + READ_BYTES_TIMER_NAME, readBytesFromBlobId(blobId));
    }

    private CompletableFuture<byte[]> readBytesFromBlobId(BlobId blobId) {
        return CompletableFuture.completedFuture(retrieveStoredValue(blobId));
    }

    @Override
    public InputStream read(BlobId blobId) {
        return metricFactory
            .runPublishingTimerMetric(metricNamePrefix() + READ_TIMER_NAME, () -> readFromBlobId(blobId));
    }

    private InputStream readFromBlobId(BlobId blobId) {
        return new ByteArrayInputStream(retrieveStoredValue(blobId));
    }

    @Override
    public String metricNamePrefix() {
        return "MemoryBlobStore:";
    }

    private byte[] retrieveStoredValue(BlobId blobId) {
        return blobs.getOrDefault(blobId, new byte[]{});
    }
}
