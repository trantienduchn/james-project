/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.james;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;

import javax.mail.Authenticator;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.search.FlagTerm;

import org.apache.james.core.Domain;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.SpoolerProbe;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import com.google.common.io.Resources;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

interface MailsShouldBeWellReceived {

    String JAMES_SERVER_HOST = "127.0.0.1";
    String DOMAIN = "apache.org";
    String JAMES_USER = "james-user@" + DOMAIN;
    String PASSWORD = "secret";
    ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(Duration.ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(Duration.ONE_HUNDRED_MILLISECONDS)
        .await();

    ConditionFactory CALMLY_AWAIT_FIVE_MINUTE = CALMLY_AWAIT.timeout(Duration.FIVE_MINUTES);
    String SENDER = "bob@apache.org";
    String UNICODE_BODY = "unicode character 'Ð'";


    static Message readFirstMessageJavax(int imapPort) throws MessagingException {
        Session imapSession = Session.getDefaultInstance(new Properties());
        Store store = imapSession.getStore("imap");
        store.connect("localhost", imapPort, JAMES_USER, PASSWORD);
        Folder inbox = store.getFolder(IMAPMessageReader.INBOX);
        inbox.open(Folder.READ_ONLY);

        CALMLY_AWAIT.untilAsserted(() ->
            assertThat(searchForUnSeen(inbox))
                .hasSize(1));

        return searchForUnSeen(inbox)[0];
    }

    static Message[] searchForUnSeen(Folder inbox) throws MessagingException {
        return inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
    }

    static void sendMessageJavax(GuiceJamesServer server) throws MessagingException {
        Port smtpPort = server.getProbe(SmtpGuiceProbe.class).getSmtpPort();

        Properties props = new Properties();
        props.put("mail.smtp.host", "localhost");
        props.put("mail.smtp.port", smtpPort.getValue() + "");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.allow8bitmime", "true"); //force to use 8bit(UTF-8), otherwise, it automatically converts into 7bit

        Authenticator auth = new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SENDER, PASSWORD);
            }
        };

        Session session = Session.getInstance(props, auth);
        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(SENDER));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(JAMES_USER));
        message.setText(UNICODE_BODY);

        Transport.send(message);
    }

    @Test
    default void mailsContentWithUnicodeCharactersShouldBeKeptUnChanged(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(JAMES_USER, PASSWORD);

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox("#private", JAMES_USER, DefaultMailboxes.INBOX);

        sendMessageJavax(server);

        CALMLY_AWAIT.until(() -> server.getProbe(SpoolerProbe.class).processingFinished());

        try (IMAPMessageReader reader = new IMAPMessageReader()) {
            int imapPort = server.getProbe(ImapGuiceProbe.class).getImapPort();
            reader.connect(JAMES_SERVER_HOST, imapPort)
                .login(JAMES_USER, PASSWORD)
                .select(IMAPMessageReader.INBOX);

            assertThat(readFirstMessageJavax(imapPort).getInputStream())
                .hasContent(UNICODE_BODY);
        }
    }

    @Test
    default void mailsShouldBeWellReceived(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(JAMES_USER, PASSWORD);

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox("#private", JAMES_USER, DefaultMailboxes.INBOX);

        Port smtpPort = server.getProbe(SmtpGuiceProbe.class).getSmtpPort();
        String message = Resources.toString(Resources.getResource("eml/htmlMail.eml"), StandardCharsets.UTF_8);

        try (SMTPMessageSender sender = new SMTPMessageSender(Domain.LOCALHOST.asString())) {
            Mono.fromRunnable(
                Throwing.runnable(() -> {
                    sender.connect(JAMES_SERVER_HOST, smtpPort);
                    sendUniqueMessage(sender, message);
                }))
                .subscribeOn(Schedulers.elastic())
                .block();
        }

        CALMLY_AWAIT.until(() -> server.getProbe(SpoolerProbe.class).processingFinished());

        try (IMAPMessageReader reader = new IMAPMessageReader()) {
            reader.connect(JAMES_SERVER_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(JAMES_USER, PASSWORD)
                .select(IMAPMessageReader.INBOX)
                .awaitMessageCount(CALMLY_AWAIT, 1);
        }

    }

    @Test
    default void oneHundredMailsShouldBeWellReceived(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(JAMES_USER, PASSWORD);

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox("#private", JAMES_USER, DefaultMailboxes.INBOX);

        int messageCount = 100;

        Port smtpPort = server.getProbe(SmtpGuiceProbe.class).getSmtpPort();
        String message = Resources.toString(Resources.getResource("eml/htmlMail.eml"), StandardCharsets.UTF_8);

        try (SMTPMessageSender sender = new SMTPMessageSender(Domain.LOCALHOST.asString())) {
            Mono.fromRunnable(
                Throwing.runnable(() -> {
                    sender.connect(JAMES_SERVER_HOST, smtpPort);
                    sendUniqueMessage(sender, message);
            }))
                .repeat(messageCount - 1)
                .subscribeOn(Schedulers.elastic())
                .blockLast();
        }

        CALMLY_AWAIT_FIVE_MINUTE.until(() -> server.getProbe(SpoolerProbe.class).processingFinished());

        try (IMAPMessageReader reader = new IMAPMessageReader()) {
            reader.connect(JAMES_SERVER_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(JAMES_USER, PASSWORD)
                .select(IMAPMessageReader.INBOX)
                .awaitMessageCount(CALMLY_AWAIT, messageCount);
        }
    }

    default void sendUniqueMessage(SMTPMessageSender sender, String message) throws IOException {
        String uniqueMessage = message.replace("banana", "UUID " + UUID.randomUUID().toString());
        sender.sendMessageWithHeaders("bob@apache.org", JAMES_USER, uniqueMessage);
    }
}
