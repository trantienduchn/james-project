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

package org.apache.james.backend.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class RabbitMQQueueNameTest {

    @Test
    void fromStringShouldThrowWhenNull() {
        assertThatThrownBy(() -> RabbitMQQueueName.fromString(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void fromStringShouldReturnInstanceWhenEmptyString() {
        assertThat(RabbitMQQueueName.fromString("")).isNotNull();
    }

    @Test
    void fromStringShouldReturnInstanceWhenArbitraryString() {
        assertThat(RabbitMQQueueName.fromString("whatever")).isNotNull();
    }

    @Test
    void fromRabbitWorkQueueNameShouldThrowWhenNull() {
        assertThatThrownBy(() -> RabbitMQQueueName.fromRabbitWorkQueueName(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void fromRabbitWorkQueueNameShouldReturnEmptyWhenArbitraryString() {
        assertThat(RabbitMQQueueName.fromRabbitWorkQueueName("whatever"))
            .isEmpty();
    }

    @Test
    void fromRabbitWorkQueueNameShouldReturnInstanceWhenPrefixOnlyString() {
        assertThat(RabbitMQQueueName.fromRabbitWorkQueueName(RabbitMQQueueName.WORKQUEUE_PREFIX))
            .contains(RabbitMQQueueName.fromString(""));
    }

    @Test
    void fromRabbitWorkQueueNameShouldReturnInstanceWhenValidQueueName() {
        assertThat(RabbitMQQueueName.fromRabbitWorkQueueName(RabbitMQQueueName.WORKQUEUE_PREFIX + "myQueue"))
            .contains(RabbitMQQueueName.fromString("myQueue"));
    }

    @Test
    void shouldConformToBeanContract() {
        EqualsVerifier.forClass(RabbitMQQueueName.class).verify();
    }

    @Test
    void exchangeNameShouldConformToBeanContract() {
        EqualsVerifier.forClass(RabbitMQQueueName.ExchangeName.class).verify();
    }

    @Test
    void workQueueNameShouldConformToBeanContract() {
        EqualsVerifier.forClass(RabbitMQQueueName.WorkQueueName.class).verify();
    }

    @Test
    void fromRabbitWorkQueueNameShouldReturnIdentityWhenToRabbitWorkQueueName() {
        RabbitMQQueueName myQueue = RabbitMQQueueName.fromString("myQueue");
        assertThat(RabbitMQQueueName.fromRabbitWorkQueueName(myQueue.toWorkQueueName().asString()))
            .contains(myQueue);
    }

}