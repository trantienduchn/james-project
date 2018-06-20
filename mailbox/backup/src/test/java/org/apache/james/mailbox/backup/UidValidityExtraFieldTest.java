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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.ZipException;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.bouncycastle.util.Arrays;

import com.google.common.base.Charsets;

public class UidValidityExtraFieldTest {
    private static final byte[] ZERO_AS_BYTE_ARRAY = {0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] _123456789ABCDEF0_AS_LE_BYTE_ARRAY = new byte[] {(byte) 0xF0, (byte) 0xDE, (byte) 0xBC, (byte) 0x9A, 0x78, 0x56, 0x34, 0x12};
    private static final byte[] FEDCBA9876543210_AS_LE_BYTE_ARRAY = new byte[] {0x10, 0x32, 0x54, 0x76, (byte) 0x98, (byte) 0xBA, (byte) 0xDC, (byte) 0xFE};
    private static final byte[] UNUSED = new byte[] {(byte) 0xDE, (byte) 0xAD};

    @Nested
    class GetHeaderId {

        @Test
        void getHeaderIdShouldReturnSpecificStringInLittleEndian() {
            UidValidityExtraField testee = new UidValidityExtraField();

            ByteBuffer byteBuffer = ByteBuffer.wrap(testee.getHeaderId().getBytes())
                .order(ByteOrder.LITTLE_ENDIAN);
            assertThat(Charsets.US_ASCII.decode(byteBuffer).toString())
                .isEqualTo("an");
        }
    }

    @Nested
    class GetLocalFileDataLength {
        @Test
        void getLocalFileDataLengthShouldReturnIntegerSize() {
            UidValidityExtraField testee = new UidValidityExtraField();

            assertThat(testee.getLocalFileDataLength().getValue())
                .isEqualTo(Long.BYTES);
        }
    }

    @Nested
    class GetCentralDirectoryLength {

        @Test
        void getCentralDirectoryLengthShouldReturnIntegerSize() {
            UidValidityExtraField testee = new UidValidityExtraField();

            assertThat(testee.getCentralDirectoryLength().getValue())
                .isEqualTo(Long.BYTES);
        }


        @Test
        void getCentralDirectoryDataShouldThrowWhenNoValue() {
            UidValidityExtraField testee = new UidValidityExtraField();

            assertThatThrownBy(() -> testee.getCentralDirectoryData())
                .isInstanceOf(RuntimeException.class);
        }

        @Test
        void getCentralDirectoryDataShouldReturnZeroWhenZero() {
            byte[] actual = new UidValidityExtraField(0).getCentralDirectoryData();
            assertThat(actual).isEqualTo(ZERO_AS_BYTE_ARRAY);
        }

        @Test
        void getCentralDirectoryDataShouldReturnValueInLittleIndianWhen123456789ABCDEF0() {
            byte[] actual = new UidValidityExtraField(0x123456789ABCDEF0L).getCentralDirectoryData();
            assertThat(actual).isEqualTo(_123456789ABCDEF0_AS_LE_BYTE_ARRAY);
        }

        @Test
        void getCentralDirectoryDataShouldReturnValueInLittleIndianWhenFEDCBA9876543210() {
            byte[] actual = new UidValidityExtraField(0xFEDCBA9876543210L).getCentralDirectoryData();
            assertThat(actual).isEqualTo(FEDCBA9876543210_AS_LE_BYTE_ARRAY);
        }
    }

    @Nested
    class GetLocalFileDataData {

        @Test
        void getLocalFileDataDataShouldThrowWhenNoValue() {
            UidValidityExtraField testee = new UidValidityExtraField();

            assertThatThrownBy(() -> testee.getLocalFileDataData())
                .isInstanceOf(RuntimeException.class);
        }

        @Test
        void getLocalFileDataDataShouldReturnZeroWhenZero() {
            byte[] actual = new UidValidityExtraField(0).getLocalFileDataData();
            assertThat(actual).isEqualTo(ZERO_AS_BYTE_ARRAY);
        }

        @Test
        void getLocalFileDataDataShouldReturnValueInLittleIndianWhen123456789ABCDEF0() {
            byte[] actual = new UidValidityExtraField(0x123456789ABCDEF0L).getLocalFileDataData();
            assertThat(actual).isEqualTo(_123456789ABCDEF0_AS_LE_BYTE_ARRAY);
        }

        @Test
        void getLocalFileDataDataShouldReturnValueInLittleIndianWhenFEDCBA9876543210() {
            byte[] actual = new UidValidityExtraField(0xFEDCBA9876543210L).getLocalFileDataData();
            assertThat(actual).isEqualTo(FEDCBA9876543210_AS_LE_BYTE_ARRAY);
        }
    }

    @Nested
    class ParseFromLocalFileData {

