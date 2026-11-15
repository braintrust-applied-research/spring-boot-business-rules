package com.braintrustappliedresearch.businessrules;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Callers may only supply declared inputs. KIE treats a value supplied under a decision's name as that decision's
 * result and skips the rule, which would let a caller overwrite an outcome such as an eligibility check.
 */
class RulesControllerInputsTest {

    private final RulesController controller = new RulesController(
            new RulesEngine(List.of(new ClassPathResource("test-models/model-chain.dmn"))), true);

    @Test
    void declaredInputsStillWork() {
        ResponseEntity<Map<String, Object>> response = controller.evaluate(Map.of("evaluate", "Top", "n", 5));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((BigDecimal) response.getBody().get("result")).isEqualByComparingTo("11"); // 5 * 2 + 1
    }

    @Test
    void aDecisionResultCannotBeSuppliedByTheCaller() {
        // Without the check, KIE would skip the Base rule and compute Top as 100 + 1
        ResponseEntity<Map<String, Object>> response = controller.evaluate(
                Map.of("evaluate", "Top", "n", 5, "Base", 100));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error").toString()).contains("Unknown input(s): Base");
        assertThat(response.getBody()).doesNotContainKey("result");
    }

    @Test
    void theSameHoldsForEvaluateAll() {
        ResponseEntity<Map<String, Object>> response = controller.evaluate(
                Map.of("evaluate", "all", "n", 5, "Base", 100));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownKeysAreRejectedToo() {
        ResponseEntity<Map<String, Object>> response = controller.evaluate(
                Map.of("evaluate", "Top", "n", 5, "bogus", 1));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error").toString()).contains("bogus");
    }

    @Test
    void theEngineAlsoIgnoresNonInputsEvenIfACallerSkipsTheControllerCheck() {
        RulesEngine engine = new RulesEngine(List.of(new ClassPathResource("test-models/model-chain.dmn")));
        var model = engine.findModel(null, "Top").orElseThrow();

        RulesEngine.Evaluation evaluation = engine.evaluate(model, "Top", Map.of("n", 5, "Base", 100));

        assertThat(engine.unknownInputs(model, Map.of("n", 5, "Base", 100))).containsExactly("Base");
        assertThat((BigDecimal) evaluation.result()).isEqualByComparingTo("11");
    }

    @Test
    void errorsRevealTheModelsOnlyWhenInstructionsAreEnabled() {
        RulesController quiet = new RulesController(
                new RulesEngine(List.of(new ClassPathResource("test-models/model-chain.dmn"))), false);

        assertThat(controller.evaluate(Map.of("evaluate", "Top", "n", 5, "Base", 1)).getBody()).containsKey("models");
        assertThat(quiet.evaluate(Map.of("evaluate", "Top", "n", 5, "Base", 1)).getBody()).doesNotContainKey("models");
    }
}
