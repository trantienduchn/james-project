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

package org.apache.james.jmap.mailet.filter;

import java.util.Map;

import com.google.common.collect.ImmutableMap;

class FieldContentExtractorProvider {

    static final String FROM = "from";
    static final String TO = "to";
    static final String CC = "cc";
    static final String SUBJECT = "subject";
    static final String RECIPIENT = "recipient";

    private static final Map<String, FieldContentExtractor> MAPPER = ImmutableMap.<String, FieldContentExtractor>builder()
            .put(FROM, new FieldContentExtractor.FromExtractor())
            .put(TO, new FieldContentExtractor.ToExtractor())
            .put(CC, new FieldContentExtractor.CCExtractor())
            .put(SUBJECT, new FieldContentExtractor.SubjectExtractor())
            .put(RECIPIENT, new FieldContentExtractor.RecipientExtractor())
            .build();

    static FieldContentExtractor getFieldContentExtractor(String field) {
        FieldContentExtractor fieldExtractor = MAPPER.get(field);
        if (fieldExtractor == null) {
            throw new IllegalArgumentException("unexpected field");
        } else {
            return fieldExtractor;
        }
    }
}
