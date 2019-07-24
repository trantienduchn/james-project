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

package org.apache.james.utils.smtp;

import java.io.IOException;

import org.subethamail.smtp.RejectException;

public interface MessageStateHandler {

    interface RejectingHandler extends MessageStateHandler {

        static RejectingHandler error(int statusCode, String message) {
            return mailState -> {
                throw new RejectException(statusCode, message);
            };
        }

        RejectingHandler ERROR_431_OUT_OF_MEMORY = error(431, "out of memory");
        RejectingHandler ERROR_450_RETRY_FAILS = error(450, "Server will retry but will fail");
        RejectingHandler ERROR_451_SERVER_ERROR = error(451, "Requested action aborted – Local error in processing");
        RejectingHandler ERROR_450_RETRY_SUCCESSES = mailState -> {
            try {
                mailState.getSession().sendResponse("450 Server will retry later and will be succeed");
                mailState.complete();
            } catch (IOException e) {
                ERROR_451_SERVER_ERROR.handle(mailState);
            }
        };
        RejectingHandler ERROR_500_COMMAND_NOT_FOUND = error(500, "the server could not recognize the command");


    }

    static MessageStateHandler noop() {
        return mailState -> {};
    }

    void handle(MockMailProcessingState mailState) throws RejectException;
}
