package org.apache.james.backends.cassandra.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.DockerCassandra;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.apache.james.util.Host;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ResilientClusterProviderTest {

    private static final String KEYSPACE = "my_keyspace";

    @RegisterExtension
    static CassandraClusterExtension cassandraExtension = new CassandraClusterExtension(CassandraModule.NO_MODULE);

    private ClusterConfiguration clusterConfiguration;

    @BeforeEach
    void setUp(DockerCassandra dockerCassandra) {
        Host host = dockerCassandra.getHost();

        clusterConfiguration = DockerCassandra.configurationBuilder(host)
            .keyspace(KEYSPACE)
            .build();
    }

    @AfterEach
    void tearDown(DockerCassandra dockerCassandra) {
        dockerCassandra.administrator()
            .dropKeyspace(KEYSPACE);
    }

    @Nested
    class WhenAllowCreatingKeySpace {

        @Disabled("JAMES-3061 com.datastax.driver.core.exceptions.UnauthorizedException: " +
            "User james_testing has no CREATE permission on <all keyspaces> or any of its parents" +
            "(No authorization to create a keyspace - expected)")
        @Test
        void initializationShouldCreateKeyspaceWhenNotExisted(DockerCassandra dockerCassandra) {
            new ResilientClusterProvider(clusterConfiguration);

            assertThat(keyspaceExist(dockerCassandra, KEYSPACE))
                .isTrue();
        }

        @Test
        void initializationShouldNotThrownWhenKeyspaceAlreadyExisted(DockerCassandra dockerCassandra) {
            dockerCassandra.administrator()
                .initializeKeyspace(KEYSPACE);

            assertThatCode(() -> new ResilientClusterProvider(clusterConfiguration))
                .doesNotThrowAnyException();
        }

        @Test
        void initializationShouldNotImpactToKeyspaceExistentWhenAlreadyExisted(DockerCassandra dockerCassandra) {
            dockerCassandra.administrator()
                .initializeKeyspace(KEYSPACE);

            new ResilientClusterProvider(clusterConfiguration);

            assertThat(keyspaceExist(dockerCassandra, KEYSPACE))
                .isTrue();
        }
    }

    private boolean keyspaceExist(DockerCassandra dockerCassandra, String keyspace) {
        return dockerCassandra.administrator()
            .keyspaceExist(keyspace);
    }
}