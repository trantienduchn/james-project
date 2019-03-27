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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.export.api.BlobExportMechanism;
import org.apache.james.core.MailAddress;
import org.apache.james.core.User;
import org.apache.james.vault.DeletedMessage;
import org.apache.james.vault.DeletedMessageVault;
import org.apache.james.vault.DeletedMessageZipper;
import org.apache.james.vault.search.Query;

import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ExportService {
    private static File temporaryFile() throws IOException {
        return Files.createTempFile(TEMPORARY_FILE_NAME_PREFIX, TEMPORARY_FILE_EXTENSION).toFile();
    }

    private static final String TEMPORARY_FILE_EXTENSION = ".temp";
    private static final String TEMPORARY_FILE_NAME_PREFIX = "james-vault-export-";

    private final BlobExportMechanism blobExport;
    private final BlobStore blobStore;
    private final DeletedMessageZipper zipper;
    private final DeletedMessageVault vault;

    @Inject
    @VisibleForTesting
    ExportService(BlobExportMechanism blobExport, BlobStore blobStore, DeletedMessageZipper zipper, DeletedMessageVault vault) {
        this.blobExport = blobExport;
        this.blobStore = blobStore;
        this.zipper = zipper;
        this.vault = vault;
    }

    void export(User user, Query exportQuery, MailAddress exportToAddress, Runnable messageToExportCallback) throws IOException {
        Flux<DeletedMessage> matchedMessages = Flux.from(vault.search(user, exportQuery))
            .doOnNext(any -> messageToExportCallback.run());
        File file = zipToFile(user, matchedMessages);

        try {
            BlobId blobId = copyIntoBlobStore(file);
            blobExport.blobId(blobId)
                .with(exportToAddress)
                .explanation(exportMessage(user))
                .export();
        } finally {
            FileUtils.forceDelete(file);
        }
    }

    private File zipToFile(User user, Flux<DeletedMessage> messages) throws IOException {
        File tempFile = temporaryFile();

        try (FileOutputStream fileOutputStream = new FileOutputStream(tempFile)) {
            zipper.zip(contentLoader(user), messages.toStream(), fileOutputStream);
            return tempFile;
        }
    }

    private BlobId copyIntoBlobStore(File file) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            return blobStore.save(fileInputStream, file.length()).block();
        }
    }

    private DeletedMessageZipper.DeletedMessageContentLoader contentLoader(User user) {
        return message -> Mono.from(vault.loadMimeMessage(user, message.getMessageId())).blockOptional();
    }

    private String exportMessage(User user) {
        return String.format("Some deleted messages from user %s has been shared to you", user.asString());
    }
}
