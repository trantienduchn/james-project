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

import org.apache.james.backends.cassandra.CassandraTestingResources.SchemaProvisionStep;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.util.Host;

import com.datastax.driver.core.Session;

public final class CassandraCluster implements AutoCloseable {

    public static CassandraCluster create(CassandraModule module, Host host) {
        assertClusterNotRunning();
        CassandraCluster cassandraCluster = new CassandraCluster(module, host);
        startStackTrace = Optional.of(new Exception("initial connection call trace"));
        return cassandraCluster;
    }

    private static void assertClusterNotRunning() {
      startStackTrace.ifPresent(e -> {
          throw new IllegalStateException("Cluster already running, look at the cause for the initial connection creation call trace", e);
      });
    }

    private static Optional<Exception> startStackTrace = Optional.empty();

    public static final String KEYSPACE = "testing";

    private CassandraTestingResources testingResources;
    private final Host host;

    private CassandraCluster(CassandraModule module, Host host) throws RuntimeException {
        this.host = host;
        this.testingResources = new CassandraTestingResources(module, host);

        this.testingResources.provision(SchemaProvisionStep.all());
    }

    public Session getConf() {
        return testingResources.nonPrivilegedSession;
    }

    public CassandraTypesProvider getTypesProvider() {
        return testingResources.typesProvider;
    }

    @Override
    public void close() {
        if (!testingResources.isClosed()) {
            testingResources.close();
            startStackTrace = Optional.empty();
        }
    }

    public void clearTables() {
        testingResources.clearTables();
    }

    public Host getHost() {
        return host;
    }
}
