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

package org.apache.james.mock.smtp.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.apache.james.mock.smtp.server.Behaviors.Behavior;
import org.apache.james.mock.smtp.server.Behaviors.Behavior.BehavingState;
import org.apache.james.mock.smtp.server.Behaviors.LinkedBehavior;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.subethamail.smtp.RejectException;

class BehaviorsTest {

    private static final String INPUT_DATA = "inputData";
    private static final String FIRST_BEHAVIOR_RESULT = "firstBehaviorResult";
    private static final String SECOND_BEHAVIOR_RESULT = "secondBehaviorResult";
    private static final String THIRD_BEHAVIOR_RESULT = "thirdBehaviorResult";

    @Nested
    class LinkedBehaviorTest {

        @Test
        void behaveShouldExecuteTheCurrentBehavior() {
            List<String> behaviorResults = new ArrayList<>();
            Behavior<String> currentBehavior = state -> behaviorResults.add(FIRST_BEHAVIOR_RESULT);

            LinkedBehavior.current(currentBehavior)
                .behave(BehavingState.nonMatched(INPUT_DATA));

            assertThat(behaviorResults)
                .containsExactly(FIRST_BEHAVIOR_RESULT);
        }

        @Test
        void behaveShouldExecuteTheCurrentBehaviorAndNextBehaviorSequentially() {
            List<String> behaviorResults = new ArrayList<>();
            Behavior<String> currentBehavior = state -> behaviorResults.add(FIRST_BEHAVIOR_RESULT);
            Behavior<String> nextBehavior = state -> behaviorResults.add(SECOND_BEHAVIOR_RESULT);

            LinkedBehavior.current(currentBehavior)
                .withNext(nextBehavior)
                .behave(BehavingState.nonMatched(INPUT_DATA));

            assertThat(behaviorResults)
                .containsExactly(FIRST_BEHAVIOR_RESULT, SECOND_BEHAVIOR_RESULT);
        }

        @Test
        void behaveShouldExecuteSequentiallyTheChain() {
            List<String> behaviorResults = new ArrayList<>();
            Behavior<String> firstBehavior = state -> behaviorResults.add(FIRST_BEHAVIOR_RESULT);
            Behavior<String> secondBehavior = state -> behaviorResults.add(SECOND_BEHAVIOR_RESULT);
            Behavior<String> thirdBehavior = state -> behaviorResults.add(THIRD_BEHAVIOR_RESULT);

            LinkedBehavior.current(firstBehavior)
                .withNext(secondBehavior)
                .withNext(thirdBehavior)
                .behave(BehavingState.nonMatched(INPUT_DATA));

            assertThat(behaviorResults)
                .containsExactly(FIRST_BEHAVIOR_RESULT, SECOND_BEHAVIOR_RESULT, THIRD_BEHAVIOR_RESULT);
        }

        @Test
        void chainOfBehaviorCouldProduceErrorState() {
            List<String> behaviorResults = new ArrayList<>();
            Behavior<String> firstBehavior = state -> behaviorResults.add(FIRST_BEHAVIOR_RESULT);
            Behavior<String> errorBehavior = state -> state.setRejectException(new RejectException("error"));

            BehavingState<String> state = BehavingState.nonMatched(INPUT_DATA);
            LinkedBehavior.current(firstBehavior)
                .withNext(errorBehavior)
                .behave(state);

            assertThat(state.getRejectException())
                .containsInstanceOf(RejectException.class);
        }

        @Test
        void behaveShouldInfluenceMutableStateAcrossBehaviors() {
            List<String> behaviorResults = new ArrayList<>();
            Behavior<String> firstBehavior = state -> behaviorResults.add(FIRST_BEHAVIOR_RESULT);
            Behavior<String> errorBehavior = state -> state.setRejectException(new RejectException("error"));
            Behavior<String> thirdBehavior = state -> {
                if (!state.hasRejectException()) {
                    behaviorResults.add(THIRD_BEHAVIOR_RESULT);
                }
            };

            LinkedBehavior.current(firstBehavior)
                .withNext(errorBehavior)
                .withNext(thirdBehavior)
                .behave(BehavingState.nonMatched(INPUT_DATA));

            assertThat(behaviorResults)
                .containsExactly(FIRST_BEHAVIOR_RESULT);
        }
    }
}
