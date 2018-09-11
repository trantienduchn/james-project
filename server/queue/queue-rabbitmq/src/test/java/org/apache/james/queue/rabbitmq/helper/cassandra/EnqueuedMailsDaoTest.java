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

package org.apache.james.queue.rabbitmq.helper.cassandra;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.queue.rabbitmq.MailQueueName;
import org.apache.james.queue.rabbitmq.helper.cassandra.model.EnqueuedMail;
import org.apache.james.queue.rabbitmq.helper.cassandra.model.MailKey;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.assertj.core.api.SoftAssertions;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

public class EnqueuedMailsDaoTest {

    private static final MailQueueName OUT_GOING_1 = MailQueueName.fromString("OUT_GOING_1");
    private static final MailQueueName OUT_GOING_2 = MailQueueName.fromString("OUT_GOING_2");
    private static final MailKey MAIL_KEY_1 = MailKey.of("mailkey1");
    private static final MailKey MAIL_KEY_2 = MailKey.of("mailkey2");
    private static int ENQUEUED_PACE_UPDATE = 5;
    private static int BUCKET_ID = 10;
    private static Instant NOW = Instant.now();

    @ClassRule
    public static DockerCassandraRule cassandraServer = new DockerCassandraRule();
    private static CassandraCluster cassandra;

    private EnqueuedMailsDAO testee;

    @BeforeClass
    public static void setUpClass() {
        cassandra = CassandraCluster.create(CassandraRabbitMQHelperModule.MODULE, cassandraServer.getHost());
    }

    @Before
    public void setUp() {
        CassandraRabbitMQHelperConfiguration configuration = new CassandraRabbitMQHelperConfiguration(1, ENQUEUED_PACE_UPDATE, Duration.ofHours(1));

        testee = new EnqueuedMailsDAO(
            cassandra.getConf(),
            CassandraUtils.WITH_DEFAULT_CONFIGURATION,
            cassandra.getTypesProvider(),
            configuration);
    }

    @After
    public void tearDown() {
        cassandra.clearTables();
    }

    @AfterClass
    public static void tearDownClass() {
        cassandra.closeCluster();
    }

    @Test
    public void insertShouldWork() throws Exception {
        Mail mail = FakeMail.builder()
            .name(MAIL_KEY_1.getMailKey())
            .build();
        EnqueuedMail enqueuedMail = EnqueuedMail.builder()
            .mail(mail)
            .mailQueueName(OUT_GOING_1)
            .mailKey(MAIL_KEY_1)
            .bucketId(BUCKET_ID)
            .timeRangeStart(NOW)
            .build();
        testee.insert(enqueuedMail).join();

        Stream<EnqueuedMail> selectedEnqueuedMails = testee.selectEnqueuedMails(OUT_GOING_1, BUCKET_ID, NOW).join();

        assertThat(selectedEnqueuedMails).hasSize(1);
    }

    @Test
    public void selectEnqueuedMailsShouldSelectShouldWork() throws Exception {
        Mail mail1 = FakeMail.builder()
            .name(MAIL_KEY_1.getMailKey())
            .build();
        EnqueuedMail enqueuedMail1 = EnqueuedMail.builder()
            .mail(mail1)
            .mailQueueName(OUT_GOING_1)
            .mailKey(MAIL_KEY_1)
            .bucketId(BUCKET_ID)
            .timeRangeStart(NOW)
            .build();
        testee.insert(enqueuedMail1).join();

        Mail mail2 = FakeMail.builder()
            .name(MAIL_KEY_1.getMailKey())
            .build();
        EnqueuedMail enqueuedMail2 = EnqueuedMail.builder()
            .mail(mail2)
            .mailQueueName(OUT_GOING_1)
            .mailKey(MAIL_KEY_1)
            .bucketId(BUCKET_ID)
            .timeRangeStart(NOW)
            .build();
        testee.insert(enqueuedMail2).join();

        Stream<EnqueuedMail> selectedEnqueuedMails = testee.selectEnqueuedMails(OUT_GOING_1, BUCKET_ID, NOW)
            .join();

        assertThat(selectedEnqueuedMails)
            .hasSize(1)
            .hasOnlyOneElementSatisfying(selectedEnqueuedMail -> {
                SoftAssertions softly = new SoftAssertions();
                softly.assertThat(selectedEnqueuedMail.getMailQueueName()).isEqualTo(OUT_GOING_1);
                softly.assertThat(selectedEnqueuedMail.getBucketId()).isEqualTo(BUCKET_ID);
                softly.assertThat(selectedEnqueuedMail.getTimeRangeStart()).isEqualTo(NOW);
                softly.assertThat(selectedEnqueuedMail.getMailKey()).isEqualTo(MAIL_KEY_1);
            });
    }
}