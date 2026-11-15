package com.braintrustappliedresearch.businessrules;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Optional help route: {@code GET <path>}, mapped at the same path as {@link RulesController}.
 *
 * <p>It describes how to call the endpoint, reports the effective {@code spring-boot-business-rules.*} settings, and
 * lists every loaded model with its inputs and decisions. That is useful while developing but reveals the shape of
 * your rules and where they are loaded from, so this bean is registered only when
 * {@code spring-boot-business-rules.instructions-enabled=true}. By default it does not exist, and a {@code GET}
 * on the path is answered {@code 405 Method Not Allowed} (only {@code POST} is mapped).
 *
 * <p>The paths in the usage text are built from configuration only: the context path, the servlet prefix and the
 * configured endpoint path. Nothing from the request's own URL (its {@code Host} header, scheme, query or path
 * parameters) is repeated, so a forged request cannot plant text or a hostile address in the response.
 */
@RestController
@RequestMapping("${" + BusinessRulesProperties.PREFIX + ".path:/rules}")
public class RulesInstructionsController {

    private final RulesEngine engine;
    private final BusinessRulesProperties properties;
    private final String servletPath;

    /**
     * Creates the controller.
     *
     * @param engine      the rules engine whose models are described
     * @param properties  the effective configuration to report
     * @param servletPath the Spring MVC servlet prefix ({@code spring.mvc.servlet.path}) that sits between the context
     *                    path and the endpoint path, or an empty string if there is none
     */
    public RulesInstructionsController(RulesEngine engine, BusinessRulesProperties properties, String servletPath) {
        this.engine = engine;
        this.properties = properties;
        this.servletPath = servletPath;
    }

    /**
     * Describes how to use the endpoint.
     *
     * <p>Handles {@code GET <path>}.
     *
     * @param request the current request, used only for its context path
     * @return usage instructions, the effective configuration (keyed by full property name), the loaded models with
     *         their inputs and decisions, and a {@code curl} example with a {@code <base-url>} placeholder
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> instructions(HttpServletRequest request) {
        String evaluate = RulesController.EVALUATE;
        String model = RulesController.MODEL;
        String path = endpointPath(request);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("description", "DMN business rules. POST a JSON body of rule inputs plus an '" + evaluate
                + "' key naming the decision to evaluate.");
        body.put("usage", Map.of(
                "GET " + path, "this help",
                "POST " + path, "evaluate a decision: every key except '" + evaluate + "' and '" + model
                        + "' is passed to the DMN model as an input; '" + evaluate + "' names the decision; "
                        + "optional '" + model + "' disambiguates when decision names collide across models; use '"
                        + evaluate + "': '" + RulesController.ALL + "' to run every decision in a model at once"));
        body.put("configuration", configuration());
        body.put("models", engine.describe());
        body.put("example", Map.of(
                "curl", "curl -X POST <base-url>" + path + " -H 'Content-Type: application/json'"
                        + " -d '{\"evaluate\":\"<decision>\", \"<input>\":\"<value>\"}'"));
        return body;
    }

    /** Context path + servlet prefix + configured path: configuration only, never the request's own URL. */
    private String endpointPath(HttpServletRequest request) {
        String configured = properties.path().startsWith("/") ? properties.path() : "/" + properties.path();
        return request.getContextPath() + servletPath + configured;
    }

    /** The effective settings, keyed by the property name that sets each one. */
    private Map<String, Object> configuration() {
        String prefix = BusinessRulesProperties.PREFIX + ".";
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put(prefix + "enabled", properties.enabled());
        configuration.put(prefix + "path", properties.path());
        configuration.put(prefix + "rules-location", properties.rulesLocation());
        configuration.put(prefix + "instructions-enabled", properties.instructionsEnabled());
        configuration.put(prefix + "limits.max-inputs", properties.limits().maxInputs());
        configuration.put(prefix + "limits.max-name-length", properties.limits().maxNameLength());
        configuration.put(prefix + "limits.max-value-length", properties.limits().maxValueLength());
        return configuration;
    }
}
