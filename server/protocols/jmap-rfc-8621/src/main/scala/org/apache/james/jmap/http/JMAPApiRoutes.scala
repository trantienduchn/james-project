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

package org.apache.james.jmap.http

import java.nio.charset.StandardCharsets

import org.apache.commons.io.IOUtils
import org.apache.james.jmap.JMAPRoutes
import org.apache.james.jmap.http.JMAPApiRoutes.JMAP
import org.apache.james.jmap.json.Serializer
import org.apache.james.jmap.model.{Invocation, ResponseObject}
import org.apache.james.jmap.model.Invocation.MethodName
import org.apache.james.util.ReactorUtils
import org.slf4j.{Logger, LoggerFactory}
import reactor.netty.http.server.HttpServerRoutes
import org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE_UTF8
import io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE
import io.netty.handler.codec.http.HttpResponseStatus.OK
import reactor.core.publisher.Mono
import eu.timepit.refined.auto._
import play.api.libs.json.Json

object JMAPApiRoutes {
  private val JMAP = "/jmap"
  private val LOGGER = LoggerFactory.getLogger(classOf[JMAPApiRoutes])
}

class JMAPApiRoutes(val serializer: Serializer = new Serializer) extends JMAPRoutes {
  val logger: Logger = JMAPApiRoutes.LOGGER

  override def define(builder: HttpServerRoutes): HttpServerRoutes = {
    builder.post(JMAP, (request, response) => {
      val requestBody = IOUtils.toString(ReactorUtils.toInputStream(request.receive.asByteBuffer), StandardCharsets.UTF_8) // WIP
      val requestObject = serializer.deserializeRequestObject(requestBody).get // WIP
      val responseInvocations: Seq[Invocation] = requestObject.methodCalls
        .map(methodCall => methodCall.methodName match {
          case MethodName("Core/echo") => EchoMethod.response(methodCall)
          case _ => notSupportedMethod()
        })
      response
        .header(CONTENT_TYPE, JSON_CONTENT_TYPE_UTF8) // add headers
        .status(OK) // set status
        .sendString(Mono.fromCallable(() => Json.stringify(
            serializer.serialize(
              ResponseObject(
                sessionState = "75128aab4b1b",
                methodResponses = responseInvocations)))))
    })
  }

  def notSupportedMethod(): Invocation = {
    throw new UnsupportedOperationException
    // should not throw, instead returning an error response object
  }
}
