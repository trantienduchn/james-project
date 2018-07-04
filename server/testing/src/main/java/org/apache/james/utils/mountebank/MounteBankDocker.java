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

package org.apache.james.utils.mountebank;

import org.apache.james.util.docker.Images;
import org.apache.james.util.docker.SwarmGenericContainer;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;

import com.jayway.awaitility.core.ConditionFactory;

public class MounteBankDocker implements TestRule {

    private static int MOUTEBANK_API = 2525;
    private final SwarmGenericContainer container;

    public MounteBankDocker() {
        container = new SwarmGenericContainer(Images.MOUNTE_BANK)
            .portBinding(MOUTEBANK_API, MOUTEBANK_API)
            .withAffinityToContainer()
            .waitingFor(new HostPortWaitStrategy());
    }

    @Override
    public Statement apply(Statement statement, Description description) {
        return container.apply(statement, description);
    }

    public void awaitStarted(ConditionFactory calmyAwait) {
        calmyAwait.until(() -> container.tryConnect(MOUTEBANK_API));
    }

    public SwarmGenericContainer getContainer() {
        return container;
    }
}
