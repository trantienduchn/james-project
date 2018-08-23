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

package org.apache.james.jmap.mailet.filter;

import static org.assertj.core.api.Assertions.assertThat;

import javax.mail.MessagingException;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FieldContentExtractorTest {

    private static final String TO_HEADER = "to";
    private static final String CC_HEADER = "cc";
    private static final String SUBJECT_HEADER = "subject";

    private static final String SENDER = "sender@james.org";
    private static final String RECIPIENT_1 = "recipient1@james.org";
    private static final String RECIPIENT_2 = "recipient2@james.org";
    private static final String TO_1 = "to1@james.org";
    private static final String TO_2 = "to2@james.org";
    private static final String CC_1 = "cc1@james.org";
    private static final String CC_2 = "cc2@james.org";
    private static final String SUBJECT_1 = "subject1@james.org";
    private static final String SUBJECT_2 = "subject2@james.org";

    @Nested
    class FromExtractorTest {

        @Test
        void getContentShouldReturnStreamOfTheSender() throws MessagingException {
            FakeMail mail = FakeMail.builder()
                    .sender(SENDER)
                    .recipients(RECIPIENT_1, RECIPIENT_2)
                    .build();

            FieldContentExtractor.FromExtractor testee = new FieldContentExtractor.FromExtractor();

            assertThat(testee.getContent(mail))
                .containsOnly(SENDER);
        }
    }

    @Nested
    class ToExtractorTest {

        @Test
        void getContentShouldReturnStreamOfTOs() throws MessagingException {
            FakeMail mail = FakeMail.builder()
                    .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                        .addHeader(TO_HEADER, TO_1)
                        .addHeader(TO_HEADER, TO_2)
                        .build())
                    .build();

            FieldContentExtractor.ToExtractor testee = new FieldContentExtractor.ToExtractor();

            assertThat(testee.getContent(mail))
                .containsExactly(TO_1, TO_2);
        }
    }

    @Nested
    class CCExtractorTest {

        @Test
        void getContentShouldReturnStreamOfCCs() throws MessagingException {
            FakeMail mail = FakeMail.builder()
                    .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                        .addHeader(CC_HEADER, CC_1)
                        .addHeader(CC_HEADER, CC_2)
                        .build())
                    .build();

            FieldContentExtractor.CCExtractor testee = new FieldContentExtractor.CCExtractor();

            assertThat(testee.getContent(mail))
                .containsExactly(CC_1, CC_2);
        }
    }

    @Nested
    class SubjectExtractorTest {

        @Test
        void getContentShouldReturnStreamOfCCs() throws MessagingException {
            FakeMail mail = FakeMail.builder()
                    .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                        .addHeader(SUBJECT_HEADER, SUBJECT_1)
                        .addHeader(SUBJECT_HEADER, SUBJECT_2)
                        .build())
                    .build();

            FieldContentExtractor.SubjectExtractor testee = new FieldContentExtractor.SubjectExtractor();

            assertThat(testee.getContent(mail))
                .containsExactly(SUBJECT_1, SUBJECT_2);
        }
    }

    @Nested
    class RecipientExtractorTest {

        @Test
        void getContentShouldReturnStreamOfCCs() throws MessagingException {
            FakeMail mail = FakeMail.builder()
                    .sender(SENDER)
                    .recipients(RECIPIENT_1, RECIPIENT_2)
                    .build();

            FieldContentExtractor.RecipientExtractor testee = new FieldContentExtractor.RecipientExtractor();

            assertThat(testee.getContent(mail))
                .containsExactly(RECIPIENT_1, RECIPIENT_2);
        }
    }
}