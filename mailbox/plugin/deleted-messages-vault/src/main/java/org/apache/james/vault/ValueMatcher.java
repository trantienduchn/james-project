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

import java.util.List;
import java.util.Locale;

public interface ValueMatcher<T> {
    boolean matches(T value);

    class Equals<T> implements ValueMatcher<T> {
        private final T referenceValue;

        public Equals(T referenceValue) {
            this.referenceValue = referenceValue;
        }

        @Override
        public boolean matches(T value) {
            return referenceValue.equals(value);
        }
    }

    class StringContains implements ValueMatcher<String> {
        private final String referenceValue;

        public StringContains(String referenceValue) {
            this.referenceValue = referenceValue;
        }

        @Override
        public boolean matches(String value) {
            return value.contains(referenceValue);
        }
    }

    class StringContainsIgnoreCase implements ValueMatcher<String> {
        private final String referenceValue;

        public StringContainsIgnoreCase(String referenceValue) {
            this.referenceValue = referenceValue;
        }

        @Override
        public boolean matches(String value) {
            return value.toLowerCase(Locale.US).contains(referenceValue.toLowerCase(Locale.US));
        }
    }

    class ListContains<T> implements ValueMatcher<List<T>> {
        private final T referenceValue;

        public ListContains(T referenceValue) {
            this.referenceValue = referenceValue;
        }

        @Override
        public boolean matches(List<T> value) {
            return value.contains(referenceValue);
        }
    }
}
