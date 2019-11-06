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

package org.apache.james.backends.cassandra.init;

import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.QueryOptions;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SocketOptions;
import com.google.common.base.Preconditions;

public class ClusterFactory {
    public static Cluster create(ClusterConfiguration configuration) {
        Preconditions.checkState(configuration.getUsername().isPresent() == configuration.getPassword().isPresent(), "If you specify username, you must specify password");

        Cluster.Builder clusterBuilder = Cluster.builder()
            .withoutJMXReporting();
        configuration.getHosts().forEach(server -> clusterBuilder
            .addContactPoint(server.getHostName())
            .withPort(server.getPort()));

        configuration.getUsername().ifPresent(username ->
            configuration.getPassword().ifPresent(password ->
                clusterBuilder.withCredentials(username, password)));

        clusterBuilder.withQueryOptions(queryOptions());

        SocketOptions socketOptions = new SocketOptions();
        socketOptions.setReadTimeoutMillis(configuration.getReadTimeoutMillis());
        socketOptions.setConnectTimeoutMillis(configuration.getConnectTimeoutMillis());
        clusterBuilder.withSocketOptions(socketOptions);
        configuration.getPoolingOptions().ifPresent(clusterBuilder::withPoolingOptions);

        if (configuration.useSsl()) {
            clusterBuilder.withSSL();
        }

        Cluster cluster = clusterBuilder.build();
        try {
            configuration.getQueryLoggerConfiguration().map(queryLoggerConfiguration ->
                cluster.register(queryLoggerConfiguration.getQueryLogger()));
            return cluster;
        } catch (Exception e) {
            cluster.close();
            throw e;
        }
    }

    public static Cluster createWithKeyspace(ClusterConfiguration clusterConfiguration) {
        Cluster cluster = create(clusterConfiguration);
        createKeyspace(clusterConfiguration, cluster);
        return cluster;
    }

    public static void createKeyspace(ClusterConfiguration clusterConfiguration, Cluster cluster) {
        try (Session session = cluster.connect()) {
            session.execute("CREATE KEYSPACE IF NOT EXISTS " + clusterConfiguration.getKeyspace()
                + " WITH replication = {'class':'SimpleStrategy', 'replication_factor':" + clusterConfiguration.getReplicationFactor() + "}"
                + " AND durable_writes = " + clusterConfiguration.isDurableWrites()
                + ";");
        }
    }

    private static QueryOptions queryOptions() {
        return new QueryOptions()
                .setConsistencyLevel(ConsistencyLevel.QUORUM);
    }
}
