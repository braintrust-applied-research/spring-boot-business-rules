package com.braintrustappliedresearch.businessrules;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Optional help route: {@code GET <path>}, mapped at the same path as {@link RulesController}.
 *
 * <p>It describes how to call the endpoint, reports the effective {@code spring-boot-business-rules.*} settings, and
 * lists every loaded model with its inputs and decisions. That is useful while developing but reveals the shape of
 * your rules and where they are loaded from, so this bean is registered only when
 * {@code spring-boot-business-rules.instructions-enabled=true}. By default it does not exist, and a {@code GET}
 * on the path is answered {@code 405 Method Not Allowed} (only {@code POST} is mapped).
 */
@RestController
@RequestMapping("${" + BusinessRulesProperties.PREFIX + ".path:/rules}")
public class RulesInstructionsController {

    private final RulesEngine engine;
    private final BusinessRulesProperties properties;

    /**
     * Creates the controller.
     *
     * @param engine     the rules engine whose models are described
     * @param properties the effective configuration to report
     */
    public RulesInstructionsController(RulesEngine engine, BusinessRulesProperties properties) {
        this.engine = engine;
        this.properties = properties;
    }

    /**
     * Describes how to use the endpoint.
     *
     * <p>Handles {@code GET <path>}.
     *
     * @return usage instructions, the effective configuration (keyed by full property name), the loaded models with
     *         their inputs and decisions, and a {@code curl} example that points at the URL of this request
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> instructions() {
        String evaluate = RulesController.EVALUATE;
        String model = RulesController.MODEL;
        String url = ServletUriComponentsBuilder.fromCurrentRequest().build().toUriString();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("description", "DMN business rules. POST a JSON body of rule inputs plus an '" + evaluate
                + "' key naming the decision to evaluate.");
        body.put("usage", Map.of(
                "GET " + url, "this help",
                "POST " + url, "evaluate a decision: every key except '" + evaluate + "' and '" + model
                        + "' is passed to the DMN model as an input; '" + evaluate + "' names the decision; "
                        + "optional '" + model + "' disambiguates when decision names collide across models; use '"
                        + evaluate + "': '" + RulesController.ALL + "' to run every decision in a model at once"));
        body.put("configuration", configuration());
        body.put("models", engine.describe());
        body.put("example", Map.of(
                "curl", "curl -X POST " + url + " -H 'Content-Type: application/json'"
                        + " -d '{\"evaluate\":\"<decision>\", \"<input>\":\"<value>\"}'"));
        return body;
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
