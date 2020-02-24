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

import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;

import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.google.common.annotations.VisibleForTesting;

public class KeyspaceFactory {
    public static void createKeyspace(ClusterConfiguration clusterConfiguration, Cluster cluster) {
        if (clusterConfiguration.shouldCreateKeyspace()) {
            doCreateKeyspace(clusterConfiguration, cluster);
        }
    }

    private static void doCreateKeyspace(ClusterConfiguration clusterConfiguration, Cluster cluster) {
        try (Session session = cluster.connect()) {
            if (!keyspaceExist(cluster, clusterConfiguration.getKeyspace())) {
                session.execute("CREATE KEYSPACE " + clusterConfiguration.getKeyspace()
                    + " WITH replication = {'class':'SimpleStrategy', 'replication_factor':" + clusterConfiguration.getReplicationFactor() + "}"
                    + " AND durable_writes = " + clusterConfiguration.isDurableWrites()
                    + ";");
            }
        }
    }

    @VisibleForTesting
    public static boolean keyspaceExist(Cluster cluster, String keyspaceName) {
        try (Session session = cluster.connect("system_schema")) {
            long numberOfKeyspaces = session.execute(select()
                    .countAll()
                    .from("keyspaces")
                    .where(eq("keyspace_name", keyspaceName)))
                .one()
                .getLong("count");

            if (numberOfKeyspaces > 1 || numberOfKeyspaces < 0) {
                throw new IllegalStateException("unexpected keyspace('" + keyspaceName + "') count being " + numberOfKeyspaces);
            }

            return numberOfKeyspaces == 1;
        }
    }
}
