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

package org.apache.james.webadmin.vault.routes;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.core.User;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.AppendCommand;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.vault.DeletedMessage;
import org.apache.james.vault.DeletedMessageVault;
import org.apache.james.vault.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DeletedMessageVaultRestoreTask implements Task {

    private interface MailboxComponentSupplier<T> {
        T get() throws MailboxException;
    }

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final long successfulRestoreCount;
        private final long errorRestoreCount;
        private final long failRemovingCount;

        AdditionalInformation(long successfulRestoreCount, long errorRestoreCount, long failRemovingCount) {
            this.successfulRestoreCount = successfulRestoreCount;
            this.errorRestoreCount = errorRestoreCount;
            this.failRemovingCount = failRemovingCount;
        }

        public long getSuccessfulRestoreCount() {
            return successfulRestoreCount;
        }

        public long getErrorRestoreCount() {
            return errorRestoreCount;
        }

        public long getFailRemovingCount() {
            return failRemovingCount;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(DeletedMessageVaultRestoreTask.class);

    static final String TYPE = "deletedMessages/restore";
    static final String RESTORE_MAILBOX_NAME = "deleted messages restore";

    private final DeletedMessageVault deletedMessageVault;
    private final User userToRestore;
    private final MailboxManager mailboxManager;
    private final MailboxPath restoreMailbox;
    private final MailboxSession session;

    private final AtomicLong successfulRestoreCount;
    private final AtomicLong errorRestoreCount;
    private final AtomicLong failRemovingCount;

    DeletedMessageVaultRestoreTask(DeletedMessageVault deletedMessageVault, User userToRestore, MailboxManager mailboxManager) {
        this.deletedMessageVault = deletedMessageVault;
        this.userToRestore = userToRestore;
        this.mailboxManager = mailboxManager;
        this.restoreMailbox = MailboxPath.forUser(userToRestore.asString(), RESTORE_MAILBOX_NAME);
        this.session = getComponent(() -> mailboxManager.createSystemSession(userToRestore.asString()));
        this.successfulRestoreCount = new AtomicLong();
        this.errorRestoreCount = new AtomicLong();
        this.failRemovingCount = new AtomicLong();
    }

    @Override
    public Result run() {
        MessageManager restoreMailboxManager = restoreMailboxManager();

        return Flux.from(deletedMessageVault.search(userToRestore, Query.ALL)).toStream()
            .map(deletedMessage -> moveToMailbox(restoreMailboxManager, deletedMessage))
            .reduce(Result.COMPLETED, Task::combine);
    }

    private Result moveToMailbox(MessageManager restoreMailboxManager, DeletedMessage deletedMessage) {
        Result appended = appendToMailbox(restoreMailboxManager, deletedMessage);

        return Optional.of(appended)
            .filter(appendedResult -> appendedResult.equals(Result.COMPLETED))
            .map(appendedResult -> Task.combine(appendedResult, deleteInTheVault(deletedMessage)))
            .orElse(Result.PARTIAL);
    }

    private Result deleteInTheVault(DeletedMessage deletedMessage) {
        try {
            Mono.from(deletedMessageVault.delete(userToRestore, deletedMessage.getMessageId())).block();
            return Result.COMPLETED;
        } catch (Exception anyException) {
            LOGGER.error("deleting message {} in the vault got error", deletedMessage.getMessageId(), anyException);
            this.failRemovingCount.incrementAndGet();
        }
        return Result.PARTIAL;
    }

    private Result appendToMailbox(MessageManager restoreMailboxManager, DeletedMessage deletedMessage) {
        try {
            restoreMailboxManager.appendMessage(AppendCommand.builder().build(deletedMessage.getContent().get()), session);
        } catch (MailboxException e) {
            LOGGER.error("append message {} to restore mailbox {} didn't success",
                deletedMessage.getMessageId(), restoreMailbox.asString(), e);

            this.errorRestoreCount.incrementAndGet();
            return Result.PARTIAL;
        }

        this.successfulRestoreCount.incrementAndGet();
        return Result.COMPLETED;
    }

    private MessageManager restoreMailboxManager() {
        return getComponent(() -> Optional.of(mailboxManager.mailboxExists(restoreMailbox, session))
            .filter(mailboxExist -> mailboxExist)
            .map(Throwing.function(any -> mailboxManager.getMailbox(restoreMailbox, session)))
            .orElseGet(this::createRestoreMailbox));
    }

    private MessageManager createRestoreMailbox() {
        return getComponent(() -> mailboxManager.createMailbox(restoreMailbox, session)
            .map(Throwing.function(mailboxId -> mailboxManager.getMailbox(mailboxId, session)))
            .orElseThrow(() -> new RuntimeException("createMailbox " + restoreMailbox.asString() + " returns an empty mailboxId")));
    }

    private <T> T getComponent(MailboxComponentSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(new AdditionalInformation(successfulRestoreCount.get(), errorRestoreCount.get(), failRemovingCount.get()));
    }
}
