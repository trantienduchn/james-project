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

package org.apache.james.queue.rabbitmq;

import static org.apache.james.queue.api.Mails.defaultMail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import javax.mail.internet.MimeMessage;

import org.apache.http.client.utils.URIBuilder;
import org.apache.james.backend.rabbitmq.DockerRabbitMQ;
import org.apache.james.backend.rabbitmq.RabbitChannelPool;
import org.apache.james.backend.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backend.rabbitmq.RabbitMQConnectionFactory;
import org.apache.james.backend.rabbitmq.ReusableDockerRabbitMQExtension;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.blob.cassandra.CassandraBlobsDAO;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueMetricContract;
import org.apache.james.queue.api.MailQueueMetricExtension;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.api.ManageableMailQueueContract;
import org.apache.james.queue.rabbitmq.view.api.MailQueueView;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewConfiguration;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewTestFactory;
import org.apache.james.util.streams.Iterators;
import org.apache.mailet.Mail;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.nurkiewicz.asyncretry.AsyncRetryExecutor;

@ExtendWith({ReusableDockerRabbitMQExtension.class, DockerCassandraExtension.class})
public class RabbitMQMailQueueTest implements ManageableMailQueueContract, MailQueueMetricContract {
    private static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();
    public static final int THREE_BUCKET_COUNT = 3;
    public static final int UPDATE_BROWSE_START_PACE = 2;
    public static final Duration ONE_HOUR_SLICE_WINDOW = Duration.ofHours(1);
    public static final String SPOOL = "spool";

    private static CassandraCluster cassandra;

    private RabbitMQMailQueueFactory mailQueueFactory;
    private Clock clock;

    @BeforeAll
    static void setUpClass(DockerCassandraExtension.DockerCassandra dockerCassandra) {
        cassandra = CassandraCluster.create(
            CassandraModule.aggregateModules(
                CassandraBlobModule.MODULE,
                CassandraMailQueueViewModule.MODULE),
            dockerCassandra.getHost());
    }

    @BeforeEach
    void setup(DockerRabbitMQ rabbitMQ, MailQueueMetricExtension.MailQueueMetricTestSystem metricTestSystem) throws IOException, TimeoutException, URISyntaxException {
        CassandraBlobsDAO blobsDAO = new CassandraBlobsDAO(cassandra.getConf(), CassandraConfiguration.DEFAULT_CONFIGURATION, BLOB_ID_FACTORY);
        Store<MimeMessage, MimeMessagePartsId> mimeMessageStore = MimeMessageStore.factory(blobsDAO).mimeMessageStore();

        URI amqpUri = new URIBuilder()
            .setScheme("amqp")
            .setHost(rabbitMQ.getHostIp())
            .setPort(rabbitMQ.getPort())
            .build();
        URI rabbitManagementUri = new URIBuilder()
            .setScheme("http")
            .setHost(rabbitMQ.getHostIp())
            .setPort(rabbitMQ.getAdminPort())
            .build();
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.parse("2007-12-03T10:15:30.00Z"));

        MailQueueView mailQueueView = CassandraMailQueueViewTestFactory.factory(clock, cassandra.getConf(), cassandra.getTypesProvider(),
            CassandraMailQueueViewConfiguration.builder()
                    .bucketCount(THREE_BUCKET_COUNT)
                    .updateBrowseStartPace(UPDATE_BROWSE_START_PACE)
                    .sliceWindow(ONE_HOUR_SLICE_WINDOW)
                    .build())
            .create(MailQueueName.fromString(SPOOL));

        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(amqpUri)
            .managementUri(rabbitManagementUri)
            .build();

        RabbitMQConnectionFactory rabbitMQConnectionFactory = new RabbitMQConnectionFactory(
            rabbitMQConfiguration,
            new AsyncRetryExecutor(Executors.newSingleThreadScheduledExecutor()));

