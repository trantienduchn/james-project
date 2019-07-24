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

import java.util.ArrayList;
import java.util.List;

import org.apache.james.core.User;
import org.subethamail.smtp.server.Session;

import com.google.common.base.Preconditions;

class MockMailProcessingState {

    static MockMailProcessingState start(MessageStateHandler completeHandler, Session session) {
        return new MockMailProcessingState(completeHandler, session);
    }

    private final MessageStateHandler completeHandler;
    private final Session session;

    private User from;
    private List<User> recipients;
    private String content;

    MockMailProcessingState(MessageStateHandler completeHandler, Session session) {
        this.completeHandler = completeHandler;
        this.session = session;
        this.recipients = new ArrayList<>();
    }

    void setFrom(String from) {
        this.from = User.fromUsername(from);
    }

    void addRecipient(String recipient) {
        Preconditions.checkNotNull(recipient);

        this.recipients.add(User.fromUsername(recipient));
    }

    void setContent(String content) {
        Preconditions.checkNotNull(content);

        this.content = content;
    }

    void complete() {
        completeHandler.handle(this);
    }

    Session getSession() {
        return session;
    }

    MockMail buildMockMail() {
        return new MockMail(from, recipients, content);
    }
}
