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

package org.apache.james.backends.cassandra;

import static org.apache.james.backends.cassandra.CassandraCluster.KEYSPACE;

import java.util.List;
import java.util.stream.Stream;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.CassandraTableManager;
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.init.ClusterFactory;
import org.apache.james.backends.cassandra.init.KeyspaceFactory;
import org.apache.james.backends.cassandra.init.SessionWithInitializedTablesFactory;
import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.apache.james.util.Host;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.github.steveash.guavate.Guavate;

public class CassandraTestingResources implements AutoCloseable {

    public enum SchemaProvisionStep {
        CREATE_KEYSPACE,
        CREATE_TABLES;

        public static List<SchemaProvisionStep> all() {
            return Stream.of(values())
                .collect(Guavate.toImmutableList());
        }
    }

    public static ClusterConfiguration configurationForNonPrivilegedUser(Host host) {
        return configurationBuilder(host)
            .username(CASSANDRA_TESTING_USER)
            .password(CASSANDRA_TESTING_PASSWORD)
            .build();
    }

    public static ClusterConfiguration configurationForSuperUser(Host host) {
        return configurationBuilder(host)
            .username(CASSANDRA_SUPER_USER)
            .password(CASSANDRA_SUPER_USER_PASSWORD)
            .build();
    }

    private static ClusterConfiguration.Builder configurationBuilder(Host host) {
        return ClusterConfiguration.builder()
            .host(host)
            .keyspace(KEYSPACE)
            .createKeyspace()
            .disableDurableWrites()
            .replicationFactor(1)
            .maxRetry(2);
    }

    private static final String CASSANDRA_SUPER_USER = "cassandra";
    private static final String CASSANDRA_SUPER_USER_PASSWORD = "cassandra";
    private static final String CASSANDRA_TESTING_USER = "james_testing";
    private static final String CASSANDRA_TESTING_PASSWORD = "james_testing_password";

    private final CassandraModule module;
    private final Host host;
    private final Cluster privilegedCluster;
    private final ClusterConfiguration nonPrivilegeConfiguration;

    private Cluster nonPrivilegedCluster;
    Session nonPrivilegedSession;
    CassandraTypesProvider typesProvider;

    public CassandraTestingResources(CassandraModule module, Host host) {
        this.module = module;
        this.host = host;
        this.nonPrivilegeConfiguration = configurationForNonPrivilegedUser(host);

        privilegedCluster = ClusterFactory.create(configurationForSuperUser(host));
        provisionNonPrivilegedUser();
        nonPrivilegedCluster = ClusterFactory.create(configurationForNonPrivilegedUser(host));
    }

    public void provision(List<SchemaProvisionStep> provisionSteps) {
        provisionSteps.forEach(this::setup);
    }

    private void setup(SchemaProvisionStep step) {
        switch (step) {
            case CREATE_KEYSPACE:
                createKeyspace();
                break;
            case CREATE_TABLES:
                createTables();
                break;
            default:
                throw new RuntimeException("unknow step " + step.name());
        }
    }

    private void createKeyspace() {
        KeyspaceFactory.createKeyspace(configurationForSuperUser(host), privilegedCluster);
        grantPermissionTestingUser();
    }

    private void createTables() {
        nonPrivilegedSession = new SessionWithInitializedTablesFactory(configurationForNonPrivilegedUser(host),
            nonPrivilegedCluster, module).get();
        typesProvider = new CassandraTypesProvider(module, nonPrivilegedSession);
    }

    private void provisionNonPrivilegedUser() {
        try (Session session = privilegedCluster.newSession()) {
            session.execute("CREATE ROLE IF NOT EXISTS "+ CASSANDRA_TESTING_USER + " WITH PASSWORD = '" + CASSANDRA_TESTING_PASSWORD + "' AND LOGIN = true");
        }
    }

    private void grantPermissionTestingUser() {
        try (Session session = privilegedCluster.newSession()) {
            session.execute("GRANT CREATE ON KEYSPACE " + KEYSPACE + " TO " + CASSANDRA_TESTING_USER);
            session.execute("GRANT SELECT ON KEYSPACE "+ KEYSPACE + " TO " + CASSANDRA_TESTING_USER);
            session.execute("GRANT MODIFY ON KEYSPACE "+ KEYSPACE + " TO " + CASSANDRA_TESTING_USER);
            session.execute("GRANT DROP ON KEYSPACE "+ KEYSPACE + " TO " + CASSANDRA_TESTING_USER); // some tests require dropping in setups
        }
    }

    @Override
    public void close() {
        clearTables();
        closeConnections();
    }

    void clearTables() {
        if (!nonPrivilegedSession.isClosed()) {
            new CassandraTableManager(module, nonPrivilegedSession).clearAllTables();
        }
    }

    void closeConnections() {
        if (!nonPrivilegedSession.isClosed()) {
            nonPrivilegedSession.closeAsync().force();
        }
        if (!privilegedCluster.isClosed()) {
            privilegedCluster.closeAsync().force();
        }
        if (!nonPrivilegedCluster.isClosed()) {
            nonPrivilegedCluster.closeAsync().force();
        }
    }

    boolean isClosed() {
        return nonPrivilegedSession.isClosed()
            && privilegedCluster.isClosed()
            && nonPrivilegedCluster.isClosed();
    }

    public Cluster getNonPrivilegedCluster() {
        return nonPrivilegedCluster;
    }

    public ClusterConfiguration getNonPrivilegeConfiguration() {
        return nonPrivilegeConfiguration;
    }

    public Cluster getPrivilegedCluster() {
        return privilegedCluster;
    }
}