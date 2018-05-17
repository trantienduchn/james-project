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

package org.apache.james.webadmin;

import static org.apache.james.webadmin.utils.ErrorResponder.ErrorType.INVALID_ARGUMENT;
import static org.apache.james.webadmin.utils.ErrorResponder.ErrorType.NOT_FOUND;
import static org.apache.james.webadmin.utils.ErrorResponder.ErrorType.SERVER_ERROR;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.eclipse.jetty.http.HttpStatus.INTERNAL_SERVER_ERROR_500;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;

import java.util.Set;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.Port;
import org.apache.james.webadmin.authentication.AuthenticationFilter;
import org.apache.james.webadmin.mdc.LoggingRequestFilter;
import org.apache.james.webadmin.mdc.LoggingResponseFilter;
import org.apache.james.webadmin.mdc.MDCCleanupFilter;
import org.apache.james.webadmin.mdc.MDCFilter;
import org.apache.james.webadmin.metric.MetricPostFilter;
import org.apache.james.webadmin.metric.MetricPreFilter;
import org.apache.james.webadmin.routes.CORSRoute;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonExtractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import spark.Service;

public class WebAdminServer implements Configurable {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebAdminServer.class);
    public static final HierarchicalConfiguration NO_CONFIGURATION = null;
    public static final int DEFAULT_PORT = 8080;

    private final WebAdminConfiguration configuration;
    private final Set<Routes> routesList;
    private final Service service;
    private final AuthenticationFilter authenticationFilter;
    private final MetricFactory metricFactory;
    private Port port;

    // Spark do not allow to retrieve allocated port when using a random port. Thus we generate the port.
    @Inject
    protected WebAdminServer(WebAdminConfiguration configuration, Set<Routes> routesList, AuthenticationFilter authenticationFilter,
                           MetricFactory metricFactory) {
        this.configuration = configuration;
        this.routesList = routesList;
        this.authenticationFilter = authenticationFilter;
        this.metricFactory = metricFactory;
        this.service = Service.ignite();
    }

    @Override
    public void configure(HierarchicalConfiguration config) throws ConfigurationException {
        if (configuration.isEnabled()) {
            service.port(configuration.getPort().get().getValue());
            configureExceptionHanding();
            configureHTTPS();
            configureCORS();
            configureMetrics();
            service.before(authenticationFilter);
            service.before((request, response) -> response.type(Constants.JSON_CONTENT_TYPE));
            configureMDC();
            routesList.forEach(routes -> routes.define(service));
            service.awaitInitialization();
            port = new Port(service.port());
            LOGGER.info("Web admin server started");
        }
    }

    private void configureMDC() {
        service.before(new MDCFilter());
        service.before(new LoggingRequestFilter());
        service.after(new LoggingResponseFilter());
        service.after(new MDCCleanupFilter());
    }

    private void configureMetrics() {
        service.before(new MetricPreFilter(metricFactory));
        service.after(new MetricPostFilter());
    }

    private void configureHTTPS() {
        if (configuration.isTlsEnabled()) {
            TlsConfiguration tlsConfiguration = configuration.getTlsConfiguration();
            service.secure(tlsConfiguration.getKeystoreFilePath(),
                tlsConfiguration.getKeystorePassword(),
                tlsConfiguration.getTruststoreFilePath(),
                tlsConfiguration.getTruststorePassword());
            LOGGER.info("Web admin set up to use HTTPS");
        }
    }

    private void configureCORS() {
        if (configuration.isEnabled()) {
            service.before(new CORSFilter(configuration.getUrlCORSOrigin()));
            new CORSRoute().define(service);
            LOGGER.info("Web admin set up to enable CORS from {}", configuration.getUrlCORSOrigin());
        }
    }

    private void configureExceptionHanding() {
        service.notFound((req, res) -> ErrorResponder.builder()
            .statusCode(NOT_FOUND_404)
            .type(NOT_FOUND)
            .message(String.format("%s %s can not be found", req.requestMethod(), req.pathInfo()))
            .toResponse(res));

        service.internalServerError((req, res) -> ErrorResponder.builder()
            .statusCode(INTERNAL_SERVER_ERROR_500)
            .type(SERVER_ERROR)
            .message("WebAdmin encountered an unexpected internal error. Please check logs for more details.")
            .toResponse(res));

        service.exception(JsonExtractException.class, (ex, req, res) -> ErrorResponder.builder()
            .statusCode(BAD_REQUEST_400)
            .type(INVALID_ARGUMENT)
            .message("JSON payload of the request is not valid")
            .cause(ex)
            .toResponse(res));
    }

    @PreDestroy
    public void destroy() {
        if (configuration.isEnabled()) {
            service.stop();
            LOGGER.info("Web admin server stopped");
        }
    }

    public void await() {
        service.awaitInitialization();
    }

    public Port getPort() {
        Preconditions.checkState(port != null, "WebAdminServer should be configured.");
        return port;
    }
}
