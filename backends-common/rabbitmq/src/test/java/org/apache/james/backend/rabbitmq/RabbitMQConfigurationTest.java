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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class RabbitMQConfigurationTest {

    @Test
    void shouldRespectBeanContract() {
        EqualsVerifier.forClass(RabbitMQConfiguration.class).verify();
    }

    @Test
    void managementCredentialShouldRespectBeanContract() {
            EqualsVerifier.forClass(RabbitMQConfiguration.ManagementCredential.class)
                .verify();
        }

    @Test
    void fromShouldThrowWhenURIIsNotInTheConfiguration() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify the URI of RabbitMQ");
    }

    @Test
    void fromShouldThrowWhenURIIsNull() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("uri", null);

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify the URI of RabbitMQ");
    }

    @Test
    void fromShouldThrowWhenURIIsEmpty() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("uri", "");

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify the URI of RabbitMQ");
    }

    @Test
    void fromShouldThrowWhenURIIsInvalid() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("uri", ":invalid");

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify a valid URI");
    }

    @Test
    void fromShouldThrowWhenManagementURIIsNotInTheConfiguration() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("uri", "amqp://james:james@rabbitmq_host:5672");

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify the management URI of RabbitMQ");
    }

    @Test
    void fromShouldThrowWhenManagementURIIsNull() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("uri", "amqp://james:james@rabbitmq_host:5672");
        configuration.addProperty("management.uri", null);

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify the management URI of RabbitMQ");
    }

    @Test
    void fromShouldThrowWhenManagementURIIsEmpty() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("uri", "amqp://james:james@rabbitmq_host:5672");
        configuration.addProperty("management.uri", "");

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify the management URI of RabbitMQ");
    }

    @Test
    void fromShouldThrowWhenManagementURIIsInvalid() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("uri", "amqp://james:james@rabbitmq_host:5672");
        configuration.addProperty("management.uri", ":invalid");

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify a valid URI");
    }

    @Test
    void fromShouldReturnTheConfigurationWhenRequiredParametersAreGiven() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String amqpUri = "amqp://james:james@rabbitmq_host:5672";
        configuration.addProperty("uri", amqpUri);
        String managementUri = "http://james:james@rabbitmq_host:15672/api/";
        configuration.addProperty("management.uri", managementUri);

        assertThat(RabbitMQConfiguration.from(configuration))
            .isEqualTo(RabbitMQConfiguration.builder()
                .amqpUri(URI.create(amqpUri))
                .managementUri(URI.create(managementUri))
                .build());
    }

    @Test
    void maxRetriesShouldEqualsDefaultValueWhenNotGiven() throws URISyntaxException {
        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(new URI("amqp://james:james@rabbitmq_host:5672"))
            .managementUri(new URI("http://james:james@rabbitmq_host:15672/api/"))
            .build();

        assertThat(rabbitMQConfiguration.getMaxRetries())
            .isEqualTo(RabbitMQConfiguration.Builder.DEFAULT_MAX_RETRIES);
    }

    @Test
    void maxRetriesShouldEqualsCustomValueWhenGiven() throws URISyntaxException {
        int maxRetries = 1;

        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(new URI("amqp://james:james@rabbitmq_host:5672"))
            .managementUri(new URI("http://james:james@rabbitmq_host:15672/api/"))
            .maxRetries(maxRetries)
            .build();

        assertThat(rabbitMQConfiguration.getMaxRetries())
            .isEqualTo(maxRetries);
    }

    @Test
    void minDelayShouldEqualsDefaultValueWhenNotGiven() throws URISyntaxException {
        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(new URI("amqp://james:james@rabbitmq_host:5672"))
            .managementUri(new URI("http://james:james@rabbitmq_host:15672/api/"))
            .build();

        assertThat(rabbitMQConfiguration.getMinDelay())
            .isEqualTo(RabbitMQConfiguration.Builder.DEFAULT_MIN_DELAY);
    }

    @Test
    void minDelayShouldEqualsCustomValueWhenGiven() throws URISyntaxException {
        int minDelay = 1;

        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(new URI("amqp://james:james@rabbitmq_host:5672"))
            .managementUri(new URI("http://james:james@rabbitmq_host:15672/api/"))
            .minDelay(minDelay)
            .build();

        assertThat(rabbitMQConfiguration.getMinDelay())
            .isEqualTo(minDelay);
    }

    @Test
    void managementCredentialShouldEqualsDefaultValueWhenNotGiven() throws URISyntaxException {
        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(new URI("amqp://james:james@rabbitmq_host:5672"))
            .managementUri(new URI("http://james:james@rabbitmq_host:15672/api/"))
            .build();

        assertThat(rabbitMQConfiguration.getManagementCredential())
            .isEqualTo(RabbitMQConfiguration.Builder.DEFAULT_MANAGEMENT_CREDENTIAL);
    }

    @Test
    void managementCredentialShouldEqualsCustomValueWhenGiven() throws URISyntaxException {
        RabbitMQConfiguration.ManagementCredential credential = new RabbitMQConfiguration.ManagementCredential(
            "james", "james".toCharArray());
        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(new URI("amqp://james:james@rabbitmq_host:5672"))
            .managementUri(new URI("http://james:james@rabbitmq_host:15672/api/"))
            .managementCredential(Optional.of(credential))
            .build();

        assertThat(rabbitMQConfiguration.getManagementCredential())
            .isEqualTo(credential);
    }
}
