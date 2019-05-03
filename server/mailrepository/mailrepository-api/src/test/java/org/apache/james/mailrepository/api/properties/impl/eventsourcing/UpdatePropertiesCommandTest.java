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

package org.apache.james.mailrepository.api.properties.impl.eventsourcing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mailrepository.api.properties.MailRepositoryProperties;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class UpdatePropertiesCommandTest {

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(UpdatePropertiesCommand.class)
            .verify();
    }

    @Test
    void constructorShouldThrowWhenPassingNullUrl() {
        MailRepositoryUrl nullUrl = null;
        assertThatThrownBy(() -> new UpdatePropertiesCommand(nullUrl,
            MailRepositoryProperties.builder()
                .canBrowse()
                .build()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorShouldThrowWhenPassingNullProperties() {
        MailRepositoryProperties nullProperties = null;
        assertThatThrownBy(() -> new UpdatePropertiesCommand(
                MailRepositoryUrl.from("protocol://mail/repository"),
                nullProperties))
            .isInstanceOf(NullPointerException.class);
    }
}