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

package org.apache.james;

import java.io.IOException;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.util.Runnables;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.junit.rules.TemporaryFolder;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Module;

public class CassandraJmapTestExtension implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback, AfterEachCallback, ParameterResolver {

    public static CassandraJmapTestExtensionBuilder.CoreModuleStage builder() {
        return new CassandraJmapTestExtensionBuilder.CoreModuleStage();
    }

    private final TemporaryFolder temporaryFolder;
    private final DockerCassandraRule cassandra;
    private final Module coreModule;
    private final Module overrideModules;
    private final ImmutableList<GuiceModuleTestExtension> extensions;

    private GuiceJamesServer jamesServer;

    CassandraJmapTestExtension(Module coreModule,
                               Module overrideModules,
                               ImmutableList<GuiceModuleTestExtension> extensions) {
        Preconditions.checkNotNull(coreModule);
        Preconditions.checkNotNull(overrideModules);
        Preconditions.checkNotNull(extensions);

        this.coreModule = coreModule;
        this.cassandra = new DockerCassandraRule();
        this.temporaryFolder = new TemporaryFolder();
        this.overrideModules = overrideModules;
        this.extensions = extensions;
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        temporaryFolder.create();

        Runnable[] extensionBeforeAll = toRunnables(extension -> extension.beforeAll(extensionContext));
        Runnables.runParallel(ArrayUtils.add(extensionBeforeAll, cassandra::start));
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        Runnable[] extensionBeforeEach = toRunnables(extension -> extension.beforeEach(extensionContext));
        Runnables.runParallel(extensionBeforeEach);

        jamesServer = createJmapServer();
        jamesServer.start();
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        Runnable[] extensionAfterEach = toRunnables(extension -> extension.afterEach(extensionContext));
        Runnables.runParallel(extensionAfterEach);

        jamesServer.stop();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        Runnable[] extensionAfterAll = toRunnables(extension -> extension.afterAll(extensionContext));
        Runnables.runParallel(ArrayUtils.add(extensionAfterAll, cassandra::stop));
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == GuiceJamesServer.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return jamesServer;
    }

    public GuiceJamesServer createJmapServer(Module... customModules) throws IOException {
        Configuration configuration = Configuration.builder()
            .workingDirectory(temporaryFolder.newFolder())
            .configurationFromClasspath()
            .build();

        ImmutableList<Module> extensionModules = this.extensions.stream()
            .map(GuiceModuleTestExtension::getModule)
            .collect(ImmutableList.toImmutableList());

        return GuiceJamesServer.forConfiguration(configuration)
            .combineWith(coreModule)
            .overrideWith(cassandra.getModule())
            .overrideWith(overrideModules)
            .overrideWith(extensionModules)
            .overrideWith(customModules);
    }

    private Runnable[] toRunnables(ThrowingConsumer<GuiceModuleTestExtension> runnableExecution) {
        return extensions.stream()
                .map(extension -> Throwing.runnable(() -> runnableExecution.accept(extension)))
                .toArray(Runnable[]::new);
    }
}