        RabbitClient rabbitClient = new RabbitClient(new RabbitChannelPool(rabbitMQConnectionFactory));
        RabbitMQMailQueue.Factory factory = new RabbitMQMailQueue.Factory(
            metricTestSystem.getSpyMetricFactory(),
            rabbitClient,
            mimeMessageStore,
            BLOB_ID_FACTORY,
            mailQueueView);
        RabbitMQManagementApi mqManagementApi = new RabbitMQManagementApi(rabbitManagementUri, new RabbitMQManagementCredentials("guest", "guest".toCharArray()));
        mailQueueFactory = new RabbitMQMailQueueFactory(rabbitClient, mqManagementApi, factory);
    }

    @AfterEach
    void tearDown() {
        cassandra.clearTables();
    }

    @AfterAll
    static void tearDownClass() {
        cassandra.closeCluster();
    }

    @Override
    public MailQueue getMailQueue() {
        return mailQueueFactory.createQueue(SPOOL);
    }

    @Override
    public ManageableMailQueue getManageableMailQueue() {
        return mailQueueFactory.createQueue(SPOOL);
    }

    @Test
    void browseShouldReturnCurrentlyEnqueuedMailFromAllSlices() throws Exception {
        Instant inSlice1 = Instant.parse("2007-12-03T10:15:30.00Z");
        Instant inSlice2 = Instant.parse("2007-12-03T11:15:30.00Z");
        Instant inSlice3 = Instant.parse("2007-12-03T12:15:30.00Z");
        Instant inSlice5 = Instant.parse("2007-12-03T15:15:30.00Z");
        Instant inSlice6 = Instant.parse("2007-12-03T16:15:30.00Z");

        when(clock.instant()).thenReturn(inSlice1);

        ManageableMailQueue mailQueue = getManageableMailQueue();

        mailQueue.enQueue(defaultMail().name("1-1").build());
        mailQueue.enQueue(defaultMail().name("1-2").build());
        mailQueue.enQueue(defaultMail().name("1-3").build());
        mailQueue.enQueue(defaultMail().name("1-4").build());
        mailQueue.enQueue(defaultMail().name("1-5").build());

        when(clock.instant()).thenReturn(inSlice2);

        mailQueue.enQueue(defaultMail().name("2-1").build());
        mailQueue.enQueue(defaultMail().name("2-2").build());
        mailQueue.enQueue(defaultMail().name("2-3").build());
        mailQueue.enQueue(defaultMail().name("2-4").build());
        mailQueue.enQueue(defaultMail().name("2-5").build());

        when(clock.instant()).thenReturn(inSlice3);

        mailQueue.enQueue(defaultMail().name("3-1").build());
        mailQueue.enQueue(defaultMail().name("3-2").build());
        mailQueue.enQueue(defaultMail().name("3-3").build());
        mailQueue.enQueue(defaultMail().name("3-4").build());
        mailQueue.enQueue(defaultMail().name("3-5").build());

        when(clock.instant()).thenReturn(inSlice5);

        mailQueue.enQueue(defaultMail().name("5-1").build());
        mailQueue.enQueue(defaultMail().name("5-2").build());
        mailQueue.enQueue(defaultMail().name("5-3").build());
        mailQueue.enQueue(defaultMail().name("5-4").build());
        mailQueue.enQueue(defaultMail().name("5-5").build());

        when(clock.instant()).thenReturn(inSlice6);

        Stream<String> names = Iterators.toStream(mailQueue.browse())
            .map(ManageableMailQueue.MailQueueItemView::getMail)
            .map(Mail::getName);

        assertThat(names).containsExactly(
            "1-1", "1-2", "1-3", "1-4", "1-5",
            "2-1", "2-2", "2-3", "2-4", "2-5",
            "3-1", "3-2", "3-3", "3-4", "3-5",
            "5-1", "5-2", "5-3", "5-4", "5-5");
    }

    @Test
    void browseAndDequeueShouldCombineWellWhenDifferentSlices() throws Exception {
        Instant inSlice1 = Instant.parse("2007-12-03T10:15:30.00Z");
        Instant inSlice2 = Instant.parse("2007-12-03T11:15:30.00Z");
        Instant inSlice3 = Instant.parse("2007-12-03T12:15:30.00Z");
        Instant inSlice5 = Instant.parse("2007-12-03T15:15:30.00Z");
        Instant inSlice6 = Instant.parse("2007-12-03T16:15:30.00Z");

        when(clock.instant()).thenReturn(inSlice1);

        ManageableMailQueue mailQueue = getManageableMailQueue();

        mailQueue.enQueue(defaultMail().name("1-1").build());
        mailQueue.enQueue(defaultMail().name("1-2").build());
        mailQueue.enQueue(defaultMail().name("1-3").build());
        mailQueue.enQueue(defaultMail().name("1-4").build());
        mailQueue.enQueue(defaultMail().name("1-5").build());

        when(clock.instant()).thenReturn(inSlice2);

        mailQueue.enQueue(defaultMail().name("2-1").build());
        mailQueue.enQueue(defaultMail().name("2-2").build());
        mailQueue.enQueue(defaultMail().name("2-3").build());
        mailQueue.enQueue(defaultMail().name("2-4").build());
        mailQueue.enQueue(defaultMail().name("2-5").build());

        when(clock.instant()).thenReturn(inSlice3);

        mailQueue.enQueue(defaultMail().name("3-1").build());
        mailQueue.enQueue(defaultMail().name("3-2").build());
        mailQueue.enQueue(defaultMail().name("3-3").build());
        mailQueue.enQueue(defaultMail().name("3-4").build());
        mailQueue.enQueue(defaultMail().name("3-5").build());

        when(clock.instant()).thenReturn(inSlice5);

        mailQueue.enQueue(defaultMail().name("5-1").build());
        mailQueue.enQueue(defaultMail().name("5-2").build());
        mailQueue.enQueue(defaultMail().name("5-3").build());
        mailQueue.enQueue(defaultMail().name("5-4").build());
        mailQueue.enQueue(defaultMail().name("5-5").build());

        when(clock.instant()).thenReturn(inSlice6);

        MailQueue.MailQueueItem item1_1 = mailQueue.deQueue();
        MailQueue.MailQueueItem item1_2 = mailQueue.deQueue();
        MailQueue.MailQueueItem item1_3 = mailQueue.deQueue();
        MailQueue.MailQueueItem item1_4 = mailQueue.deQueue();
        MailQueue.MailQueueItem item1_5 = mailQueue.deQueue();

        MailQueue.MailQueueItem item2_1 = mailQueue.deQueue();
        MailQueue.MailQueueItem item2_2 = mailQueue.deQueue();
        MailQueue.MailQueueItem item2_3 = mailQueue.deQueue();
        MailQueue.MailQueueItem item2_4 = mailQueue.deQueue();
        MailQueue.MailQueueItem item2_5 = mailQueue.deQueue();

        MailQueue.MailQueueItem item3_1 = mailQueue.deQueue();
        MailQueue.MailQueueItem item3_2 = mailQueue.deQueue();
        MailQueue.MailQueueItem item3_3 = mailQueue.deQueue();
        MailQueue.MailQueueItem item3_4 = mailQueue.deQueue();
        MailQueue.MailQueueItem item3_5 = mailQueue.deQueue();

        item1_1.done(true);
        item1_2.done(true);
        item1_3.done(true);
        item1_4.done(true);
        item1_5.done(true);

        item2_1.done(true);
        item2_2.done(true);
        item2_3.done(true);
        item2_4.done(false);
        item2_5.done(true);

        item3_1.done(true);
        item3_2.done(true);
        item3_3.done(true);
        item3_4.done(true);
        item3_5.done(true);

        Stream<String> names = Iterators.toStream(mailQueue.browse())
            .map(ManageableMailQueue.MailQueueItemView::getMail)
            .map(Mail::getName);

        assertThat(names)
            .containsExactly("2-4", "5-1", "5-2", "5-3", "5-4", "5-5");
    }

    @Disabled
    @Override
    public void clearShouldNotFailWhenBrowsingIterating() {

    }

    @Disabled
    @Override
    public void browseShouldNotFailWhenConcurrentClearWhenIterating() {

    }

    @Disabled
    @Override
    public void removeShouldNotFailWhenBrowsingIterating() {

    }

    @Disabled
    @Override
    public void browseShouldNotFailWhenConcurrentRemoveWhenIterating() {

    }

    @Disabled
    @Override
    public void removeByNameShouldRemoveSpecificEmail() {

    }

    @Disabled
    @Override
    public void removeBySenderShouldRemoveSpecificEmail() {

    }

    @Disabled
    @Override
    public void removeByRecipientShouldRemoveSpecificEmail() {

    }

    @Disabled
    @Override
    public void removeByRecipientShouldRemoveSpecificEmailWhenMultipleRecipients() {

    }

    @Disabled
    @Override
    public void removeByNameShouldNotFailWhenQueueIsEmpty() {

    }

    @Disabled
    @Override
    public void removeBySenderShouldNotFailWhenQueueIsEmpty() {

    }

    @Disabled
    @Override
    public void removeByRecipientShouldNotFailWhenQueueIsEmpty() {

    }

    @Disabled
    @Override
    public void clearShouldNotFailWhenQueueIsEmpty() {

    }

    @Disabled
    @Override
    public void clearShouldRemoveAllElements() {

    }

    @Disabled("RabbitMQ Mail Queue do not yet implement getSize()")
    @Override
    public void constructorShouldRegisterGetQueueSizeGauge(MailQueueMetricExtension.MailQueueMetricTestSystem testSystem) {

    }
}
