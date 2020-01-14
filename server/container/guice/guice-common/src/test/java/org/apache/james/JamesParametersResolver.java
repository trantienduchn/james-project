package org.apache.james;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

class JamesParametersResolver implements ParameterResolver {

    static class Builder {
        @FunctionalInterface
        interface RequireJamesServer {
            RequireRegistrableExtension jamesServer(GuiceJamesServer jamesServer);
        }

        @FunctionalInterface
        interface RequireRegistrableExtension {
            ReadyToBuild registrableExtension(RegistrableExtension registrableExtension);
        }

        static class ReadyToBuild {
            private final GuiceJamesServer jamesServer;
            private final RegistrableExtension registrableExtension;

            ReadyToBuild(GuiceJamesServer jamesServer, RegistrableExtension registrableExtension) {
                this.jamesServer = jamesServer;
                this.registrableExtension = registrableExtension;
            }

            JamesParametersResolver build() {
                return new JamesParametersResolver(jamesServer, registrableExtension);
            }
        }
    }

    static Builder.RequireJamesServer builder() {
        return jamesServer -> registrableExtension -> new Builder.ReadyToBuild(jamesServer, registrableExtension);
    }

    private final GuiceJamesServer jamesServer;
    private final RegistrableExtension registrableExtension;

    private JamesParametersResolver(GuiceJamesServer jamesServer, RegistrableExtension registrableExtension) {
        this.jamesServer = jamesServer;
        this.registrableExtension = registrableExtension;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == GuiceJamesServer.class)
            || registrableExtension.supportsParameter(parameterContext, extensionContext);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        if (registrableExtension.supportsParameter(parameterContext, extensionContext)) {
            return registrableExtension.resolveParameter(parameterContext, extensionContext);
        }
        return jamesServer;
    }
}
