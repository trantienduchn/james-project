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

package org.apache.james.sieve.cassandra;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.core.User;
import org.apache.james.core.quota.QuotaSize;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public class CassandraSieveQuotaDAOTest {

    public static final User USER = User.fromUsername("user");
    public static final QuotaSize QUOTA_SIZE = QuotaSize.size(15L);

    @ClassRule public static DockerCassandraRule cassandraServer = new DockerCassandraRule();
    
    private CassandraCluster cassandra;
    private CassandraSieveQuotaDAO sieveQuotaDAO;

    @Before
    public void setUp() throws Exception {
        cassandra = CassandraCluster.create(new CassandraSieveRepositoryModule(), cassandraServer.getIp(), cassandraServer.getBindingPort());
        sieveQuotaDAO = new CassandraSieveQuotaDAO(cassandra.getConf());
    }

    @After
    public void tearDown() {
        cassandra.close();
    }

    @Test
    public void getQuotaShouldReturnEmptyByDefault() {
        assertThat(sieveQuotaDAO.getQuota().join())
            .isEmpty();
    }

    @Test
    public void getQuotaUserShouldReturnEmptyByDefault() {
        assertThat(sieveQuotaDAO.getQuota(USER).join())
            .isEmpty();
    }

    @Test
    public void getQuotaShouldReturnStoredValue() {
        sieveQuotaDAO.setQuota(QUOTA_SIZE).join();

        assertThat(sieveQuotaDAO.getQuota().join())
            .contains(QUOTA_SIZE);
    }

    @Test
    public void getQuotaUserShouldReturnStoredValue() {
        sieveQuotaDAO.setQuota(USER, QUOTA_SIZE).join();

        assertThat(sieveQuotaDAO.getQuota(USER).join())
            .contains(QUOTA_SIZE);
    }

    @Test
    public void removeQuotaShouldDeleteQuota() {
        sieveQuotaDAO.setQuota(QUOTA_SIZE).join();

        sieveQuotaDAO.removeQuota().join();

        assertThat(sieveQuotaDAO.getQuota().join())
            .isEmpty();
    }

    @Test
    public void removeQuotaUserShouldDeleteQuotaUser() {
        sieveQuotaDAO.setQuota(USER, QUOTA_SIZE).join();

        sieveQuotaDAO.removeQuota(USER).join();

        assertThat(sieveQuotaDAO.getQuota(USER).join())
            .isEmpty();
    }

    @Test
    public void removeQuotaShouldWorkWhenNoneStore() {
        sieveQuotaDAO.removeQuota().join();

        assertThat(sieveQuotaDAO.getQuota().join())
            .isEmpty();
    }

    @Test
    public void removeQuotaUserShouldWorkWhenNoneStore() {
        sieveQuotaDAO.removeQuota(USER).join();

        assertThat(sieveQuotaDAO.getQuota(USER).join())
            .isEmpty();
    }

    @Test
    public void spaceUsedByShouldReturnZeroByDefault() {
        assertThat(sieveQuotaDAO.spaceUsedBy(USER).join()).isEqualTo(0);
    }

    @Test
    public void spaceUsedByShouldReturnStoredValue() {
        long spaceUsed = 18L;

        sieveQuotaDAO.updateSpaceUsed(USER, spaceUsed).join();

        assertThat(sieveQuotaDAO.spaceUsedBy(USER).join()).isEqualTo(spaceUsed);
    }

    @Test
    public void updateSpaceUsedShouldBeAdditive() {
        long spaceUsed = 18L;

        sieveQuotaDAO.updateSpaceUsed(USER, spaceUsed).join();
        sieveQuotaDAO.updateSpaceUsed(USER, spaceUsed).join();

        assertThat(sieveQuotaDAO.spaceUsedBy(USER).join()).isEqualTo(2 * spaceUsed);
    }

    @Test
    public void updateSpaceUsedShouldWorkWithNegativeValues() {
        long spaceUsed = 18L;

        sieveQuotaDAO.updateSpaceUsed(USER, spaceUsed).join();
        sieveQuotaDAO.updateSpaceUsed(USER, -1 * spaceUsed).join();

        assertThat(sieveQuotaDAO.spaceUsedBy(USER).join()).isEqualTo(0L);
    }
}
