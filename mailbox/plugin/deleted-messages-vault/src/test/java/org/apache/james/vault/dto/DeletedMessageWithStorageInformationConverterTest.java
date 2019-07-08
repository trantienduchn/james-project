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

package org.apache.james.vault.dto;

import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE_WITH_SUBJECT;
import static org.apache.james.vault.dto.DeletedMessageWithStorageInformationDTOFixture.DELETED_MESSAGE_DTO;
import static org.apache.james.vault.dto.DeletedMessageWithStorageInformationDTOFixture.DELETED_MESSAGE_WITH_STORAGE_INFO;
import static org.apache.james.vault.dto.DeletedMessageWithStorageInformationDTOFixture.DELETED_MESSAGE_WITH_STORAGE_INFO_DTO;
import static org.apache.james.vault.dto.DeletedMessageWithStorageInformationDTOFixture.DELETED_MESSAGE_WITH_SUBJECT_DTO;
import static org.apache.james.vault.dto.DeletedMessageWithStorageInformationDTOFixture.STORAGE_INFORMATION_DTO;
import static org.apache.james.vault.metadata.DeletedMessageVaultMetadataFixture.STORAGE_INFORMATION;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.blob.api.HashBlobId;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeletedMessageWithStorageInformationConverterTest {

    private DeletedMessageWithStorageInformationConverter converter;

    @BeforeEach
    void setup() {
        converter = new DeletedMessageWithStorageInformationConverter(
            new HashBlobId.Factory(),
            new InMemoryMessageId.Factory(),
            new InMemoryId.Factory());
    }

    @Test
    void shouldConvertStorageInformationDTOToStorageInformation() {
        assertThat(converter.toStorageInformation(STORAGE_INFORMATION_DTO))
            .isEqualTo(STORAGE_INFORMATION);
    }

    @Test
    void shouldConvertDeletedMessageDTOToDeletedMessage() {
        assertThat(converter.toDeletedMessage(DELETED_MESSAGE_DTO))
            .isEqualTo(DELETED_MESSAGE);
    }

    @Test
    void shouldConvertDeletedMessageDTOWithSubjectToDeletedMessage() {
        assertThat(converter.toDeletedMessage(DELETED_MESSAGE_WITH_SUBJECT_DTO))
            .isEqualTo(DELETED_MESSAGE_WITH_SUBJECT);
    }

    @Test
    void shouldConvertDeletedMessageWithStorageInformationDTOToDeletedMessageWithStorageInformation() {
        assertThat(converter.toDeletedMessageWithStorageInformation(DELETED_MESSAGE_WITH_STORAGE_INFO_DTO))
            .isEqualTo(DELETED_MESSAGE_WITH_STORAGE_INFO);
    }
}
