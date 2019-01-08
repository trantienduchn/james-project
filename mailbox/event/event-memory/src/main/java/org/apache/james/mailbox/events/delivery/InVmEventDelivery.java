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

package org.apache.james.mailbox.events.delivery;

import java.util.Collection;

import javax.inject.Inject;

import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class InVmEventDelivery implements EventDelivery {
    private static final Logger LOGGER = LoggerFactory.getLogger(InVmEventDelivery.class);
    private static final int MAX_RETRIES = 3;

    private final MetricFactory metricFactory;

    @Inject
    @VisibleForTesting
    public InVmEventDelivery(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
    }

    @Override
    public ExecutionStages deliver(Collection<MailboxListener> mailboxListeners, Event event) {
        Mono<Void> synchronousListeners = doDeliver(
            filterByExecutionMode(mailboxListeners, MailboxListener.ExecutionMode.SYNCHRONOUS), event)
            .cache();
        Mono<Void> asyncListener = doDeliver(
            filterByExecutionMode(mailboxListeners, MailboxListener.ExecutionMode.ASYNCHRONOUS), event)
            .cache();

        synchronousListeners.subscribe();
        asyncListener.subscribe();

        return new ExecutionStages(synchronousListeners, asyncListener);
    }

    private ImmutableList<MailboxListener> filterByExecutionMode(Collection<MailboxListener> mailboxListeners, MailboxListener.ExecutionMode executionMode) {
        return mailboxListeners.stream()
            .filter(listener -> listener.getExecutionMode() == executionMode)
            .collect(Guavate.toImmutableList());
    }

    private Mono<Void> doDeliver(Collection<MailboxListener> mailboxListeners, Event event) {
        return Flux.fromIterable(mailboxListeners)
            .flatMap(mailboxListener -> deliveryWithRetries(event, mailboxListener))
            .then()
            .subscribeOn(Schedulers.elastic());
    }

    private Mono<Object> deliveryWithRetries(Event event, MailboxListener mailboxListener) {
        return Mono.fromRunnable(() -> doDeliverToListener(mailboxListener, event))
            .retry(MAX_RETRIES)
            .doOnError(throwable -> LOGGER.error("listener {} exceeded maximum retry({}) to handle event {}",
                mailboxListener.getClass().getCanonicalName(),
                MAX_RETRIES,
                event.getClass().getCanonicalName(),
                throwable));
    }

    private void doDeliverToListener(MailboxListener mailboxListener, Event event) {
        TimeMetric timer = metricFactory.timer("mailbox-listener-" + mailboxListener.getClass().getSimpleName());
        try {
            mailboxListener.event(event);
        } catch (Exception ex) {
            LOGGER.error("Error while processing listener {} for {}",
                mailboxListener.getClass().getCanonicalName(), event.getClass().getCanonicalName(), ex);
            throw ex;
        } finally {
            timer.stopAndPublish();
        }
    }

}
