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

package org.apache.james.quota.search.scanning;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.inject.Inject;

import org.apache.james.core.User;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.QuotaCount;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaSize;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.quota.search.QuotaClause;

import com.google.common.collect.ImmutableMap;

public class ClauseConverter {

    private final UserQuotaRootResolver quotaRootResolver;
    private final QuotaManager quotaManager;
    private final Map<Class<? extends QuotaClause>, Function<QuotaClause, Predicate<User>>> toPredicates;

    @Inject
    public ClauseConverter(UserQuotaRootResolver quotaRootResolver, QuotaManager quotaManager) {
        this.quotaRootResolver = quotaRootResolver;
        this.quotaManager = quotaManager;
        this.toPredicates = ImmutableMap.of(
            QuotaClause.And.class, this::andToPredicate,
            QuotaClause.LessThan.class, this::lessThanToPredicate,
            QuotaClause.MoreThan.class, this::moreThanToPredicate,
            QuotaClause.HasDomain.class, this::hasDomainToPredicate);
    }

    public Predicate<User> andToPredicate(QuotaClause.And and) {
        return and.getClauses()
            .stream()
            .map(this::toPredicate)
            .reduce((p1, p2) -> (user -> p1.test(user) && p2.test(user)))
            .orElse(user -> true);
    }

    public Predicate<User> toPredicate(QuotaClause clause) {
        return toPredicates.get(clause.getClass())
            .apply(clause);
    }

    public Predicate<User> moreThanToPredicate(QuotaClause clause) {
        QuotaClause.MoreThan moreThan = (QuotaClause.MoreThan) clause;
        return user -> retrieveUserRatio(user) >= moreThan.getQuotaBoundary().getRatio();
    }

    public Predicate<User> lessThanToPredicate(QuotaClause clause) {
        QuotaClause.LessThan lessThan = (QuotaClause.LessThan) clause;
        return user -> retrieveUserRatio(user) <= lessThan.getQuotaBoundary().getRatio();
    }

    public Predicate<User> hasDomainToPredicate(QuotaClause clause) {
        QuotaClause.HasDomain hasDomain = (QuotaClause.HasDomain) clause;
        return user -> user.getDomainPart()
            .map(hasDomain.getDomain()::equals)
            .orElse(false);
    }

    public Predicate<User> andToPredicate(QuotaClause clause) {
        QuotaClause.And and = (QuotaClause.And) clause;
        return andToPredicate(and);
    }

    public double retrieveUserRatio(User user) {
        try {
            QuotaRoot quotaRoot = quotaRootResolver.forUser(user);
            Quota<QuotaSize> storageQuota = quotaManager.getStorageQuota(quotaRoot);
            Quota<QuotaCount> messageQuota = quotaManager.getMessageQuota(quotaRoot);

            return Math.max(storageQuota.getRatio(), messageQuota.getRatio());
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
    }
}
