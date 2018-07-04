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

import org.apache.james.utils.mountebank.protocol.Protocol;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class Contract {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Integer port;
        private String protocol;
        private List<Stub> stubs;

        public Builder() {
            stubs = new ArrayList<>();
        }

        public Builder port(Integer port) {
            this.port = port;
            return this;
        }

        public Builder protocol(Protocol protocol) {
            Preconditions.checkNotNull(protocol);

            this.protocol = protocol.name();
            return this;
        }

        public Builder addStub(Stub.Builder stubBuilder) {
            stubs.add(stubBuilder.buildStub());
            return this;
        }

        public Contract buidContract() {
            return new Contract(port, protocol, ImmutableList.copyOf(stubs));
        }
    }

    private final Integer port;
    private final String protocol;
    private final List<Stub> stubs;

    private Contract(Integer port, String protocol, List<Stub> stubs) {
        Preconditions.checkNotNull(port);
        Preconditions.checkNotNull(protocol);
        Preconditions.checkNotNull(stubs);
        Preconditions.checkArgument(!stubs.isEmpty());

        this.port = port;
        this.protocol = protocol;
        this.stubs = stubs;
    }

    public Integer getPort() {
        return port;
    }

    public String getProtocol() {
        return protocol;
    }

    public List<Stub> getStubs() {
        return stubs;
    }
}
