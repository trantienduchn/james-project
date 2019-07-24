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

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class MockMail {

    public static class Builder {
        private User from;
        private List<User> recipients;
        private String content;

        public Builder() {
            this.recipients = new ArrayList<>();
        }

        void setFrom(String from) {
            this.from = User.fromUsername(from);
        }

        void addRecipient(String recipient) {
            Preconditions.checkNotNull(recipient);

            this.recipients.add(User.fromUsername(recipient));
        }

        public void setContent(String content) {
            this.content = content;
        }

        public MockMail build() {
            return new MockMail(from, recipients, content);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final User from;
    private final List<User> recipients;
    private final String content;

    private MockMail(User from, List<User> recipients, String content) {
        Preconditions.checkNotNull(from);
        Preconditions.checkNotNull(recipients);
        Preconditions.checkNotNull(content);

        this.from = from;
        this.recipients = recipients;
        this.content = content;
    }

    public User getFrom() {
        return from;
    }

    public List<User> getRecipients() {
        return recipients;
    }

    public String getContent() {
        return content;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("from", from)
            .add("recipients", recipients)
            .add("content", content)
            .toString();
    }
}
