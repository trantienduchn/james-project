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

package org.apache.james.mailbox.backup;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.compress.archivers.zip.ZipExtraField;
import org.apache.commons.compress.archivers.zip.ZipShort;

public abstract class StringExtraField implements ZipExtraField {

    private Optional<String> value;

    public StringExtraField() {
        this(Optional.empty());
    }

    public StringExtraField(String value) {
        this(Optional.of(value));
    }

    public StringExtraField(Optional<String> value) {
        this.value = value;
    }

    @Override
    public ZipShort getLocalFileDataLength() {
        return value
            .map(value -> value.getBytes(StandardCharsets.UTF_8).length)
            .map(ZipShort::new)
            .orElseThrow(() -> new RuntimeException("Value must by initialized"));
    }

    @Override
    public ZipShort getCentralDirectoryLength() {
        return getLocalFileDataLength();
    }

    @Override
    public byte[] getLocalFileDataData() {
        return value
            .map(value -> value.getBytes(StandardCharsets.UTF_8))
            .orElseThrow(() -> new RuntimeException("Value must by initialized"));
    }

    @Override
    public byte[] getCentralDirectoryData() {
        return getLocalFileDataData();
    }

    @Override
    public void parseFromLocalFileData(byte[] buffer, int offset, int length) {
        value = Optional.of(new String(buffer, offset, length, StandardCharsets.UTF_8));
    }

    @Override
    public void parseFromCentralDirectoryData(byte[] buffer, int offset, int length) {
        parseFromLocalFileData(buffer, offset, length);
    }

    public Optional<String> getValue() {
        return value;
    }

    @Override
    public final boolean equals(Object o) {
        if (this.getClass() == o.getClass()) {
            StringExtraField that = (StringExtraField) o;

            return Objects.equals(this.getValue(), that.getValue())
                && Objects.equals(this.getHeaderId(), that.getHeaderId());
        }
        return false;
    }
}
