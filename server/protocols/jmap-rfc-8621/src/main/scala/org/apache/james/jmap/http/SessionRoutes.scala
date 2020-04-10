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

import java.util.function.BiFunction

import io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE
import io.netty.handler.codec.http.HttpResponseStatus.OK
import javax.inject.Inject
import org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE_UTF8
import org.apache.james.jmap.JMAPRoutes
import org.apache.james.jmap.exceptions.UnauthorizedException
import org.apache.james.jmap.http.SessionRoutes.JMAP_SESSION
import org.apache.james.jmap.json.Serializer
import org.apache.james.jmap.model.Session
import org.reactivestreams.Publisher
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono
import reactor.core.scheduler.Schedulers
import reactor.netty.http.server.{HttpServerRequest, HttpServerResponse, HttpServerRoutes}

object SessionRoutes {
  private val JMAP_SESSION = "/jmap/session"
  private val LOGGER = LoggerFactory.getLogger(classOf[SessionRoutes])
}

@Inject
class SessionRoutes(val authFilter: Authenticator,
                    val sessionSupplier: SessionSupplier = new SessionSupplier(),
                    val serializer: Serializer = new Serializer) extends JMAPRoutes {

  val logger: Logger = SessionRoutes.LOGGER
  val generateSession: BiFunction[HttpServerRequest, HttpServerResponse, Publisher[Void]] =
    (request, response) => SMono.fromPublisher(authFilter.authenticate(request))
      .map(_.getUser)
      .flatMap(sessionSupplier.generate)
      .flatMap(session => sendRespond(session, response))
      .onErrorResume(throwable => SMono.fromPublisher(errorHandling(throwable, response)))
      .subscribeOn(Schedulers.elastic())

  override def define(builder: HttpServerRoutes): HttpServerRoutes = {
    builder.get(JMAP_SESSION, generateSession)
  }

  private def sendRespond(session: Session, resp: HttpServerResponse): SMono[Void] =
    SMono.fromPublisher(resp.header(CONTENT_TYPE, JSON_CONTENT_TYPE_UTF8)
      .status(OK)
      .sendString(SMono.fromCallable(() => Json.stringify(serializer.serialize(session))))
      .`then`())

  def errorHandling(throwable: Throwable, response: HttpServerResponse): Mono[Void] =
    throwable match {
      case _: UnauthorizedException => handleAuthenticationFailure(response, throwable)
      case _ => handleInternalError(response, throwable)
    }
}
