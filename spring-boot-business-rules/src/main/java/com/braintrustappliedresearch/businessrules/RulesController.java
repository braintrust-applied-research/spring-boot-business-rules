package com.braintrustappliedresearch.businessrules;

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
 */
@RestController
@RequestMapping("${" + BusinessRulesProperties.PREFIX + ".path:/rules}")
public class RulesController {

    static final String EVALUATE = "evaluate";
    static final String MODEL = "model";
    /** Reserved {@code evaluate} value that runs every decision in a model. */
    static final String ALL = "all";

    private final RulesEngine engine;
    private final boolean listModelsInErrors;

    /**
     * Creates the controller.
     *
     * @param engine             the rules engine used to evaluate decisions
     * @param listModelsInErrors whether a {@code 404} response lists the loaded models. Pass the value of
     *                           {@code spring-boot-business-rules.instructions-enabled}: model names, inputs and decisions
     *                           are only revealed to callers when the instructions route is deliberately enabled
     */
    public RulesController(RulesEngine engine, boolean listModelsInErrors) {
        this.engine = engine;
        this.listModelsInErrors = listModelsInErrors;
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
     * with the {@code model} key. A model that defines a decision actually called {@code all} keeps that meaning:
     * the real decision is evaluated and the shortcut does not apply.
     *
     * <p>Responses:
     * <ul>
     *   <li>{@code 200}: the decision result, plus every decision evaluated and any DMN messages</li>
     *   <li>{@code 400}: the {@code evaluate} key is missing or not a non-blank string, {@code "all"} was
     *       requested without a {@code model} while several models are loaded, or the body contains a key that is not
     *       a declared input of the model. The last rule matters for security: values supplied under a decision's name
     *       would otherwise replace that decision's result and bypass the rule</li>
     *   <li>{@code 404}: no loaded model has a decision with that name, or the named {@code model} does not exist
     *       (the body lists the available models only if instructions are enabled)</li>
     *   <li>{@code 422}: DMN reported errors, for example a required input was not supplied</li>
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
        String modelName = model == null ? null : model.toString();
        DMNModel target = engine.findModel(modelName, decisionName).orElse(null);
        if (target == null && ALL.equals(decisionName)) {
            return evaluateAll(modelName, inputs);
        }
        if (target == null) {
            return error(HttpStatus.NOT_FOUND, "No decision named '" + decisionName + "'", modelsForError());
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
            String message = modelName == null ? "No models are loaded" : "No model named '" + modelName + "'";
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
        return error(HttpStatus.BAD_REQUEST, "Unknown input(s): " + String.join(", ", unknown)
                + ". Only the model's declared inputs may be supplied", modelsForError());
    }

    private ResponseEntity<Map<String, Object>> respond(String evaluated, RulesEngine.Evaluation evaluation) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(EVALUATE, evaluated);
        body.put(MODEL, evaluation.model());
        body.put("result", evaluation.result());
        body.put("decisions", evaluation.decisions());
        body.put("messages", evaluation.messages());
        return ResponseEntity.status(evaluation.hasErrors() ? HttpStatus.UNPROCESSABLE_CONTENT : HttpStatus.OK)
                .body(body);
    }

    /** The loaded models, or {@code null} when the instructions route is off and models must not be revealed. */
    private List<Map<String, Object>> modelsForError() {
        return listModelsInErrors ? engine.describe() : null;
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
