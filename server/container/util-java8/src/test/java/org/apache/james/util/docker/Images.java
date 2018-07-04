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

package org.apache.james.util.docker;

public interface Images {
    String MOUNTE_BANK = "expert360/mountebank:latest";
    String FAKE_SMTP = "weave/rest-smtp-sink:latest";
    String RABBITMQ = "rabbitmq:3.7.5";
    String ELASTICSEARCH = "elasticsearch:2.2.2";
    String NGINX = "nginx:1.7.1";
    String TIKA = "logicalspark/docker-tikaserver:1.15rc2";
    String SPAMASSASSIN = "dinkel/spamassassin:3.4.0";
}
