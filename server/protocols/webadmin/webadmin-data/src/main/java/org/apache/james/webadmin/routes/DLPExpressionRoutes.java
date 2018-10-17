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

package org.apache.james.webadmin.routes;

import static org.apache.james.webadmin.Constants.JSON_CONTENT_TYPE;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.inject.Inject;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.DLPExpressionSampleMatchRequestDTO;
import org.apache.james.webadmin.dto.DLPExpressionSampleMatchResponseDTO;
import org.apache.james.webadmin.dto.DLPExpressionValidationRequestDTO;
import org.apache.james.webadmin.dto.DLPExpressionValidationResponseDTO;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonExtractException;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModel;
import spark.Request;
import spark.Response;
import spark.Service;

@Api(tags = "DLPExpression")
@ApiModel(description = "DLP (stands for Data Leak Prevention) is supported by James. A DLP matcher will, on incoming emails, " +
        "execute regular expressions on email sender, recipients or content, in order to report suspicious emails to" +
        "an administrator. WebAdmin can be used to test expression of these DLP rules.")
@Path(DLPExpressionRoutes.BASE_PATH)
@Produces(JSON_CONTENT_TYPE)
public class DLPExpressionRoutes implements Routes {

    static final String BASE_PATH = "/dlp/expression";
    private static final String VALIDATE_PATH = BASE_PATH + "/validate";
    private static final String MATCH_PATH = BASE_PATH + "/sampleMatch";

    private final JsonTransformer jsonTransformer;
    private final JsonExtractor<DLPExpressionValidationRequestDTO> validationJsonExtractor;
    private final JsonExtractor<DLPExpressionSampleMatchRequestDTO> sampleMatchJsonExtractor;

    @Inject
    public DLPExpressionRoutes(JsonTransformer jsonTransformer) {
        this.jsonTransformer = jsonTransformer;
        this.validationJsonExtractor = new JsonExtractor<>(DLPExpressionValidationRequestDTO.class);
        this.sampleMatchJsonExtractor = new JsonExtractor<>(DLPExpressionSampleMatchRequestDTO.class);
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        service.post(VALIDATE_PATH, this::validateExpression, jsonTransformer);
        service.post(MATCH_PATH, this::sampleMatch, jsonTransformer);
    }

    private DLPExpressionValidationResponseDTO validateExpression(Request request, Response response) throws JsonExtractException {
        DLPExpressionValidationRequestDTO requestDTO = validationJsonExtractor.parse(request.body());
        validateExpressionDTO(requestDTO);
        try {
            Pattern.compile(requestDTO.getExpression());
            return DLPExpressionValidationResponseDTO.valid();
        } catch (PatternSyntaxException e) {
            return DLPExpressionValidationResponseDTO.invalid();
        }
    }

    private DLPExpressionSampleMatchResponseDTO sampleMatch(Request request, Response response) throws JsonExtractException {
        DLPExpressionSampleMatchRequestDTO requestDTO = sampleMatchJsonExtractor.parse(request.body());
        validateSampleMatchDTO(requestDTO);
        try {
            Pattern pattern = Pattern.compile(requestDTO.getExpression());
            boolean isMatched = pattern.asPredicate().test(requestDTO.getSampleValue());
            return DLPExpressionSampleMatchResponseDTO.matches(isMatched);
        } catch (PatternSyntaxException e) {
            return DLPExpressionSampleMatchResponseDTO.invalid();
        }
    }

    private void validateExpressionDTO(DLPExpressionValidationRequestDTO requestDTO) {
        haltBadRequest(requestDTO.getExpression() == null, "The regex expression can not be null");
    }

    private void validateSampleMatchDTO(DLPExpressionSampleMatchRequestDTO requestDTO) {
        haltBadRequest(requestDTO.getExpression() == null,"The regex expression can not be null");
        haltBadRequest(requestDTO.getSampleValue() == null, "The sample match value can not be null");
    }

    private void haltBadRequest(boolean matchedCondition, String message) {
        if (matchedCondition) {
            ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message(message)
                .haltError();
        }
    }
}
