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

import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.util.OptionalUtils;

import com.fasterxml.jackson.annotation.JsonProperty;

import feign.Feign;
import feign.Logger;
import feign.Param;
import feign.RequestLine;
import feign.RetryableException;
import feign.Retryer;
import feign.auth.BasicAuthRequestInterceptor;
import feign.codec.ErrorDecoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;

public class RabbitMQManagementApi {

    private static final ErrorDecoder RETRY_500 = (methodKey, response) -> {
        if (response.status() == 500) {
            throw new RetryableException("Error encountered, scheduling retry", response.request().httpMethod(), new Date());
        }
        throw new RuntimeException("Non recoverable exception status: " + response.status());
    };

    public interface Api {

        class MessageQueue {
            @JsonProperty("name")
            String name;

            @JsonProperty("vhost")
            String vhost;
        }

        @RequestLine("GET /api/queues")
        List<MessageQueue> listQueues();

        @RequestLine(value = "DELETE /api/queues/{vhost}/{name}", decodeSlash = false)
        void deleteQueue(@Param("vhost") String vhost, @Param("name") String name);
    }

    private final Api api;

    @Inject
    public RabbitMQManagementApi(RabbitMQConfiguration configuration) {
        RabbitMQConfiguration.ManagementCredentials credentials = configuration.getManagementCredentials();
        api = Feign.builder()
            .requestInterceptor(new BasicAuthRequestInterceptor(credentials.getUser(), new String(credentials.getPassword())))
            .logger(new Slf4jLogger(RabbitMQManagementApi.class))
            .logLevel(Logger.Level.FULL)
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .retryer(new Retryer.Default())
            .errorDecoder(RETRY_500)
            .target(Api.class, configuration.getManagementUri().toString());
    }

    public Stream<RabbitMQQueueName> listCreatedMailQueueNames() {
        return api.listQueues()
            .stream()
            .map(x -> x.name)
            .map(RabbitMQQueueName::fromRabbitWorkQueueName)
            .flatMap(OptionalUtils::toStream)
            .distinct();
    }

    public void deleteAllQueues() {
        api.listQueues()
            .forEach(queue -> api.deleteQueue("/", queue.name));
    }
}
