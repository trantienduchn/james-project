/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.james.blob.objectstorage;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Supplier;

import org.apache.commons.io.FileUtils;
import org.apache.james.blob.api.BlobId;
import org.jclouds.blobstore.domain.Blob;

import reactor.core.publisher.Mono;

/**
 * Implementations may have specific behaviour when uploading a blob,
 * such cases are not well handled by jClouds.
 *
 * For example:
 * AWS S3 need a length while uploading with jClouds
 * whereas you don't need one by using the S3 client.
 *
 */

public interface BlobPutter {

    class TempBlob {

        private static void destroy(TempBlob tempBlob) {
            FileUtils.deleteQuietly(tempBlob.content);
        }

        private final BlobId blobId;
        private final File content;

        private TempBlob(BlobId blobId, File content) {
            this.blobId = blobId;
            this.content = content;
        }

        public BlobId getBlobId() {
            return blobId;
        }

        public File getContent() {
            return content;
        }
    }

    Mono<Void> putDirectly(ObjectStorageBucketName bucketName, Blob blob);

    default Mono<BlobId> putAndComputeId(ObjectStorageBucketName bucketName, Blob initialBlob, Supplier<BlobId> blobIdSupplier) {
        return Mono.using(
            () -> temporallySaving(initialBlob, blobIdSupplier),
            tempBlob -> putTempBlob(bucketName, tempBlob)
                .then(Mono.just(tempBlob.blobId)),
            TempBlob::destroy);
    }

    default TempBlob temporallySaving(Blob blob, Supplier<BlobId> blobIdSupplier) throws IOException {
        File file = File.createTempFile(UUID.randomUUID().toString(), ".tmp");
        FileUtils.copyToFile(blob.getPayload().openStream(), file);

        return new TempBlob(blobIdSupplier.get(), file);
    }

    Mono<Void> putTempBlob(ObjectStorageBucketName bucketName, TempBlob tempBlob);
}
