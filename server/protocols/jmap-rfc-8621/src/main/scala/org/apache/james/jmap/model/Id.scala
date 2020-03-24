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

object Id {
  implicit val idTypeValidate: Validate.Plain[String, IdType] =
    Validate.fromPredicate(
      idAsString => idAsString != null
        && idAsString.length > 0 && idAsString.length < 256
        && idAsString.matches("^[a-zA-Z0-9-_]*"),
      _ => "value does not meet Id requirements",
      IdType())
}

final case class IdType()
final case class Id(value: String Refined IdType)
