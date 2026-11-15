package com.braintrustappliedresearch.businessrules;

import java.io.IOException;
import java.util.Arrays;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * Spring Boot auto-configuration that registers the DMN rules engine and its REST endpoint.
 *
 * <p>It is active in servlet web applications and contributes these beans, each of which backs off if the
 * application defines its own:
 * <ul>
 *   <li>a {@link RulesEngine}, built from every DMN model found at
 *       {@link BusinessRulesProperties#rulesLocation() spring-boot-business-rules.rules-location}</li>
 *   <li>a {@link RulesController}, served at {@link BusinessRulesProperties#path() spring-boot-business-rules.path}</li>
 *   <li>a {@link RulesInstructionsController} (the {@code GET} help route), <strong>only</strong> when
 *       {@link BusinessRulesProperties#instructionsEnabled() spring-boot-business-rules.instructions-enabled}
 *       is {@code true}; it is off by default. That property also decides how much a response reveals about the rules
 *       (see {@link RulesController})</li>
 * </ul>
 *
 * <p>Turn the whole thing off with {@code spring-boot-business-rules.enabled=false}.
 *
 * <p><strong>The endpoint is unauthenticated.</strong> The library adds no authentication, so protect it in the host
 * application. When Spring Security is not on the classpath, a warning is logged at startup (see
 * {@link UnprotectedEndpointWarning}).
 *
 * @see BusinessRulesProperties
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBooleanProperty(name = BusinessRulesProperties.PREFIX + ".enabled", matchIfMissing = true)
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
     * @return the rules engine, loaded with every matching model
     * @throws IOException if the resource pattern cannot be resolved
     */
    @Bean
    @ConditionalOnMissingBean
    RulesEngine rulesEngine(BusinessRulesProperties properties, ResourcePatternResolver resolver) throws IOException {
        return new RulesEngine(Arrays.asList(resolver.getResources(properties.rulesLocation())));
    }

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
        return new RulesController(engine, properties.instructionsEnabled());
    }

    /**
     * Exposes the opt-in {@code GET} help route. Absent unless
     * {@code spring-boot-business-rules.instructions-enabled=true}.
     *
     * @param engine     the rules engine whose models are described
     * @param properties the bound {@code spring-boot-business-rules.*} configuration, reported by the route
     * @return the instructions controller, mapped at the same path as the rules controller
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBooleanProperty(name = BusinessRulesProperties.PREFIX + ".instructions-enabled")
    RulesInstructionsController rulesInstructionsController(RulesEngine engine, BusinessRulesProperties properties) {
        return new RulesInstructionsController(engine, properties);
    }

    /**
     * Registers the startup warning when Spring Security is absent. With Spring Security on the classpath, Spring Boot
     * secures every endpoint by default, so nothing is registered.
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
