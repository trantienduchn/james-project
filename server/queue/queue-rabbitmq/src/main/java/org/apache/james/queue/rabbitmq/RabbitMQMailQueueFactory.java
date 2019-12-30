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

import static org.apache.james.backends.rabbitmq.Constants.AUTO_DELETE;
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.apache.james.backends.rabbitmq.Constants.EXCLUSIVE;
import static org.apache.james.backends.rabbitmq.Constants.NO_ARGUMENTS;
import static org.apache.james.queue.api.MailQueue.QUEUE_SIZE_METRIC_NAME_PREFIX;

import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.inject.Inject;

import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;
import org.apache.james.queue.rabbitmq.view.RabbitMQMailQueueConfiguration;
import org.apache.james.queue.rabbitmq.view.api.MailQueueView;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.QueueSpecification;

public class RabbitMQMailQueueFactory implements MailQueueFactory<RabbitMQMailQueue> {

    @VisibleForTesting static class PrivateFactory {
        private final MetricFactory metricFactory;
        private final GaugeRegistry gaugeRegistry;
        private final ReactorRabbitMQChannelPool reactorRabbitMQChannelPool;
        private final BlobStore blobStore;
        private final MailReferenceSerializer mailReferenceSerializer;
        private final Function<MailReferenceDTO, MailWithEnqueueId> mailLoader;
        private final MailQueueView.Factory mailQueueViewFactory;
        private final Clock clock;
        private final MailQueueItemDecoratorFactory decoratorFactory;
        private final RabbitMQMailQueueConfiguration configuration;

        @Inject
        @VisibleForTesting PrivateFactory(MetricFactory metricFactory,
                                          GaugeRegistry gaugeRegistry,
                                          ReactorRabbitMQChannelPool reactorRabbitMQChannelPool,
                                          BlobStore blobStore,
                                          BlobId.Factory blobIdFactory,
                                          MailQueueView.Factory mailQueueViewFactory,
                                          Clock clock,
                                          MailQueueItemDecoratorFactory decoratorFactory,
                                          RabbitMQMailQueueConfiguration configuration) {
            this.metricFactory = metricFactory;
            this.gaugeRegistry = gaugeRegistry;
            this.reactorRabbitMQChannelPool = reactorRabbitMQChannelPool;
            this.blobStore = blobStore;
            this.mailQueueViewFactory = mailQueueViewFactory;
            this.clock = clock;
            this.decoratorFactory = decoratorFactory;
            this.mailReferenceSerializer = new MailReferenceSerializer();
            this.mailLoader = Throwing.function(new MailLoader(blobStore, blobIdFactory)::load).sneakyThrow();
            this.configuration = configuration;
        }

        RabbitMQMailQueue create(MailQueueName mailQueueName) {
            MailQueueView mailQueueView = mailQueueViewFactory.create(mailQueueName);
            mailQueueView.initialize(mailQueueName);

            RabbitMQMailQueue rabbitMQMailQueue = new RabbitMQMailQueue(
                metricFactory,
                mailQueueName,
                new Enqueuer(mailQueueName, reactorRabbitMQChannelPool, blobStore, mailReferenceSerializer,
                    metricFactory, mailQueueView, clock),
                new Dequeuer(mailQueueName, reactorRabbitMQChannelPool, mailLoader, mailReferenceSerializer,
                    metricFactory, mailQueueView),
                mailQueueView,
                decoratorFactory);

            registerGaugeFor(rabbitMQMailQueue);
            return rabbitMQMailQueue;
        }

        private void registerGaugeFor(RabbitMQMailQueue rabbitMQMailQueue) {
            if (configuration.isSizeMetricsEnabled()) {
                this.gaugeRegistry.register(QUEUE_SIZE_METRIC_NAME_PREFIX + rabbitMQMailQueue.getName(), rabbitMQMailQueue::getSize);
            }
        }
    }

    /**
     * RabbitMQMailQueue should have a single instance in a given JVM for a given MailQueueName.
     * This class helps at keeping track of previously instantiated MailQueues.
     */
    private class RabbitMQMailQueueObjectPool {

        private final ConcurrentHashMap<MailQueueName, RabbitMQMailQueue> instantiatedQueues;

        RabbitMQMailQueueObjectPool() {
            this.instantiatedQueues = new ConcurrentHashMap<>();
        }

        RabbitMQMailQueue retrieveInstanceFor(MailQueueName name) {
            return instantiatedQueues.computeIfAbsent(name, privateFactory::create);
        }
    }

    private final RabbitMQMailQueueManagement mqManagementApi;
    private final PrivateFactory privateFactory;
    private final RabbitMQMailQueueObjectPool mailQueueObjectPool;
    private final ReactorRabbitMQChannelPool reactorRabbitMQChannelPool;

    @VisibleForTesting
    @Inject
    RabbitMQMailQueueFactory(ReactorRabbitMQChannelPool reactorRabbitMQChannelPool,
                             RabbitMQMailQueueManagement mqManagementApi,
                             PrivateFactory privateFactory) {
        this.reactorRabbitMQChannelPool = reactorRabbitMQChannelPool;
        this.mqManagementApi = mqManagementApi;
        this.privateFactory = privateFactory;
        this.mailQueueObjectPool = new RabbitMQMailQueueObjectPool();
    }

    @Override
    public Optional<RabbitMQMailQueue> getQueue(String name) {
        return getQueueFromRabbitServer(MailQueueName.fromString(name));
    }

    @Override
    public RabbitMQMailQueue createQueue(String name) {
        MailQueueName mailQueueName = MailQueueName.fromString(name);
        return getQueueFromRabbitServer(mailQueueName)
            .orElseGet(() -> createQueueIntoRabbitServer(mailQueueName));
    }

    @Override
    public Set<RabbitMQMailQueue> listCreatedMailQueues() {
        return mqManagementApi.listCreatedMailQueueNames()
            .map(mailQueueObjectPool::retrieveInstanceFor)
            .collect(ImmutableSet.toImmutableSet());
    }

    private RabbitMQMailQueue createQueueIntoRabbitServer(MailQueueName mailQueueName) {
        String exchangeName = mailQueueName.toRabbitExchangeName().asString();
        Flux.concat(
            reactorRabbitMQChannelPool.getSender().declareExchange(ExchangeSpecification.exchange(exchangeName)
                .durable(true)
                .type("direct")),
            reactorRabbitMQChannelPool.getSender().declareQueue(QueueSpecification.queue(mailQueueName.toWorkQueueName().asString())
                .durable(DURABLE)
                .exclusive(!EXCLUSIVE)
                .autoDelete(!AUTO_DELETE)
                .arguments(NO_ARGUMENTS)),
            reactorRabbitMQChannelPool.getSender()
                .bind(BindingSpecification.binding()
                .exchange(mailQueueName.toRabbitExchangeName().asString())
                .queue(mailQueueName.toWorkQueueName().asString())
                .routingKey(EMPTY_ROUTING_KEY)))
            .then()
            .block();
        return mailQueueObjectPool.retrieveInstanceFor(mailQueueName);
    }

    private Optional<RabbitMQMailQueue> getQueueFromRabbitServer(MailQueueName name) {
        return mqManagementApi.listCreatedMailQueueNames()
            .filter(name::equals)
            .map(mailQueueObjectPool::retrieveInstanceFor)
            .findFirst();
    }
}
