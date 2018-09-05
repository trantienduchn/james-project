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

package org.apache.james.queue.jms;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.queue.api.DelayedManageableMailQueueContract;
import org.apache.james.queue.api.DelayedPriorityMailQueueContract;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueMetricContract;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.api.PriorityManageableMailQueueContract;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(BrokerExtension.class)
public class JMSMailQueueTest implements DelayedManageableMailQueueContract, PriorityManageableMailQueueContract, DelayedPriorityMailQueueContract,
    MailQueueMetricContract {

    private JMSMailQueue mailQueue;
    private NoopMetricFactory metricFactory;
    private NoopGaugeRegistry gaugeRegistry;
    private Metric enqueuedMailsMetric;
    private Metric dequeuedMailsMetric;

    @BeforeEach
    void setUp(BrokerService broker) {
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?create=false");
        RawMailQueueItemDecoratorFactory mailQueueItemDecoratorFactory = new RawMailQueueItemDecoratorFactory();
        metricFactory = spy(new NoopMetricFactory());
        gaugeRegistry = spy(new NoopGaugeRegistry());
        mockMetrics();
        String queueName = BrokerExtension.generateRandomQueueName(broker);

        mailQueue = spy(new JMSMailQueue(connectionFactory, mailQueueItemDecoratorFactory, queueName, metricFactory, gaugeRegistry));
    }

    @AfterEach
    void tearDown() {
        mailQueue.dispose();
    }

    @Override
    public MailQueue getMailQueue() {
        return mailQueue;
    }

    void mockMetrics() {
        enqueuedMailsMetric = mock(Metric.class);
        dequeuedMailsMetric = mock(Metric.class);
        when(metricFactory.generate(anyString())).thenReturn(enqueuedMailsMetric, dequeuedMailsMetric);
    }

    @Override
    public GaugeRegistry gaugeRegistry() {
        return gaugeRegistry;
    }

    @Override
    public Metric enqueuedMailsMetric() {
        return enqueuedMailsMetric;
    }

    @Override
    public Metric dequeuedMailsMetric() {
        return dequeuedMailsMetric;
    }

    @Test
    @Override
    public ManageableMailQueue getManageableMailQueue() {
        return mailQueue;
    }

    @Override
    @Disabled("JAMES-2295 Disabled as test was dead-locking")
    public void dequeueCanBeChainedBeforeAck() {

    }

    @Test
    @Override
    @Disabled("JAMES-2295 Disabled as test was dead-locking")
    public void dequeueCouldBeInterleavingWithOutOfOrderAck() {

    }

    @Test
    @Override
    @Disabled("JAMES-2308 Flushing JMS mail queue randomly re-order them" +
        "Random test failing around 1% of the time")
    public void flushShouldPreserveBrowseOrder() {

    }

    @Test
    @Override
    @Disabled("JAMES-2312 JMS clear mailqueue can ommit some messages" +
        "Random test failing around 1% of the time")
    public void clearShouldRemoveAllElements() {

    }
}
