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

package org.apache.james.utils.smtp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.apache.commons.io.IOUtils;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.RejectException;
import org.subethamail.smtp.TooMuchDataException;

public class MockMessageHandler implements MessageHandler {

    public static class Builder {

        private Consumer<String> fromMessageHandler;
        private Consumer<String> recipientMessageHandler;
        private Consumer<String> dataMessageHandler;
        private Consumer<MockMail> endMessageHandler;

        private Builder() {
            this.fromMessageHandler = from -> {};
            this.recipientMessageHandler = recipient -> {};
            this.dataMessageHandler = data -> {};
            this.endMessageHandler = mockMail -> {};
        }

        public Builder fromMessageHandler(Consumer<String> fromMessageHandler) {
            this.fromMessageHandler = fromMessageHandler;
            return this;
        }

        public Builder recipientMessageHandler(Consumer<String> recipientMessageHandler) {
            this.recipientMessageHandler = recipientMessageHandler;
            return this;
        }

        public Builder dataMessageHandler(Consumer<String> dataMessageHandler) {
            this.dataMessageHandler = dataMessageHandler;
            return this;
        }

        Builder endMessageHandler(Consumer<MockMail> endMessageHandler) {
            this.endMessageHandler = endMessageHandler;
            return this;
        }

        public MockMessageHandler build() {
            return new MockMessageHandler(fromMessageHandler, recipientMessageHandler,
                dataMessageHandler, endMessageHandler);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final Consumer<String> fromMessageHandler;
    private final Consumer<String> recipientMessageHandler;
    private final Consumer<String> dataMessageHandler;
    private final Consumer<MockMail> endMessageHandler;
    private final MockMail.Builder mailBuilder;

    private MockMessageHandler(Consumer<String> fromMessageHandler, Consumer<String> recipientMessageHandler,
                              Consumer<String> dataMessageHandler, Consumer<MockMail> endMessageHandler) {
        this.fromMessageHandler = fromMessageHandler;
        this.recipientMessageHandler = recipientMessageHandler;
        this.dataMessageHandler = dataMessageHandler;
        this.endMessageHandler = endMessageHandler;
        this.mailBuilder = MockMail.builder();
    }

    @Override
    public void from(String from) throws RejectException {
        fromMessageHandler.accept(from);
        mailBuilder.setFrom(from);
    }

    @Override
    public void recipient(String recipient) throws RejectException {
        recipientMessageHandler.accept(recipient);
        mailBuilder.addRecipient(recipient);
    }

    @Override
    public void data(InputStream data) throws RejectException, TooMuchDataException, IOException {
        String dataString = IOUtils.toString(data, StandardCharsets.UTF_8);
        dataMessageHandler.accept(dataString);
        mailBuilder.setContent(dataString);
    }

    @Override
    public void done() {
        MockMail mockMail = mailBuilder.build();
        endMessageHandler.accept(mockMail);
    }
}