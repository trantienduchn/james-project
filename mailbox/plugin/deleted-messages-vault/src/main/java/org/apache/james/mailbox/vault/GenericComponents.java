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

package org.apache.james.mailbox.vault;

import java.util.Objects;

class GenericComponents {

    interface OperatorSign {
        String GREATER_OR_EQUAL_SIGN = "gte";
        OperatorSign GREATER_OR_EQUAL = () -> GREATER_OR_EQUAL_SIGN;

        String LESS_THAN_OR_EQUAL_SIGN = "lte";
        OperatorSign LESS_THAN_OR_EQUAL = () -> LESS_THAN_OR_EQUAL_SIGN;

        String CONTAINS_SIGN = "contains";
        OperatorSign CONTAINS = () -> CONTAINS_SIGN;

        String sign();
    }

    static class FieldName {

        static FieldName withName(String name) {
            return new FieldName(name);
        }

        final String fieldName;

        FieldName(String fieldName) {
            this.fieldName = fieldName;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof FieldName) {
                FieldName fieldName1 = (FieldName) o;

                return Objects.equals(this.fieldName, fieldName1.fieldName);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(fieldName);
        }
    }

    static class Value {

        final Object value;
        final FieldName fieldName;

        Value(Object value, FieldName fieldName) {
            this.value = value;
            this.fieldName = fieldName;
        }

        Class<?> type() {
            return value.getClass();
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Value) {
                Value value1 = (Value) o;

                return Objects.equals(this.value, value1.value)
                    && Objects.equals(this.fieldName, value1.fieldName);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(value, fieldName);
        }
    }
}
