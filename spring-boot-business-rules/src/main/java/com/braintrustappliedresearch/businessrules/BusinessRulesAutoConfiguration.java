package com.braintrustappliedresearch.businessrules;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * Spring Boot auto-configuration that registers the DMN rules engine and, separately, its REST endpoint.
 *
 * <p>The engine is always available. It is registered in every Spring Boot application, web or not, and whether or
 * not the endpoint is enabled, so application code can inject it and evaluate rules directly (see {@link RulesEngine}):
 * <ul>
 *   <li>a {@link RulesEngine}, built from every DMN model found at
 *       {@link BusinessRulesProperties#rulesLocation() spring-boot-business-rules.rules-location}</li>
 * </ul>
 *
 * <p>The REST endpoint is the gated part. These beans are registered only in servlet web applications, and only
 * only when {@link BusinessRulesProperties.Web#enabled() spring-boot-business-rules.web.enabled} is {@code true}.
 * It is off by default, so the web side is opt-in:
 * <ul>
 *   <li>a {@link RulesController}, served at {@link BusinessRulesProperties#path() spring-boot-business-rules.path}</li>
 *   <li>a {@link RulesInstructionsController} (the {@code GET} help route), <strong>only</strong> when
 *       {@link BusinessRulesProperties#instructionsEnabled() spring-boot-business-rules.instructions-enabled}
 *       is {@code true}; it is off by default. That property also decides how much a response reveals about the rules
 *       (see {@link RulesController})</li>
 * </ul>
 *
 * <p>Each bean backs off if the application defines its own.
 *
 * <p>Turn the endpoint on with {@code spring-boot-business-rules.web.enabled=true}. Leaving it off keeps the HTTP gate
 * closed; in-process evaluation through {@link RulesEngine} keeps working.
 *
 * <p><strong>The endpoint is unauthenticated.</strong> The library adds no authentication, so protect it in the host
 * application. When Spring Security is not on the classpath, a warning is logged at startup (see
 * {@link UnprotectedEndpointWarning}).
 *
 * @see BusinessRulesProperties
 */
@AutoConfiguration
@EnableConfigurationProperties(BusinessRulesProperties.class)
public class BusinessRulesAutoConfiguration {

    /** Creates the auto-configuration. Instantiated by Spring Boot; not intended to be called directly. */
    public BusinessRulesAutoConfiguration() {
        // Nothing to initialise: all state lives in the beans declared below.
    }

    /**
     * Builds the rules engine from the configured DMN models.
     *
     * @param properties the bound {@code spring-boot-business-rules.*} configuration
     * @param resolver   resolves the {@code rules-location} pattern to resources
     * @param environment checked for the renamed {@code spring-boot-business-rules.enabled} property
     * @return the rules engine, loaded with every matching model
     * @throws IOException if the resource pattern cannot be resolved
     */
    @Bean
    @ConditionalOnMissingBean
    RulesEngine rulesEngine(BusinessRulesProperties properties, ResourcePatternResolver resolver,
            Environment environment) throws IOException {
        // The old switch used to turn everything off. Ignoring it silently could leave an endpoint exposed that its
        // owner believes is closed, so refuse to start instead.
        if (environment.containsProperty(BusinessRulesProperties.PREFIX + ".enabled")) {
            throw new IllegalStateException("The property " + BusinessRulesProperties.PREFIX + ".enabled was renamed to "
                    + BusinessRulesProperties.PREFIX + ".web.enabled, and now closes only the REST endpoint. Rename it.");
        }
        return new RulesEngine(Arrays.asList(resolver.getResources(properties.rulesLocation())));
    }

    /** {@code spring.mvc.servlet.path} without a trailing slash; empty when the servlet is mapped at the root. */
    static String servletPrefix(Environment environment) {
        String path = environment.getProperty("spring.mvc.servlet.path", "");
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path.isEmpty() || path.startsWith("/") ? path : "/" + path;
    }

    /** The gated REST endpoint: servlet web applications only, and only when the endpoint has been opted into. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBooleanProperty(name = BusinessRulesProperties.PREFIX + ".web.enabled")
    static class RestEndpointConfiguration {

        /**
         * Exposes the rules engine over REST.
         *
         * @param engine     the rules engine to evaluate decisions with
         * @param properties the bound {@code spring-boot-business-rules.*} configuration
         * @return the controller mapped at {@code spring-boot-business-rules.path}
         */
        @Bean
        @ConditionalOnMissingBean
        RulesController rulesController(RulesEngine engine, BusinessRulesProperties properties) {
            return new RulesController(engine, properties.instructionsEnabled(), properties.limits());
        }

        /**
         * Adds {@code nosniff} and {@code no-store} headers to every response from the rules endpoint.
         *
         * @param properties  the bound {@code spring-boot-business-rules.*} configuration (for the endpoint path)
         * @param environment read for {@code spring.mvc.servlet.path}, so the filter matches the real path
         * @return the registration, which maps the filter to exactly the endpoint path
         */
        @Bean
        @ConditionalOnMissingBean(name = "rulesResponseHeadersFilter")
        FilterRegistrationBean<ResponseHeadersFilter> rulesResponseHeadersFilter(BusinessRulesProperties properties,
                Environment environment) {
            String path = properties.path().startsWith("/") ? properties.path() : "/" + properties.path();
            FilterRegistrationBean<ResponseHeadersFilter> registration = new FilterRegistrationBean<>(
                    new ResponseHeadersFilter());
            registration.setUrlPatterns(List.of(servletPrefix(environment) + path));
            return registration;
        }

        /**
         * Exposes the opt-in {@code GET} help route. Absent unless
         * {@code spring-boot-business-rules.instructions-enabled=true}.
         *
         * @param engine      the rules engine whose models are described
         * @param properties  the bound {@code spring-boot-business-rules.*} configuration, reported by the route
         * @param environment read for {@code spring.mvc.servlet.path}, so the usage text shows the real path
         * @return the instructions controller, mapped at the same path as the rules controller
         */
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBooleanProperty(name = BusinessRulesProperties.PREFIX + ".instructions-enabled")
        RulesInstructionsController rulesInstructionsController(RulesEngine engine, BusinessRulesProperties properties,
                Environment environment) {
            return new RulesInstructionsController(engine, properties, servletPrefix(environment));
        }

        /**
         * Registers the startup warning when Spring Security is absent. With Spring Security on the classpath, Spring
         * Boot secures every endpoint by default, so nothing is registered.
         */
        @Configuration(proxyBeanMethods = false)
        @ConditionalOnMissingClass("org.springframework.security.web.SecurityFilterChain")
        static class UnprotectedEndpointWarningConfiguration {

            @Bean
            UnprotectedEndpointWarning unprotectedEndpointWarning(BusinessRulesProperties properties) {
                return new UnprotectedEndpointWarning(properties.path());
            }
        }
    }
}
