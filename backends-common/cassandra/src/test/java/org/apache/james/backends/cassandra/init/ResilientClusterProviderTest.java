package org.apache.james.backends.cassandra.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.james.backends.cassandra.DockerCassandraExtension;
import org.apache.james.backends.cassandra.DockerCassandraExtension.DockerCassandra;
import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;

@ExtendWith(DockerCassandraExtension.class)
class ResilientClusterProviderTest {

    private final static String TESTING_KEYSPACE = "resilient_cluster_provider_test";

    @Nested
    class WhenAllowCreatingKeySpace {

        private ClusterConfiguration clusterConfiguration;
        private Cluster cluster;

        @BeforeEach
        void setUp(DockerCassandra dockerCassandra) {
            clusterConfiguration = ClusterConfiguration.builder()
                .host(dockerCassandra.getHost())
                .keyspace(TESTING_KEYSPACE)
                .createKeyspace()
                .disableDurableWrites()
                .build();

            cluster = ClusterFactory.create(clusterConfiguration);
        }

        @AfterEach
        void tearDown() {
            dropKeyspace(TESTING_KEYSPACE);
            cluster.close();
        }

        @Test
        void initializationShouldCreateKeyspaceWhenNotExisted() {
            new ResilientClusterProvider(clusterConfiguration);

            assertThat(keyspaceExist(TESTING_KEYSPACE))
                .isTrue();
        }

        @Test
        void initializationShouldNotThrownWhenKeyspaceAlreadyExisted() {
            KeyspaceFactory.createKeyspace(clusterConfiguration, cluster);

            assertThatCode(() -> new ResilientClusterProvider(clusterConfiguration))
                .doesNotThrowAnyException();
        }

        @Test
        void initializationShouldNotImpactToKeyspaceExistentWhenAlreadyExisted() {
            KeyspaceFactory.createKeyspace(clusterConfiguration, cluster);

            assertThatCode(() -> new ResilientClusterProvider(clusterConfiguration))
                .doesNotThrowAnyException();

            assertThat(keyspaceExist(TESTING_KEYSPACE))
                .isTrue();
        }

        private boolean keyspaceExist(String keyspaceName) {
            try (Session cassandraSession = cluster.newSession()) {
                long numberOfKeyspaces = cassandraSession
                    .execute("SELECT COUNT(*) FROM system_schema.keyspaces where keyspace_name = '" + keyspaceName +"'")
                    .one()
                    .getLong("count");
                if (numberOfKeyspaces > 1 || numberOfKeyspaces < 0) {
                    throw new IllegalStateException("unexpected keyspace('" + keyspaceName + "') count being " + numberOfKeyspaces);
                }

                return numberOfKeyspaces == 1;
            }
        }

        private void dropKeyspace(String keyspaceName) {
            try (Session cassandraSession = cluster.newSession()) {
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