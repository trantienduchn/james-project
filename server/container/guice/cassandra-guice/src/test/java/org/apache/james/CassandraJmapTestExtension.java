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

import static org.apache.james.CassandraJamesServerMain.ALL_BUT_JMX_CASSANDRA_MODULE;

import java.io.IOException;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.store.search.PDFTextExtractor;
import org.apache.james.modules.TestESMetricReporterModule;
import org.apache.james.modules.TestJMAPServerModule;
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
import com.google.common.collect.ImmutableList;
import com.google.inject.Module;

public class CassandraJmapTestExtension implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback, AfterEachCallback, ParameterResolver {

    public static class Builder {
        public static Builder withDefaultModules() {
            return withDefaultFromModules();
        }

        public static Builder withExtension(GuiceModuleTestExtension extension, Module... modules) {
            return builder()
                .modules(modules)
                .extensions(ImmutableList.of(extension));
        }

        public static Builder withDefaultFromModules(Module... modules) {
            return builder()
                .modules(modules)
                .extensions(EMBEDDED_ES_EXTENSION_ONLY);
        }

        private ImmutableList<Module> additionalModules;
        private ImmutableList<GuiceModuleTestExtension> extensions;
        private boolean ignoreEach;

        public Builder() {
            this.additionalModules = EMPTY_MODULES;
            this.extensions = EMPTY_EXTENSIONS;
            this.ignoreEach = false;
        }

        public Builder modules(Module... additionalModules) {
            this.additionalModules = ImmutableList.copyOf(additionalModules);
            return this;
        }

        public Builder extensions(ImmutableList<GuiceModuleTestExtension> extensions) {
            this.extensions = extensions;
            return this;
        }

        public Builder ignoreEach() {
            this.ignoreEach = true;
            return this;
        }

        public CassandraJmapTestExtension build() {
            return new CassandraJmapTestExtension(additionalModules, extensions, ignoreEach);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private static final ImmutableList<Module> EMPTY_MODULES = ImmutableList.of();
    private static final ImmutableList<GuiceModuleTestExtension> EMPTY_EXTENSIONS = ImmutableList.of();
    private static final ImmutableList<GuiceModuleTestExtension> EMBEDDED_ES_EXTENSION_ONLY = ImmutableList.of(new EmbeddedElasticSearchExtension());

    private static final int LIMIT_TO_10_MESSAGES = 10;

    private final TemporaryFolder temporaryFolder;
    private final DockerCassandraRule cassandra;
    private final ImmutableList<Module> additionalModules;
    private final ImmutableList<GuiceModuleTestExtension> extensions;
    private final boolean ignoreEach;

    private GuiceJamesServer jamesServer;

    private CassandraJmapTestExtension(ImmutableList<Module> additionalModules,
                                      ImmutableList<GuiceModuleTestExtension> extensions,
                                      boolean ignoreEach) {
        this.ignoreEach = ignoreEach;
        this.cassandra = new DockerCassandraRule();
        this.temporaryFolder = new TemporaryFolder();
        this.additionalModules = additionalModules;
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
        if (!ignoreEach) {
            jamesServer = createJmapServer();
            jamesServer.start();
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        if (!ignoreEach) {
            jamesServer.stop();
        }
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        Runnable[] extensionAfterAll = toRunnables(extension -> extension.beforeAll(extensionContext));
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

    public void await() {
        extensions
            .parallelStream()
            .forEach(GuiceModuleTestExtension::await);
    }
    public GuiceJamesServer getJamesServer() {
        return jamesServer;
    }

    public DockerCassandraRule getCassandra() {
        return cassandra;
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
            .combineWith(ALL_BUT_JMX_CASSANDRA_MODULE)
            .overrideWith(binder -> binder.bind(TextExtractor.class).to(PDFTextExtractor.class))
            .overrideWith(new TestJMAPServerModule(LIMIT_TO_10_MESSAGES))
            .overrideWith(new TestESMetricReporterModule())
            .overrideWith(extensionModules)
            .overrideWith(cassandra.getModule())
            .overrideWith(additionalModules)
            .overrideWith(customModules);
    }

    private Runnable[] toRunnables(ThrowingConsumer<GuiceModuleTestExtension> runnableExecution) {
        return extensions.stream()
                .map(extension -> Throwing.runnable(() -> runnableExecution.accept(extension)))
                .toArray(Runnable[]::new);
    }
}
