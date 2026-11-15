package com.braintrustappliedresearch.businessrules;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the business rules endpoint, bound from the {@code spring-boot-business-rules.*} properties.
 *
 * <p>The parameter descriptions below are plain text on purpose: Spring copies them verbatim into the generated
 * configuration metadata that IDEs show on hover, where Javadoc markup would appear as literal characters.
 *
 * <p>For example, with {@code server.servlet.context-path=/api} and
 * {@code spring-boot-business-rules.path=/business/rules}, the endpoint is served at {@code /api/business/rules}.
 *
 * @param enabled       whether the rules endpoint is registered
 * @param path          path of the rules endpoint, appended to the application's server context path
 *                      and any Spring MVC servlet path
 * @param rulesLocation Spring resource pattern used to find DMN (.dmn) models
 * @param instructionsEnabled whether callers may learn about the rules' structure. It serves the GET help route (usage,
 *                      the effective settings, the loaded models with their inputs and decisions) and enables
 *                      evaluate=all and detailed responses. Off by default; when off, only POST exists, responses name
 *                      only the requested decision, DMN messages are replaced by a generic one, and evaluate=all is
 *                      unavailable
 */
@ConfigurationProperties(BusinessRulesProperties.PREFIX)
public record BusinessRulesProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("/rules") String path,
        @DefaultValue("classpath*:rules/**/*.dmn") String rulesLocation,
        @DefaultValue("false") boolean instructionsEnabled) {

    /** The property prefix: {@value}. */
    public static final String PREFIX = "spring-boot-business-rules";
}
