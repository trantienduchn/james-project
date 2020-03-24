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

import eu.timepit.refined.auto._
import eu.timepit.refined.refineV
import org.apache.james.jmap.model.Id.idTypeValidate
import org.scalatest.{Matchers, WordSpec}

class IdTest extends WordSpec with Matchers {

  private val INVALID_CHARACTERS = List("\"", "(", ")", ",", ":", ";", "<", ">", "@", "[", "\\", "]", " ")

  /**
   * From Refined documentation:
   *
   * Macros can only validate literals because their values are known at
   * compile-time. To validate arbitrary (runtime) values we can use the
   * refineV function
   */
  "apply" when {
    "in Runtime" should {
      "return left(error message) when null value" in {
        val nullString: String = null
        val maybeId: Option[String] = refineV[IdType](nullString) match {
          case Left(errorMessage) => Option(errorMessage)
          case Right(validValue) => Option.empty
        }

        maybeId.get should equal("Predicate failed: value does not meet Id requirements.")
      }

      "return left(error message) when empty value" in {
        val maybeId: Option[String] = refineV[IdType]("") match {
          case Left(errorMessage) => Option(errorMessage)
          case Right(validValue) => Option.empty
        }

        maybeId.get should equal("Predicate failed: value does not meet Id requirements.")
      }

      "return left(error message) when too long value" in {
        val maybeId: Option[String] = refineV[IdType]("a" * 256) match {
          case Left(errorMessage) => Option(errorMessage)
          case Right(validValue) => Option.empty
        }

        maybeId.get should equal("Predicate failed: value does not meet Id requirements.")
      }

      "return left(error message) when containing invalid characters" in {
        INVALID_CHARACTERS.foreach { invalidChar =>
          val maybeId: Option[String] = refineV[IdType](invalidChar) match {
            case Left(errorMessage) => Option(errorMessage)
            case Right(validValue) => Option.empty
          }

          maybeId.get should equal("Predicate failed: value does not meet Id requirements.")
        }
      }

      "return right when valid value" in {
        val maybeId: Option[Id] = refineV[IdType]("myId") match {
          case Left(errorMessage) => Option.empty
          case Right(validValue) => Option(Id(validValue))
        }

        maybeId.get should equal(Id("myId"))
      }
    }
  }
}