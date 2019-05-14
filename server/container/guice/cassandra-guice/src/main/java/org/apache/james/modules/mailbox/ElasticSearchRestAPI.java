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

import org.apache.james.util.Host;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;

import feign.Feign;
import feign.Logger;
import feign.RequestLine;
import feign.RetryableException;
import feign.Retryer;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;

public interface ElasticSearchRestAPI {

    class About {

        static class Version {
            private final String number;
            private final String luceneVersion;

            Version(@JsonProperty("number") String number,
                    @JsonProperty("lucene_version") String luceneVersion) {
                this.number = number;
                this.luceneVersion = luceneVersion;
            }

            public String getNumber() {
                return number;
            }

            public String getLuceneVersion() {
                return luceneVersion;
            }
        }

        private final String name;
        private final Version version;

        public About(@JsonProperty("name") String name,
                     @JsonProperty("version") Version version) {
            this.name = name;
            this.version = version;
        }

        public String getName() {
            return name;
        }

        public Version getVersion() {
            return version;
        }
    }

    @VisibleForTesting
    static ElasticSearchRestAPI from(Host esHost) throws RetryableException {
        return Feign.builder()
            .logger(new Slf4jLogger(ElasticSearchRestAPI.class))
            .logLevel(Logger.Level.FULL)
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .retryer(new Retryer.Default())
            .target(ElasticSearchRestAPI.class, "http://" + esHost.toString());
    }

    @RequestLine("GET")
    About about();
}
