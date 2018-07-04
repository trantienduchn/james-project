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

package org.apache.james.utils.mountebank.response;

import org.apache.james.utils.mountebank.NodeBuilder;
import org.apache.james.utils.mountebank.Data;

import com.google.common.base.Preconditions;

public class TCPResponse implements Response {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder implements NodeBuilder<Response> {

        private String data;

        public Builder data(String data) {
            this.data = data;
            return this;
        }

        @Override
        public TCPResponse build() {
            return new TCPResponse(Data.from(data));
        }
    }

    private final Data is;

    private TCPResponse(Data is) {
        Preconditions.checkNotNull(is);

        this.is = is;
    }

    public Data getIs() {
        return is;
    }
}
