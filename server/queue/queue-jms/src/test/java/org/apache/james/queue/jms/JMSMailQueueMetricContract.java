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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.stream.IntStream;

import javax.mail.MessagingException;

import org.apache.james.metrics.api.Gauge;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.Metric;
import org.apache.james.queue.api.MailQueue;
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
            .forEach(Throwing.intConsumer(time -> testSystem.getMockMailQueue().enQueue(fakeMail())));
    }

    default void deQueueMail(JMSMailQueueMetricExtension.JMSMailQueueMetricTestSystem testSystem, Integer times) throws Exception {
        IntStream
            .rangeClosed(1, times)
            .forEach(Throwing.intConsumer(time -> testSystem.getMockMailQueue().deQueue().done(true)));
    }

    @Test
    default void enqueueShouldIncreaseMetric(JMSMailQueueMetricExtension.JMSMailQueueMetricTestSystem testSystem) throws Exception {
        Metric enqueuedMailsMetric = testSystem.getMockEnqueuedMailsMetric();

        enQueueMail(testSystem, 2);

        verify(enqueuedMailsMetric, times(2)).increment();
        verifyNoMoreInteractions(enqueuedMailsMetric);
    }

    @Test
    default void enqueueShouldRegisterGauge(JMSMailQueueMetricExtension.JMSMailQueueMetricTestSystem testSystem) throws Exception {
        Metric enqueuedMailsMetric = testSystem.getMockEnqueuedMailsMetric();
        GaugeRegistry gaugeRegistry = testSystem.getMockGaugeRegistry();

        enQueueMail(testSystem, 2);

        verify(enqueuedMailsMetric, times(2)).increment();
        verifyNoMoreInteractions(enqueuedMailsMetric);

        ArgumentCaptor<Gauge> gaugeCaptor = ArgumentCaptor.forClass(Gauge.class);
        verify(gaugeRegistry, times(2)).register(anyString(), gaugeCaptor.capture());
        verifyNoMoreInteractions(gaugeRegistry);

        assertThat(gaugeCaptor.getValue().get())
            .isEqualTo(2L);
    }

    @Test
    default void enqueueShouldNotDecreaseMetric(JMSMailQueueMetricExtension.JMSMailQueueMetricTestSystem testSystem) throws Exception {
        Metric enqueuedMailsMetric = testSystem.getMockEnqueuedMailsMetric();
        Metric dequeuedMailsMetric = testSystem.getMockDequeuedMailsMetric();

        enQueueMail(testSystem, 2);

        verify(enqueuedMailsMetric, times(2)).increment();
        verifyNoMoreInteractions(dequeuedMailsMetric);
    }

    @Test
    default void enqueueShouldWorkWhenMetricRecordingGotException(JMSMailQueueMetricExtension.JMSMailQueueMetricTestSystem testSystem) throws Exception {
        JMSMailQueue mailQueue = testSystem.getMockMailQueue();
        when(mailQueue.getSize()).thenThrow(MailQueue.MailQueueException.class);

        enQueueMail(testSystem, 1);

        MailQueue.MailQueueItem mailQueueItem = testSystem.getMockMailQueue().deQueue();
        assertThat(mailQueueItem.getMail().getName())
            .isEqualTo("name1");
    }

    @Test
    default void dequeueShouldDecreaseMetric(JMSMailQueueMetricExtension.JMSMailQueueMetricTestSystem testSystem) throws Exception {
        Metric dequeuedMailsMetric = testSystem.getMockDequeuedMailsMetric();

        enQueueMail(testSystem, 2);
        deQueueMail(testSystem, 2);

        verify(dequeuedMailsMetric, times(2)).decrement();
        verifyNoMoreInteractions(dequeuedMailsMetric);
    }

    @Test
    default void dequeueShouldRegisterGauge(JMSMailQueueMetricExtension.JMSMailQueueMetricTestSystem testSystem) throws Exception {
        Metric enqueuedMailsMetric = testSystem.getMockDequeuedMailsMetric();
        GaugeRegistry gaugeRegistry = testSystem.getMockGaugeRegistry();

        enQueueMail(testSystem, 3);
        deQueueMail(testSystem, 2);

        verify(enqueuedMailsMetric, times(2)).decrement();
        verifyNoMoreInteractions(enqueuedMailsMetric);

        ArgumentCaptor<Gauge> gaugeCaptor = ArgumentCaptor.forClass(Gauge.class);
        verify(gaugeRegistry, times(5)).register(anyString(), gaugeCaptor.capture());
        verifyNoMoreInteractions(gaugeRegistry);

        assertThat(gaugeCaptor.getValue().get())
            .isEqualTo(1L);
    }

    @Test
    default void dequeueShouldNotIncreaseMetric(JMSMailQueueMetricExtension.JMSMailQueueMetricTestSystem testSystem) throws Exception {
        Metric enqueuedMailsMetric = testSystem.getMockEnqueuedMailsMetric();
        Metric dequeuedMailsMetric = testSystem.getMockDequeuedMailsMetric();

        enQueueMail(testSystem, 2);
        deQueueMail(testSystem, 2);

        verify(dequeuedMailsMetric, times(2)).decrement();
        verifyNoMoreInteractions(dequeuedMailsMetric);

        verify(enqueuedMailsMetric, times(2)).increment();
        verifyNoMoreInteractions(enqueuedMailsMetric);
    }

    @Test
    default void dequeueShouldWorkWhenMetricRecordingGotException(JMSMailQueueMetricExtension.JMSMailQueueMetricTestSystem testSystem) throws Exception {
        JMSMailQueue mailQueue = testSystem.getMockMailQueue();
        when(mailQueue.getSize()).thenThrow(MailQueue.MailQueueException.class, MailQueue.MailQueueException.class);

        enQueueMail(testSystem, 1);

        MailQueue.MailQueueItem mailQueueItem = testSystem.getMockMailQueue().deQueue();
        assertThat(mailQueueItem.getMail().getName())
            .isEqualTo("name1");
    }
}
