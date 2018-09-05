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

package org.apache.james.queue.api;

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
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.github.fge.lambdas.Throwing;

public interface MailQueueMetricContract extends MailQueueContract {

    GaugeRegistry gaugeRegistry();
    Metric enqueuedMailsMetric();
    Metric dequeuedMailsMetric();

    default FakeMail fakeMail() throws MessagingException {
        return defaultMail()
            .name("name1")
            .build();
    }

    default void enQueueMail(Integer times) throws Exception {
        MailQueue mailQueue = getMailQueue();

        IntStream
            .rangeClosed(1, times)
            .forEach(Throwing.intConsumer(time -> mailQueue.enQueue(fakeMail())));
    }

    default void deQueueMail(Integer times) throws Exception {
        MailQueue mailQueue = getMailQueue();

        IntStream
            .rangeClosed(1, times)
            .forEach(Throwing.intConsumer(time -> mailQueue.deQueue().done(true)));
    }

    @Test
    default void enqueueShouldIncreaseMetric() throws Exception {
        Metric enqueuedMailsMetric = enqueuedMailsMetric();

        enQueueMail(2);

        verify(enqueuedMailsMetric, times(2)).increment();
        verifyNoMoreInteractions(enqueuedMailsMetric);
    }

    @Test
    default void enqueueShouldRegisterGauge() throws Exception {
        Metric enqueuedMailsMetric = enqueuedMailsMetric();
        GaugeRegistry gaugeRegistry = gaugeRegistry();

        enQueueMail(2);

        verify(enqueuedMailsMetric, times(2)).increment();
        verifyNoMoreInteractions(enqueuedMailsMetric);

        ArgumentCaptor<Gauge> gaugeCaptor = ArgumentCaptor.forClass(Gauge.class);
        verify(gaugeRegistry, times(2)).register(anyString(), gaugeCaptor.capture());
        verifyNoMoreInteractions(gaugeRegistry);

        assertThat(gaugeCaptor.getValue().get())
            .isEqualTo(2L);
    }

    @Test
    default void enqueueShouldNotDecreaseMetric() throws Exception {
        Metric enqueuedMailsMetric = enqueuedMailsMetric();
        Metric dequeuedMailsMetric = dequeuedMailsMetric();

        enQueueMail(2);

        verify(enqueuedMailsMetric, times(2)).increment();
        verifyNoMoreInteractions(dequeuedMailsMetric);
    }

    @Test
    default void enqueueShouldWorkWhenMetricRecordingGotException() throws Exception {
        ManageableMailQueue mailQueue = (ManageableMailQueue) getMailQueue();
        when(mailQueue.getSize()).thenThrow(MailQueue.MailQueueException.class);

        enQueueMail(1);

        MailQueue.MailQueueItem mailQueueItem = getMailQueue().deQueue();
        assertThat(mailQueueItem.getMail().getName())
            .isEqualTo("name1");
    }

    @Test
    default void dequeueShouldDecreaseMetric() throws Exception {
        Metric dequeuedMailsMetric = dequeuedMailsMetric();

        enQueueMail(2);
        deQueueMail(2);

        verify(dequeuedMailsMetric, times(2)).decrement();
        verifyNoMoreInteractions(dequeuedMailsMetric);
    }

    @Test
    default void dequeueShouldRegisterGauge() throws Exception {
        Metric enqueuedMailsMetric = dequeuedMailsMetric();
        GaugeRegistry gaugeRegistry = gaugeRegistry();

        enQueueMail(3);
        deQueueMail(2);

        verify(enqueuedMailsMetric, times(2)).decrement();
        verifyNoMoreInteractions(enqueuedMailsMetric);

        ArgumentCaptor<Gauge> gaugeCaptor = ArgumentCaptor.forClass(Gauge.class);
        verify(gaugeRegistry, times(5)).register(anyString(), gaugeCaptor.capture());
        verifyNoMoreInteractions(gaugeRegistry);

        assertThat(gaugeCaptor.getValue().get())
            .isEqualTo(1L);
    }

    @Test
    default void dequeueShouldNotIncreaseMetric() throws Exception {
        Metric enqueuedMailsMetric = enqueuedMailsMetric();
        Metric dequeuedMailsMetric = dequeuedMailsMetric();

        enQueueMail(2);
        deQueueMail(2);

        verify(dequeuedMailsMetric, times(2)).decrement();
        verifyNoMoreInteractions(dequeuedMailsMetric);

        verify(enqueuedMailsMetric, times(2)).increment();
        verifyNoMoreInteractions(enqueuedMailsMetric);
    }

    @Test
    default void dequeueShouldWorkWhenMetricRecordingGotException() throws Exception {
        ManageableMailQueue mailQueue = (ManageableMailQueue) getMailQueue();
        when(mailQueue.getSize()).thenThrow(MailQueue.MailQueueException.class, MailQueue.MailQueueException.class);

        enQueueMail(1);

        MailQueue.MailQueueItem mailQueueItem = getMailQueue().deQueue();
        assertThat(mailQueueItem.getMail().getName())
            .isEqualTo("name1");
    }
}
