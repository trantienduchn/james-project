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

package org.apache.james.mailbox.backup.task;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.function.Function;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.export.api.BlobExportMechanism;
import org.apache.james.blob.export.api.FileExtension;
import org.apache.james.core.Username;
import org.apache.james.mailbox.backup.MailboxBackup;
import org.apache.james.task.Task;
import org.apache.james.user.api.UsersRepository;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Mono;

// TODO Move me to WebAdminMailboxesBackUp
// TODO add more 2 test: assert the name of the zip file; assert the zipExtendion of the zip file
public class ExportService {

    private final MailboxBackup mailboxBackup;
    private final BlobStore blobStore;
    private final BlobExportMechanism blobExport;
    private final UsersRepository usersRepository;

    public ExportService(MailboxBackup mailboxBackup, BlobStore blobStore, BlobExportMechanism blobExport, UsersRepository usersRepository) {
        this.mailboxBackup = mailboxBackup;
        this.blobStore = blobStore;
        this.blobExport = blobExport;
        this.usersRepository = usersRepository;
    }

    public Mono<Task.Result> exportUserMailboxesData(Username username) {
        // TODO Close the stream
        Function<InputStream, Mono<BlobId>> saveAsBlob = inputStream -> blobStore.save(blobStore.getDefaultBucketName(), inputStream, BlobStore.StoragePolicy.LOW_COST);

        return zipMailboxesContent(username)
            .flatMap(saveAsBlob)
            .flatMap(blobId -> export(username, blobId))
            .thenReturn(Task.Result.COMPLETED);
    }

    private Mono<Task.Result> export(Username username, BlobId blobId) {
        return Mono.fromRunnable(Throwing.runnable(() ->
            blobExport.blobId(blobId)
                .with(usersRepository.getMailAddressFor(username))
                .explanation("anything")
                .filePrefix("mailbox-backup-" + username.asString())
                .fileExtension(FileExtension.ZIP)
                .export()));
    }

    private Mono<InputStream> zipMailboxesContent(Username username) {
        //TODO improve stream handling
        return Mono.fromCallable(() -> {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            mailboxBackup.backupAccount(username, out);
            return new ByteArrayInputStream(out.toByteArray());
        });
    }
}
