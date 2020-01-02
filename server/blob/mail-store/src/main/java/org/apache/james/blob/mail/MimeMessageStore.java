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
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.SequenceInputStream;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.api.Store.BlobType;
import org.apache.james.util.BodyOffsetInputStream;
import org.apache.james.util.BodyOffsetInputStream.Splitter.MessageParts;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
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
            try {
                MessageParts messageParts = readMessage(message);
                return Stream.of(
                    Pair.of(HEADER_BLOB_TYPE, new Store.Impl.InputStreamToSave(messageParts.getHeaderContent())),
                    Pair.of(BODY_BLOB_TYPE, new Store.Impl.InputStreamToSave(messageParts.getBodyContent())));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private static MessageParts readMessage(MimeMessage message) throws IOException {
            PipedOutputStream outputStream = new PipedOutputStream();

            Mono.fromRunnable(Throwing.runnable(() -> {
                    message.writeTo(outputStream);
                    outputStream.close();
                }))
                .subscribeOn(Schedulers.elastic())
                .subscribe();

            return BodyOffsetInputStream.Splitter
                .split(new BodyOffsetInputStream(new PipedInputStream(outputStream)));
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
