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

package org.apache.james.queue.rabbitmq.view.cassandra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

class EnqueuedMailsDaoUtilTest {

    @Test
    void toAttributesShouldReturnEmptyWhenEmptyRawAttributeMap() {
        ImmutableMap<String, ByteBuffer> attrMap = ImmutableMap.of();

        assertThat(EnqueuedMailsDaoUtil.toAttributes(attrMap))
            .isEmpty();
    }

    @Test
    void toAttributesShouldConvertRawAttributeMapButItDoesntWork() {
        ImmutableMap<String, ByteBuffer> attrMap = ImmutableMap
            .of("Header-1", toByteBuffer("{\"serializer\":\"StringSerializer\",\"value\":\"02f49920-e7c8-11e8-a690-3df818dd6a01\"}"));

        assertThatThrownBy(() -> EnqueuedMailsDaoUtil.toAttributes(attrMap))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("java.io.StreamCorruptedException: invalid stream header:");
    }

    private ByteBuffer toByteBuffer(String value) {
        return ByteBuffer.wrap(value.getBytes());
    }
}