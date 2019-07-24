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

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;

import org.subethamail.smtp.server.SMTPServer;
import org.subethamail.smtp.server.Session;

public class MockSmtpServer {

    private static class MockSmtpMailRepository {

        private final ConcurrentLinkedQueue<MockMail> mailList;

        MockSmtpMailRepository() {
            mailList = new ConcurrentLinkedQueue<>();
        }

        void store(MockMail mockMail) {
            mailList.add(mockMail);
        }

        Stream<MockMail> asStream() {
            return mailList.stream();
        }
    }

    private final SMTPServer server;
    private final MockSmtpMailRepository mailRepository;

    private MockMessageHandler.Builder getMessageHandlerBuilder;

    public MockSmtpServer() {
        this.mailRepository = new MockSmtpMailRepository();
        this.getMessageHandlerBuilder = MockMessageHandler.builder();
        this.server = new SMTPServer(ctx -> prepareMessageBuilder((Session) ctx).build());
        this.server.setPort(0);
    }

    public void setMessageHandler(MockMessageHandler.Builder getMessageHandlerBuilder) {
        this.getMessageHandlerBuilder = getMessageHandlerBuilder;
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop();
    }

    public int getPort() {
        return server.getPort();
    }

    public Stream<MockMail> messageStream() {
        return mailRepository.asStream();
    }

    private MockMessageHandler.Builder getMessageHandlerBuilder() {
        return getMessageHandlerBuilder;
    }

    private MockMessageHandler.Builder prepareMessageBuilder(Session session) {
        return getMessageHandlerBuilder()
            .session(session)
            .endMessageHandler(mailState -> mailRepository.store(mailState.buildMockMail()));
    }
}
