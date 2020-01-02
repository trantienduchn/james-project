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

package org.apache.james.blob.mail;

import static org.apache.james.blob.mail.MimeMessagePartsId.BODY_BLOB_TYPE;
import static org.apache.james.blob.mail.MimeMessagePartsId.HEADER_BLOB_TYPE;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.api.Store.BlobType;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class MimeMessageStore {

    public static class Factory {
        private final BlobStore blobStore;

        @Inject
        public Factory(BlobStore blobStore) {
            this.blobStore = blobStore;
        }

        public Store<MimeMessage, MimeMessagePartsId> mimeMessageStore() {
            return new Store.Impl<>(
                new MimeMessagePartsId.Factory(),
                new MimeMessageEncoder(),
                new MimeMessageDecoder(),
                blobStore);
        }
    }

    static class MimeMessageEncoder implements Store.Impl.Encoder<MimeMessage> {
        @Override
        public Stream<Pair<BlobType, Store.Impl.ValueToSave>> encode(MimeMessage message) {
            Preconditions.checkNotNull(message);
            MessageStreamTransformer transformer = new MessageStreamTransformer(message);
            return Stream.of(
                Pair.of(HEADER_BLOB_TYPE, new Store.Impl.InputStreamToSave(transformer.getHeaderInputStream())),
                Pair.of(BODY_BLOB_TYPE, new Store.Impl.InputStreamToSave(transformer.getBodyInputStream())));
        }
    }

    static class MessageStreamTransformer {

        static class HeaderInputStream extends InputStream {

            private final Supplier<Integer> readAction;
            private final AtomicBoolean readingTriggered;
            private final Mono<Void> publisherAcknowledgement;
            private final AtomicBoolean finished;

            HeaderInputStream(Supplier<Integer> readAction, Mono<Void> publisherAcknowledgement) {
                this.readAction = readAction;
                this.publisherAcknowledgement = publisherAcknowledgement;
                this.readingTriggered = new AtomicBoolean(false);
                this.finished = new AtomicBoolean(false);
            }

            @Override
            public int read() throws IOException {
                if (!finished.get()) {
                    if (!readingTriggered.get()) {
                        publisherAcknowledgement.subscribe();
                        readingTriggered.set(true);
                    }

                    int byteValue = readAction.get();
                    if (byteValue == -1) {
                        finished.set(true);
                    }

                    return byteValue;
                }

                return -1;
            }
        }

        static class BodyInputStream extends InputStream {

            private final Supplier<Integer> readAction;
            private final AtomicBoolean finished;

            BodyInputStream(Supplier<Integer> readAction) {
                this.readAction = readAction;
                this.finished = new AtomicBoolean(false);
            }

            @Override
            public int read() throws IOException {
                if (!finished.get()) {
                    int byteValue = readAction.get();
                    if (byteValue == -1) {
                        finished.set(true);
                    }

                    return byteValue;
                }

                return -1;
            }
        }

        static class LinkedOutputStream extends OutputStream {

            private static final int[] HEADER_END_PATTERN = {0x0D, 0x0A, 0x0D, 0x0A};

            private final BlockingQueue<Integer> bodySource;
            private final BlockingQueue<Integer> headerSource;
            private final EvictingQueue<Integer> headerEndSignal;
            private final AtomicBoolean headerEnded;

            LinkedOutputStream(BlockingQueue<Integer> headerSource,
                               BlockingQueue<Integer> bodySource) {
                this.bodySource = bodySource;
                this.headerSource = headerSource;
                this.headerEndSignal = EvictingQueue.create(4);
                this.headerEnded = new AtomicBoolean(false);
            }

            @Override
            public void write(int b) throws IOException {
                try {
                    headerEndSignal.offer(b);
                    if (!headerEnded.get()) {
                        headerEnded.set(isHeaderEnded());
                        headerSource.put(b);
                    } else {
                        headerSource.put(-1);
                        bodySource.put(b);
                    }
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
            }

            private boolean isHeaderEnded() {
                return Arrays.equals(
                    headerEndSignal.stream().mapToInt(i -> i).toArray(),
                    HEADER_END_PATTERN);
            }

            @Override
            public void close() throws IOException {
                try {
                    headerSource.put(-1);
                    bodySource.put(-1);
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
            }
        }

        private final BlockingQueue<Integer> headerSource;
        private final BlockingQueue<Integer> bodySource;
        private final HeaderInputStream headerInputStream;
        private final BodyInputStream bodyInputStream;
        private final LinkedOutputStream outputStream;

        MessageStreamTransformer(MimeMessage message) {
            this.headerSource = new LinkedBlockingQueue<>();
            this.bodySource = new LinkedBlockingQueue<>();
            this.outputStream = new LinkedOutputStream(headerSource, bodySource);
            this.bodyInputStream = new BodyInputStream(Throwing.supplier(bodySource::take));
            this.headerInputStream = new HeaderInputStream(Throwing.supplier(headerSource::take),
                Mono.<Void>fromRunnable(Throwing.runnable(() -> {
                        message.writeTo(outputStream);
                        outputStream.close();
                    }))
                    .subscribeOn(Schedulers.elastic()));
        }

        public HeaderInputStream getHeaderInputStream() {
            return headerInputStream;
        }

        public BodyInputStream getBodyInputStream() {
            return bodyInputStream;
        }
    }

    static class MimeMessageDecoder implements Store.Impl.Decoder<MimeMessage> {
        @Override
        public MimeMessage decode(Stream<Pair<BlobType, byte[]>> streams) {
            Preconditions.checkNotNull(streams);
            Map<BlobType,byte[]> pairs = streams.collect(ImmutableMap.toImmutableMap(Pair::getLeft, Pair::getRight));
            Preconditions.checkArgument(pairs.containsKey(HEADER_BLOB_TYPE));
            Preconditions.checkArgument(pairs.containsKey(BODY_BLOB_TYPE));

            return toMimeMessage(
                new SequenceInputStream(
                    new ByteArrayInputStream(pairs.get(HEADER_BLOB_TYPE)),
                    new ByteArrayInputStream(pairs.get(BODY_BLOB_TYPE))));
        }

        private MimeMessage toMimeMessage(InputStream inputStream) {
            try {
                return new MimeMessage(Session.getInstance(new Properties()), inputStream);
            } catch (MessagingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static Factory factory(BlobStore blobStore) {
        return new Factory(blobStore);
    }
}
