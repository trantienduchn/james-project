package org.apache.james;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

class JamesParametersResolver implements ParameterResolver {

    private final GuiceJamesServer jamesServer;
    private final RegistrableExtension registrableExtension;

    JamesParametersResolver(GuiceJamesServer jamesServer, RegistrableExtension registrableExtension) {
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
