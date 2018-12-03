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

package org.apache.james.quota.search.elasticsearch.events;

import static org.apache.james.quota.search.QuotaSearchFixture.TestConstants.BOB_USER;
import static org.apache.james.quota.search.QuotaSearchFixture.TestConstants.NOW;
import static org.apache.james.quota.search.QuotaSearchFixture.TestConstants.QUOTAROOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

import java.util.concurrent.Executors;

import org.apache.james.backends.es.ElasticSearchConfiguration;
import org.apache.james.backends.es.ElasticSearchIndexer;
import org.apache.james.backends.es.EmbeddedElasticSearch;
import org.apache.james.backends.es.utils.TestingClientProvider;
import org.apache.james.core.User;
import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener.QuotaUsageUpdatedEvent;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.quota.QuotaFixture.Counts;
import org.apache.james.mailbox.quota.QuotaFixture.Sizes;
import org.apache.james.quota.search.elasticsearch.QuotaRatioElasticSearchConstants;
import org.apache.james.quota.search.elasticsearch.QuotaSearchIndexCreationUtil;
import org.apache.james.quota.search.elasticsearch.json.QuotaRatioToElasticSearchJson;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class ElasticSearchQuotaMailboxListenerTest {

    private static final int BATCH_SIZE = 1;
    private static final Event DUMB_EVENT = new Event() {
        @Override
        public User getUser() {
            return null;
        }

        @Override
        public MailboxSession.SessionId getSessionId() {
            return null;
        }
    };

    private TemporaryFolder temporaryFolder = new TemporaryFolder();
    private EmbeddedElasticSearch embeddedElasticSearch = new EmbeddedElasticSearch(temporaryFolder);

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(temporaryFolder).around(embeddedElasticSearch);
    private ElasticSearchQuotaMailboxListener quotaMailboxListener;
    private Client client;

    @Before
    public void setUp() {
        client = QuotaSearchIndexCreationUtil.prepareDefaultClient(
            new TestingClientProvider(embeddedElasticSearch.getNode()).get(), ElasticSearchConfiguration.DEFAULT_CONFIGURATION);

        quotaMailboxListener = new ElasticSearchQuotaMailboxListener(
            new ElasticSearchIndexer(client,
                Executors.newSingleThreadExecutor(),
                QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_WRITE_ALIAS,
                QuotaRatioElasticSearchConstants.QUOTA_RATIO_TYPE,
                BATCH_SIZE),
            new QuotaRatioToElasticSearchJson());
    }

    @Test
    public void eventShouldDoNothingWhenNoQuotaEvent() throws Exception {
        quotaMailboxListener.event(DUMB_EVENT);

        embeddedElasticSearch.awaitForElasticSearch();

        SearchResponse searchResponse = client.prepareSearch(QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_READ_ALIAS.getValue())
            .setTypes(QuotaRatioElasticSearchConstants.QUOTA_RATIO_TYPE.getValue())
            .setQuery(matchAllQuery())
            .execute()
            .get();
        assertThat(searchResponse.getHits().totalHits()).isEqualTo(0);
    }

    @Test
    public void eventShouldIndexEventWhenQuotaEvent() throws Exception {
        quotaMailboxListener.event(new QuotaUsageUpdatedEvent(BOB_USER, QUOTAROOT, Counts._52_PERCENT, Sizes._55_PERCENT, NOW));

        embeddedElasticSearch.awaitForElasticSearch();

        SearchResponse searchResponse = client.prepareSearch(QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_READ_ALIAS.getValue())
            .setTypes(QuotaRatioElasticSearchConstants.QUOTA_RATIO_TYPE.getValue())
            .setQuery(matchAllQuery())
            .execute()
            .get();
        assertThat(searchResponse.getHits().totalHits()).isEqualTo(1);
    }
}