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

package org.apache.james.utils.mountebank;

import java.util.ArrayList;
import java.util.List;

import org.apache.james.utils.mountebank.predicate.Predicate;
import org.apache.james.utils.mountebank.response.Response;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class Stub {

    public static class Builder {

        private List<Predicate> predicates;
        private List<Response> responses;

        public Builder() {
            predicates = new ArrayList<>();
            responses = new ArrayList<>();
        }

        public Builder predicateToResponse(NodeBuilder<Predicate> predicateBuilder, NodeBuilder<Response> responseBuilder) {
            Preconditions.checkNotNull(predicateBuilder);
            Preconditions.checkNotNull(responseBuilder);

            predicates.add(predicateBuilder.build());
            responses.add(responseBuilder.build());

            return this;
        }

        public Stub buildStub() {
            return new Stub(ImmutableList.copyOf(predicates), ImmutableList.copyOf(responses));
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final List<Predicate> predicates;
    private final List<Response> responses;

    public Stub(List<Predicate> predicates, List<Response> responses) {
        this.predicates = predicates;
        this.responses = responses;
    }

    public List<Predicate> getPredicates() {
        return predicates;
    }

    public List<Response> getResponses() {
        return responses;
    }
}
