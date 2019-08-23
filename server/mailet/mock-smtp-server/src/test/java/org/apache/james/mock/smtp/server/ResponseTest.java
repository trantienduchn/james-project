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

package org.apache.james.mock.smtp.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class ResponseTest {
    static final int OK_250_CODE = 250;
    static final Response.SMTPStatusCode OK_250 = Response.SMTPStatusCode.of(OK_250_CODE);

    @Nested
    class SMTPStatusCodeTest {
        @Test
        void shouldMatchBeanContract() {
            EqualsVerifier.forClass(Response.SMTPStatusCode.class)
                .verify();
        }

        @Test
        void constructorShouldThrowWhenStatusCodeIsNegative() {
            assertThatThrownBy(() -> Response.SMTPStatusCode.of(-1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void constructorShouldThrowWhenStatusCodeIsZero() {
            assertThatThrownBy(() -> Response.SMTPStatusCode.of(0))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void constructorShouldThrowWhenStatusCodeIsTooBig() {
            assertThatThrownBy(() -> Response.SMTPStatusCode.of(600))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void constructorShouldThrowWhenStatusCodeIsTooLittle() {
            assertThatThrownBy(() -> Response.SMTPStatusCode.of(99))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void getCodeShouldReturnInternalValue() {
            assertThat(OK_250.getCode())
                .isEqualTo(OK_250_CODE);
        }
    }

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(Response.class)
            .verify();
    }

    @Test
    void constructorShouldThrowWhenMessageIsNull() {
        assertThatThrownBy(() -> Response.serverReject(OK_250, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorShouldThrowWhenCodeIsNull() {
        assertThatThrownBy(() -> Response.serverReject(null, "message"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void asReplyStringShouldReturnASMTPResponseLine() {
        assertThat(Response.serverReject(OK_250, "message").asReplyString())
            .isEqualTo("250 message");
    }

    @Test
    void asReplyStringShouldReturnASMTPResponseLineWhenEmptyMessage() {
        assertThat(Response.serverReject(OK_250, "").asReplyString())
            .isEqualTo("250 ");
    }

    @Test
    void isServerRejectedShouldReturnTrueWhenServerReject() {
        assertThat(Response.serverReject(OK_250, "message").isServerRejected())
            .isTrue();
    }

    @Test
    void isServerRejectedShouldReturnFalseWhenServerAccept() {
        assertThat(Response.serverAccept(OK_250, "message").isServerRejected())
            .isFalse();
    }
}