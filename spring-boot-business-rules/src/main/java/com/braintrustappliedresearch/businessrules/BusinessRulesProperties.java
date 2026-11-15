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
 * @param limits        caps on the size of a request, so one caller cannot make the endpoint do or echo unbounded work
 */
@ConfigurationProperties(BusinessRulesProperties.PREFIX)
public record BusinessRulesProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("/rules") String path,
        @DefaultValue("classpath*:rules/**/*.dmn") String rulesLocation,
        @DefaultValue("false") boolean instructionsEnabled,
        @DefaultValue Limits limits) {

    /** The property prefix: {@value}. */
    public static final String PREFIX = "spring-boot-business-rules";

    /**
     * Caps on a request. A request over a cap is rejected with {@code 400} and a message that names the rule without
     * repeating the offending content. These bound what the endpoint will parse into the engine and echo back; the
     * size of the request body as a whole is not capped here, because Spring reads it before the controller runs, so
     * put a body-size limit in front of the application too.
     *
     * @param maxInputs      most inputs (JSON keys other than evaluate and model) one request may carry
     * @param maxNameLength  longest allowed evaluate value, model value, input name, or name inside an input value, in
     *                       characters
     * @param maxValueLength longest allowed single text value anywhere in the inputs, including inside lists and
     *                       objects, in characters
     */
    public record Limits(
            @DefaultValue("500") int maxInputs,
            @DefaultValue("256") int maxNameLength,
            @DefaultValue("10000") int maxValueLength) {

        /**
         * The default limits, the same values the properties bind to when nothing is configured.
         *
         * @return the defaults
         */
        public static Limits defaults() {
            return new Limits(500, 256, 10_000);
        }
    }
}
