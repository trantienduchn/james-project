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

package org.apache.james.jmap.memory;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.MemoryJmapTestExtension;
import org.apache.james.jmap.methods.integration.FilterTest;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.MailboxId;
import org.junit.jupiter.api.extension.RegisterExtension;

public class MemoryFilterTest extends FilterTest {
    private static final AtomicLong MAILBOX_ID_GENERATOR = new AtomicLong(0);

    @RegisterExtension
    static MemoryJmapTestExtension jmapExtension = MemoryJmapTestExtension.builder().build();

    @Override
    protected MailboxId randomMailboxId() {
        return InMemoryId.of(MAILBOX_ID_GENERATOR.incrementAndGet());
    }
}
