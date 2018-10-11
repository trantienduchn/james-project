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
import java.util.concurrent.atomic.AtomicReference;

import com.github.fge.lambdas.Throwing;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

public class SimpleChannelPool implements RabbitMQChannelPool {
    private final AtomicReference<Channel> channelReference;
    private final AtomicReference<Connection> connectionReference;
    private final RabbitMQConnectionFactory connectionFactory;

    public SimpleChannelPool(RabbitMQConnectionFactory factory) {
        this.connectionFactory = factory;
        this.connectionReference = new AtomicReference<>();
        this.channelReference = new AtomicReference<>();
    }

    @Override
    public synchronized  <T, E extends Throwable> T execute(RabbitFunction<T, E> f) throws E, ConnectionFailedException {
        return f.execute(getResilientChannel());
    }

    @Override
    public synchronized  <E extends Throwable> void execute(RabbitConsumer<E> f) throws E, ConnectionFailedException {
        f.execute(getResilientChannel());
    }

    @Override
    public void close() throws Exception {
        Channel channel = channelReference.get();
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        Connection connection = connectionReference.get();
        if (connection != null && connection.isOpen()) {
            connection.close();
        }
    }

    private Connection getResilientConnection() {
        return connectionReference.updateAndGet(this::getOpenConnection);
    }

    private Connection getOpenConnection(Connection checkedConnection) {
        if (checkedConnection != null && checkedConnection.isOpen()) {
            return checkedConnection;
        }
        return connectionFactory.create();
    }

    private Channel getResilientChannel() {
        return channelReference.updateAndGet(Throwing.unaryOperator(this::getOpenChannel));
    }

    private Channel getOpenChannel(Channel checkedChannel) throws IOException {
        if (checkedChannel != null && checkedChannel.isOpen()) {
            return checkedChannel;
        }
        return getResilientConnection().createChannel();
    }
}
