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

import java.util.Optional;

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

public final class CassandraCluster implements AutoCloseable {

    public static ClusterConfiguration configurationForTestingUser(Host host) {
        return ClusterConfiguration.builder()
            .host(host)
            .keyspace(KEYSPACE)
            .createKeyspace()
            .disableDurableWrites()
            .username(CASSANDRA_TESTING_USER)
            .password(CASSANDRA_TESTING_PASSWORD)
            .maxRetry(2)
            .build();
    }

    public static ClusterConfiguration configurationForSuperUser(Host host) {
        return ClusterConfiguration.builder()
            .host(host)
            .keyspace(KEYSPACE)
            .createKeyspace()
            .disableDurableWrites()
            .username(CASSANDRA_SUPER_USER)
            .password(CASSANDRA_SUPER_USER_PASSWORD)
            .maxRetry(2)
            .build();
    }

    private static final String CASSANDRA_SUPER_USER = "cassandra";
    private static final String CASSANDRA_SUPER_USER_PASSWORD = "cassandra";

    private static final String CASSANDRA_TESTING_USER = "james_testing";
    private static final String CASSANDRA_TESTING_PASSWORD = "james_testing_password";

    public static final String KEYSPACE = "testing";

    private static Optional<Exception> startStackTrace = Optional.empty();
    private final CassandraModule module;
    private final Host host;

    private Session session;
    private CassandraTypesProvider typesProvider;
    private Cluster privilegedCluster;
    private Cluster nonPrivilegedCluster;

    public static CassandraCluster create(CassandraModule module, Host host) {
        return create(module, host, true);
    }

    public static CassandraCluster noRsourcesProvisioned(CassandraModule module, Host host) {
        return create(module, host, false);
    }

    static CassandraCluster create(CassandraModule module, Host host, boolean provisionResources) {
        assertClusterNotRunning();
        CassandraCluster cassandraCluster = new CassandraCluster(module, host, provisionResources);
        startStackTrace = Optional.of(new Exception("initial connection call trace"));
        return cassandraCluster;
    }

    private static void assertClusterNotRunning() {
      startStackTrace.ifPresent(e -> {
          throw new IllegalStateException("Cluster already running, look at the cause for the initial connection creation call trace", e);
      });
    }

    private CassandraCluster(CassandraModule module, Host host, boolean provisionResources) throws RuntimeException {
        this.module = module;
        this.host = host;

        privilegedCluster = ClusterFactory.create(configurationForSuperUser(host));
        if (provisionResources) {
            provisionResources();
        }
    }

    public void provisionResources() {
        try {
            KeyspaceFactory.createKeyspace(configurationForSuperUser(host), privilegedCluster);

            createTestingUser();
            grantPermissionTestingUser();

            ClusterConfiguration testingConfig = configurationForTestingUser(host);
            nonPrivilegedCluster = ClusterFactory.create(testingConfig);
            session = new SessionWithInitializedTablesFactory(testingConfig, nonPrivilegedCluster, module).get();
            typesProvider = new CassandraTypesProvider(module, session);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    public void createTestingUser() {
        try (Session session = privilegedCluster.newSession()) {
            session.execute("CREATE ROLE "+ CASSANDRA_TESTING_USER + " WITH PASSWORD = '" + CASSANDRA_TESTING_PASSWORD + "' AND LOGIN = true");
        }
    }

    private void grantPermissionTestingUser() {
        try (Session session = privilegedCluster.newSession()) {
            session.execute("GRANT CREATE ON KEYSPACE " + KEYSPACE + " TO " + CASSANDRA_TESTING_USER);
            session.execute("GRANT SELECT ON KEYSPACE "+ KEYSPACE + " TO " + CASSANDRA_TESTING_USER);
            session.execute("GRANT MODIFY ON KEYSPACE "+ KEYSPACE + " TO " + CASSANDRA_TESTING_USER);
        }
    }

    public Session getConf() {
        return session;
    }

    public CassandraTypesProvider getTypesProvider() {
        return typesProvider;
    }

    @Override
    public void close() {
        if (!privilegedCluster.isClosed()) {
            clearTables();
            closeCluster();
        }
    }

    public void closeCluster() {
        privilegedCluster.closeAsync().force();
        nonPrivilegedCluster.close();
        startStackTrace = Optional.empty();
    }

    public void clearTables() {
        new CassandraTableManager(module, session).clearAllTables();
    }

    public Host getHost() {
        return host;
    }
}
