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

package org.apache.james.queue.api;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.mailet.base.test.MimeMessageBuilder;

public interface MailQueueFixture {
    String NAME0 = "name0";
    String NAME1 = "name1";
    String NAME2 = "name2";
    String NAME3 = "name3";
    String NAME4 = "name4";
    String NAME5 = "name5";
    String NAME6 = "name6";
    String NAME7 = "name7";
    String NAME8 = "name8";
    String NAME9 = "name9";
    String NAME10 = "name10";

    static MimeMessage createMimeMessage() throws MessagingException {
        return MimeMessageBuilder.mimeMessageBuilder()
            .setText("test")
            .addHeader("testheader", "testvalie")
            .build();
    }
}
