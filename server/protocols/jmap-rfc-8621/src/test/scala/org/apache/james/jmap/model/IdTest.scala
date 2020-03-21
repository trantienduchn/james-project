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

import eu.timepit.refined.api.Refined
import eu.timepit.refined.refineV
import org.apache.james.jmap.model.Id.IdConstraints
import org.scalatest.{Matchers, WordSpec}

class IdTest extends WordSpec with Matchers {

  "apply" when {
    "in Runtime" should {
      "throws when null" in {
        assertThrows[NullPointerException] {
          val either: Either[String, String Refined IdConstraints] = refineV[IdConstraints](null)
        }
      }

      "return left(error message) when empty value" in {
        val either: Either[String, String Refined IdConstraints] = refineV[IdConstraints]("")
        either.isLeft should be(true)
      }

      "return left(error message) when  too long value" in {
        val idWith256Chars = "a" * 256
        val either: Either[String, String Refined IdConstraints] = refineV[IdConstraints](idWith256Chars)
        either.isLeft should be(true)
      }

      "return right when valid value" in {
        val either: Either[String, String Refined IdConstraints] = refineV[IdConstraints]("myid")
        either.isRight should be(true)
        either.map(Id(_)).toOption.get.asString() should be("myid")
      }
    }
  }
}
