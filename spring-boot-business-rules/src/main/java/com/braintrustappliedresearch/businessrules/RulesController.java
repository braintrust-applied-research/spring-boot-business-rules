package com.braintrustappliedresearch.businessrules;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.kie.dmn.api.core.DMNModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rules endpoint ({@code POST}). Mapped at {@code spring-boot-business-rules.path} (default {@code /rules}), which
 * Spring MVC resolves relative to the server context path and servlet path.
 *
 * <p>The {@code GET} help route is a separate, opt-in bean: see {@link RulesInstructionsController}.
 *
 * <p>Whether callers may learn about the rules' structure is decided by
 * {@code spring-boot-business-rules.instructions-enabled}. When it is off, responses are <em>quiet</em>: they name only
 * the decision that was asked for, leave out the model name, replace DMN's messages (which name inputs, decisions and
 * DMN ids) with one generic message, and treat {@code "evaluate": "all"} as an unknown decision. Otherwise a caller
 * could still map the model through ordinary responses even though the help route is off.
 */
@RestController
@RequestMapping("${" + BusinessRulesProperties.PREFIX + ".path:/rules}")
public class RulesController {

    static final String EVALUATE = "evaluate";
    static final String MODEL = "model";
    /** Reserved {@code evaluate} value that runs every decision in a model. Only available when instructions are on. */
    static final String ALL = "all";
    /** Shown instead of DMN's own messages when instructions are off, so the response names nothing in the model. */
    static final String QUIET_MESSAGE = "The evaluation reported a problem with the supplied inputs. Details are not shown "
            + "because " + BusinessRulesProperties.PREFIX + ".instructions-enabled is off.";

    /** Most unknown input names listed in a 400 body; the rest are counted. */
    private static final int MAX_LISTED = 10;

    private final RulesEngine engine;
    private final boolean instructionsEnabled;
    private final RequestLimits limits;

    /**
     * Creates the controller.
     *
     * @param engine              the rules engine used to evaluate decisions
     * @param instructionsEnabled whether callers may learn about the rules' structure. Pass the value of
     *                            {@code spring-boot-business-rules.instructions-enabled}. When {@code false} responses
     *                            are quiet (see the class description) and {@code "evaluate": "all"} is unavailable;
     *                            when {@code true} responses include the model name, every evaluated decision and DMN's
     *                            messages, and error responses list the loaded models
     */
    public RulesController(RulesEngine engine, boolean instructionsEnabled) {
        this(engine, instructionsEnabled, BusinessRulesProperties.Limits.defaults());
    }

    /**
     * Creates the controller with explicit request limits.
     *
     * @param engine              the rules engine used to evaluate decisions
     * @param instructionsEnabled see {@link #RulesController(RulesEngine, boolean)}
     * @param limits              caps on the size of a request (see {@link BusinessRulesProperties.Limits}); a request
     *                            over a cap is rejected with {@code 400}
     */
    public RulesController(RulesEngine engine, boolean instructionsEnabled, BusinessRulesProperties.Limits limits) {
        this.engine = engine;
        this.instructionsEnabled = instructionsEnabled;
        this.limits = new RequestLimits(limits);
    }

