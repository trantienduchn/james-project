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

import static org.awaitility.Duration.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Duration.ONE_MINUTE;

import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;

import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.AMQP;

public class RabbitMQFixture {
    public static final boolean DURABLE = true;
    public static final boolean AUTO_ACK = true;
    public static final AMQP.BasicProperties NO_PROPERTIES = null;
    public static final String EXCHANGE_NAME = "exchangeName";
    public static final String ROUTING_KEY = "routingKey";
    public static final String DIRECT = "direct";
    public static final boolean EXCLUSIVE = true;
    public static final boolean AUTO_DELETE = true;
    public static final ImmutableMap<String, Object> NO_ARGUMENTS = ImmutableMap.of();
    public static final String WORK_QUEUE = "workQueue";

    static final String DEFAULT_USER = "guest";
    static final String DEFAULT_PASSWORD_STRING = "guest";
    static final char[] DEFAULT_PASSWORD = DEFAULT_PASSWORD_STRING.toCharArray();
    public static final RabbitMQConfiguration.ManagementCredentials DEFAULT_MANAGEMENT_CREDENTIAL = new RabbitMQConfiguration.ManagementCredentials(DEFAULT_USER, DEFAULT_PASSWORD);

    public static Duration slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS;
    public static ConditionFactory calmlyAwait = Awaitility.with()
        .pollInterval(slowPacedPollInterval)
        .and()
        .with()
        .pollDelay(slowPacedPollInterval)
        .await();
    public static ConditionFactory awaitAtMostOneMinute = calmlyAwait.atMost(ONE_MINUTE);
}
