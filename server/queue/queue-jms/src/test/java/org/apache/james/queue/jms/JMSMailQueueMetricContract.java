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

import static org.apache.james.queue.api.Mails.defaultMail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.stream.IntStream;

import javax.mail.MessagingException;

import org.apache.james.metrics.api.Gauge;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.Metric;
import org.apache.james.queue.api.MailQueueContract;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import com.github.fge.lambdas.Throwing;

@ExtendWith(JMSMailQueueMetricExtension.class)
public interface JMSMailQueueMetricContract extends MailQueueContract {

    default FakeMail fakeMail() throws MessagingException {
        return defaultMail()
            .name("name1")
            .build();
    }

    default void enQueueMail(JMSMailQueueMetricExtension.JMSMailQueueMetricTestSystem testSystem, Integer times) throws Exception {
        IntStream
            .rangeClosed(1, times)
            .forEach(Throwing.intConsumer(time -> testSystem.getSpyMailQueue().enQueue(fakeMail())));
    }

    default void deQueueMail(JMSMailQueueMetricExtension.JMSMailQueueMetricTestSystem testSystem, Integer times) throws Exception {
        IntStream
            .rangeClosed(1, times)
            .forEach(Throwing.intConsumer(time -> testSystem.getSpyMailQueue().deQueue().done(true)));
    }

    @Test
    default void constructorShouldRegisterGetQueueSizeGauge(JMSMailQueueMetricExtension.JMSMailQueueMetricTestSystem testSystem) throws Exception {
        GaugeRegistry gaugeRegistry = testSystem.getSpyGaugeRegistry();

        ArgumentCaptor<Gauge> gaugeCaptor = ArgumentCaptor.forClass(Gauge.class);
        verify(gaugeRegistry, times(1)).register(any(), gaugeCaptor.capture());
        verifyNoMoreInteractions(gaugeRegistry);

        Gauge registerdGauge = gaugeCaptor.getValue();
        assertThat(registerdGauge.get()).isEqualTo(0L);
    }

    @Test
    default void enqueueShouldIncreaseEnQueueMetric(JMSMailQueueMetricExtension.JMSMailQueueMetricTestSystem testSystem) throws Exception {
        Metric mockedEnqueuedMailsMetric = testSystem.getMockEnqueuedMailsMetric();

        enQueueMail(testSystem, 2);

        verify(mockedEnqueuedMailsMetric, times(2)).increment();
        verifyNoMoreInteractions(mockedEnqueuedMailsMetric);
    }

    @Test
    default void enqueueShouldNotDecreaseEnqueueMetric(JMSMailQueueMetricExtension.JMSMailQueueMetricTestSystem testSystem) throws Exception {
        Metric mockedEnqueuedMailsMetric = testSystem.getMockEnqueuedMailsMetric();
        Metric mockedDequeuedMailsMetric = testSystem.getMockDequeuedMailsMetric();

        enQueueMail(testSystem, 2);

        verify(mockedEnqueuedMailsMetric, times(2)).increment();
        verifyNoMoreInteractions(mockedDequeuedMailsMetric);
    }

    @Test
    default void dequeueShouldDecreaseDequeueMetric(JMSMailQueueMetricExtension.JMSMailQueueMetricTestSystem testSystem) throws Exception {
        Metric mockedDequeuedMailsMetric = testSystem.getMockDequeuedMailsMetric();

        enQueueMail(testSystem, 2);
        deQueueMail(testSystem, 2);

        verify(mockedDequeuedMailsMetric, times(2)).decrement();
        verifyNoMoreInteractions(mockedDequeuedMailsMetric);
    }

    @Test
    default void dequeueShouldNotIncreaseDequeueMetric(JMSMailQueueMetricExtension.JMSMailQueueMetricTestSystem testSystem) throws Exception {
        Metric mockedEnqueuedMailsMetric = testSystem.getMockEnqueuedMailsMetric();
        Metric mockedDequeuedMailsMetric = testSystem.getMockDequeuedMailsMetric();

        enQueueMail(testSystem, 2);
        deQueueMail(testSystem, 2);

        verify(mockedDequeuedMailsMetric, times(2)).decrement();
        verifyNoMoreInteractions(mockedDequeuedMailsMetric);

        verify(mockedEnqueuedMailsMetric, times(2)).increment();
        verifyNoMoreInteractions(mockedEnqueuedMailsMetric);
    }
}
