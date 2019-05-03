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

package org.apache.james.mailrepository.api.properties.eventsourcing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class PropertiesAggregateIdTest {

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(PropertiesAggregateId.class)
            .verify();
    }

    @Test
    void constructorShouldThrowWhenPassingNullUrl() {
        assertThatThrownBy(() -> new PropertiesAggregateId(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void parseShouldThrowWhenAggregateIdStringIsNull() {
        assertThatThrownBy(() -> PropertiesAggregateId.parse(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void parseShouldThrowWhenAggregateKeyStringStartsWithASlash() {
        assertThatThrownBy(() -> PropertiesAggregateId.parse("/MailRepositoryProperties/protocol://var/mail"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("aggregate key string should start with 'MailRepositoryProperties/'");
    }

    @Test
    void parseShouldThrowWhenAggregateKeyStringDoesntContainsTheRightAggregateKey() {
        assertThatThrownBy(() -> PropertiesAggregateId.parse("AnotherAggregateKey/protocol://var/mail"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("aggregate key string should start with 'MailRepositoryProperties/'");
    }

    @Test
    void parseShouldThrowWhenMailRepositoryUrlStringIsInvalid() {
        assertThatThrownBy(() -> PropertiesAggregateId.parse("/MailRepositoryProperties/protocol:var/mail"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseShouldReturnTheCorrespondingAggregateId() {
        String mailRepositoryUrlString = "protocol://var/mail";

        assertThat(PropertiesAggregateId.parse("MailRepositoryProperties/" + mailRepositoryUrlString))
            .isEqualTo(new PropertiesAggregateId(MailRepositoryUrl.from(mailRepositoryUrlString)));
    }
}