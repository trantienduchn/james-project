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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.objectstorage.BlobPutter;
import org.apache.james.blob.objectstorage.ObjectStorageBlobStoreBuilder;
import org.apache.james.blob.objectstorage.ObjectStorageBucketName;
import org.apache.james.util.Size;
import org.apache.james.util.concurrent.NamedThreadFactory;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.retry.PredefinedRetryPolicies;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;

import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.scheduler.Schedulers;
import reactor.retry.Retry;

public class AwsS3ObjectStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(AwsS3ObjectStorage.class);

    private static final Iterable<Module> JCLOUDS_MODULES = ImmutableSet.of(new SLF4JLoggingModule());
    private static final int MAX_THREADS = 5;
    private static final boolean DO_NOT_SHUTDOWN_THREAD_POOL = false;
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

    private final ExecutorService executorService;

    @Inject
    @VisibleForTesting
    public AwsS3ObjectStorage() {
        executorService = Executors.newFixedThreadPool(MAX_THREADS, NamedThreadFactory.withClassName(AwsS3ObjectStorage.class));
    }

    @PreDestroy
    public void tearDown() {
        executorService.shutdownNow();
    }

    public static ObjectStorageBlobStoreBuilder.RequireBlobIdFactory blobStoreBuilder(AwsS3AuthConfiguration configuration) {
        return ObjectStorageBlobStoreBuilder.forBlobStore(new BlobStoreBuilder(configuration));
    }

    public Optional<BlobPutter> putBlob(AwsS3AuthConfiguration configuration) {
        return Optional.of(new AwsS3BlobPutter(configuration, executorService));
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

        private static class UploadFailedException extends Exception {
        }

        private static final Duration FIRST_BACK_OFF = Duration.ofMillis(100);
        private static final Duration FOREVER = Duration.ofMillis(Long.MAX_VALUE);
        private static final long RETRY_ONE_LAST_TIME_ON_CONCURRENT_SAVING = 1;

        private final AwsS3AuthConfiguration configuration;
        private final ExecutorService executorService;

        AwsS3BlobPutter(AwsS3AuthConfiguration configuration, ExecutorService executorService) {
            this.configuration = configuration;
            this.executorService = executorService;
        }

        @Override
        public Mono<Void> putDirectly(ObjectStorageBucketName bucketName, Blob blob) {
            return putWithRetry(bucketName, uploadByBlob(bucketName, blob));
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
                .flatMap(blobId -> putWithRetry(bucketName, uploadByFile(bucketName, blobId, file))
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
                    .onlyIf(retryContext -> failOnNoSuchBucket(retryContext.exception(), bucketName))
                    .exponentialBackoff(FIRST_BACK_OFF, FOREVER)
                    .withBackoffScheduler(Schedulers.elastic())
                    .retryMax(MAX_RETRY_ON_EXCEPTION)
                    .doOnRetry(retryContext -> createBucket(bucketName, configuration)))
                .retryWhen(Retry
                    .onlyIf(retryContext -> failOnExistingBucket(retryContext.exception(), bucketName))
                    .withBackoffScheduler(Schedulers.elastic())
                    .exponentialBackoff(FIRST_BACK_OFF, FOREVER)
                    .retryMax(RETRY_ONE_LAST_TIME_ON_CONCURRENT_SAVING));
        }

        private Mono<Void> uploadByFile(ObjectStorageBucketName bucketName, BlobId blobId, File file) {
            return Mono.fromSupplier(() -> new PutObjectRequest(bucketName.asString(), blobId.asString(), file))
                .flatMap(this::upload);
        }

        private Mono<Void> uploadByBlob(ObjectStorageBucketName bucketName, Blob blob) {
            return Mono.fromCallable(() -> blob.getPayload().openStream())
                .flatMap(inputStream -> upload(new PutObjectRequest(
                        bucketName.asString(),
                        blob.getMetadata().getName(),
                        inputStream,
                        new ObjectMetadata()))
                    .doFinally(ignored -> closeQuietly(inputStream)));
        }

        private Mono<Void> upload(PutObjectRequest request) {
            return Mono.create(sink -> {
                TransferManager transferManager = getTransferManager();
                transferManager
                    .upload(request)
                    .addProgressListener((ProgressListener) progressEvent -> watchingUploadEvent(sink, transferManager, progressEvent));
            });
        }

        private void watchingUploadEvent(MonoSink<Void> sink, TransferManager transferManager, ProgressEvent progressEvent) {
            if (progressEvent.getEventType() == ProgressEventType.TRANSFER_FAILED_EVENT) {
                sink.error(new UploadFailedException());
                transferManager.shutdownNow();
            } else if (progressEvent.getEventType() == ProgressEventType.TRANSFER_COMPLETED_EVENT) {
                sink.success();
                transferManager.shutdownNow();
            }

            // ignore other events
        }

        private void closeQuietly(InputStream inputStream) {
            try {
                inputStream.close();
            } catch (IOException e) {
                LOGGER.error("Cannot close resource", e);
            }
        }

        private void createBucket(ObjectStorageBucketName bucketName, AwsS3AuthConfiguration configuration) {
            getS3Client(configuration, getClientConfiguration())
                .createBucket(bucketName.asString());
        }

        private boolean failOnNoSuchBucket(Throwable th, ObjectStorageBucketName bucketName) {
            return th instanceof UploadFailedException
                && !doesBucketExist(bucketName);
        }

        private boolean failOnExistingBucket(Throwable th, ObjectStorageBucketName bucketName) {
            return th instanceof UploadFailedException
                && doesBucketExist(bucketName);
        }

        private boolean doesBucketExist(ObjectStorageBucketName bucketName) {
            return getS3Client(configuration, getClientConfiguration())
                .doesBucketExistV2(bucketName.asString());
        }

        private TransferManager getTransferManager() {
            ClientConfiguration clientConfiguration = getClientConfiguration();
            AmazonS3 amazonS3 = getS3Client(configuration, clientConfiguration);

            return TransferManagerBuilder
                    .standard()
                    .withS3Client(amazonS3)
                    .withMultipartUploadThreshold(MULTIPART_UPLOAD_THRESHOLD.getValue())
                    .withExecutorFactory(() -> executorService)
                    .withShutDownThreadPools(DO_NOT_SHUTDOWN_THREAD_POOL)
                    .build();
        }

        private static AmazonS3 getS3Client(AwsS3AuthConfiguration configuration, ClientConfiguration clientConfiguration) {
            return AmazonS3ClientBuilder
                    .standard()
                    .withClientConfiguration(clientConfiguration)
                    .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(configuration.getAccessKeyId(), configuration.getSecretKey())))
                    .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(configuration.getEndpoint(), null))
                    .build();
        }

        private static ClientConfiguration getClientConfiguration() {
            ClientConfiguration clientConfiguration = new ClientConfiguration();
            clientConfiguration.setRetryPolicy(PredefinedRetryPolicies.getDefaultRetryPolicyWithCustomMaxRetries(MAX_ERROR_RETRY));
            return clientConfiguration;
        }
    }
}
