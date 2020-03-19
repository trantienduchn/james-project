/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/

package org.apache.james.jmap.model

import eu.timepit.refined.api.{Refined, Validate}
import eu.timepit.refined.boolean.And
import eu.timepit.refined.string.MatchesRegex
import org.apache.james.jmap.model.Id.IdConstraints

final case class NonNull()

object NonNull {
  implicit def matchesNonNull[Any]: Validate.Plain[Any, NonNull] =
    Validate.fromPredicate(
      value => value != null,
      _ => "value cannot be null",
      NonNull())
}

object Id {
  type IdConstraints = NonNull And MatchesRegex["^[a-zA-Z0-9-_]{1,255}$"]
}

final case class Id private(value: String Refined IdConstraints) {
  def asString(): String = {
    value.value
  }
}
