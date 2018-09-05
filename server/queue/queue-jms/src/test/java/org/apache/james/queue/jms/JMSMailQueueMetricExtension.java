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

import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class JMSMailQueueMetricExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback, ParameterResolver {

    class JMSMailQueueMetricTestSystem {
        private final JMSMailQueue mockMailQueue;
        private final MetricFactory mockMetricFactory;
        private final GaugeRegistry mockGaugeRegistry;
        private final Metric mockEnqueuedMailsMetric;
        private final Metric mockDequeuedMailsMetric;

        public JMSMailQueueMetricTestSystem(JMSMailQueueTest jmsMailQueueTest) {
            mockMetricFactory = spy(new NoopMetricFactory());
            mockGaugeRegistry = spy(new NoopGaugeRegistry());
            mockEnqueuedMailsMetric = mock(Metric.class);
            mockDequeuedMailsMetric = mock(Metric.class);

            when(mockMetricFactory.generate(anyString())).thenReturn(mockEnqueuedMailsMetric, mockDequeuedMailsMetric);

            this.mockMailQueue = spy(new JMSMailQueue(jmsMailQueueTest.connectionFactory,
                jmsMailQueueTest.mailQueueItemDecoratorFactory,
                jmsMailQueueTest.queueName,
                mockMetricFactory,
                mockGaugeRegistry));
        }

        public MetricFactory getMockMetricFactory() {
            return mockMetricFactory;
        }

        public GaugeRegistry getMockGaugeRegistry() {
            return mockGaugeRegistry;
        }

        public JMSMailQueue getMockMailQueue() {
            return mockMailQueue;
        }

        public Metric getMockEnqueuedMailsMetric() {
            return mockEnqueuedMailsMetric;
        }

        public Metric getMockDequeuedMailsMetric() {
            return mockDequeuedMailsMetric;
        }
    }

    private JMSMailQueueMetricTestSystem testSystem;

    @Override
    public void beforeTestExecution(ExtensionContext extensionContext) throws Exception {
        JMSMailQueueTest jmsMailQueueTest = (JMSMailQueueTest) extensionContext.getTestInstance().get();

        testSystem = new JMSMailQueueMetricTestSystem(jmsMailQueueTest);
    }

    @Override
    public void afterTestExecution(ExtensionContext extensionContext) throws Exception {
        testSystem.mockMailQueue.dispose();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == JMSMailQueueMetricTestSystem.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return testSystem;
    }

}
