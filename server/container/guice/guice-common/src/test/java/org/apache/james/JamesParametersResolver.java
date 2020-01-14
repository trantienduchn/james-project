package org.apache.james;

import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import com.google.common.base.Preconditions;

class JamesParametersResolver implements ParameterResolver {

    static class TestParametersRegistration {
        private final Map<Class<?>, Function<GuiceJamesServer, ?>> registration;

        TestParametersRegistration(Map<Class<?>, Function<GuiceJamesServer, ?>> registration) {
            Preconditions.checkNotNull(registration);
            this.registration = registration;
        }

        boolean supportsParameterType(Class<?> paramClass) {
            return registration.containsKey(paramClass);
        }

        Object resolveParameter(Class<?> paramClass, GuiceJamesServer jamesServer) {
            return registration.get(paramClass)
                .apply(jamesServer);
        }
    }

    static class Builder {
        @FunctionalInterface
        interface RequireJamesServer {
            RequireRegistrableExtension jamesServer(GuiceJamesServer jamesServer);
        }

        @FunctionalInterface
        interface RequireRegistrableExtension {
            RequireParamRegistration registrableExtension(RegistrableExtension registrableExtension);
        }

        @FunctionalInterface
        interface RequireParamRegistration {
            ReadyToBuild paramsRegistration(TestParametersRegistration paramsRegistration);
        }

        static class ReadyToBuild {
            private final GuiceJamesServer jamesServer;
            private final RegistrableExtension registrableExtension;
            private final TestParametersRegistration paramsRegistration;

            ReadyToBuild(GuiceJamesServer jamesServer, RegistrableExtension registrableExtension,
                         TestParametersRegistration paramsRegistration) {
                this.jamesServer = jamesServer;
                this.registrableExtension = registrableExtension;
                this.paramsRegistration = paramsRegistration;
            }

            JamesParametersResolver build() {
                return new JamesParametersResolver(jamesServer, registrableExtension, paramsRegistration);
            }
        }
    }

    static Builder.RequireJamesServer builder() {
        return jamesServer -> registrableExtension -> paramsRegistration ->
            new Builder.ReadyToBuild(jamesServer, registrableExtension, paramsRegistration);
    }

    private final GuiceJamesServer jamesServer;
    private final RegistrableExtension registrableExtension;
    private final TestParametersRegistration paramsRegistration;

    private JamesParametersResolver(GuiceJamesServer jamesServer, RegistrableExtension registrableExtension,
                                    TestParametersRegistration paramsRegistration) {
        this.jamesServer = jamesServer;
        this.registrableExtension = registrableExtension;
        this.paramsRegistration = paramsRegistration;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == GuiceJamesServer.class)
            || registrableExtension.supportsParameter(parameterContext, extensionContext)
            || paramsRegistration.supportsParameterType(parameterContext.getParameter().getType());
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        if (registrableExtension.supportsParameter(parameterContext, extensionContext)) {
            return registrableExtension.resolveParameter(parameterContext, extensionContext);
        } else if (paramsRegistration.supportsParameterType(parameterContext.getParameter().getType())) {
            return parameterContext.getParameter().getType()
                .cast(paramsRegistration.resolveParameter(parameterContext.getParameter().getType(), jamesServer));
        }

        return jamesServer;
    }
}