    /**
     * Evaluates a decision.
     *
     * <p>Handles {@code POST <path>}. Every key of the JSON body except {@code evaluate} and {@code model} must be a
     * declared input of the DMN model and is passed to it as that input; any other key is rejected (see below).
     * {@code evaluate} names the decision to run, and the decisions it depends on are evaluated too. The optional
     * {@code model} key picks a model when decision names collide across models.
     *
     * <p>The reserved value {@code "evaluate": "all"} runs <em>every</em> decision in a model and returns them all,
     * so callers do not have to request each decision one by one. If more than one model is loaded, name the model
     * with the {@code model} key. It is only available when instructions are enabled, because it exists to list the
     * model's decisions; otherwise it is an unknown decision ({@code 404}). A model that defines a decision actually
     * called {@code all} keeps that meaning: the real decision is evaluated and the shortcut does not apply.
     *
     * <p>Responses:
     * <ul>
     *   <li>{@code 200}: the decision result. With instructions enabled the body also names the model, lists every
     *       decision evaluated, and carries any DMN messages; with instructions off it names only the requested
     *       decision and carries at most one generic message</li>
     *   <li>{@code 400}: the request is over a limit (see {@link BusinessRulesProperties.Limits}) or {@code model}
     *       is not a string; the {@code evaluate} key is missing or not a non-blank string, {@code "all"} was
     *       requested without a {@code model} while several models are loaded, or the body contains a key that is not
     *       a declared input of the model. The last rule matters for security: values supplied under a decision's name
     *       would otherwise replace that decision's result and bypass the rule</li>
     *   <li>{@code 404}: no loaded model has a decision with that name, or the named {@code model} does not exist
     *       (the body lists the available models only if instructions are enabled)</li>
     *   <li>{@code 422}: nothing was decided. DMN reported an error or a warning (a required input was not supplied,
     *       or an input had the wrong type), or a decision produced no result ({@code null}). The body still carries
     *       whatever was computed. A {@code null} result is never a successful answer here (the message is generic when
     *       instructions are off)</li>
     * </ul>
     *
     * @param request the JSON body: rule inputs plus the {@code evaluate} key
     * @return the response body and status described above
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> evaluate(@RequestBody Map<String, Object> request) {
        Map<String, Object> inputs = new LinkedHashMap<>(request);
        Object decision = inputs.remove(EVALUATE);
        Object model = inputs.remove(MODEL);
        if (!(decision instanceof String decisionName) || decisionName.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "Missing required string key '" + EVALUATE + "'", null);
        }
        String tooLarge = limits.violation(decisionName, model, inputs);
        if (tooLarge != null) {
            return error(HttpStatus.BAD_REQUEST, tooLarge, null);
        }
        String modelName = (String) model;   // violation() guaranteed it is a string or absent
        DMNModel target = engine.findModel(modelName, decisionName).orElse(null);
        if (target == null && ALL.equals(decisionName) && instructionsEnabled) {
            return evaluateAll(modelName, inputs);
        }
        if (target == null) {
            return error(HttpStatus.NOT_FOUND, "No decision named '" + RequestLimits.shorten(decisionName) + "'",
                    modelsForError());
        }
        List<String> unknown = engine.unknownInputs(target, inputs);
        if (!unknown.isEmpty()) {
            return unknownInputs(unknown);
        }
        return respond(decisionName, engine.evaluate(target, decisionName, inputs));
    }

    private ResponseEntity<Map<String, Object>> evaluateAll(String modelName, Map<String, Object> inputs) {
        List<DMNModel> candidates = engine.findModels(modelName);
        if (candidates.isEmpty()) {
            String message = modelName == null ? "No models are loaded"
                    : "No model named '" + RequestLimits.shorten(modelName) + "'";
            return error(HttpStatus.NOT_FOUND, message, modelsForError());
        }
        if (candidates.size() > 1) {
            return error(HttpStatus.BAD_REQUEST, "Several models are loaded; add a '" + MODEL
                    + "' key to say which one to evaluate with '" + ALL + "'", modelsForError());
        }
        DMNModel target = candidates.get(0);
        List<String> unknown = engine.unknownInputs(target, inputs);
        if (!unknown.isEmpty()) {
            return unknownInputs(unknown);
        }
        return respond(ALL, engine.evaluateAll(target, inputs));
    }

    private ResponseEntity<Map<String, Object>> unknownInputs(List<String> unknown) {
        // Cap the list: every key of a large request can be unknown, and each one is caller-supplied text
        int shown = Math.min(unknown.size(), MAX_LISTED);
        String names = String.join(", ", unknown.subList(0, shown).stream().map(RequestLimits::shorten).toList());
        if (unknown.size() > shown) {
            names += " and " + (unknown.size() - shown) + " more";
        }
        return error(HttpStatus.BAD_REQUEST, "Unknown input(s): " + names
                + ". Only the model's declared inputs may be supplied", modelsForError());
    }

    private ResponseEntity<Map<String, Object>> respond(String evaluated, RulesEngine.Evaluation evaluation) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(EVALUATE, evaluated);
        if (instructionsEnabled) {
            body.put(MODEL, evaluation.model());
            body.put("result", evaluation.result());
            body.put("decisions", evaluation.decisions());
            body.put("messages", evaluation.messages());
        } else {
            // Quiet: say nothing about the model beyond what the caller asked for. singletonMap allows a null result.
            body.put("result", evaluation.result());
            body.put("decisions", Collections.singletonMap(evaluated, evaluation.result()));
            body.put("messages", evaluation.messages().isEmpty() ? List.of() : List.of(QUIET_MESSAGE));
        }
        return ResponseEntity.status(evaluation.failed() ? HttpStatus.UNPROCESSABLE_CONTENT : HttpStatus.OK)
                .body(body);
    }

    /** The loaded models, or {@code null} when the instructions route is off and models must not be revealed. */
    private List<Map<String, Object>> modelsForError() {
        return instructionsEnabled ? engine.describe() : null;
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message, List<?> models) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        if (models != null) {
            body.put("models", models);
        }
        return ResponseEntity.status(status).body(body);
    }
}
