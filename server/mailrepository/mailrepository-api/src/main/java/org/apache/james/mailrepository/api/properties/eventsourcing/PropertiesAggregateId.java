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

package org.apache.james.mailrepository.api.properties.eventsourcing;

import java.util.Objects;

import org.apache.james.eventsourcing.AggregateId;
import org.apache.james.mailrepository.api.MailRepositoryUrl;

import com.google.common.base.Preconditions;

public class PropertiesAggregateId implements AggregateId {

    private static final String PREFIX = "MailRepositoryProperties/";

    public static PropertiesAggregateId parse(String aggregateIdAsString) {
        Preconditions.checkNotNull(aggregateIdAsString);
        Preconditions.checkArgument(aggregateIdAsString.startsWith(PREFIX), "aggregate key string should start with '" + PREFIX + "'");
        MailRepositoryUrl url = MailRepositoryUrl.from(aggregateIdAsString.substring(PREFIX.length()));
        return new PropertiesAggregateId(url);
    }

    private final MailRepositoryUrl url;

    public PropertiesAggregateId(MailRepositoryUrl url) {
        Preconditions.checkNotNull(url);

        this.url = url;
    }

    @Override
    public String asAggregateKey() {
        return PREFIX + url.asString();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof PropertiesAggregateId) {
            PropertiesAggregateId that = (PropertiesAggregateId) o;

            return Objects.equals(this.url, that.url);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(url);
    }
}
