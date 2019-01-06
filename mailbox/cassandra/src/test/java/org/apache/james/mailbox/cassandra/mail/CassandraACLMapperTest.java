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
package org.apache.james.mailbox.cassandra.mail;

import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.utils.GuiceUtils;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.table.CassandraACLTable;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxACL;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

public class CassandraACLMapperTest {

    public static final CassandraId MAILBOX_ID = CassandraId.of(UUID.fromString("464765a0-e4e7-11e4-aba4-710c1de3782b"));

    @ClassRule public static DockerCassandraRule cassandraServer = new DockerCassandraRule();
    private static CassandraCluster cassandra;

    private CassandraACLMapper cassandraACLMapper;
    private ExecutorService executor;

    @BeforeClass
    public static void setUpClass() {
        cassandra = CassandraCluster.create(new CassandraAclModule(), cassandraServer.getHost());
    }

    @Before
    public void setUp() {
        cassandraACLMapper = GuiceUtils.testInjector(cassandra)
            .getInstance(CassandraACLMapper.class);
        executor = Executors.newFixedThreadPool(2);
    }

    @After
    public void tearDown() {
        executor.shutdownNow();
        cassandra.clearTables();
    }

    @AfterClass
    public static void tearDownClass() {
        cassandra.closeCluster();
    }

    @Test
    public void retrieveACLWhenInvalidInBaseShouldReturnEmptyACL() throws Exception {
        cassandra.getConf().execute(
            insertInto(CassandraACLTable.TABLE_NAME)
                .value(CassandraACLTable.ID, MAILBOX_ID.asUuid())
                .value(CassandraACLTable.ACL, "{\"entries\":{\"bob\":invalid}}")
                .value(CassandraACLTable.VERSION, 1));

        assertThat(cassandraACLMapper.getACL(MAILBOX_ID).join()).isEqualTo(MailboxACL.EMPTY);
    }

    @Test
    public void retrieveACLWhenNoACLStoredShouldReturnEmptyACL() {
        assertThat(cassandraACLMapper.getACL(MAILBOX_ID).join()).isEqualTo(MailboxACL.EMPTY);
    }

    @Test
    public void addACLWhenNoneStoredShouldReturnUpdatedACL() throws Exception {
        MailboxACL.EntryKey key = new MailboxACL.EntryKey("bob", MailboxACL.NameType.user, false);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights(MailboxACL.Right.Read);

        cassandraACLMapper.updateACL(MAILBOX_ID,
            MailboxACL.command().key(key).rights(rights).asAddition());

        assertThat(cassandraACLMapper.getACL(MAILBOX_ID).join())
            .isEqualTo(new MailboxACL().union(key, rights));
    }

    @Test
    public void modifyACLWhenStoredShouldReturnUpdatedACL() throws MailboxException {
        MailboxACL.EntryKey keyBob = new MailboxACL.EntryKey("bob", MailboxACL.NameType.user, false);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights(MailboxACL.Right.Read);

        cassandraACLMapper.updateACL(MAILBOX_ID, MailboxACL.command().key(keyBob).rights(rights).asAddition());
        MailboxACL.EntryKey keyAlice = new MailboxACL.EntryKey("alice", MailboxACL.NameType.user, false);
        cassandraACLMapper.updateACL(MAILBOX_ID, MailboxACL.command().key(keyAlice).rights(rights).asAddition());

        assertThat(cassandraACLMapper.getACL(MAILBOX_ID).join())
            .isEqualTo(new MailboxACL().union(keyBob, rights).union(keyAlice, rights));
    }

    @Test
    public void removeWhenStoredShouldReturnUpdatedACL() throws MailboxException {
        MailboxACL.EntryKey key = new MailboxACL.EntryKey("bob", MailboxACL.NameType.user, false);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights(MailboxACL.Right.Read);

        cassandraACLMapper.updateACL(MAILBOX_ID, MailboxACL.command().key(key).rights(rights).asAddition());
        cassandraACLMapper.updateACL(MAILBOX_ID, MailboxACL.command().key(key).rights(rights).asRemoval());

        assertThat(cassandraACLMapper.getACL(MAILBOX_ID).join()).isEqualTo(MailboxACL.EMPTY);
    }

    @Test
    public void replaceForSingleKeyWithNullRightsWhenSingleKeyStoredShouldReturnEmptyACL() throws MailboxException {
        MailboxACL.EntryKey key = new MailboxACL.EntryKey("bob", MailboxACL.NameType.user, false);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights(MailboxACL.Right.Read);

        cassandraACLMapper.updateACL(MAILBOX_ID, MailboxACL.command().key(key).rights(rights).asAddition());
        cassandraACLMapper.updateACL(MAILBOX_ID, MailboxACL.command().key(key).noRights().asReplacement());

        assertThat(cassandraACLMapper.getACL(MAILBOX_ID).join()).isEqualTo(MailboxACL.EMPTY);
    }

