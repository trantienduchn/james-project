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

import java.io.IOException;
import java.util.Optional;

import javax.inject.Inject;

import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;

public class RabbitMQClient {

    private static final boolean AUTO_ACK = true;
    private static final boolean AUTO_DELETE = true;
    private static final boolean DURABLE = true;
    private static final boolean EXCLUSIVE = true;
    private static final boolean MULTIPLE = true;
    private static final ImmutableMap<String, Object> NO_ARGUMENTS = ImmutableMap.of();
    private static final String ROUTING_KEY = "";
    public static final boolean REQUEUE = true;

    private final RabbitMQChannelPool channelPool;

    @Inject
    public RabbitMQClient(RabbitMQChannelPool channelPool) {
        this.channelPool = channelPool;
    }

    public void attemptQueueCreation(RabbitMQQueueName name) {
        channelPool.execute(channel -> {
            try {
                channel.exchangeDeclare(name.toRabbitExchangeName().asString(), "direct", DURABLE);
                channel.queueDeclare(name.toWorkQueueName().asString(), DURABLE, !EXCLUSIVE, !AUTO_DELETE, NO_ARGUMENTS);
                channel.queueBind(name.toWorkQueueName().asString(), name.toRabbitExchangeName().asString(), ROUTING_KEY);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void publish(RabbitMQQueueName name, byte[] message) {
        channelPool.execute(channel -> {
            try {
                channel.basicPublish(name.toRabbitExchangeName().asString(), ROUTING_KEY, new AMQP.BasicProperties(), message);
            } catch (IOException e) {
                throw new RuntimeException("Unable to publish to RabbitMQ", e);
            }
        });
    }

    public void ack(long deliveryTag) throws IOException {
        RabbitMQChannelPool.RabbitConsumer<IOException> consumer = channel -> channel.basicAck(deliveryTag, !MULTIPLE);
        channelPool.execute(consumer);
    }

    public void nack(long deliveryTag) throws IOException {
        RabbitMQChannelPool.RabbitConsumer<IOException> consumer = channel -> channel.basicNack(deliveryTag, !MULTIPLE, REQUEUE);
        channelPool.execute(consumer);
    }

    public Optional<GetResponse> poll(RabbitMQQueueName name) throws IOException {
        RabbitMQChannelPool.RabbitFunction<Optional<GetResponse>, IOException> f = channel ->
            Optional.ofNullable(channel.basicGet(name.toWorkQueueName().asString(), !AUTO_ACK));
        return channelPool.execute(f);
    }
}
