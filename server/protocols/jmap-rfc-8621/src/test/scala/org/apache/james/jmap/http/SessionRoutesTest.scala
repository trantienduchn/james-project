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

package org.apache.james.jmap.http

import java.nio.charset.StandardCharsets

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured
import io.restassured.builder.RequestSpecBuilder
import io.restassured.config.EncoderConfig.encoderConfig
import io.restassured.config.RestAssuredConfig.newConfig
import io.restassured.http.ContentType
import org.apache.http.HttpStatus
import org.apache.james.core.Username
import org.apache.james.jmap.http.SessionRoutesTest.{BOB, TEST_CONFIGURATION}
import org.apache.james.jmap._
import org.apache.james.mailbox.MailboxSession
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import reactor.core.publisher.Mono

import scala.jdk.CollectionConverters._

object SessionRoutesTest {
  private val JMAP_SESSION = "/jmap/session"
  private val TEST_CONFIGURATION = JMAPConfiguration.builder.enable.randomPort.build
  private val BOB = Username.of("bob@james.org")
}

class SessionRoutesTest extends AnyFlatSpec with BeforeAndAfter with Matchers {

  var jmapServer: JMAPServer = _

  before {
    val mockedSession = mock(classOf[MailboxSession])
    when(mockedSession.getUser)
      .thenReturn(BOB)

    val mockedAuthFilter = mock(classOf[Authenticator])
    when(mockedAuthFilter.authenticate(any()))
      .thenReturn(Mono.just(mockedSession))

    val sessionRoutes = new SessionRoutes(
      sessionSupplier = new SessionSupplier(),
      authenticator = mockedAuthFilter)
    jmapServer = new JMAPServer(
      TEST_CONFIGURATION,
      Set(new JMAPRoutesHandler(Version.RFC8621, sessionRoutes)).asJava,
      new VersionParser(Set(Version.RFC8621).asJava))
    jmapServer.start()

    RestAssured.requestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader(ACCEPT.toString, s"application/json; jmapVersion=${Version.RFC8621.asString}")
      .setConfig(newConfig.encoderConfig(encoderConfig.defaultContentCharset(StandardCharsets.UTF_8)))
      .setPort(jmapServer.getPort.getValue)
      .setBasePath(SessionRoutesTest.JMAP_SESSION)
      .build()
  }

  after {
    jmapServer.stop()
  }

  "get" should "return OK status" in {
    RestAssured.when()
      .get
    .then
      .statusCode(HttpStatus.SC_OK)
      .contentType(ContentType.JSON)
  }

  "get" should "return correct session" in {
    val sessionJson = RestAssured.`with`()
        .get
      .thenReturn
        .getBody
        .asString()
    val expectedJson = """{
                         |  "capabilities" : {
                         |    "urn:ietf:params:jmap:core" : {
                         |      "maxSizeUpload" : 10000000,
                         |      "maxConcurrentUpload" : 4,
                         |      "maxSizeRequest" : 10000000,
                         |      "maxConcurrentRequests" : 4,
                         |      "maxCallsInRequest" : 16,
                         |      "maxObjectsInGet" : 500,
                         |      "maxObjectsInSet" : 500,
                         |      "collationAlgorithms" : [ "i;unicode-casemap" ]
                         |    },
                         |    "urn:ietf:params:jmap:mail" : {
                         |      "maxMailboxesPerEmail" : 10000000,
                         |      "maxMailboxDepth" : null,
                         |      "maxSizeMailboxName" : 200,
                         |      "maxSizeAttachmentsPerEmail" : 20000000,
                         |      "emailQuerySortOptions" : [ "receivedAt", "cc", "from", "to", "subject", "size", "sentAt", "hasKeyword", "uid", "Id" ],
                         |      "mayCreateTopLevelMailbox" : true
                         |    }
                         |  },
                         |  "accounts" : {
                         |    "0fe275bf13ff761407c17f64b1dfae2f4b3186feea223d7267b79f873a105401" : {
                         |      "name" : "bob@james.org",
                         |      "isPersonal" : true,
                         |      "isReadOnly" : false,
                         |      "accountCapabilities" : {
                         |        "urn:ietf:params:jmap:core" : {
                         |          "maxSizeUpload" : 10000000,
                         |          "maxConcurrentUpload" : 4,
                         |          "maxSizeRequest" : 10000000,
                         |          "maxConcurrentRequests" : 4,
                         |          "maxCallsInRequest" : 16,
                         |          "maxObjectsInGet" : 500,
                         |          "maxObjectsInSet" : 500,
                         |          "collationAlgorithms" : [ "i;unicode-casemap" ]
                         |        },
                         |        "urn:ietf:params:jmap:mail" : {
                         |          "maxMailboxesPerEmail" : 10000000,
                         |          "maxMailboxDepth" : null,
                         |          "maxSizeMailboxName" : 200,
                         |          "maxSizeAttachmentsPerEmail" : 20000000,
                         |          "emailQuerySortOptions" : [ "receivedAt", "cc", "from", "to", "subject", "size", "sentAt", "hasKeyword", "uid", "Id" ],
                         |          "mayCreateTopLevelMailbox" : true
                         |        }
                         |      }
                         |    }
                         |  },
                         |  "primaryAccounts" : {
                         |    "urn:ietf:params:jmap:core" : "0fe275bf13ff761407c17f64b1dfae2f4b3186feea223d7267b79f873a105401",
                         |    "urn:ietf:params:jmap:mail" : "0fe275bf13ff761407c17f64b1dfae2f4b3186feea223d7267b79f873a105401"
                         |  },
                         |  "username" : "bob@james.org",
                         |  "apiUrl" : "http://this-url-is-hardcoded.org/jmap",
                         |  "downloadUrl" : "http://this-url-is-hardcoded.org/download",
                         |  "uploadUrl" : "http://this-url-is-hardcoded.org/upload",
                         |  "eventSourceUrl" : "http://this-url-is-hardcoded.org/eventSource",
                         |  "state" : "000001"
                         |}""".stripMargin

    Json.parse(sessionJson) should equal(Json.parse(expectedJson))
  }
}
