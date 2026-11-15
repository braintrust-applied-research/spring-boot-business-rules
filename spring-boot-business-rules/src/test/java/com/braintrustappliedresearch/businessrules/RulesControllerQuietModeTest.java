package com.braintrustappliedresearch.businessrules;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * With instructions off, ordinary responses must not let a caller map the model: they name only the decision that was
 * asked for, omit the model name, replace DMN's messages (which name inputs, decisions and DMN ids), and "all" is an
 * unknown decision.
 */
class RulesControllerQuietModeTest {

    private static RulesController controller(boolean instructionsEnabled, String... models) {
        List<ClassPathResource> resources = Arrays.stream(models)
                .map(m -> new ClassPathResource("test-models/" + m + ".dmn")).toList();
        return new RulesController(new RulesEngine(List.copyOf(resources)), instructionsEnabled);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> decisions(ResponseEntity<Map<String, Object>> response) {
        return (Map<String, Object>) response.getBody().get("decisions");
    }

    @Test
    void aSuccessfulResponseNamesOnlyTheRequestedDecision() {
        // Top depends on Base, so both are evaluated; only Top may be reported
        ResponseEntity<Map<String, Object>> response = controller(false, "model-chain")
                .evaluate(Map.of("evaluate", "Top", "n", 5));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).doesNotContainKey("model");
        assertThat(decisions(response)).containsOnlyKeys("Top");
        assertThat((BigDecimal) response.getBody().get("result")).isEqualByComparingTo("11");
        assertThat(response.getBody().get("messages")).isEqualTo(List.of());
    }

    @Test
    void anErrorMessageIsGenericAndNamesNothingInTheModel() {
        ResponseEntity<Map<String, Object>> response = controller(false, "model-a")
                .evaluate(Map.of("evaluate", "Double"));   // the required input x is missing

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("messages")).isEqualTo(List.of(RulesController.QUIET_MESSAGE));
        String everything = response.getBody().toString();
        // DMN's own text, other decisions, the model name and DMN ids must not appear
        assertThat(everything).doesNotContain("Required dependency", "Triple", "ModelA", "d_", "'x'");
        assertThat(decisions(response)).containsOnlyKeys("Double");
    }

    @Test
    void allIsAnUnknownDecisionWhenInstructionsAreOff() {
        ResponseEntity<Map<String, Object>> response = controller(false, "model-a")
                .evaluate(Map.of("evaluate", "all", "x", 1));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("error", "No decision named 'all'").doesNotContainKey("models");
        assertThat(response.getBody().toString()).doesNotContain("Double", "Triple", "ModelA");
    }

    @Test
    void aRealDecisionCalledAllStillWorks() {
        ResponseEntity<Map<String, Object>> response = controller(false, "model-all")
                .evaluate(Map.of("evaluate", "all", "n", 7));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(decisions(response)).containsOnlyKeys("all");      // "other" is not reported
        assertThat((BigDecimal) response.getBody().get("result")).isEqualByComparingTo("7");
    }

    @Test
    void withInstructionsOnTheFullDetailIsStillReturned() {
        ResponseEntity<Map<String, Object>> response = controller(true, "model-chain")
                .evaluate(Map.of("evaluate", "Top", "n", 5));

        assertThat(response.getBody()).containsEntry("model", "ModelChain");
        assertThat(decisions(response)).containsKeys("Base", "Top");
    }
}
