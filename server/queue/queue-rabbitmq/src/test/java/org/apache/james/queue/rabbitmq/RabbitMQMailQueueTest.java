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

import static java.time.temporal.ChronoUnit.HOURS;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.mail.internet.MimeMessage;

import org.apache.http.client.utils.URIBuilder;
import org.apache.james.backend.rabbitmq.DockerRabbitMQ;
import org.apache.james.backend.rabbitmq.RabbitChannelPool;
import org.apache.james.backend.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backend.rabbitmq.RabbitMQConnectionFactory;
import org.apache.james.backend.rabbitmq.ReusableDockerRabbitMQExtension;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;
import com.nurkiewicz.asyncretry.AsyncRetryExecutor;

@ExtendWith(ReusableDockerRabbitMQExtension.class)
public class RabbitMQMailQueueTest implements ManageableMailQueueContract, MailQueueMetricContract {
    private static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();
    private static final int THREE_BUCKET_COUNT = 3;
    private static final int UPDATE_BROWSE_START_PACE = 2;
    private static final Duration ONE_HOUR_SLICE_WINDOW = Duration.ofHours(1);
    private static final String SPOOL = "spool";
    private static final Instant IN_SLICE_1 = Instant.parse("2007-12-03T10:15:30.00Z");
    private static final Instant IN_SLICE_2 = IN_SLICE_1.plus(1, HOURS);
    private static final Instant IN_SLICE_3 = IN_SLICE_1.plus(2, HOURS);
    private static final Instant IN_SLICE_5 = IN_SLICE_1.plus(4, HOURS);
    private static final Instant IN_SLICE_6 = IN_SLICE_1.plus(6, HOURS);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraModule.aggregateModules(
        CassandraBlobModule.MODULE,
        CassandraMailQueueViewModule.MODULE));

    private RabbitMQMailQueueFactory mailQueueFactory;
    private Clock clock;

    @BeforeEach
    void setup(DockerRabbitMQ rabbitMQ, CassandraCluster cassandra, MailQueueMetricExtension.MailQueueMetricTestSystem metricTestSystem) throws IOException, TimeoutException, URISyntaxException {
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
        when(clock.instant()).thenReturn(IN_SLICE_1);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        MailQueueView mailQueueView = CassandraMailQueueViewTestFactory.factory(clock, random, cassandra.getConf(), cassandra.getTypesProvider(),
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

        RabbitMQConnectionFactory rabbitMQConnectionFactory = new RabbitMQConnectionFactory(rabbitMQConfiguration,
                new AsyncRetryExecutor(Executors.newSingleThreadScheduledExecutor()));

        RabbitClient rabbitClient = new RabbitClient(new RabbitChannelPool(rabbitMQConnectionFactory));
        RabbitMQMailQueue.Factory factory = new RabbitMQMailQueue.Factory(
            metricTestSystem.getSpyMetricFactory(),
            rabbitClient,
            mimeMessageStore,
            BLOB_ID_FACTORY,
            mailQueueView,
            Clock.systemUTC());
        RabbitMQManagementApi mqManagementApi = new RabbitMQManagementApi(rabbitManagementUri, new RabbitMQManagementCredentials("guest", "guest".toCharArray()));
        mailQueueFactory = new RabbitMQMailQueueFactory(rabbitClient, mqManagementApi, factory);
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
        ManageableMailQueue mailQueue = getManageableMailQueue();
        int emailCount = 5;

        when(clock.instant()).thenReturn(IN_SLICE_1);
        enqueueMailsInSlice(1, emailCount);

        when(clock.instant()).thenReturn(IN_SLICE_2);
        enqueueMailsInSlice(2, emailCount);

        when(clock.instant()).thenReturn(IN_SLICE_3);
        enqueueMailsInSlice(3, emailCount);

        when(clock.instant()).thenReturn(IN_SLICE_5);
        enqueueMailsInSlice(5, emailCount);

        when(clock.instant()).thenReturn(IN_SLICE_6);
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
        ManageableMailQueue mailQueue = getManageableMailQueue();
        int emailCount = 5;

        when(clock.instant()).thenReturn(IN_SLICE_1);
        enqueueMailsInSlice(1, emailCount);

        when(clock.instant()).thenReturn(IN_SLICE_2);
        enqueueMailsInSlice(2, emailCount);

        when(clock.instant()).thenReturn(IN_SLICE_3);
        enqueueMailsInSlice(3, emailCount);

        when(clock.instant()).thenReturn(IN_SLICE_5);
        enqueueMailsInSlice(5, emailCount);

        when(clock.instant()).thenReturn(IN_SLICE_6);

        dequeueMails(5);
        dequeueMails(5);
        dequeueMails(3);

        Stream<String> names = Iterators.toStream(mailQueue.browse())
            .map(ManageableMailQueue.MailQueueItemView::getMail)
            .map(Mail::getName);

        assertThat(names)
            .containsExactly("3-4", "3-5", "5-1", "5-2", "5-3", "5-4", "5-5");
    }

    @Disabled("RabbitMQ Mail Queue do not yet implement getSize()")
    @Override
    public void constructorShouldRegisterGetQueueSizeGauge(MailQueueMetricExtension.MailQueueMetricTestSystem testSystem) {
    }
    
    private void enqueueMailsInSlice(int slice, int emailCount) {
        ManageableMailQueue mailQueue = getManageableMailQueue();

        IntStream.rangeClosed(1, emailCount)
            .forEach(Throwing.intConsumer(bucketId -> mailQueue.enQueue(defaultMail()
                .name(slice + "-" + bucketId)
                .build())));
    }

    private void dequeueMails(int times) {
        ManageableMailQueue mailQueue = getManageableMailQueue();
        IntStream.rangeClosed(1, times)
            .forEach(Throwing.intConsumer(bucketId -> mailQueue.deQueue().done(true)));
    }
}
