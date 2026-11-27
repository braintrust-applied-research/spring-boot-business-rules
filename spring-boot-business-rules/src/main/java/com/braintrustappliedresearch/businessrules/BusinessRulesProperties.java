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
 * @param web           the web side: the REST endpoint and its help route. The rules engine is registered either way,
 *                      so rules can still be evaluated from Java
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
        @DefaultValue Web web,
        @DefaultValue("/rules") String path,
        @DefaultValue("classpath*:rules/**/*.dmn") String rulesLocation,
        @DefaultValue("false") boolean instructionsEnabled,
        @DefaultValue Limits limits) {

    /**
     * The web side of the library: the REST endpoint, its response-headers filter and its help route. Leaving it off
     * (the default) does not stop the rules engine, which application code can still call directly.
     *
     * @param enabled whether the REST endpoint is registered in a servlet web application. Off by default: the web
     *                side is opt-in, so set it to true to serve the endpoint. Either way, evaluating rules from Java
     *                keeps working
     */
    public record Web(@DefaultValue("false") boolean enabled) {
    }

    /** The property prefix: {@value}. */
    public static final String PREFIX = "spring-boot-business-rules";

    /**
     * Caps on a request. A request over a cap is rejected with {@code 400} and a message that names the rule without
     * repeating the offending content ({@code 413} for the body size). These bound what the endpoint will read, parse
     * into the engine and echo back. The body is read with a cap, so an oversized one is refused without being
     * buffered; a body-size limit in front of the application is still worthwhile.
     *
     * @param maxInputs      most inputs (JSON keys other than evaluate and model) one request may carry
     * @param maxNameLength  longest allowed evaluate value, model value, input name, or name inside an input value, in
     *                       characters
     * @param maxValueLength longest allowed single text value anywhere in the inputs, including inside lists and
     *                       objects, in characters
     * @param maxBodyBytes   largest request body, in bytes; a larger one is rejected with 413 before it is parsed
     */
    public record Limits(
            @DefaultValue("500") int maxInputs,
            @DefaultValue("256") int maxNameLength,
            @DefaultValue("10000") int maxValueLength,
            @DefaultValue("1048576") int maxBodyBytes) {

        /**
         * The default limits, the same values the properties bind to when nothing is configured.
         *
         * @return the defaults
         */
        public static Limits defaults() {
            return new Limits(500, 256, 10_000, 1_048_576);
        }
    }
}
