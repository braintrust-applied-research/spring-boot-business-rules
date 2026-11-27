package com.braintrustappliedresearch.businessrules;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.kie.api.builder.Message;
import org.kie.api.io.Resource;
import org.kie.dmn.api.core.DMNContext;
import org.kie.dmn.api.core.DMNDecisionResult;
import org.kie.dmn.api.core.DMNDecisionResult.DecisionEvaluationStatus;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.core.DMNResult;
import org.kie.dmn.api.core.DMNRuntime;
import org.kie.dmn.core.internal.utils.DMNRuntimeBuilder;
import org.kie.internal.io.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StreamUtils;

/**
 * Thin wrapper over the KIE DMN runtime.
 *
 * <p>The engine is built once from a set of {@code .dmn} resources and is then used to describe the loaded models
 * and to evaluate decisions. If no models are found it still starts, logs a warning, and has nothing to evaluate.
 * Instances are immutable after construction and safe to share between threads.
 *
 * <p><strong>Only rules that were loaded at startup can be evaluated.</strong> There is no way to hand the engine a
 * one-off {@code .dmn} file, resource or string and run it: the constructor is not public (the auto-configuration
 * builds the one engine from {@code rules-location}), models are looked up by name, and a {@link DMNModel} that this
 * engine did not load is refused. To add or change a rule, put the {@code .dmn} file under {@code rules-location} and
 * restart.
 */
public final class RulesEngine {

    private static final Logger log = LoggerFactory.getLogger(RulesEngine.class);

    private final DMNRuntime runtime;
    private final List<DMNModel> models;

    /**
     * Loads every DMN model in the given resources into one runtime. Not public on purpose: only the
     * auto-configuration, from {@code rules-location}, decides which rules exist.
     *
     * @param resources the {@code .dmn} files to load; may be empty
     * @throws IllegalStateException if a resource cannot be read or the models do not compile into a runtime
     *                               (for example because of invalid DMN)
     */
    RulesEngine(Collection<org.springframework.core.io.Resource> resources) {
        List<Resource> kieResources = new ArrayList<>();
        for (org.springframework.core.io.Resource resource : resources) {
            try (var in = resource.getInputStream()) {
                String name = String.valueOf(resource.getFilename());
                // Read eagerly so the KIE resource does not depend on the (closed) stream.
                String xml = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
                kieResources.add(ResourceFactory.newReaderResource(new StringReader(xml)).setSourcePath(name));
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read DMN resource " + resource, e);
            }
        }
        if (kieResources.isEmpty()) {
            log.warn("No DMN models found; the rules endpoint will have nothing to evaluate");
            this.runtime = null;
            this.models = List.of();
            return;
        }
        try {
            this.runtime = DMNRuntimeBuilder.fromDefaults().buildConfiguration().fromResources(kieResources)
                    .getOrElseThrow(e -> new IllegalStateException("Cannot build DMN runtime", e));
        } catch (IllegalStateException e) {
            throw e;
        } catch (RuntimeException e) {
            // KIE can fail with an unchecked exception (for example a null pointer on a file that is not DMN at all)
            throw new IllegalStateException("Cannot build DMN runtime: a resource is not valid DMN", e);
        }
        this.models = runtime.getModels();
        models.forEach(m -> log.info("Loaded DMN model '{}' ({} decisions)", m.getName(), m.getDecisions().size()));
    }

