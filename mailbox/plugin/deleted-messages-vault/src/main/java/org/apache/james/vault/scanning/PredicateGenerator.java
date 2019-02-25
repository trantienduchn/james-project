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

package org.apache.james.vault.scanning;

import java.util.function.Predicate;

import org.apache.james.vault.Criterion;
import org.apache.james.vault.DeletedMessage;
import org.apache.james.vault.Query;

public class PredicateGenerator {
    public static Predicate<DeletedMessage> from(Query query) {
        return query.getCriteria()
            .stream()
            .map(PredicateGenerator::from)
            .reduce(Predicate::and)
            .orElse(t -> true);
    }

    public static <T> Predicate<DeletedMessage> from(Criterion<T> criterion) {
        return deletedMessage -> {
            T valueToMatch = criterion.getFieldName().valueExtractor().extract(deletedMessage);
            return criterion.getValueMatcher().matches(valueToMatch);
        };
    }
}
