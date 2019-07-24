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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.apache.james.core.User;
import org.apache.james.util.Port;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.SMTPSendingException;
import org.apache.james.utils.SmtpSendingStep;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.subethamail.smtp.RejectException;

class MockSmtpServerTest {

    private static final String DOMAIN = "james.org";
    private static final User SENDER_1 = User.from("sender1", Optional.of(DOMAIN));
    private static final User SENDER_2 = User.from("sender2", Optional.of(DOMAIN));
    private static final User RECIPIENT_1 = User.from("recipient1", Optional.of(DOMAIN));
    private static final User RECIPIENT_2 = User.from("recipient2", Optional.of(DOMAIN));

    private MockSmtpServer server;
    private SMTPMessageSender smtpSender;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockSmtpServer();
        server.start();

        smtpSender = new SMTPMessageSender(DOMAIN)
            .connect("localhost", Port.of(server.getPort()));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.stop();
        smtpSender.close();
    }

    @Test
    void serverShouldReceiveAllMessagesFromSender() throws Exception {
        smtpSender.sendMessage(SENDER_1, RECIPIENT_1);
        smtpSender.sendMessage(SENDER_2, RECIPIENT_2);

        assertThat(server.messageStream())
            .anySatisfy(mockMail -> SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(mockMail.getFrom()).isEqualTo(SENDER_1);
                softly.assertThat(mockMail.getRecipients()).containsOnly(RECIPIENT_1);
            }))
            .anySatisfy(mockMail -> SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(mockMail.getFrom()).isEqualTo(SENDER_2);
                softly.assertThat(mockMail.getRecipients()).containsOnly(RECIPIENT_2);
            }));
    }

    @Test
    void serverShouldReturnErrorCodeWhenSetupErrorResponseCode() throws Exception {
        server.setMessageHandler(MockMessageHandler.builder()
            .fromMessageHandler(from -> {
                throw new RejectException(500, "the server could not recognize the command");
            }));

        assertThatThrownBy(() -> smtpSender.sendMessage(SENDER_1, RECIPIENT_1))
            .isEqualTo(new SMTPSendingException(SmtpSendingStep.Sender, "500 the server could not recognize the command"));
    }

    @Test
    void serverShouldRejectAtTheMockedStep() throws Exception {
        server.setMessageHandler(MockMessageHandler.builder()
            .recipientMessageHandler(recipient -> {
                throw new RejectException(431, "out of memory");
            }));

        assertThatThrownBy(() -> smtpSender.sendMessage(SENDER_1, RECIPIENT_1))
            .isEqualTo(new SMTPSendingException(SmtpSendingStep.RCPT, "431 out of memory"));
    }

    @Disabled("this is not supported")
    @Test
    void serverShouldAllowToSetupMultipleExpectedCallsForOneStep() throws Exception {
        server.setMessageHandler(MockMessageHandler.builder()
            .recipientMessageHandler(recipient -> {
                throw new RejectException(431, "out of memory");
            })
            .recipientMessageHandler(recipient -> {}));

        assertThatThrownBy(() -> smtpSender.sendMessage(SENDER_1, RECIPIENT_1))
            .isEqualTo(new SMTPSendingException(SmtpSendingStep.RCPT, "431 out of memory"));

        smtpSender.sendMessage(SENDER_1, RECIPIENT_1);

        assertThat(server.messageStream())
            .anySatisfy(mockMail -> SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(mockMail.getFrom()).isEqualTo(SENDER_1);
                softly.assertThat(mockMail.getRecipients()).containsOnly(RECIPIENT_1);
            }));
    }

    @Disabled("this is not supported")
    @Test
    void serverShouldAllowRetryAbleHandler() throws Exception {
        server.setMessageHandler(MockMessageHandler.builder()
            .recipientMessageHandler(recipient -> {
                throw new RejectException(450, "The server will retry to mail the message again");
            }));

        assertThatThrownBy(() -> smtpSender.sendMessage(SENDER_1, RECIPIENT_1))
            .isEqualTo(new SMTPSendingException(SmtpSendingStep.RCPT, "450 The server will retry to mail the message again"));

        assertThat(server.messageStream())
            .anySatisfy(mockMail -> SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(mockMail.getFrom()).isEqualTo(SENDER_1);
                softly.assertThat(mockMail.getRecipients()).containsOnly(RECIPIENT_1);
            }));
    }
}