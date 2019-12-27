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

package org.apache.james.blob.objectstorage.aws;

import static software.amazon.awssdk.regions.Region.US_EAST_1;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.commons.io.FileUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.objectstorage.BlobPutter;
import org.apache.james.blob.objectstorage.ObjectStorageBlobStoreBuilder;
import org.apache.james.blob.objectstorage.ObjectStorageBucketName;
import org.apache.james.util.Size;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.retry.Retry;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

public class AwsS3ObjectStorage {

    private static final Iterable<Module> JCLOUDS_MODULES = ImmutableSet.of(new SLF4JLoggingModule());
    private static final int MAX_ERROR_RETRY = 5;
    private static final int MAX_RETRY_ON_EXCEPTION = 3;
    private static Size MULTIPART_UPLOAD_THRESHOLD;

    static {
        try {
            MULTIPART_UPLOAD_THRESHOLD = Size.parse("5M");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ObjectStorageBlobStoreBuilder.RequireBlobIdFactory blobStoreBuilder(AwsS3AuthConfiguration configuration) {
        return ObjectStorageBlobStoreBuilder.forBlobStore(new BlobStoreBuilder(configuration));
    }

    public Optional<BlobPutter> putBlob(AwsS3AuthConfiguration configuration) {
        return Optional.of(new AwsS3BlobPutter(configuration));
    }

    private static class BlobStoreBuilder implements Supplier<BlobStore> {
        private final AwsS3AuthConfiguration configuration;

        private BlobStoreBuilder(AwsS3AuthConfiguration configuration) {
            this.configuration = configuration;
        }

        public BlobStore get() {
            Properties overrides = new Properties();
            overrides.setProperty("PROPERTY_S3_VIRTUAL_HOST_BUCKETS", "false");

            return contextBuilder()
                .endpoint(configuration.getEndpoint())
                .credentials(configuration.getAccessKeyId(), configuration.getSecretKey())
                .overrides(overrides)
                .modules(JCLOUDS_MODULES)
                .buildView(BlobStoreContext.class)
                .getBlobStore();
        }

        private ContextBuilder contextBuilder() {
            return ContextBuilder.newBuilder("s3");
        }
    }

    private static class AwsS3BlobPutter implements BlobPutter {

        private static final int NOT_FOUND_STATUS_CODE = 404;
        private static final String BUCKET_NOT_FOUND_ERROR_CODE = "NoSuchBucket";
        private static final Duration FIRST_BACK_OFF = Duration.ofMillis(100);
        private static final Duration FOREVER = Duration.ofMillis(Long.MAX_VALUE);

        private final AwsS3AuthConfiguration configuration;

        AwsS3BlobPutter(AwsS3AuthConfiguration configuration) {
            this.configuration = configuration;
        }

        @Override
        public Mono<Void> putDirectly(ObjectStorageBucketName bucketName, Blob blob) {
            return Mono.using(
                () -> createTempFile(blob),
                file -> putWithRetry(bucketName, uploadAsync(bucketName, blob.getMetadata().getName(), file)),
                FileUtils::deleteQuietly);
        }

        @Override
        public Mono<BlobId> putAndComputeId(ObjectStorageBucketName bucketName, Blob initialBlob, Supplier<BlobId> blobIdSupplier) {
            return Mono.using(
                () -> createTempFile(initialBlob),
                file -> putByFile(bucketName, blobIdSupplier, file),
                FileUtils::deleteQuietly);
        }

        private Mono<BlobId> putByFile(ObjectStorageBucketName bucketName, Supplier<BlobId> blobIdSupplier, File file) {
            return Mono.fromSupplier(blobIdSupplier)
                .flatMap(blobId -> putWithRetry(bucketName, uploadAsync(bucketName, blobId.asString(), file))
                    .then(Mono.just(blobId)));
        }

        private File createTempFile(Blob blob) throws IOException {
            File file = File.createTempFile(UUID.randomUUID().toString(), ".tmp");
            FileUtils.copyToFile(blob.getPayload().openStream(), file);
            return file;
        }

        private Mono<Void> putWithRetry(ObjectStorageBucketName bucketName, Mono<Void> puttingAttempt) {
            return puttingAttempt
                .publishOn(Schedulers.elastic())
                .retryWhen(Retry
                    .onlyIf(retryContext -> failOnNoSuchBucket(retryContext.exception()))
                    .exponentialBackoff(FIRST_BACK_OFF, FOREVER)
                    .withBackoffScheduler(Schedulers.elastic())
                    .retryMax(MAX_RETRY_ON_EXCEPTION)
                    .doOnRetry(retryContext -> createBucket(bucketName)));
        }

        private Mono<Void> uploadAsync(ObjectStorageBucketName bucketName, String blobIdAsString, File file) {
            return s3Act(client -> Mono
                .fromFuture(putObject(bucketName, blobIdAsString, file, client))
                .then());
        }

        private CompletableFuture<PutObjectResponse> putObject(ObjectStorageBucketName bucketName,
                                                               String blobIdAsString,
                                                               File file,
                                                               S3AsyncClient client) {
            return client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucketName.asString())
                    .key(blobIdAsString)
                    .build(),
                AsyncRequestBody.fromFile(file));
        }

        private void createBucket(ObjectStorageBucketName bucketName) {
            s3Act(client -> Mono.fromFuture(client
                .createBucket(CreateBucketRequest.builder()
                    .bucket(bucketName.asString())
                    .build())))
                .block();
        }

        private <T> Mono<T> s3Act(Function<S3AsyncClient, Mono<T>> s3Action) {
            return Mono.using(
                () -> getS3AsyncClient(configuration),
                s3Action,
                this::closeClient);
        }

        private void closeClient(S3AsyncClient client) {
            Mono.fromRunnable(client::close)
                .subscribeOn(Schedulers.elastic())
                .subscribe();
        }

        private boolean failOnNoSuchBucket(Throwable th) {
            if (th instanceof NoSuchBucketException) {
                NoSuchBucketException s3Exception = (NoSuchBucketException) th;
                return NOT_FOUND_STATUS_CODE == s3Exception.statusCode()
                    && BUCKET_NOT_FOUND_ERROR_CODE.equals(s3Exception.awsErrorDetails().errorCode());
            }

            return false;
        }

        private static S3AsyncClient getS3AsyncClient(AwsS3AuthConfiguration configuration) throws URISyntaxException {
            return S3AsyncClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(configuration.getAccessKeyId(), configuration.getSecretKey())))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                    .retryPolicy(RetryPolicy.builder()
                        .numRetries(MAX_ERROR_RETRY)
                        .backoffStrategy(BackoffStrategy.defaultStrategy())
                        .build())
                    .build())
                .endpointOverride(new URI(configuration.getEndpoint()))
                .region(US_EAST_1)
                .build();
        }
    }
}
