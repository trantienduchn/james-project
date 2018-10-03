/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.james;

import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;
import org.apache.james.backend.rabbitmq.DockerRabbitMQ;
import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.apache.james.modules.TestRabbitMQModule;
import org.apache.james.server.CassandraTruncateTableTask;
import org.apache.james.util.Host;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.testcontainers.containers.GenericContainer;

public class DockerRabbitMQRule implements GuiceModuleTestRule {

    private DockerRabbitMQ rabbitMQContainer = DockerRabbitMQ.withoutCookie();

    @Override
    public Statement apply(Statement base, Description description) {
        try {
            return base;
        } finally {
            rabbitMQContainer.stop();
        }
    }

    @Override
    public void await() {
    }

    @Override
    public Module getModule() {
        rabbitMQContainer.start();
        return new TestRabbitMQModule(rabbitMQContainer);
    }

}
