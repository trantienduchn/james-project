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

package org.apache.james.jmap.api.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestMessageId;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public interface MessagePreviewStoreContract {

    MessageId MESSAGE_ID_1 = TestMessageId.of(1);
    Preview PREVIEW_1 = Preview.from("message id 1");
    MessageId MESSAGE_ID_2 = TestMessageId.of(2);
    Preview PREVIEW_2 = Preview.from("message id 2");

    MessagePreviewStore testee();

    @Test
    default void retrieveShouldThrowWhenNullMessageId() {
        assertThatThrownBy(() -> Mono.from(testee().retrieve(null)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void retrieveShouldReturnStoredPreview() {
        Mono.from(testee().store(MESSAGE_ID_1, PREVIEW_1))
            .block();

        assertThat(Mono.from(testee().retrieve(MESSAGE_ID_1)).block())
            .isEqualTo(PREVIEW_1);
    }

    @Test
    default void retrieveShouldReturnEmptyWhenMessageIdNotFound() {
        Mono.from(testee().store(MESSAGE_ID_1, PREVIEW_1))
            .block();

        assertThat(Mono.from(testee().retrieve(MESSAGE_ID_2)).blockOptional())
            .isEmpty();
    }

    @Test
    default void retrieveShouldReturnTheRightPreviewWhenStoringMultipleMessageIds() {
        Mono.from(testee().store(MESSAGE_ID_1, PREVIEW_1))
            .block();
        Mono.from(testee().store(MESSAGE_ID_2, PREVIEW_2))
            .block();

        SoftAssertions.assertSoftly(softly -> {
           softly.assertThat(Mono.from(testee().retrieve(MESSAGE_ID_1)).block())
               .isEqualTo(PREVIEW_1);
           softly.assertThat(Mono.from(testee().retrieve(MESSAGE_ID_2)).block())
               .isEqualTo(PREVIEW_2);
        });
    }

    @Test
    default void storeShouldThrowWhenNullMessageId() {
        assertThatThrownBy(() -> Mono.from(testee().store(null, PREVIEW_1)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void storeShouldThrowWhenNullPreview() {
        assertThatThrownBy(() -> Mono.from(testee().store(MESSAGE_ID_1, null)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void storeShouldOverrideOldRecord() {
        Mono.from(testee().store(MESSAGE_ID_1, PREVIEW_1))
            .block();

        Mono.from(testee().store(MESSAGE_ID_1, PREVIEW_2))
            .block();

        assertThat(Mono.from(testee().retrieve(MESSAGE_ID_1)).block())
            .isEqualTo(PREVIEW_2);
    }

    @Test
    default void storeShouldBeIdempotent() {
        Mono.from(testee().store(MESSAGE_ID_1, PREVIEW_1))
            .block();

        Mono.from(testee().store(MESSAGE_ID_1, PREVIEW_1))
            .block();

        assertThat(Mono.from(testee().retrieve(MESSAGE_ID_1)).block())
            .isEqualTo(PREVIEW_1);
    }

    @Test
    default void concurrentStoreShouldStoreTheLatestValueDespiteOfCollisions() throws Exception {
        int threadCount = 10;
        int stepCount = 100;
        int repeatCount = 10;

        ConcurrentHashMap<Integer, Integer> indexCounter = new ConcurrentHashMap<>();

        Flux.range(0, stepCount)
            .repeat(repeatCount)
            .parallel(threadCount)
            .runOn(Schedulers.newParallel("preview", threadCount))
            .flatMap(index -> {
                Integer counter = indexCounter.compute(index, (id, count) -> {
                    if (count == null) {
                        return 0;
                    }
                    return count + 1;
                });

                return Mono.from(testee()
                    .store(TestMessageId.of(index), Preview.from(String.valueOf(counter))))
                    .then();
            })
            .then()
            .block();

        IntStream.range(0, stepCount)
            .forEach(index -> assertThat(Mono.from(testee()
                    .retrieve(TestMessageId.of(index)))
                    .block())
                .isEqualTo(Preview.from(String.valueOf(repeatCount))));
    }

    @Test
    default void deleteShouldThrowWhenNullMessageId() {
        assertThatThrownBy(() -> Mono.from(testee().delete(null)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void deleteShouldNotThrowWhenMessageIdNotFound() {
        assertThatCode(() -> Mono.from(testee().delete(MESSAGE_ID_1)).block())
            .doesNotThrowAnyException();
    }

    @Test
    default void deleteShouldDeleteStoredRecord() {
        Mono.from(testee().store(MESSAGE_ID_1, PREVIEW_1))
            .block();

        Mono.from(testee().delete(MESSAGE_ID_1))
            .block();

        assertThat(Mono.from(testee().retrieve(MESSAGE_ID_1)).blockOptional())
            .isEmpty();
    }

    @Test
    default void deleteShouldNotDeleteAnotherRecord() {
        Mono.from(testee().store(MESSAGE_ID_1, PREVIEW_1))
            .block();
        Mono.from(testee().store(MESSAGE_ID_2, PREVIEW_2))
            .block();

        Mono.from(testee().delete(MESSAGE_ID_1))
            .block();

        assertThat(Mono.from(testee().retrieve(MESSAGE_ID_2)).block())
            .isEqualTo(PREVIEW_2);
    }

    @Test
    default void deleteShouldBeIdempotent() {
        Mono.from(testee().store(MESSAGE_ID_1, PREVIEW_1))
            .block();

        Mono.from(testee().delete(MESSAGE_ID_1))
            .block();
        Mono.from(testee().delete(MESSAGE_ID_1))
            .block();

        assertThat(Mono.from(testee().retrieve(MESSAGE_ID_1)).blockOptional())
            .isEmpty();
    }
}