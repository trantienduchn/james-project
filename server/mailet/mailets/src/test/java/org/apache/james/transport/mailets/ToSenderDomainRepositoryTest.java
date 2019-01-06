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

package org.apache.james.transport.mailets;

import static org.apache.mailet.base.MailAddressFixture.JAMES_LOCAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.mail.MessagingException;

import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.memory.MemoryMailRepository;
import org.apache.james.mailrepository.mock.MockMailRepositoryStore;
import org.apache.mailet.Mail;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ToSenderDomainRepositoryTest {

    private static final String MEMORY_URL_PREFIX = "memory://var/mail/dlp/";
    private static final FakeMailetConfig DEFAULT_MAILET_CONFIG = FakeMailetConfig.builder()
        .mailetName("TestConfig")
        .setProperty("urlPrefix", MEMORY_URL_PREFIX)
        .build();
    private ToSenderDomainRepository mailet;
    private MockMailRepositoryStore mailRepositoryStore;

    @BeforeEach
    void setup() {
        mailRepositoryStore = new MockMailRepositoryStore();
        mailRepositoryStore.add(MEMORY_URL_PREFIX + JAMES_LOCAL, new MemoryMailRepository());
        mailet = new ToSenderDomainRepository(mailRepositoryStore);
    }

    @Test
    void initShouldThrowExceptionWhenUrlPrefixIsAbsent() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .build();

        assertThatThrownBy(() -> mailet.init(mailetConfig))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void initShouldNotThrowWhenUrlPrefixIsPresent() {
        assertThatCode(
            () -> mailet.init(DEFAULT_MAILET_CONFIG))
            .doesNotThrowAnyException();
    }

    @Test
    void serviceShouldStoreMailInRepository() throws Exception {
        mailet.init(DEFAULT_MAILET_CONFIG);

        String mailName = "mailName";
        mailet.service(FakeMail.builder()
            .name(mailName)
            .sender(MailAddressFixture.SENDER)
            .build());

        MailRepository mailRepository = mailRepositoryStore.select(MEMORY_URL_PREFIX + JAMES_LOCAL);

        assertThat(mailRepository.list())
            .extracting(mailRepository::retrieve)
            .extracting(Mail::getName)
            .containsOnly(mailName);
    }

    @Test
    void serviceShouldGhostMailWhenNotPassThrough() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("TestConfig")
            .setProperty("passThrough", "false")
            .setProperty("urlPrefix", MEMORY_URL_PREFIX)
            .build();
        mailet.init(mailetConfig);

        FakeMail mail = FakeMail.builder()
            .name("mailName")
            .sender(MailAddressFixture.SENDER)
            .state(Mail.DEFAULT)
            .build();

        mailet.service(mail);

        assertThat(mail.getState())
            .isEqualTo(Mail.GHOST);
    }

    @Test
    void serviceShouldPreserveMailStateWhenPassThrough() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("TestConfig")
            .setProperty("passThrough", "true")
            .setProperty("urlPrefix", MEMORY_URL_PREFIX)
            .build();
        mailet.init(mailetConfig);

        FakeMail mail = FakeMail.builder()
            .name("mailName")
            .sender(MailAddressFixture.SENDER)
            .state(Mail.DEFAULT)
            .build();

        mailet.service(mail);

        assertThat(mail.getState())
            .isEqualTo(Mail.DEFAULT);
    }

    @Test
    void passThroughShouldDefaultToFalse() throws Exception {
        mailet.init(DEFAULT_MAILET_CONFIG);

        FakeMail mail = FakeMail.builder()
            .name("mailName")
            .sender(MailAddressFixture.SENDER)
            .state(Mail.DEFAULT)
            .build();

        mailet.service(mail);

        assertThat(mail.getState())
            .isEqualTo(Mail.GHOST);
    }

    @Test
    void initShouldSetNotPassThroughWhenPassThroughIsNotSet() throws Exception {
        MailRepositoryStore mailRepositoryStore = mock(MailRepositoryStore.class);
        ToSenderDomainRepository mailet = new ToSenderDomainRepository(mailRepositoryStore);
        when(mailRepositoryStore.select(any()))
            .thenThrow(new MailRepositoryStore.MailRepositoryStoreException("any"));

        mailet.init(DEFAULT_MAILET_CONFIG);

        FakeMail mail = FakeMail.builder()
            .name("mailName")
            .sender(MailAddressFixture.SENDER)
            .state(Mail.DEFAULT)
            .build();

        assertThatThrownBy(() -> mailet.service(mail))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void initShouldSetNotPassThroughWhenPassThroughIsNotBoolean() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("TestConfig")
            .setProperty("urlPrefix", MEMORY_URL_PREFIX)
            .setProperty("passThrough", "not boolean")
            .build();

        mailet.init(mailetConfig);

        FakeMail mail = FakeMail.builder()
            .name("mailName")
            .sender(MailAddressFixture.SENDER)
            .state(Mail.DEFAULT)
            .build();

        mailet.service(mail);

        assertThat(mail.getState())
            .isEqualTo(Mail.GHOST);
    }

    @Test
    void getMailetInfoShouldReturnExpectedResult() {
        assertThat(mailet.getMailetInfo())
            .isEqualTo("ToSenderDomainRepository Mailet");
    }
}
