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

package org.apache.james;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.user.ldap.LdapGenericContainer;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.google.inject.Module;

class LdapExtension implements GuiceModuleTestExtension {

    private static final String DOMAIN = "james.org";
    private static final String ADMIN_PASSWORD = "mysecretpassword";

    private final LdapGenericContainer ldapContainer;

    public LdapExtension() {
        this.ldapContainer = LdapGenericContainer.builder()
            .domain(DOMAIN)
            .password(ADMIN_PASSWORD)
            .build();
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        ldapContainer.start();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        ldapContainer.stop();
    }

    @Override
    public Module getModule() {
        return binder -> binder.bind(LdapRepositoryConfiguration.class)
                .toInstance(computeConfiguration(ldapContainer.getLdapHost()));
    }

    private LdapRepositoryConfiguration computeConfiguration(String ldapIp) {
        try {
            return LdapRepositoryConfiguration.builder()
                .ldapHost(ldapIp)
                .principal("cn=admin,dc=james,dc=org")
                .credentials("mysecretpassword")
                .userBase("ou=People,dc=james,dc=org")
                .userIdAttribute("uid")
                .userObjectClass("inetOrgPerson")
                .maxRetries(4)
                .retryStartInterval(0)
                .retryMaxInterval(8)
                .scale(1000)
                .build();
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }
}
