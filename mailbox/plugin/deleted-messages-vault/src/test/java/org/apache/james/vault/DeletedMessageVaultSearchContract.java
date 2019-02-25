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

package org.apache.james.vault;

import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE_2;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE_WITH_SUBJECT;
import static org.apache.james.vault.DeletedMessageFixture.USER;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.vault.search.CriterionFactory;
import org.apache.james.vault.search.Query;
import org.apache.james.vault.search.ValueMatcher;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DeletedMessageVaultSearchContract {
    DeletedMessageVault getVault();

    @Test
    default void test() {
        Mono.from(getVault().append(USER, DELETED_MESSAGE_WITH_SUBJECT)).block();
        Mono.from(getVault().append(USER, DELETED_MESSAGE_2)).block();

        Query query = Query.of(CriterionFactory.subject(new ValueMatcher.StringContains("kangourou")));


        assertThat(Flux.from(getVault().search(USER, query)).collectList().block())
            .containsOnly(DELETED_MESSAGE_WITH_SUBJECT);
    }
}
