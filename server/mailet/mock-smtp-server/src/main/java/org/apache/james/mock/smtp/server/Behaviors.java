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

package org.apache.james.mock.smtp.server;

import java.io.IOException;
import java.util.Optional;

import org.apache.james.mock.smtp.server.model.MockSMTPBehavior;
import org.apache.james.mock.smtp.server.model.Response;
import org.subethamail.smtp.RejectException;
import org.subethamail.smtp.server.Session;

import com.google.common.annotations.VisibleForTesting;

interface Behaviors {

    @FunctionalInterface
    interface Behavior<T> {

        class BehavingState<T> {

            static <T> BehavingState<T> nonMatched(T inputData) {
                return new BehavingState<>(Optional.empty(), inputData);
            }

            static <T> BehavingState<T> init(Optional<MockSMTPBehavior> matchedBehavior, T inputData) {
                return new BehavingState<>(matchedBehavior, inputData);
            }

            private final Optional<MockSMTPBehavior> matchedBehavior;
            private final T inputData;
            private Optional<RejectException> rejectException;

            BehavingState(Optional<MockSMTPBehavior> matchedBehavior, T inputData) {
                this.matchedBehavior = matchedBehavior;
                this.inputData = inputData;
                this.rejectException = Optional.empty();
            }

            void setRejectException(RejectException exception) {
                rejectException = Optional.of(exception);
            }

            T getInputData() {
                return inputData;
            }

            @VisibleForTesting
            Optional<RejectException> getRejectException() {
                return rejectException;
            }

            @VisibleForTesting
            boolean hasRejectException() {
                return rejectException.isPresent();
            }
        }

        void behave(BehavingState<T> state) throws RejectException;
    }

    class LinkedBehavior<T> implements Behavior<T> {

        static <T> LinkedBehavior<T> current(Behavior<T> current) {
            return new LinkedBehavior<>(current);
        }

        private final Behavior<T> current;
        private Optional<Behavior<T>> next;

        LinkedBehavior(Behavior<T> current) {
            this.current = current;
            this.next = Optional.empty();
        }

        private LinkedBehavior(Behavior<T> current, Behavior<T> next) {
            this.current = current;
            this.next = Optional.ofNullable(next);
        }

        @Override
        public void behave(BehavingState<T> state) throws RejectException {
            current.behave(state);
            next.ifPresent(nextBehavior -> nextBehavior.behave(state));
        }

        LinkedBehavior<T> withNext(Behavior<T> next) {
            return this.next
                .map(existedNextBehavior -> LinkedBehavior.current(this)
                    .withNext(next))
                .orElseGet(() -> new LinkedBehavior<>(current, next));
        }
    }

    class Terminator<T> implements Behavior<T> {

        @Override
        public void behave(BehavingState<T> state) throws RejectException {
            state.rejectException
                .map(rejectException -> {
                    throw rejectException;
                });
        }
    }

    class MockBehavior<T> implements Behavior<T> {

        private final Session smtpSession;

        MockBehavior(Session smtpSession) {
            this.smtpSession = smtpSession;
        }

        @Override
        public void behave(BehavingState<T> state) throws RejectException {
            state.matchedBehavior.ifPresent(behavior -> {
                Response response = behavior.getResponse();
                if (response.isServerRejected()) {
                    state.setRejectException(new RejectException(response.getCode().getRawCode(), response.getMessage()));
                } else {
                    sendResponse(behavior);
                }
            });
        }

        private void sendResponse(MockSMTPBehavior behavior) {
            try {
                smtpSession.sendResponse(
                    behavior.getResponse().asReplyString());
            } catch (IOException e) {
                throw new RuntimeException(String.format("Cannot send response '%s' to the client",
                    behavior.getResponse().asReplyString()));
            }
        }
    }

    class SMTPBehaviorRepositoryUpdater<T> implements Behavior<T> {

        private final SMTPBehaviorRepository behaviorRepository;

        SMTPBehaviorRepositoryUpdater(SMTPBehaviorRepository behaviorRepository) {
            this.behaviorRepository = behaviorRepository;
        }

        @Override
        public void behave(BehavingState<T> state) throws RejectException {
            state.matchedBehavior.ifPresent(behaviorRepository::decreaseRemainingAnswers);
        }
    }
}
