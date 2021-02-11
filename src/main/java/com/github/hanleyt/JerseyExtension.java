package com.github.hanleyt;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.test.DeploymentContext;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestContainerFactory;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Application;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class JerseyExtension implements BeforeAllCallback, AfterAllCallback,
        BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final Collection<Class<?>> INJECTABLE_PARAMETER_TYPES = Arrays.asList(Client.class, WebTarget.class, URI.class);

    private final Function<ExtensionContext, TestContainerFactory> testContainerFactoryProvider;
    private final Function<ExtensionContext, DeploymentContext> deploymentContextProvider;
    private final BiFunction<ExtensionContext, ClientConfig, ClientConfig> configProvider;
    private final boolean singleContainer;

    private JerseyExtension() {
        throw new IllegalStateException("JerseyExtension must be registered programmatically");
    }

    /**
     * @deprecated Use {@link Builder} instead
     */
    @Deprecated
    public JerseyExtension(Supplier<Application> applicationSupplier) {
        this((unused) -> applicationSupplier.get(), null);
    }

    /**
     * @deprecated Use {@link Builder} instead
     */
    @Deprecated
    public JerseyExtension(Supplier<Application> applicationSupplier,
                           BiFunction<ExtensionContext, ClientConfig, ClientConfig> configProvider) {
        this((unused) -> applicationSupplier.get(), configProvider);
    }

    /**
     * @deprecated Use {@link Builder} instead
     */
    @Deprecated
    public JerseyExtension(Function<ExtensionContext, Application> applicationProvider) {
        this(applicationProvider, null);
    }

    /**
     * @deprecated Use {@link Builder} instead
     */
    @Deprecated
    public JerseyExtension(Function<ExtensionContext, Application> applicationProvider,
                           BiFunction<ExtensionContext, ClientConfig, ClientConfig> configProvider) {
        this(null, (context) -> DeploymentContext.builder(applicationProvider.apply(context)).build(), configProvider);
    }

    /**
     * @deprecated Use {@link Builder} instead
     */
    @Deprecated
    public JerseyExtension(Function<ExtensionContext, TestContainerFactory> testContainerFactoryProvider,
                           Function<ExtensionContext, DeploymentContext> deploymentContextProvider,
                           BiFunction<ExtensionContext, ClientConfig, ClientConfig> configProvider) {
        this(testContainerFactoryProvider, deploymentContextProvider, configProvider, false);
    }

    private JerseyExtension(Function<ExtensionContext, TestContainerFactory> testContainerFactoryProvider,
                           Function<ExtensionContext, DeploymentContext> deploymentContextProvider,
                           BiFunction<ExtensionContext, ClientConfig, ClientConfig> configProvider,
                           boolean singleContainer) {
        this.testContainerFactoryProvider = testContainerFactoryProvider;
        this.deploymentContextProvider = deploymentContextProvider;
        this.configProvider = configProvider;
        this.singleContainer = singleContainer;
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (singleContainer) {
            initJerseyTest(context);
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        if (singleContainer) {
            ExtensionContext.Store store = getStore(context);
            store.remove(JerseyTest.class, JerseyTest.class).tearDown();
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        JerseyTest jerseyTest;
        if (singleContainer) {
            jerseyTest = getStore(context).get(JerseyTest.class, JerseyTest.class);
        } else {
            jerseyTest = initJerseyTest(context);
        }
        getStore(context).put(Client.class, jerseyTest.client());
        getStore(context).put(WebTarget.class, jerseyTest.target());
        getStore(context).put(URI.class, jerseyTest.target().getUri());
    }

    private JerseyTest initJerseyTest(ExtensionContext context) throws Exception {
        JerseyTest jerseyTest = new JerseyTest() {
            @Override
            protected DeploymentContext configureDeployment() {
                return deploymentContextProvider.apply(context);
            }

            @Override
            protected TestContainerFactory getTestContainerFactory() throws TestContainerException {
                if (testContainerFactoryProvider != null) {
                    return testContainerFactoryProvider.apply(context);
                }
                return super.getTestContainerFactory();
            }

            @Override
            protected void configureClient(ClientConfig config) {
                if (configProvider != null) {
                    config = configProvider.apply(context, config);
                }
                super.configureClient(config);
            }
        };
        jerseyTest.setUp();
        getStore(context).put(JerseyTest.class, jerseyTest);
        return jerseyTest;
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        ExtensionContext.Store store = getStore(context);
        if (!singleContainer) {
            store.remove(JerseyTest.class, JerseyTest.class).tearDown();
        }
        INJECTABLE_PARAMETER_TYPES.forEach(store::remove);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> parameterType = parameterContext.getParameter().getType();
        return INJECTABLE_PARAMETER_TYPES.contains(parameterType);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> parameterType = parameterContext.getParameter().getType();
        return getStore(extensionContext).get(parameterType, parameterType);
    }

    public static ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getStore(ExtensionContext.Namespace.GLOBAL);
    }

    public static class Builder {
        private Function<ExtensionContext, TestContainerFactory> testContainerFactoryProvider;
        private Function<ExtensionContext, DeploymentContext> deploymentContextProvider;
        private BiFunction<ExtensionContext, ClientConfig, ClientConfig> configProvider;
        private boolean singleContainer;

        public Builder() {
        }

        public Builder application(Supplier<Application> applicationSupplier) {
            return application((unused) -> applicationSupplier.get());
        }

        public Builder application(Function<ExtensionContext, Application> applicationProvider) {
            return deploymentContext((context) -> DeploymentContext.builder(applicationProvider.apply(context)).build());
        }

        public Builder deploymentContext(Function<ExtensionContext, DeploymentContext> deploymentContextProvider) {
            this.deploymentContextProvider = deploymentContextProvider;
            return this;
        }

        public Builder testContainerFactory(Function<ExtensionContext, TestContainerFactory> testContainerFactoryProvider) {
            this.testContainerFactoryProvider = testContainerFactoryProvider;
            return this;
        }

        public Builder clientConfig(BiFunction<ExtensionContext, ClientConfig, ClientConfig> configProvider) {
            this.configProvider = configProvider;
            return this;
        }

        public Builder singleContainer(boolean singleContainer) {
            this.singleContainer = singleContainer;
            return this;
        }

        public JerseyExtension build() {
            if (deploymentContextProvider == null) {
                throw new IllegalStateException("DeploymentContext not set. Use one of application or deploymentContext methods.");
            }
            return new JerseyExtension(testContainerFactoryProvider, deploymentContextProvider, configProvider, singleContainer);
        }
    }

}
