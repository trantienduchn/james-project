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

import static org.apache.james.mailbox.vault.GenericComponents.OperatorSign.CONTAINS;
import static org.apache.james.mailbox.vault.GenericComponents.OperatorSign.GREATER_OR_EQUAL;
import static org.apache.james.mailbox.vault.GenericComponents.OperatorSign.LESS_THAN_OR_EQUAL;

import java.util.List;
import java.util.Optional;

import org.apache.james.mailbox.vault.GenericComponents.FieldName;
import org.apache.james.mailbox.vault.GenericComponents.OperatorSign;
import org.apache.james.mailbox.vault.GenericComponents.Value;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableTable;

public class SupportedChecks {

    static class ComparedValue extends Value {
        ComparedValue(Object value, FieldName fieldName) {
            super(value, fieldName);
        }
    }

    interface ComparedValues {
        List<SupportedChecks.ComparedValue> values();
    }

    interface Operator {
        Class<?> supportedType();

        OperatorSign operatorSign();

        boolean matches(Query.QueryElementValue valueToCheck, ComparedValue comparedValue);
    }

    interface IntegerOperator extends Operator {
        class GreaterThanOrEqual implements IntegerOperator {

            @Override
            public OperatorSign operatorSign() {
                return GREATER_OR_EQUAL;
            }

            @Override
            public boolean matches(Query.QueryElementValue valueToCheck, ComparedValue comparedValue) {
                return (Integer) comparedValue.value >= (Integer) valueToCheck.value;
            }
        }

        class LessThanOrEqual implements IntegerOperator {

            @Override
            public OperatorSign operatorSign() {
                return LESS_THAN_OR_EQUAL;
            }

            @Override
            public boolean matches(Query.QueryElementValue valueToCheck, ComparedValue comparedValue) {
                return (Integer) comparedValue.value <= (Integer) valueToCheck.value;
            }
        }

        Class<?> SUPPORTED_TYPE = Integer.class;

        IntegerOperator.GreaterThanOrEqual INTEGER_GREATER_OR_EQUAL = new IntegerOperator.GreaterThanOrEqual();
        IntegerOperator.LessThanOrEqual LESS_GREATER_OR_EQUAL = new IntegerOperator.LessThanOrEqual();

        ImmutableTable<Class<?>, OperatorSign, Operator> SUPPORTED_OPERATORS = ImmutableTable.<Class<?>, OperatorSign, Operator>builder()
            .put(SUPPORTED_TYPE, INTEGER_GREATER_OR_EQUAL.operatorSign(), INTEGER_GREATER_OR_EQUAL)
            .put(SUPPORTED_TYPE, LESS_GREATER_OR_EQUAL.operatorSign(), LESS_GREATER_OR_EQUAL)
            .build();

        @Override
        default Class<?> supportedType() {
            return SUPPORTED_TYPE;
        }
    }

    interface StringOperator extends Operator {
        class Contains implements IntegerOperator {

            @Override
            public OperatorSign operatorSign() {
                return CONTAINS;
            }

            @Override
            public boolean matches(Query.QueryElementValue valueToCheck, ComparedValue comparedValue) {
                return ((String) comparedValue.value).contains((String) valueToCheck.value);
            }
        }

        Class<?> SUPPORTED_TYPE = String.class;

        StringOperator.Contains STRING_CONTAINS = new StringOperator.Contains();

        ImmutableTable<Class<?>, OperatorSign, Operator> SUPPORTED_OPERATORS = ImmutableTable.<Class<?>, OperatorSign, Operator>builder()
            .put(SUPPORTED_TYPE, STRING_CONTAINS.operatorSign(), STRING_CONTAINS)
            .build();

        @Override
        default Class<?> supportedType() {
            return SUPPORTED_TYPE;
        }
    }

    private static ImmutableTable<Class<?>, OperatorSign, Operator> SUPPORTED_CHECKS = ImmutableTable
        .<Class<?>, OperatorSign, Operator>builder()
        .putAll(IntegerOperator.SUPPORTED_OPERATORS)
        .putAll(StringOperator.SUPPORTED_OPERATORS)
        .build();

    private static boolean matches(Query.QueryElement element, ComparedValue comparedValue) {
        Preconditions.checkArgument(SUPPORTED_CHECKS.containsRow(element.value.type()), element.value.type().getName() + " is not supported");
        Preconditions.checkArgument(SUPPORTED_CHECKS.containsRow(comparedValue.type()), comparedValue.type().getName() + " is not supported");

        if (element.value.type().equals(comparedValue.type()) // E.g, String field and Integer field dont have same type, so ignore
            && element.value.fieldName.equals(comparedValue.fieldName)) { // E.g, deliveryDate field and deletionDate field don't have same name, so ignore
            return Optional.ofNullable(SUPPORTED_CHECKS.get(element.value.type(), element.operatorSign))
                .map(operator -> operator.matches(element.value, comparedValue))
                .orElseThrow(RuntimeException::new);
        }

        return true; // no match type or field name, then ignore, that means element is qualified, good to be returned to users
    }

    private static boolean matches(Query query, ComparedValue comparedValue) {
        return query.elements.stream()
            .map(element -> matches(element, comparedValue))
            .reduce(true, (first, second) -> first & second);
    }

    public static boolean matches(Query query, ComparedValues comparedValues) {
        return comparedValues.values().stream()
            .map(comparedValue -> matches(query, comparedValue))
            .reduce(true, (first, second) -> first & second);
    }
}
