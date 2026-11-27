package com.braintrustappliedresearch.businessrules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Evaluating a named model directly from Java, without the REST controller. */
class RulesEngineProgrammaticTest {

    private final RulesEngine engine = new RulesEngine(List.of(new ClassPathResource("test-models/model-chain.dmn")));

    @Test
    void evaluatesOneDecisionByModelName() {
        RulesEngine.Evaluation evaluation = engine.evaluate("ModelChain", "Top", Map.of("n", 5));

        assertThat(evaluation.failed()).isFalse();
        assertThat((BigDecimal) evaluation.result()).isEqualByComparingTo("11"); // 5 * 2 + 1
        assertThat(evaluation.decisions()).containsKeys("Base", "Top");
    }

    @Test
    void evaluatesEveryDecisionByModelName() {
        RulesEngine.Evaluation evaluation = engine.evaluateAll("ModelChain", Map.of("n", 5));

        assertThat(evaluation.failed()).isFalse();
        assertThat(evaluation.decisions()).containsKeys("Base", "Top");
    }

    @Test
    void anUnknownModelIsRejected() {
        assertThatThrownBy(() -> engine.evaluate("Nope", "Top", Map.of("n", 5)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No DMN model named 'Nope'");
        assertThatThrownBy(() -> engine.evaluateAll("Nope", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anUnknownDecisionIsRejected() {
        assertThatThrownBy(() -> engine.evaluate("ModelChain", "Nope", Map.of("n", 5)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no decision 'Nope'");
    }

    @Test
    void aDecisionResultCannotBeSuppliedAsAnInput() {
        assertThatThrownBy(() -> engine.evaluate("ModelChain", "Top", Map.of("n", 5, "Base", 100)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unknown input(s): Base");
        assertThatThrownBy(() -> engine.evaluateAll("ModelChain", Map.of("n", 5, "Base", 100)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unknown input(s): Base");
    }

    @Test
    void aMissingInputIsReportedAsAFailedEvaluationNotAnException() {
        assertThat(engine.evaluate("ModelChain", "Top", Map.of()).failed()).isTrue();
    }

    @Test
    void anEngineWithNoModelsRejectsEveryName() {
        RulesEngine empty = new RulesEngine(List.of());

        assertThatThrownBy(() -> empty.evaluate("ModelChain", "Top", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aModelThatDoesNotCompileFailsAtLoadTime() {
        var broken = new org.springframework.core.io.ByteArrayResource("<definitions>".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new RulesEngine(List.of(broken))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aModelFromAnotherEngineIsRefused() {
        RulesEngine other = new RulesEngine(List.of(new ClassPathResource("test-models/model-chain.dmn")));
        var foreign = other.findModels("ModelChain").get(0);

        assertThatThrownBy(() -> engine.evaluate(foreign, "Top", Map.of("n", 5)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not loaded by this engine");
        assertThatThrownBy(() -> engine.evaluateAll(foreign, Map.of("n", 5)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not loaded by this engine");
    }

    @Test
    void theEngineCannotBeBuiltFromOutsideThePackage() throws NoSuchMethodException {
        assertThat(java.lang.reflect.Modifier.isPublic(
                RulesEngine.class.getDeclaredConstructor(java.util.Collection.class).getModifiers())).isFalse();
        assertThat(RulesEngine.class.getConstructors()).isEmpty();
    }
}
