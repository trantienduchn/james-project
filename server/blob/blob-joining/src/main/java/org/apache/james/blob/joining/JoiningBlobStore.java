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

package org.apache.james.blob.joining;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.cassandra.CassandraBlobsDAO;
import org.apache.james.blob.objectstorage.ObjectStorageBlobsDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.ByteStreams;
import com.google.inject.Inject;

public class JoiningBlobStore implements BlobStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(JoiningBlobStore.class);

    private final BlobStore primaryBlobStore;
    private final BlobStore secondaryBlobStore;

    @Inject
    public JoiningBlobStore(CassandraBlobsDAO cassandraBlobStore, ObjectStorageBlobsDAO swiftBlobStore) {
        this.primaryBlobStore = swiftBlobStore;
        this.secondaryBlobStore = cassandraBlobStore;
    }

    @Override
    public CompletableFuture<BlobId> save(byte[] data) {
        return primaryBlobStore.save(data);
    }

    @Override
    public CompletableFuture<BlobId> save(InputStream data) {
        return primaryBlobStore.save(data);
    }

    @Override
    public CompletableFuture<byte[]> readBytes(BlobId blobId) {
        return primaryBlobStore.readBytes(blobId)
            .thenCompose(bytes -> Optional.ofNullable(bytes)
                .filter(byteArray -> byteArray.length > 1)
                .map(CompletableFuture::completedFuture)
                .orElseGet(() -> secondaryBlobStore.readBytes(blobId)));
    }

    @Override
    public InputStream read(BlobId blobId) {
        primaryBlobStore.read(blobId)
        return readBytes(blobId)
            .thenApply(ByteBuffer::wrap)
            .get();
    }

    private byte[] readInputStream(InputStream is) {
        try {
            return ByteStreams.toByteArray(is);
        } catch (IOException e) {
            LOGGER.error("error while reading inputstream", e);
            throw new RuntimeException(e);
        }
    }
}
