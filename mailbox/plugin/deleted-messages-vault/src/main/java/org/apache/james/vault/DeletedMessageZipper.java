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

package org.apache.james.vault;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.zip.ExtraFieldUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.backup.MessageIdExtraField;
import org.apache.james.mailbox.backup.SizeExtraField;
import org.apache.james.mailbox.model.MessageId;

import com.google.common.annotations.VisibleForTesting;

class DeletedMessageZipper {

    DeletedMessageZipper() {
        ExtraFieldUtils.register(MessageIdExtraField.class);
        ExtraFieldUtils.register(SizeExtraField.class);
    }

    void zip(Stream<DeletedMessageWithContent> deletedMessages, OutputStream outputStream) throws IOException {
        try (ZipArchiveOutputStream zipOutputStream = newZipArchiveOutputStream(outputStream)) {

            AtomicReference<IOException> failureEncountered = new AtomicReference<>();
            deletedMessages.forEach(message -> putMessageToEntry(failureEncountered, zipOutputStream, message));
            zipOutputStream.finish();

            if (failureEncountered.get() != null) {
                throw failureEncountered.get();
            }
        }
    }

    @VisibleForTesting
    ZipArchiveOutputStream newZipArchiveOutputStream(OutputStream outputStream) {
        return new ZipArchiveOutputStream(outputStream);
    }

    private void putMessageToEntry(AtomicReference<IOException> failureEncountered, ZipArchiveOutputStream zipOutputStream, DeletedMessageWithContent message) {
        try (DeletedMessageWithContent closableMessage = message) {
            if (failureEncountered.get() == null) {
                putMessageToEntry(zipOutputStream, closableMessage);
            }
        } catch (IOException e) {
            failureEncountered.set(e);
        }
    }

    @VisibleForTesting
    void putMessageToEntry(ZipArchiveOutputStream zipOutputStream, DeletedMessageWithContent message) throws IOException {
        ZipArchiveEntry archiveEntry = createEntry(zipOutputStream, message);
        zipOutputStream.putArchiveEntry(archiveEntry);

        IOUtils.copy(message.getContent(), zipOutputStream);

        zipOutputStream.closeArchiveEntry();
    }

    @VisibleForTesting
    ZipArchiveEntry createEntry(ZipArchiveOutputStream zipOutputStream,
                                        DeletedMessageWithContent fullMessage) throws IOException {
        DeletedMessage message = fullMessage.getDeletedMessage();
        MessageId messageId = message.getMessageId();

        ZipArchiveEntry archiveEntry = (ZipArchiveEntry) zipOutputStream.createArchiveEntry(new File(messageId.serialize()), messageId.serialize());
        archiveEntry.addExtraField(new MessageIdExtraField(messageId.serialize()));
        archiveEntry.addExtraField(new SizeExtraField(message.getSize()));

        return archiveEntry;
    }
}
