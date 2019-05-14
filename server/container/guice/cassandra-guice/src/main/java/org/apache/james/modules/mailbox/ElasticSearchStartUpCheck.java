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

import javax.inject.Inject;

import org.apache.james.StartUpChecksPerformer;
import org.apache.james.metrics.es.ESReporterConfiguration;
import org.apache.james.modules.mailbox.ElasticSearchRestAPI.About;
import org.apache.james.util.Host;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import feign.RetryableException;

public class ElasticSearchStartUpCheck implements StartUpChecksPerformer.StartUpCheck {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchStartUpCheck.class);
    private static final String CHECK_NAME = "ElasticSearchStartUpCheck";
    private static final String CAN_NOT_FIND_VERSION_MESSAGE = "cannot find ElasticSearch version";
    private static final String SUGGESTION_VERSION_IS_NOT_MATCHED_MESSAGE = "suggested ElasticSearch version (%s) is not match " +
        "with current version (%s)";

    private static final String ES_VERSION_SUGGESTION ="2.4.6";

    private final Host esHttpHost;
    private final String esVersionSuggestion;

    @Inject
    public ElasticSearchStartUpCheck(ESReporterConfiguration esConfiguration) {
        this(Host.parseConfString(esConfiguration.getHostWithPort()), ES_VERSION_SUGGESTION);
    }

    @VisibleForTesting
    ElasticSearchStartUpCheck(Host esHttpHost, String esVersionSuggestion) {
        this.esHttpHost = esHttpHost;
        this.esVersionSuggestion = esVersionSuggestion;
    }


    @Override
    public CheckResult check() {
        try {
            About.Version version = getVersion();
            if (!version.getNumber().equals(ES_VERSION_SUGGESTION)) {
                String warnMessage = String.format(SUGGESTION_VERSION_IS_NOT_MATCHED_MESSAGE,
                    esVersionSuggestion,
                    version.getNumber());
                LOGGER.warn(warnMessage);
                return CheckResult.builder()
                    .checkName(CHECK_NAME)
                    .resultType(ResultType.BAD)
                    .description(warnMessage)
                    .build();
            }
        } catch (RetryableException e) {
            String errorMessage = CAN_NOT_FIND_VERSION_MESSAGE + ": " + e.getMessage();
            LOGGER.error(errorMessage);
            return CheckResult.builder()
                .checkName(CHECK_NAME)
                .resultType(ResultType.BAD)
                .description(errorMessage)
                .build();
        }

        return CheckResult.builder()
            .checkName(CHECK_NAME)
            .resultType(ResultType.GOOD)
            .build();
    }

    private About.Version getVersion() throws RetryableException {
        return ElasticSearchRestAPI.from(esHttpHost)
            .about()
            .getVersion();
    }
}