    /**
     * Describes the loaded models, their required inputs and decisions.
     *
     * @return one entry per model, with keys {@code model} (the model name), {@code inputs} and {@code decisions}
     *         (both sorted lists of names); empty if no models are loaded
     */
    public List<Map<String, Object>> describe() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (DMNModel model : models) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("model", model.getName());
            m.put("inputs", model.getInputs().stream().map(n -> requireNonNull(n.getName())).sorted().toList());
            m.put("decisions", model.getDecisions().stream().map(d -> requireNonNull(d.getName())).sorted().toList());
            out.add(m);
        }
        return out;
    }

    /**
     * Finds the model containing the decision, optionally restricted to a named model.
     *
     * <p>If several models define a decision with the same name, the one whose model name sorts first is chosen,
     * so pass {@code modelName} to pick a specific one.
     *
     * @param modelName    the model to look in, or {@code null} to search all models
     * @param decisionName the name of the decision to find
     * @return the matching model, or empty if no model has that decision
     */
    public Optional<DMNModel> findModel(String modelName, String decisionName) {
        return models.stream()
                .filter(m -> modelName == null || modelName.equals(m.getName()))
                .filter(m -> m.getDecisionByName(decisionName) != null)
                .min(Comparator.comparing((DMNModel m) -> requireNonNull(m.getName())));
    }

    /**
     * Finds the models to search, optionally restricted to a named model.
     *
     * @param modelName the model name, or {@code null} for every loaded model
     * @return the matching models in name order; empty if none match (including when no models are loaded)
     */
    public List<DMNModel> findModels(String modelName) {
        return models.stream()
                .filter(m -> modelName == null || modelName.equals(m.getName()))
                .sorted(Comparator.comparing((DMNModel m) -> requireNonNull(m.getName())))
                .toList();
    }

    /**
     * Returns the keys that are not declared inputs of the model.
     *
     * <p>Callers must not be able to supply anything but real inputs. In particular, KIE treats a value supplied under
     * a <em>decision's</em> name as that decision's result and skips evaluating it, so accepting arbitrary keys would
     * let a caller overwrite the outcome of a rule (for example send {@code "Eligibility":"Approved"}). The REST
     * controller rejects such requests with {@code 400}; {@link #evaluate} and {@link #evaluateAll} also drop them
     * as a second line of defence.
     *
     * @param model  the model the inputs are meant for
     * @param inputs the caller-supplied values, keyed by name
     * @return the keys that are not inputs of the model, sorted; empty if every key is a declared input
     */
    public List<String> unknownInputs(DMNModel model, Map<String, Object> inputs) {
        Set<String> declared = declaredInputs(model);
        return inputs.keySet().stream().filter(key -> !declared.contains(key)).sorted().toList();
    }

    private static Set<String> declaredInputs(DMNModel model) {
        return model.getInputs().stream().map(input -> requireNonNull(input.getName())).collect(Collectors.toSet());
    }

    private static DMNContext contextOf(DMNRuntime runtime, DMNModel model, Map<String, Object> inputs) {
        Set<String> declared = declaredInputs(model);
        DMNContext context = runtime.newContext();
        inputs.forEach((name, value) -> {
            if (declared.contains(name)) {
                context.set(name, value);
            }
        });
        return context;
    }

    /**
     * Evaluates a decision (and the decisions it depends on) against the given inputs.
     *
     * <p>DMN problems such as a missing input do not throw; they are reported through
     * {@link Evaluation#messages()} and {@link Evaluation#failed()}. An evaluation is {@code failed} when DMN reports an
     * error or a warning, or when the requested decision comes out {@code null}: a {@code null} result means "not
     * decided" (wrong-typed or null inputs end up there), never an answer.
     *
     * @param model        the model that contains the decision, as returned by {@link #findModel}
     * @param decisionName the decision to evaluate
     * @param inputs       the DMN input values, keyed by input name; keys that are not declared inputs of the model
     *                     are ignored (see {@link #unknownInputs})
     * @return the result, every decision that was evaluated along the way, and any DMN messages
     * @throws IllegalArgumentException if {@code model} was not loaded by this engine
     */
    public Evaluation evaluate(DMNModel model, String decisionName, Map<String, Object> inputs) {
        requireLoaded(model);
        DMNContext context = contextOf(runtime, model, inputs);
        DMNResult result = runtime.evaluateByName(model, context, decisionName);
        Map<String, Object> decisions = decisionResults(result);
        List<String> messages = new ArrayList<>(messages(result));
        DMNDecisionResult requested = result.getDecisionResultByName(decisionName);
        boolean noResult = requested != null && requested.getEvaluationStatus() == DecisionEvaluationStatus.SUCCEEDED
                && requested.getResult() == null;
        if (noResult && messages.isEmpty()) {
            messages.add(noResultMessage(List.of(decisionName)));
        }
        boolean failed = result.hasErrors() || hasWarnings(result) || noResult;
        return new Evaluation(model.getName(), decisions.get(decisionName), decisions, messages, failed);
    }

    /**
     * Evaluates every decision in a model against the given inputs.
     *
     * <p>Decisions are evaluated in dependency order. As with {@link #evaluate}, DMN problems do not throw: a
     * decision that cannot be evaluated (for example because one of its inputs is missing) is reported through
     * {@link Evaluation#messages()} and {@link Evaluation#failed()}, while the other decisions still return their
     * values. As with {@link #evaluate}, a decision that comes out {@code null} counts as a failure.
     *
     * @param model  the model whose decisions are evaluated
     * @param inputs the DMN input values, keyed by input name; keys that are not declared inputs of the model are
     *               ignored (see {@link #unknownInputs})
     * @return the evaluation, in which {@link Evaluation#result()} and {@link Evaluation#decisions()} both hold the
     *         value of every decision by name
     * @throws IllegalArgumentException if {@code model} was not loaded by this engine
     */
    public Evaluation evaluateAll(DMNModel model, Map<String, Object> inputs) {
        requireLoaded(model);
        DMNContext context = contextOf(runtime, model, inputs);
        DMNResult result = runtime.evaluateAll(model, context);
        Map<String, Object> decisions = Collections.unmodifiableMap(decisionResults(result));
        List<String> noResult = result.getDecisionResults().stream()
                .filter(d -> d.getEvaluationStatus() == DecisionEvaluationStatus.SUCCEEDED && d.getResult() == null)
                .map(d -> requireNonNull(d.getDecisionName()))
                .toList();
        List<String> messages = new ArrayList<>(messages(result));
        if (!noResult.isEmpty() && messages.isEmpty()) {
            messages.add(noResultMessage(noResult));
        }
        boolean failed = result.hasErrors() || hasWarnings(result) || !noResult.isEmpty();
        return new Evaluation(model.getName(), decisions, decisions, messages, failed);
    }

    /**
     * Evaluates one decision of a named model, for application code that wants an answer without going through the
     * REST endpoint.
     *
     * <p>This works whether or not the web endpoint is enabled
     * ({@code spring-boot-business-rules.web.enabled}). It applies the same rule as the endpoint: only the
     * model's declared inputs may be supplied, because a value supplied under a decision's name would replace that
     * decision's result. Unlike the endpoint it applies none of the request {@code limits}; bounding the size of data
     * handed in from untrusted callers is up to the calling code. DMN problems such as a missing input do not throw;
     * check {@link Evaluation#failed()}.
     *
     * @param modelName    the DMN model name (the {@code name} of the model in the {@code .dmn} file, not the file name)
     * @param decisionName the decision to evaluate
     * @param inputs       the input values, keyed by input name
     * @return the evaluation
     * @throws IllegalArgumentException if no model has that name, several do, the model has no such decision, or
     *                                  {@code inputs} contains a key that is not a declared input of the model
     */
    public Evaluation evaluate(String modelName, String decisionName, Map<String, Object> inputs) {
        requireNonNull(decisionName, "decisionName");
        DMNModel model = uniqueModel(modelName, inputs);
        if (model.getDecisionByName(decisionName) == null) {
            throw new IllegalArgumentException("Model '" + modelName + "' has no decision '" + decisionName + "'");
        }
        return evaluate(model, decisionName, inputs);
    }

    /**
     * Evaluates every decision of a named model. Like {@link #evaluate(String, String, Map)}, it works whether or not
     * the web endpoint is enabled and rejects keys that are not declared inputs.
     *
     * @param modelName the DMN model name
     * @param inputs    the input values, keyed by input name
     * @return the evaluation, in which {@link Evaluation#result()} and {@link Evaluation#decisions()} hold every
     *         decision's value by name
     * @throws IllegalArgumentException if no model has that name, several do, or {@code inputs} contains a key that is
     *                                  not a declared input of the model
     */
    public Evaluation evaluateAll(String modelName, Map<String, Object> inputs) {
        return evaluateAll(uniqueModel(modelName, inputs), inputs);
    }

    /** Refuses a model that this engine did not load itself, so no rule from anywhere else can be run through it. */
    private void requireLoaded(DMNModel model) {
        if (models.stream().noneMatch(loaded -> loaded == model)) {
            throw new IllegalArgumentException("That model was not loaded by this engine; only models found at startup"
                    + " can be evaluated");
        }
    }

    private DMNModel uniqueModel(String modelName, Map<String, Object> inputs) {
        requireNonNull(modelName, "modelName");
        requireNonNull(inputs, "inputs");
        List<DMNModel> matches = findModels(modelName);
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("No DMN model named '" + modelName + "' is loaded");
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("More than one DMN model is named '" + modelName + "'");
        }
        DMNModel model = matches.get(0);
        List<String> unknown = unknownInputs(model, inputs);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown input(s): " + String.join(", ", unknown));
        }
        return model;
    }

    /**
     * DMN only counts error-level messages as errors. A warning such as "no rule matched ... setting result to
     * null" (what wrong-typed or null inputs to a decision table produce) is just as much a sign that nothing was
     * decided, so it counts here too.
     */
    private static boolean hasWarnings(DMNResult result) {
        return result.getMessages().stream()
                .anyMatch(m -> m.getLevel() == Message.Level.WARNING || m.getLevel() == Message.Level.ERROR);
    }

    /** Explains a {@code null} result that DMN itself reported nothing about (FEEL turns type mismatches into null). */
    private static String noResultMessage(List<String> decisions) {
        String names = decisions.stream().map(d -> "'" + d + "'").collect(Collectors.joining(", "));
        return "DMN: " + (decisions.size() == 1 ? "the decision " : "the decisions ") + names
                + " produced no result (null) for the supplied inputs";
    }

    private static Map<String, Object> decisionResults(DMNResult result) {
        Map<String, Object> decisions = new LinkedHashMap<>();
        for (DMNDecisionResult dr : result.getDecisionResults()) {
            decisions.put(dr.getDecisionName(), dr.getResult());
        }
        return decisions;
    }

    private static List<String> messages(DMNResult result) {
        return result.getMessages().stream()
                .map(m -> requireNonNullElse(m.getText(), ""))
                .toList();
    }

    /**
     * The outcome of evaluating one decision. The collections are read-only copies.
     *
     * @param model     name of the model the decision belongs to
     * @param result    the value of the requested decision; may be {@code null}, for example when evaluation failed.
     *                  For an evaluation of every decision this is the same map as {@code decisions}
     * @param decisions the value of every decision evaluated (the requested one and its dependencies), in evaluation
     *                  order; values may be {@code null}
     * @param messages  the DMN engine's messages (errors and warnings), empty if there were none
     * @param failed    {@code true} if the evaluation should be treated as failed: DMN reported an error or a warning,
     *                  or a decision that was evaluated produced no result ({@code null}, which means "not decided")
     */
    public record Evaluation(String model, Object result, Map<String, Object> decisions,
                             List<String> messages, boolean failed) {

        /** Copies {@code decisions} and {@code messages} into read-only collections so the record stays immutable. */
        public Evaluation {
            // Defensive, read-only copies. LinkedHashMap keeps decision order and allows null results,
            // which Map.copyOf would reject.
            decisions = Collections.unmodifiableMap(new LinkedHashMap<>(decisions));
            messages = Collections.unmodifiableList(new ArrayList<>(messages));
        }
    }
}
