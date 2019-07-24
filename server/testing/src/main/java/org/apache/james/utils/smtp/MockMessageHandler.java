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

import org.apache.commons.io.IOUtils;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.RejectException;
import org.subethamail.smtp.TooMuchDataException;
import org.subethamail.smtp.server.Session;

public class MockMessageHandler implements MessageHandler {

    public static class Builder {

        private MessageStateHandler fromMessageHandler;
        private MessageStateHandler recipientMessageHandler;
        private MessageStateHandler dataMessageHandler;
        private MessageStateHandler endMessageHandler;
        private Session session;

        private Builder() {
            this.fromMessageHandler = MessageStateHandler.noop();
            this.recipientMessageHandler = MessageStateHandler.noop();
            this.dataMessageHandler = MessageStateHandler.noop();
            this.endMessageHandler = MessageStateHandler.noop();
        }

        public Builder fromMessageHandler(MessageStateHandler fromMessageHandler) {
            this.fromMessageHandler = fromMessageHandler;
            return this;
        }

        public Builder recipientMessageHandler(MessageStateHandler recipientMessageHandler) {
            this.recipientMessageHandler = recipientMessageHandler;
            return this;
        }

        public Builder dataMessageHandler(MessageStateHandler dataMessageHandler) {
            this.dataMessageHandler = dataMessageHandler;
            return this;
        }

        Builder endMessageHandler(MessageStateHandler endMessageHandler) {
            this.endMessageHandler = endMessageHandler;
            return this;
        }

        Builder session(Session session) {
            this.session = session;
            return this;
        }

        public MockMessageHandler build() {
            return new MockMessageHandler(fromMessageHandler, recipientMessageHandler,
                dataMessageHandler, endMessageHandler, session);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final MessageStateHandler fromMessageHandler;
    private final MessageStateHandler recipientMessageHandler;
    private final MessageStateHandler dataMessageHandler;
    private final MockMailProcessingState mailState;

    private MockMessageHandler(MessageStateHandler fromMessageHandler, MessageStateHandler recipientMessageHandler,
                               MessageStateHandler dataMessageHandler, MessageStateHandler endMessageHandler,
                               Session session) {
        this.fromMessageHandler = fromMessageHandler;
        this.recipientMessageHandler = recipientMessageHandler;
        this.dataMessageHandler = dataMessageHandler;
        this.mailState = MockMailProcessingState.start(endMessageHandler, session);
    }

    @Override
    public void from(String from) throws RejectException {
        mailState.setFrom(from);
        fromMessageHandler.handle(mailState);
    }

    @Override
    public void recipient(String recipient) throws RejectException {
        mailState.addRecipient(recipient);
        recipientMessageHandler.handle(mailState);
    }

    @Override
    public void data(InputStream data) throws RejectException, TooMuchDataException, IOException {
        String dataString = IOUtils.toString(data, StandardCharsets.UTF_8);
        mailState.setContent(dataString);
        dataMessageHandler.handle(mailState);
    }

    @Override
    public void done() {
        mailState.complete();
    }
}