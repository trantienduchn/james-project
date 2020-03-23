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
package org.apache.james.jmap.rfc.api.method

import java.io.InputStream

import org.apache.james.jmap.rfc.model.{RequestObject, ResponseObject}
import org.slf4j.{Logger, LoggerFactory}
import reactor.core.publisher.Mono


object EchoMethod {
  private val LOGGER = LoggerFactory.getLogger(EchoMethod.getClass)
}

class EchoMethod {
  val logger: Logger = EchoMethod.LOGGER

  def toResponseObject(requestObject: RequestObject): Mono[ResponseObject] = {
    Mono.fromCallable(() => requestObject)
      .map((requestObject: RequestObject) => {
        ResponseObject(SessionState("75128aab4b1b"), requestObject.methodCalls)
      })
  }

  def toRequestObject(inputStream: InputStream): Mono[RequestObject] = {
    Mono.fromCallable(() => inputStream)
      .map((httpContent: HttpContent) => {
        Json.fromJson[RequestObject](Json.parse(httpContent.content().array()))
      })
      .filter((jsResult: JsResult[RequestObject]) => jsResult.isSuccess)
      .map((jsResult: JsResult[RequestObject]) => jsResult.get)
  }
}