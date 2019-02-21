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

import java.util.List;
import java.util.Objects;

import org.apache.james.mailbox.vault.GenericComponents.FieldName;
import org.apache.james.mailbox.vault.GenericComponents.OperatorSign;
import org.apache.james.mailbox.vault.GenericComponents.Value;

public class Query {

    static final class QueryElementValue extends Value {
        QueryElementValue(Object value, FieldName fieldName) {
            super(value, fieldName);
        }
    }

    static class QueryElement {

        static QueryElement from(QueryElementValue value, OperatorSign operatorSign) {
            return new QueryElement(value, operatorSign);
        }

        final QueryElementValue value;
        final OperatorSign operatorSign;

        QueryElement(QueryElementValue value, OperatorSign operatorSign) {
            this.value = value;
            this.operatorSign = operatorSign;
        }
    }

    static Query of(List<QueryElement> queryElements) {
        return new Query(queryElements);
    }

    final List<QueryElement> elements;

    Query(List<QueryElement> elements) {
        this.elements = elements;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Query) {
            Query query = (Query) o;

            return Objects.equals(this.elements, query.elements);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(elements);
    }
}