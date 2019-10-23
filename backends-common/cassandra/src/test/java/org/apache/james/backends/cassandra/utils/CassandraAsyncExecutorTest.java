package org.apache.james.backends.cassandra.utils;

import static com.datastax.driver.core.DataType.cint;
import static com.datastax.driver.core.DataType.text;
import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;

class CassandraAsyncExecutorTest {

    private static final String STATEMENT_SANITIZER = "statementSanitizer";
    private static final String ID = "id";
    private static final String VALUE_1 = "value_1";
    private static final String VALUE_2 = "value_2";
    private static final String VALUE_3 = "value_3";

    private static CassandraModule TEST_MODULE = CassandraModule.builder()
        .table(STATEMENT_SANITIZER)
        .comment("For testing purpose")
        .statement(statement -> statement
            .addPartitionKey(ID, cint())
            .addColumn(VALUE_1, text())
            .addColumn(VALUE_2, text())
            .addColumn(VALUE_3, text()))
        .build();

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(TEST_MODULE);

    @Nested
    class StatementSanitizerTest {

        private static final String VALUE_1_STRING = "value 1";
        private static final String VALUE_2_STRING = "value 2";
        private PreparedStatement insertStatement;

        @BeforeEach
        void setUp() {
            insertStatement = cassandraCluster.getCassandraCluster()
                .getConf()
                .prepare(insertInto(STATEMENT_SANITIZER)
                    .value(ID, bindMarker(ID))
                    .value(VALUE_1, bindMarker(VALUE_1))
                    .value(VALUE_2, bindMarker(VALUE_2))
                    .value(VALUE_3, bindMarker(VALUE_3)));
        }

        @Test
        void bindingNullShouldSetNullToStatement() {
            BoundStatement tombstoneInsertion = insertStatement.bind()
                .setInt(ID, 1)
                .setString(VALUE_1, null);

            SoftAssertions.assertSoftly(softly -> {
                assertThat(tombstoneInsertion.isSet(VALUE_1))
                   .isTrue();
                assertThat(tombstoneInsertion.getString(VALUE_1))
                   .isNull();
            });
        }

        @Test
        void cleanShouldUnSetNullFieldsFromTheStatement() {
            BoundStatement tombstoneInsertion = insertStatement.bind()
                .setInt(ID, 1)
                .setString(VALUE_1, null)
                .setString(VALUE_2, null);

            CassandraAsyncExecutor.StatementSanitizer.clean(tombstoneInsertion);

            SoftAssertions.assertSoftly(softly -> {
                assertThat(tombstoneInsertion.isSet(VALUE_1))
                   .isFalse();
                assertThat(tombstoneInsertion.isSet(VALUE_2))
                   .isFalse();
            });
        }

        @Test
        void cleanShouldKeepUnSetFieldsInTheStatement() {
            BoundStatement tombstoneInsertion = insertStatement.bind()
                .setInt(ID, 1)
                .setString(VALUE_1, "value 1")
                .setString(VALUE_2, "value 2");

            CassandraAsyncExecutor.StatementSanitizer.clean(tombstoneInsertion);

            assertThat(tombstoneInsertion.isSet(VALUE_3))
               .isFalse();
        }

        @Test
        void cleanShouldNotUnsetNotNullFieldsInTheStatement() {
            BoundStatement tombstoneInsertion = insertStatement.bind()
                .setInt(ID, 1)
                .setString(VALUE_1, VALUE_1_STRING)
                .setString(VALUE_2, VALUE_2_STRING);

            CassandraAsyncExecutor.StatementSanitizer.clean(tombstoneInsertion);

            SoftAssertions.assertSoftly(softly -> {
                assertThat(tombstoneInsertion.getString(VALUE_1))
                    .isEqualTo(VALUE_1_STRING);
                assertThat(tombstoneInsertion.getString(VALUE_2))
                    .isEqualTo(VALUE_2_STRING);
            });
        }
    }

}