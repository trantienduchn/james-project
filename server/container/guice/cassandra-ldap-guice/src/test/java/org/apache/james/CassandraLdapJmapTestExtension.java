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

import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.backends.es.EmbeddedElasticSearch;
import org.apache.james.modules.TestESMetricReporterModule;
import org.apache.james.modules.TestElasticSearchModule;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.user.ldap.LdapGenericContainer;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.apache.james.util.Runnables;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.rules.TemporaryFolder;

import com.google.inject.Module;

class CassandraLdapJmapTestExtension implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback, AfterEachCallback {

    private static final String DOMAIN = "james.org";
    private static final String ADMIN_PASSWORD = "mysecretpassword";
    private static final int LIMIT_TO_3_MESSAGES = 3;

    private final EmbeddedElasticSearch elasticSearch;
    private final TemporaryFolder temporaryFolder;
    private final DockerCassandraRule cassandra;
    private final Module[] additionalModules;
    private final LdapGenericContainer ldapContainer;
    private GuiceJamesServer jamesServer;

    CassandraLdapJmapTestExtension(Module... additionalModules) {
        this.additionalModules = additionalModules;
        this.temporaryFolder = new TemporaryFolder();
        this.elasticSearch = new EmbeddedElasticSearch(temporaryFolder);
        this.cassandra = new DockerCassandraRule();
        this.ldapContainer = LdapGenericContainer.builder()
            .domain(DOMAIN)
            .password(ADMIN_PASSWORD)
            .build();
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        temporaryFolder.create();
        Runnables.runParallel(cassandra::start, elasticSearch::before);
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        ldapContainer.start();
        jamesServer = createJmapServer();
        jamesServer.start();
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        jamesServer.stop();
        ldapContainer.stop();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        Runnables.runParallel(cassandra::stop, elasticSearch::after);
    }

    public GuiceJamesServer getJamesServer() {
        return jamesServer;
    }

    private GuiceJamesServer createJmapServer() throws IOException {
        Configuration configuration = Configuration.builder()
            .workingDirectory(temporaryFolder.newFolder())
            .configurationFromClasspath()
            .build();

        return GuiceJamesServer.forConfiguration(configuration)
            .combineWith(CassandraLdapJamesServerMain.cassandraLdapServerModule)
            .overrideWith(new TestJMAPServerModule(LIMIT_TO_3_MESSAGES))
            .overrideWith(new TestESMetricReporterModule())
            .overrideWith(new TestElasticSearchModule(elasticSearch))
            .overrideWith(cassandra.getModule())
            .overrideWith(additionalModules)
            .overrideWith(binder -> binder.bind(LdapRepositoryConfiguration.class)
                .toInstance(computeConfiguration(ldapContainer.getLdapHost())));
    }

    private LdapRepositoryConfiguration computeConfiguration(String ldapIp) {
        try {
            return LdapRepositoryConfiguration.builder()
                .ldapHost(ldapIp)
                .principal("cn=admin,dc=james,dc=org")
                .credentials("mysecretpassword")
                .userBase("ou=People,dc=james,dc=org")
                .userIdAttribute("uid")
                .userObjectClass("inetOrgPerson")
                .maxRetries(4)
                .retryStartInterval(0)
                .retryMaxInterval(8)
                .scale(1000)
                .build();
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }
}
