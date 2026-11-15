package com.braintrustappliedresearch.businessrules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * A {@code null} result means "not decided", never an answer. DMN reports wrong-typed or null inputs only as a warning
 * ("no rule matched ... setting result to null"), or not at all (FEEL turns a type mismatch into null), and neither
 * counts as an error in DMN's own terms. The API must still answer 422 and not a 200 that looks like a success.
 */
class RulesControllerUndecidedTest {

    private static RulesController controller(boolean instructionsEnabled, String... models) {
        List<ClassPathResource> resources = Arrays.stream(models)
                .map(m -> new ClassPathResource("test-models/" + m + ".dmn")).toList();
        return new RulesController(new RulesEngine(List.copyOf(resources)), instructionsEnabled);
    }

    private static ResponseEntity<Map<String, Object>> call(RulesController controller, String evaluate, String input,
            Object value) {
        Map<String, Object> request = new HashMap<>();   // HashMap: Map.of cannot hold a null value
        request.put("evaluate", evaluate);
        request.put(input, value);
        return controller.evaluate(request);
    }

    @SuppressWarnings("unchecked")
    private static List<String> messages(ResponseEntity<Map<String, Object>> response) {
        return (List<String>) response.getBody().get("messages");
    }

    @Test
    void validInputsStillSucceed() {
        RulesController c = controller(true, "model-table");

        ResponseEntity<Map<String, Object>> high = call(c, "Label", "n", 15);
        assertThat(high.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(high.getBody()).containsEntry("result", "H");
        assertThat(messages(high)).isEmpty();
        assertThat(call(c, "Label", "n", 5).getBody()).containsEntry("result", "not high");
    }

    @Test
    void wrongTypedInputToADecisionTableIsUnprocessable() {
        ResponseEntity<Map<String, Object>> response = call(controller(true, "model-table"), "Band", "n", "abc");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("result")).isNull();
        assertThat(messages(response)).anyMatch(m -> m.contains("No rule matched"));
    }

    @Test
    void aNullInputIsUnprocessable() {
        ResponseEntity<Map<String, Object>> response = call(controller(true, "model-table"), "Band", "n", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("result")).isNull();
    }

    @Test
    void aWarningFromADependencyFailsAnOtherwiseNormalLookingResult() {
        // Label falls through to "not high" because Band came out null; that must not look like a real answer
        ResponseEntity<Map<String, Object>> response = call(controller(true, "model-table"), "Label", "n", "abc");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).containsEntry("result", "not high");   // what was computed is still returned
        assertThat(messages(response)).anyMatch(m -> m.contains("No rule matched"));
    }

    @Test
    void aSilentNullIsUnprocessableAndExplained() {
        // x * 2 with a string: FEEL yields null and DMN says nothing at all
        ResponseEntity<Map<String, Object>> response = call(controller(true, "model-a"), "Double", "x", "abc");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("result")).isNull();
        assertThat(messages(response)).hasSize(1).allMatch(m -> m.contains("'Double'") && m.contains("produced no result"));
    }

    @Test
    void aNullThroughADependencyChainIsUnprocessable() {
        ResponseEntity<Map<String, Object>> response = call(controller(true, "model-chain"), "Top", "n", "abc");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("result")).isNull();
    }

    @Test
    void allFailsButStillReturnsWhatItCouldCompute() {
        ResponseEntity<Map<String, Object>> response = call(controller(true, "model-table"), "all", "n", "abc");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        @SuppressWarnings("unchecked")
        Map<String, Object> decisions = (Map<String, Object>) response.getBody().get("decisions");
        assertThat(decisions).containsEntry("Label", "not high").containsEntry("Band", null);
    }

    @Test
    void allNamesTheDecisionsThatProducedNothing() {
        ResponseEntity<Map<String, Object>> response = call(controller(true, "model-a"), "all", "x", "abc");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(messages(response)).hasSize(1).allMatch(m -> m.contains("'Double'") && m.contains("'Triple'"));
    }

    @Test
    void withInstructionsOffTheFailureMessageStaysGeneric() {
        ResponseEntity<Map<String, Object>> response = call(controller(false, "model-a"), "Double", "x", "abc");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(messages(response)).isEqualTo(List.of(RulesController.QUIET_MESSAGE));
    }
}
