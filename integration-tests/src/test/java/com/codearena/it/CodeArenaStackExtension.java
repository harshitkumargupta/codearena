package com.codearena.it;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Skips the class when Docker or the judge images are missing, otherwise starts the shared {@link CodeArenaStack}
 * once per run and injects it.
 */
class CodeArenaStackExtension implements BeforeAllCallback, ParameterResolver {

    @Override
    public void beforeAll(ExtensionContext context) {
        CodeArenaStack.get(context);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return parameter.getParameter().getType() == CodeArenaStack.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        return CodeArenaStack.get(context);
    }
}
