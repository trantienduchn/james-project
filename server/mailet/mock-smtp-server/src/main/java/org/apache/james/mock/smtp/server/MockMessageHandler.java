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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Consumer;

import javax.mail.internet.AddressException;

import org.apache.commons.io.IOUtils;
import org.apache.james.core.MailAddress;
import org.apache.james.mock.smtp.server.Behaviors.Behavior;
import org.apache.james.mock.smtp.server.Behaviors.Behavior.BehavingState;
import org.apache.james.mock.smtp.server.Behaviors.LinkedBehavior;
import org.apache.james.mock.smtp.server.Behaviors.MockBehavior;
import org.apache.james.mock.smtp.server.Behaviors.SMTPBehaviorRepositoryUpdater;
import org.apache.james.mock.smtp.server.model.Mail;
import org.apache.james.mock.smtp.server.model.MockSMTPBehavior;
import org.apache.james.mock.smtp.server.model.MockSMTPBehaviorInformation;
import org.apache.james.mock.smtp.server.model.Response.SMTPStatusCode;
import org.apache.james.mock.smtp.server.model.SMTPCommand;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.RejectException;
import org.subethamail.smtp.TooMuchDataException;

public class MockMessageHandler implements MessageHandler {

    static <T> Behavior<T> buildMessageBehavior(Consumer<T> buildingStage) {
        return state -> buildingStage.accept(state.getInputData());
    }

    private final Mail.Envelope.Builder envelopeBuilder;
    private final Mail.Builder mailBuilder;
    private final ReceivedMailRepository mailRepository;
    private final SMTPBehaviorRepository behaviorRepository;

    MockMessageHandler(ReceivedMailRepository mailRepository, SMTPBehaviorRepository behaviorRepository) {
        this.mailRepository = mailRepository;
        this.behaviorRepository = behaviorRepository;
        this.envelopeBuilder = new Mail.Envelope.Builder();
        this.mailBuilder = new Mail.Builder();
    }

    @Override
    public void from(String from) throws RejectException {
        BehavingState<MailAddress> initState = BehavingState.init(
            firstMatchedBehavior(SMTPCommand.MAIL_FROM),
            parse(from));

        LinkedBehavior.current(buildMessageBehavior(envelopeBuilder::from))
            .withNext(new MockBehavior<>())
            .withNext(new SMTPBehaviorRepositoryUpdater<>(behaviorRepository))
            .withNext(new Behaviors.Terminator<>())
            .behave(initState);
    }

    @Override
    public void recipient(String recipient) throws RejectException {
        BehavingState<MailAddress> initState = BehavingState.init(
            firstMatchedBehavior(SMTPCommand.RCPT_TO),
            parse(recipient));

        LinkedBehavior.current(buildMessageBehavior(envelopeBuilder::addRecipient))
            .withNext(new MockBehavior<>())
            .withNext(new SMTPBehaviorRepositoryUpdater<>(behaviorRepository))
            .withNext(new Behaviors.Terminator<>())
            .behave(initState);
    }

    @Override
    public void data(InputStream data) throws RejectException, TooMuchDataException, IOException {
        BehavingState<InputStream> initState = BehavingState.init(
            firstMatchedBehavior(SMTPCommand.DATA),
            data);

        LinkedBehavior.<InputStream>current(buildMessageBehavior(content -> mailBuilder.message(readData(content))))
            .withNext(new MockBehavior<>())
            .withNext(new SMTPBehaviorRepositoryUpdater<>(behaviorRepository))
            .withNext(new Behaviors.Terminator<>())
            .behave(initState);
    }

    private Optional<MockSMTPBehavior> firstMatchedBehavior(SMTPCommand data) {
        return behaviorRepository.remainingBehaviors()
            .map(MockSMTPBehaviorInformation::getBehavior)
            .filter(behavior -> behavior.getCommand().equals(data))
            .findFirst();
    }

    @Override
    public void done() {
        Mail mail = mailBuilder.envelope(envelopeBuilder.build())
            .build();
        mailRepository.store(mail);
    }

    private String readData(InputStream data) {
        try {
            return IOUtils.toString(data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RejectException(SMTPStatusCode.SYNTAX_ERROR_IN_PARAMETERS_OR_ARGUMENTS_501.getRawCode(), "invalid data supplied");
        }
    }

    private MailAddress parse(String mailAddress) {
        try {
            return new MailAddress(mailAddress);
        } catch (AddressException e) {
            throw new RejectException(SMTPStatusCode.SYNTAX_ERROR_IN_PARAMETERS_OR_ARGUMENTS_501.getRawCode(), "invalid email address supplied");
        }
    }
}
