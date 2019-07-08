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

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.james.util.ClassLoaderUtils.getSystemResourceAsString;
import static org.apache.james.vault.dto.DeletedMessageWithStorageInformationDTO.DeletedMessageDTO;
import static org.apache.james.vault.dto.DeletedMessageWithStorageInformationDTO.StorageInformationDTO;
import static org.apache.james.vault.dto.DeletedMessageWithStorageInformationDTOFixture.DELETED_MESSAGE_DTO;
import static org.apache.james.vault.dto.DeletedMessageWithStorageInformationDTOFixture.DELETED_MESSAGE_WITH_STORAGE_INFO_DTO;
import static org.apache.james.vault.dto.DeletedMessageWithStorageInformationDTOFixture.DELETED_MESSAGE_WITH_SUBJECT_DTO;
import static org.apache.james.vault.dto.DeletedMessageWithStorageInformationDTOFixture.STORAGE_INFORMATION_DTO;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import nl.jqno.equalsverifier.EqualsVerifier;

class DeletedMessageWithStorageInformationDTOTest {
    private static final String STORAGE_INFORMATION_JSON = getSystemResourceAsString("json/storage_information.json");

    private static final String DELETED_MESSAGE_JSON = getSystemResourceAsString("json/deleted_message.json");
    private static final String DELETED_MESSAGE_WITH_SUBJECT_JSON = getSystemResourceAsString("json/deleted_message_with_subject.json");

    private static final String DELETED_MESSAGE_WITH_STORAGE_INFO_JSON =
        getSystemResourceAsString("json/deleted_message_with_storage_information.json");

    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        this.objectMapper = new ObjectMapper()
            .registerModule(new Jdk8Module())
            .setSerializationInclusion(JsonInclude.Include.NON_ABSENT);
    }

    @Test
    void storageInformationDTOShouldRespectBeanContract() {
        EqualsVerifier.forClass(StorageInformationDTO.class).verify();
    }

    @Test
    void deletedMessageDTOShouldRespectBeanContract() {
        EqualsVerifier.forClass(DeletedMessageDTO.class).verify();
    }

    @Test
    void deletedMessageWithStorageInformationDTOShouldRespectBeanContract() {
        EqualsVerifier.forClass(DeletedMessageWithStorageInformationDTO.class).verify();
    }

    @Test
    void shouldSerializeStorageInformationDTO() throws JsonProcessingException {
        assertThatJson(objectMapper.writeValueAsString(STORAGE_INFORMATION_DTO))
            .isEqualTo(STORAGE_INFORMATION_JSON);
    }

    @Test
    void shouldDeserializeStorageInformationDTO() throws IOException {
        assertThat(objectMapper.readValue(STORAGE_INFORMATION_JSON, StorageInformationDTO.class))
            .isEqualTo(STORAGE_INFORMATION_DTO);
    }

    @Test
    void shouldSerializeDeletedMessageDTO() throws JsonProcessingException {
        assertThatJson(objectMapper.writeValueAsString(DELETED_MESSAGE_DTO))
            .isEqualTo(DELETED_MESSAGE_JSON);
    }

    @Test
    void shouldDeserializeDeletedMessageDTO() throws IOException {
        assertThat(objectMapper.readValue(DELETED_MESSAGE_JSON, DeletedMessageDTO.class))
            .isEqualTo(DELETED_MESSAGE_DTO);
    }

    @Test
    void shouldSerializeDeletedMessageDTOWithSubject() throws JsonProcessingException {
        assertThatJson(objectMapper.writeValueAsString(DELETED_MESSAGE_WITH_SUBJECT_DTO))
            .isEqualTo(DELETED_MESSAGE_WITH_SUBJECT_JSON);
    }

    @Test
    void shouldDeserializeDeletedMessageDTOWithSubject() throws IOException {
        assertThat(objectMapper.readValue(DELETED_MESSAGE_WITH_SUBJECT_JSON, DeletedMessageDTO.class))
            .isEqualTo(DELETED_MESSAGE_WITH_SUBJECT_DTO);
    }

    @Test
    void shouldSerializeDeletedMessageWithStorageInformationDTO() throws JsonProcessingException {
        assertThatJson(objectMapper.writeValueAsString(DELETED_MESSAGE_WITH_STORAGE_INFO_DTO))
            .isEqualTo(DELETED_MESSAGE_WITH_STORAGE_INFO_JSON);
    }

    @Test
    void shouldDeserializeDeletedMessageWithStorageInformationDTO() throws IOException {
        assertThat(objectMapper.readValue(DELETED_MESSAGE_WITH_STORAGE_INFO_JSON, DeletedMessageWithStorageInformationDTO.class))
            .isEqualTo(DELETED_MESSAGE_WITH_STORAGE_INFO_DTO);
    }
}
