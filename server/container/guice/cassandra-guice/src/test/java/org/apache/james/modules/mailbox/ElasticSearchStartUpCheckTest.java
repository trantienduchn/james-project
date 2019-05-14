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

package org.apache.james.modules.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import org.apache.james.StartUpChecksPerformer.StartUpCheck;
import org.apache.james.backends.es.DockerElasticSearch;
import org.apache.james.backends.es.DockerElasticSearchExtension;
import org.apache.james.util.docker.Images;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ElasticSearchStartUpCheckTest {

    private static final String ES_VERSION_2_4_6 = "2.4.6";

    @RegisterExtension
    static DockerElasticSearchExtension testExtension = DockerElasticSearchExtension.withSingletonES();
    @RegisterExtension
    static DockerElasticSearchExtension es6TestExtension = new DockerElasticSearchExtension(
        new DockerElasticSearch(Images.ELASTICSEARCH_6)
            .withEnv("discovery.type", "single-node"));

    private ElasticSearchStartUpCheck testee;

    @BeforeEach
    void beforeEach() {
        testee = new ElasticSearchStartUpCheck(testExtension.getDockerES().getHttpHost(), ES_VERSION_2_4_6);
    }

    @Test
    void checkShouldReturnGoodWhenAbleToFindESVersion() {
        assertThat(testee.check().isGood())
            .isTrue();
    }

    @Test
    void checkShouldReturnBadWhenUnAbleToConnectToElasticSearch() {
        testExtension.getDockerES().stop();

        assertThat(testee.check().isBad())
            .isTrue();
    }

    @Test
    void checkShouldReturnBadWhenESVersionIsNotMatchedWithTheSuggestion() {
        testee = new ElasticSearchStartUpCheck(es6TestExtension.getDockerES().getHttpHost(), ES_VERSION_2_4_6);
        StartUpCheck.CheckResult checkResult = testee.check();

        assertSoftly(softly -> {
            softly.assertThat(checkResult.isBad()).isTrue();
            softly.assertThat(checkResult.getDescription())
                .contains("suggested ElasticSearch version (2.4.6) is not match with current version (6.5.1)");
        });
    }
}