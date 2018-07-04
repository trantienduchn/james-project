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

package org.apache.james.utils.mountebank.predicate;

import static org.apache.james.utils.mountebank.predicate.StringPredicate.Rule.startsWith;

import java.util.HashMap;
import java.util.Map;

import org.apache.james.utils.mountebank.Data;
import org.apache.james.utils.mountebank.NodeBuilder;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.google.common.base.Preconditions;

public class StringPredicate implements Predicate {

    public enum Rule {
        startsWith, contains, endsWith
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder implements NodeBuilder<Predicate> {

        private Map<Rule, Data> ruleMap;

        private Builder() {
            ruleMap = new HashMap<>();
        }

        public Builder startsWith(String data) {
            ruleMap.put(startsWith, Data.from(data));
            return this;
        }

        @Override
        public StringPredicate build() {
            return new StringPredicate(ruleMap);
        }
    }

    private final Map<Rule, Data> ruleMap;

    private StringPredicate(Map<Rule, Data> ruleMap) {
        Preconditions.checkNotNull(ruleMap);

        this.ruleMap = ruleMap;
    }

    @JsonAnyGetter
    public Map<Rule, Data> getRuleMap() {
        return ruleMap;
    }
}
