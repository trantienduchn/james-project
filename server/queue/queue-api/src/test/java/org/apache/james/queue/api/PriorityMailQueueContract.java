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

package org.apache.james.queue.api;

import static org.apache.james.queue.api.MailQueueFixture.NAME0;
import static org.apache.james.queue.api.MailQueueFixture.NAME1;
import static org.apache.james.queue.api.MailQueueFixture.NAME2;
import static org.apache.james.queue.api.MailQueueFixture.NAME3;
import static org.apache.james.queue.api.MailQueueFixture.NAME4;
import static org.apache.james.queue.api.MailQueueFixture.NAME5;
import static org.apache.james.queue.api.MailQueueFixture.NAME6;
import static org.apache.james.queue.api.MailQueueFixture.NAME7;
import static org.apache.james.queue.api.MailQueueFixture.NAME8;
import static org.apache.james.queue.api.MailQueueFixture.NAME9;
import static org.apache.james.queue.api.MailQueueFixture.createMimeMessage;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.stream.IntStream;

import javax.mail.MessagingException;

import org.apache.mailet.Mail;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

public interface PriorityMailQueueContract {

    MailQueue getMailQueue();

    @Test
    default void priorityShouldReorderMailsWhenDequeing() throws Exception {
        getMailQueue().enQueue(mailBuilder()
            .name(NAME3)
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 3)
            .build());

        getMailQueue().enQueue(mailBuilder()
            .name(NAME9)
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 9)
            .build());

        getMailQueue().enQueue(mailBuilder()
            .name(NAME1)
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 1)
            .build());

        getMailQueue().enQueue(mailBuilder()
            .name(NAME8)
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 8)
            .build());

        getMailQueue().enQueue(mailBuilder()
            .name(NAME6)
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 6)
            .build());

        getMailQueue().enQueue(mailBuilder()
            .name(NAME0)
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 0)
            .build());

        getMailQueue().enQueue(mailBuilder()
            .name(NAME7)
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 7)
            .build());

        getMailQueue().enQueue(mailBuilder()
            .name(NAME4)
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 4)
            .build());

        getMailQueue().enQueue(mailBuilder()
            .name(NAME2)
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 2)
            .build());

        getMailQueue().enQueue(mailBuilder()
            .name(NAME5)
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 5)
            .build());

        ImmutableList<MailQueue.MailQueueItem> items = IntStream.range(1, 11).boxed()
            .map(Throwing.function(i -> {
                MailQueue.MailQueueItem item1 = getMailQueue().deQueue();
                item1.done(true);
                return item1;
            }))
            .collect(Guavate.toImmutableList());

        assertThat(items)
            .extracting(MailQueue.MailQueueItem::getMail)
            .extracting(Mail::getName)
            .containsExactly(NAME9, NAME8, NAME7, NAME6, NAME5, NAME4, NAME3, NAME2, NAME1, NAME0);
    }

    @Test
    default void negativePriorityShouldBeConsideredZero() throws Exception {
        getMailQueue().enQueue(mailBuilder()
            .name(NAME2)
            .attribute(MailPrioritySupport.MAIL_PRIORITY, -1)
            .build());
        getMailQueue().enQueue(mailBuilder()
            .name(NAME1)
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 1)
            .build());

        MailQueue.MailQueueItem mailQueueItem1 = getMailQueue().deQueue();
        mailQueueItem1.done(true);
        MailQueue.MailQueueItem mailQueueItem2 = getMailQueue().deQueue();
        mailQueueItem2.done(true);
        assertThat(mailQueueItem1.getMail().getName()).isEqualTo(NAME1);
        assertThat(mailQueueItem2.getMail().getName()).isEqualTo(NAME2);
    }

    @Test
    default void tooBigPriorityShouldBeConsideredMaximum() throws Exception {
        getMailQueue().enQueue(mailBuilder()
            .name(NAME2)
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 12)
            .build());
        getMailQueue().enQueue(mailBuilder()
            .name(NAME1)
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 8)
            .build());

        MailQueue.MailQueueItem mailQueueItem1 = getMailQueue().deQueue();
        mailQueueItem1.done(true);
        MailQueue.MailQueueItem mailQueueItem2 = getMailQueue().deQueue();
        mailQueueItem2.done(true);
        assertThat(mailQueueItem1.getMail().getName()).isEqualTo(NAME2);
        assertThat(mailQueueItem2.getMail().getName()).isEqualTo(NAME1);
    }

    @Test
    default void invalidPriorityShouldBeConsideredDefault() throws Exception {
        getMailQueue().enQueue(mailBuilder()
            .name(NAME1)
            .attribute(MailPrioritySupport.MAIL_PRIORITY, "invalid")
            .build());
        getMailQueue().enQueue(mailBuilder()
            .name(NAME2)
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 4)
            .build());
        getMailQueue().enQueue(mailBuilder()
            .name(NAME3)
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 6)
            .build());

        MailQueue.MailQueueItem mailQueueItem1 = getMailQueue().deQueue();
        mailQueueItem1.done(true);
        MailQueue.MailQueueItem mailQueueItem2 = getMailQueue().deQueue();
        mailQueueItem2.done(true);
        MailQueue.MailQueueItem mailQueueItem3 = getMailQueue().deQueue();
        mailQueueItem3.done(true);
        assertThat(mailQueueItem1.getMail().getName()).isEqualTo(NAME3);
        assertThat(mailQueueItem2.getMail().getName()).isEqualTo(NAME1);
        assertThat(mailQueueItem3.getMail().getName()).isEqualTo(NAME2);
    }

    @Test
    default void defaultPriorityShouldBeNormal() throws Exception {
        getMailQueue().enQueue(mailBuilder()
            .name(NAME1)
            .build());
        getMailQueue().enQueue(mailBuilder()
            .name(NAME2)
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 4)
            .build());
        getMailQueue().enQueue(mailBuilder()
            .name(NAME3)
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 6)
            .build());

        MailQueue.MailQueueItem mailQueueItem1 = getMailQueue().deQueue();
        mailQueueItem1.done(true);
        MailQueue.MailQueueItem mailQueueItem2 = getMailQueue().deQueue();
        mailQueueItem2.done(true);
        MailQueue.MailQueueItem mailQueueItem3 = getMailQueue().deQueue();
        mailQueueItem3.done(true);
        assertThat(mailQueueItem1.getMail().getName()).isEqualTo(NAME3);
        assertThat(mailQueueItem2.getMail().getName()).isEqualTo(NAME1);
        assertThat(mailQueueItem3.getMail().getName()).isEqualTo(NAME2);
    }

    @Test
    default void priorityCanBeOmitted() throws Exception {
        getMailQueue().enQueue(mailBuilder()
            .name(NAME1)
            .build());

        MailQueue.MailQueueItem mailQueueItem = getMailQueue().deQueue();
        assertThat(mailQueueItem.getMail().getName()).isEqualTo(NAME1);
    }

    static FakeMail.Builder mailBuilder() throws MessagingException {
        return FakeMail.builder()
            .mimeMessage(createMimeMessage())
            .sender(MailAddressFixture.OTHER_AT_LOCAL)
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES)
            .lastUpdated(new Date());
    }
}
