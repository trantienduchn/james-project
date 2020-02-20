package org.apache.james.backends.cassandra.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.apache.james.util.Host;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;

class ResilientClusterProviderTest {

    @RegisterExtension
    static DockerCassandraExtension cassandraExtension = new DockerCassandraExtension();

    @Nested
    class WhenAllowCreatingKeySpace {

        private ClusterConfiguration clusterConfiguration;
        private Cluster testingResourceManagementCluster;
        private CassandraCluster cassandraCluster;

        @BeforeEach
        void setUp(DockerCassandraExtension.DockerCassandra dockerCassandra) {
            Host host = dockerCassandra.getHost();
            testingResourceManagementCluster = ClusterFactory.create(CassandraCluster.configurationForSuperUser(host));

            clusterConfiguration = CassandraCluster.configurationForTestingUser(host);
            cassandraCluster = CassandraCluster.noRsourcesProvisioned(
                CassandraModule.builder().build(),
                host);
        }

        @AfterEach
        void tearDown() {
            dropKeyspace(CassandraCluster.KEYSPACE);
            testingResourceManagementCluster.close();
            cassandraCluster.close();
        }

        @Disabled("JAMES-3061 com.datastax.driver.core.exceptions.UnauthorizedException: " +
            "User james_testing has no CREATE permission on <all keyspaces> or any of its parents" +
            "(No authorization to create a keyspace - expected)")
        @Test
        void initializationShouldCreateKeyspaceWhenNotExisted() {
            cassandraCluster.createTestingUser();

            new ResilientClusterProvider(clusterConfiguration);

            assertThat(keyspaceExist(CassandraCluster.KEYSPACE))
                .isTrue();
        }

        @Test
        void initializationShouldNotThrownWhenKeyspaceAlreadyExisted() {
            cassandraCluster.provisionResources();

            assertThatCode(() -> new ResilientClusterProvider(clusterConfiguration))
                .doesNotThrowAnyException();
        }

        @Test
        void initializationShouldNotImpactToKeyspaceExistentWhenAlreadyExisted() {
            cassandraCluster.provisionResources();

            new ResilientClusterProvider(clusterConfiguration);

            assertThat(keyspaceExist(CassandraCluster.KEYSPACE))
                .isTrue();
        }

        private boolean keyspaceExist(String keyspaceName) {
            try (Session cassandraSession = testingResourceManagementCluster.newSession()) {
                return KeyspaceFactory.keyspaceExist(cassandraSession, keyspaceName);
            }
        }

        private void dropKeyspace(String keyspaceName) {
            try (Session cassandraSession = testingResourceManagementCluster.newSession()) {
                boolean applied = cassandraSession
                    .execute("DROP KEYSPACE " + keyspaceName)
                    .wasApplied();

                if (!applied) {
                    throw new IllegalStateException("cannot drop keyspace '" + keyspaceName + "'");
                }
            }
        }
    }
}