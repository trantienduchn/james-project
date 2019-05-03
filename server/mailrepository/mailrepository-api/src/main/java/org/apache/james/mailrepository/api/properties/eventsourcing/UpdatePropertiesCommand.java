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

import org.apache.james.eventsourcing.Command;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mailrepository.api.properties.MailRepositoryProperties;

import com.google.common.base.Preconditions;

class UpdatePropertiesCommand implements Command {

    private final MailRepositoryUrl url;
    private final MailRepositoryProperties properties;

    UpdatePropertiesCommand(MailRepositoryUrl url, MailRepositoryProperties properties) {
        Preconditions.checkNotNull(url);
        Preconditions.checkNotNull(properties);

        this.url = url;
        this.properties = properties;
    }

    MailRepositoryUrl getUrl() {
        return url;
    }

    MailRepositoryProperties getProperties() {
        return properties;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof UpdatePropertiesCommand) {
            UpdatePropertiesCommand that = (UpdatePropertiesCommand) o;

            return Objects.equals(this.url, that.url)
                && Objects.equals(this.properties, that.properties);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(url, properties);
    }
}
