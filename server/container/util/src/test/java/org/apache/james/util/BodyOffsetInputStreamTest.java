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
package org.apache.james.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.james.util.BodyOffsetInputStream.Splitter.MessageParts;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BodyOffsetInputStreamTest {
    private static final String MAIL_HEADER = "Subject: test\r\n\r\n";
    private static final byte[] MAIL_HEADER_BYTES = MAIL_HEADER.getBytes(StandardCharsets.UTF_8);
    private static final String MAIL_BODY = "body";
    private static final byte[] MAIL_BODY_BYTES = MAIL_BODY.getBytes(StandardCharsets.UTF_8);

    private static final String MAIL_FULL_CONTENT = MAIL_HEADER + MAIL_BODY;
    private static final long EXPECTED_OFFSET = 17;
    private static final long MAIL_FULL_CONTENT_LENGTH = MAIL_FULL_CONTENT.length();

    private BodyOffsetInputStream testee;

    @BeforeEach
    void setUp() {
        testee = new BodyOffsetInputStream(new ByteArrayInputStream(MAIL_FULL_CONTENT.getBytes()));
    }

    @Test
    void testRead() throws IOException {
        while (testee.read() != -1) {
            // consume stream
        }
        assertThat(testee.getBodyStartOffset()).isEqualTo(EXPECTED_OFFSET);
        assertThat(testee.getReadBytes()).isEqualTo(MAIL_FULL_CONTENT_LENGTH);
        testee.close();
    }

    @Test
    void testReadWithArray() throws IOException {
        byte[] b = new byte[8];
        while (testee.read(b) != -1) {
            // consume stream
        }
        assertThat(testee.getBodyStartOffset()).isEqualTo(EXPECTED_OFFSET);
        assertThat(testee.getReadBytes()).isEqualTo(MAIL_FULL_CONTENT_LENGTH);
        testee.close();
    }

    @Test
    void testReadWithArrayBiggerThenStream() throws IOException {
        byte[] b = new byte[4096];
        while (testee.read(b) != -1) {
            // consume stream
        }
        assertThat(testee.getBodyStartOffset()).isEqualTo(EXPECTED_OFFSET);
        assertThat(testee.getReadBytes()).isEqualTo(MAIL_FULL_CONTENT_LENGTH);
        testee.close();
    }

    @Nested
    class SplitterTest {

        @Test
        void splitShouldReturnExactlyParts() throws IOException {
            MessageParts messageParts = BodyOffsetInputStream.Splitter.split(testee);
            try (InputStream headerContent = messageParts.getHeaderContent();
                 InputStream bodyContent = messageParts.getBodyContent()) {
                SoftAssertions.assertSoftly(softly -> {
                    softly.assertThat(headerContent)
                        .hasSameContentAs(new ByteArrayInputStream(MAIL_HEADER_BYTES));
                    softly.assertThat(bodyContent)
                        .hasSameContentAs(new ByteArrayInputStream(MAIL_BODY_BYTES));
                });
            }
        }

        @Test
        void splitShouldReturnEmptyPartsWhenEmptyInputStream() throws IOException {
            testee = new BodyOffsetInputStream(emptyInputStream());

            MessageParts messageParts = BodyOffsetInputStream.Splitter.split(testee);
            try (InputStream headerContent = messageParts.getHeaderContent();
                 InputStream bodyContent = messageParts.getBodyContent()) {
                SoftAssertions.assertSoftly(softly -> {
                    softly.assertThat(headerContent)
                        .hasSameContentAs(emptyInputStream());
                    softly.assertThat(bodyContent)
                        .hasSameContentAs(emptyInputStream());
                });
            }
        }

        @Test
        void splitShouldReturnEmptyBodyPartsWhenEndWithBodyOffset() throws IOException {
            testee = new BodyOffsetInputStream(new ByteArrayInputStream(MAIL_HEADER_BYTES));

            MessageParts messageParts = BodyOffsetInputStream.Splitter.split(testee);
            try (InputStream headerContent = messageParts.getHeaderContent();
                 InputStream bodyContent = messageParts.getBodyContent()) {
                SoftAssertions.assertSoftly(softly -> {
                    softly.assertThat(headerContent)
                        .hasSameContentAs(new ByteArrayInputStream(MAIL_HEADER_BYTES));
                    softly.assertThat(bodyContent)
                        .hasSameContentAs(emptyInputStream());
                });
            }
        }

        private ByteArrayInputStream emptyInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }
    }
}