        @Test
        void parseFromLocalFileDataShouldThrownWhenLengthIsSmallerThan8() {
            UidValidityExtraField testee = new UidValidityExtraField();

            byte[] input = new byte[] {0, 0, 0, 0, 0, 0, 0};
            assertThatThrownBy(() -> testee.parseFromLocalFileData(input, 0, 7))
                .isInstanceOf(ZipException.class);
        }

        @Test
        void parseFromLocalFileDataShouldThrownWhenLengthIsBiggerThan8() {
            UidValidityExtraField testee = new UidValidityExtraField();

            byte[] input = new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0};
            assertThatThrownBy(() -> testee.parseFromLocalFileData(input, 0, 9))
                .isInstanceOf(ZipException.class);
        }

        @Test
        void parseFromLocalFileDataShouldParseWhenZero() throws Exception {
            UidValidityExtraField testee = new UidValidityExtraField();

            testee.parseFromLocalFileData(ZERO_AS_BYTE_ARRAY, 0, 8);
            assertThat(testee.getValue())
                .contains(0L);
        }

        @Test
        void parseFromLocalFileDataShouldParseWhen123456789ABCDEF0InLittleEndian() throws Exception {
            UidValidityExtraField testee = new UidValidityExtraField();

            testee.parseFromLocalFileData(_123456789ABCDEF0_AS_LE_BYTE_ARRAY, 0, 8);
            assertThat(testee.getValue())
                .contains(0x123456789ABCDEF0L);
        }

        @Test
        void parseFromLocalFileDataShouldParseWhenFEDCBA9876543210InLittleEndian() throws Exception {
            UidValidityExtraField testee = new UidValidityExtraField();

            byte[] input = FEDCBA9876543210_AS_LE_BYTE_ARRAY;
            testee.parseFromLocalFileData(input, 0, 8);
            assertThat(testee.getValue())
                .contains(0xFEDCBA9876543210L);
        }

        @Test
        void parseFromLocalFileDataShouldHandleOffset() throws Exception {
            UidValidityExtraField testee = new UidValidityExtraField();

            byte[] input = Arrays.concatenate(UNUSED, _123456789ABCDEF0_AS_LE_BYTE_ARRAY);
            testee.parseFromLocalFileData(input, 2, 8);
            assertThat(testee.getValue())
                .contains(0x123456789ABCDEF0L);
        }
    }

    @Nested
    class ParseFromCentralDirectoryData {

        @Test
        void parseFromCentralDirectoryDataShouldThrownWhenLengthIsSmallerThan8() {
            UidValidityExtraField testee = new UidValidityExtraField();
            byte[] input = new byte[7];

            assertThatThrownBy(() -> testee.parseFromCentralDirectoryData(input, 0, 7))
                .isInstanceOf(ZipException.class);
        }

        @Test
        void parseFromCentralDirectoryDataShouldThrownWhenLengthIsBiggerThan8() {
            UidValidityExtraField testee = new UidValidityExtraField();
            byte[] input = new byte[9];

            assertThatThrownBy(() -> testee.parseFromCentralDirectoryData(input, 0, 9))
                .isInstanceOf(ZipException.class);
        }

        @Test
        void parseFromCentralDirectoryDataShouldParseWhenZero() throws Exception {
            UidValidityExtraField testee = new UidValidityExtraField();

            testee.parseFromCentralDirectoryData(ZERO_AS_BYTE_ARRAY, 0, 8);
            assertThat(testee.getValue())
                .contains(0L);
        }

        @Test
        void parseFromCentralDirectoryDataShouldParseWhen123456789ABCDEF0InLittleEndian() throws Exception {
            UidValidityExtraField testee = new UidValidityExtraField();

            testee.parseFromCentralDirectoryData(_123456789ABCDEF0_AS_LE_BYTE_ARRAY, 0, 8);
            assertThat(testee.getValue())
                .contains(0x123456789ABCDEF0L);
        }

        @Test
        void parseFromCentralDirectoryDataShouldParseWhenFEDCBA9876543210InLittleEndian() throws Exception {
            UidValidityExtraField testee = new UidValidityExtraField();
            byte[] input = FEDCBA9876543210_AS_LE_BYTE_ARRAY;

            testee.parseFromCentralDirectoryData(input, 0, 8);
            assertThat(testee.getValue())
                .contains(0xFEDCBA9876543210L);
        }

        @Test
        void parseFromCentralDirectoryDataShouldHandleOffset() throws Exception {
            UidValidityExtraField testee = new UidValidityExtraField();
            byte[] input = Arrays.concatenate(UNUSED, _123456789ABCDEF0_AS_LE_BYTE_ARRAY);

            testee.parseFromCentralDirectoryData(input, 2, 8);
            assertThat(testee.getValue())
                .contains(0x123456789ABCDEF0L);
        }
    }
}
