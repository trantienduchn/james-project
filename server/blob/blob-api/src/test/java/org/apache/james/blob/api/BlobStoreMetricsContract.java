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

package org.apache.james.blob.api;

import static org.apache.james.blob.api.WithMetric.READ_BYTES_TIMER_NAME;
import static org.apache.james.blob.api.WithMetric.READ_TIMER_NAME;
import static org.apache.james.blob.api.WithMetric.SAVE_BYTES_TIMER_NAME;
import static org.apache.james.blob.api.WithMetric.SAVE_INPUT_STREAM_TIMER_NAME;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

public interface BlobStoreMetricsContract extends BlobStoreContract {

    class BlobStoreMetricsExtension implements BeforeEachCallback {
        private MetricFactory metricFactory;
        private TimeMetric saveBytesTimeMetric;
        private TimeMetric saveInputStreamTimeMetric;
        private TimeMetric readBytesTimeMetric;
        private TimeMetric readTimeMetric;

        @Override
        public void beforeEach(ExtensionContext extensionContext) throws Exception {
            this.metricFactory = spy(MetricFactory.class);
            this.saveBytesTimeMetric = spy(TimeMetric.class);
            this.saveInputStreamTimeMetric = spy(TimeMetric.class);
            this.readBytesTimeMetric = spy(TimeMetric.class);
            this.readTimeMetric = spy(TimeMetric.class);

            setupExpectations();
        }

        public MetricFactory getMetricFactory() {
            return metricFactory;
        }

        private void setupExpectations() {
            when(metricFactory.timer(endsWith(SAVE_BYTES_TIMER_NAME)))
                .thenReturn(saveBytesTimeMetric);
            when(metricFactory.timer(endsWith(SAVE_INPUT_STREAM_TIMER_NAME)))
                .thenReturn(saveInputStreamTimeMetric);
            when(metricFactory.timer(endsWith(READ_BYTES_TIMER_NAME)))
                .thenReturn(readBytesTimeMetric);
            when(metricFactory.timer(endsWith(READ_TIMER_NAME)))
                .thenReturn(readTimeMetric);
        }
    }

    @RegisterExtension
    BlobStoreMetricsExtension metricsTestExtension = new BlobStoreMetricsExtension();

    byte [] BYTES_CONTENT = "bytes content".getBytes(StandardCharsets.UTF_8);

    @Test
    default void saveBytesShouldPublishSaveBytesTimerMetrics() {
        testee().save(BYTES_CONTENT).join();
        testee().save(BYTES_CONTENT).join();

        verify(metricsTestExtension.saveBytesTimeMetric, times(2)).stopAndPublish();
    }

    @Test
    default void saveInputStreamShouldPublishSaveInputStreamTimerMetrics() {
        testee().save(new ByteArrayInputStream(BYTES_CONTENT)).join();
        testee().save(new ByteArrayInputStream(BYTES_CONTENT)).join();
        testee().save(new ByteArrayInputStream(BYTES_CONTENT)).join();

        verify(metricsTestExtension.saveInputStreamTimeMetric, times(3)).stopAndPublish();
    }

    @Test
    default void readBytesShouldPublishReadBytesTimerMetrics() {
        BlobId blobId = testee().save(BYTES_CONTENT).join();

        testee().readBytes(blobId).join();
        testee().readBytes(blobId).join();

        verify(metricsTestExtension.readBytesTimeMetric, times(2)).stopAndPublish();
    }

    @Test
    default void readShouldPublishReadTimerMetrics() {
        BlobId blobId = testee().save(BYTES_CONTENT).join();

        testee().read(blobId);
        testee().read(blobId);

        verify(metricsTestExtension.readTimeMetric, times(2)).stopAndPublish();
    }
}