    @Test
    public void replaceWhenNotStoredShouldUpdateACLEntry() throws MailboxException {
        MailboxACL.EntryKey key = new MailboxACL.EntryKey("bob", MailboxACL.NameType.user, false);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights(MailboxACL.Right.Read);

        cassandraACLMapper.updateACL(MAILBOX_ID, MailboxACL.command().key(key).rights(rights).asReplacement());

        assertThat(cassandraACLMapper.getACL(MAILBOX_ID).join()).isEqualTo(new MailboxACL().union(key, rights));
    }

    @Test
    public void updateInvalidACLShouldBeBasedOnEmptyACL() throws Exception {
        cassandra.getConf().execute(
            insertInto(CassandraACLTable.TABLE_NAME)
                .value(CassandraACLTable.ID, MAILBOX_ID.asUuid())
                .value(CassandraACLTable.ACL, "{\"entries\":{\"bob\":invalid}}")
                .value(CassandraACLTable.VERSION, 1));
        MailboxACL.EntryKey key = new MailboxACL.EntryKey("bob", MailboxACL.NameType.user, false);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights(MailboxACL.Right.Read);

        cassandraACLMapper.updateACL(MAILBOX_ID, MailboxACL.command().key(key).rights(rights).asAddition());

        assertThat(cassandraACLMapper.getACL(MAILBOX_ID).join()).isEqualTo(new MailboxACL().union(key, rights));
    }

    @Test
    public void twoConcurrentUpdatesWhenNoACEStoredShouldReturnACEWithTwoEntries() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        MailboxACL.EntryKey keyBob = new MailboxACL.EntryKey("bob", MailboxACL.NameType.user, false);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights(MailboxACL.Right.Read);
        MailboxACL.EntryKey keyAlice = new MailboxACL.EntryKey("alice", MailboxACL.NameType.user, false);
        Future<Boolean> future1 = performACLUpdateInExecutor(executor, keyBob, rights, countDownLatch::countDown);
        Future<Boolean> future2 = performACLUpdateInExecutor(executor, keyAlice, rights, countDownLatch::countDown);
        awaitAll(future1, future2);

        assertThat(cassandraACLMapper.getACL(MAILBOX_ID).join())
            .isEqualTo(new MailboxACL().union(keyBob, rights).union(keyAlice, rights));
    }

    @Test
    public void twoConcurrentUpdatesWhenStoredShouldReturnACEWithTwoEntries() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        MailboxACL.EntryKey keyBenwa = new MailboxACL.EntryKey("benwa", MailboxACL.NameType.user, false);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights(MailboxACL.Right.Read);
        cassandraACLMapper.updateACL(MAILBOX_ID, MailboxACL.command().key(keyBenwa).rights(rights).asAddition());

        MailboxACL.EntryKey keyBob = new MailboxACL.EntryKey("bob", MailboxACL.NameType.user, false);
        MailboxACL.EntryKey keyAlice = new MailboxACL.EntryKey("alice", MailboxACL.NameType.user, false);
        Future<Boolean> future1 = performACLUpdateInExecutor(executor, keyBob, rights, countDownLatch::countDown);
        Future<Boolean> future2 = performACLUpdateInExecutor(executor, keyAlice, rights, countDownLatch::countDown);
        awaitAll(future1, future2);

        assertThat(cassandraACLMapper.getACL(MAILBOX_ID).join())
            .isEqualTo(new MailboxACL().union(keyBob, rights).union(keyAlice, rights).union(keyBenwa, rights));
    }

    private void awaitAll(Future<?>... futures) 
            throws InterruptedException, ExecutionException, TimeoutException {
        for (Future<?> future : futures) {
            future.get(10L, TimeUnit.SECONDS);
        }
    }

    private Future<Boolean> performACLUpdateInExecutor(ExecutorService executor, MailboxACL.EntryKey key, MailboxACL.Rfc4314Rights rights, CassandraACLMapper.CodeInjector runnable) {
        return executor.submit(() -> {
            CassandraACLMapper aclMapper = new CassandraACLMapper(
                cassandra.getConf(),
                new CassandraUserMailboxRightsDAO(cassandra.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION),
                CassandraConfiguration.DEFAULT_CONFIGURATION,
                runnable);
            try {
                aclMapper.updateACL(MAILBOX_ID, MailboxACL.command().key(key).rights(rights).asAddition());
            } catch (MailboxException exception) {
                throw new RuntimeException(exception);
            }
            return true;
        });
    }

}
