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
package org.apache.james.jmap.rabbitmq;

import java.io.IOException;

import org.apache.james.CassandraRabbitMQJamesServerMain;
import org.apache.james.DockerCassandraRule;
import org.apache.james.GuiceJamesServer;
import org.apache.james.backend.rabbitmq.DockerRabbitMQSingleton;
import org.apache.james.backends.es.EmbeddedElasticSearch;
import org.apache.james.jmap.methods.integration.JamesWithSpamAssassin;
import org.apache.james.jmap.methods.integration.SpamAssassinModule;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.store.search.PDFTextExtractor;
import org.apache.james.modules.TestESMetricReporterModule;
import org.apache.james.modules.TestElasticSearchModule;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.TestRabbitMQModule;
import org.apache.james.modules.TestSwiftBlobStoreModule;
import org.apache.james.modules.objectstore.BlobStoreChoosingConfiguration;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.spamassassin.SpamAssassinExtension;
import org.apache.james.util.Runnables;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.rules.TemporaryFolder;

public class RabbitMQJmapExtension implements BeforeAllCallback, AfterAllCallback,
    BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final int LIMIT_TO_20_MESSAGES = 20;

    private final TemporaryFolder temporaryFolder;
    private final DockerCassandraRule cassandra;
    private final EmbeddedElasticSearch elasticSearch;
    private final SpamAssassinExtension spamAssassinExtension;
    private JamesWithSpamAssassin james;

    public RabbitMQJmapExtension() {
        this.temporaryFolder = new TemporaryFolder();
        this.cassandra = new DockerCassandraRule();
        this.elasticSearch = new EmbeddedElasticSearch(temporaryFolder);
        this.spamAssassinExtension = new SpamAssassinExtension();
    }

    private JamesWithSpamAssassin james() throws IOException {
        Configuration configuration = Configuration.builder()
            .workingDirectory(temporaryFolder.newFolder())
            .configurationFromClasspath()
            .build();
        return new JamesWithSpamAssassin(
            GuiceJamesServer.forConfiguration(configuration)
                    .combineWith(CassandraRabbitMQJamesServerMain.MODULES)
                    .overrideWith(binder -> binder.bind(TextExtractor.class).to(PDFTextExtractor.class))
                    .overrideWith(new TestRabbitMQModule(DockerRabbitMQSingleton.SINGLETON))
                    .overrideWith(new TestSwiftBlobStoreModule())
                    .overrideWith(binder -> binder.bind(BlobStoreChoosingConfiguration.class)
                        .toInstance(BlobStoreChoosingConfiguration.swift()))
                    .overrideWith(new TestJMAPServerModule(LIMIT_TO_20_MESSAGES))
                    .overrideWith(new TestESMetricReporterModule())
                    .overrideWith(cassandra.getModule())
                    .overrideWith(new TestElasticSearchModule(elasticSearch))
                    .overrideWith(new SpamAssassinModule(spamAssassinExtension)),
            spamAssassinExtension);
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        temporaryFolder.create();

        Runnables.runParallel(
            cassandra::start,
            elasticSearch::before);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        elasticSearch.after();

        Runnables.runParallel(
            cassandra::stop,
            elasticSearch::after);
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        james = james();
        spamAssassinExtension.beforeEach(context);
        james.getJmapServer().start();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        james.getJmapServer().stop();
        spamAssassinExtension.afterEach(context);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == JamesWithSpamAssassin.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return james;
    }
}
