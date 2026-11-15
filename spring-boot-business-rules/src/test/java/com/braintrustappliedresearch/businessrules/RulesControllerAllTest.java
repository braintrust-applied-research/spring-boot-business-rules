package com.braintrustappliedresearch.businessrules;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** The reserved {@code "evaluate": "all"} value, which runs every decision in a model. */
class RulesControllerAllTest {

    private static RulesController controller(boolean instructionsEnabled, String... models) {
        List<ClassPathResource> resources = java.util.Arrays.stream(models)
                .map(m -> new ClassPathResource("test-models/" + m + ".dmn")).toList();
        return new RulesController(new RulesEngine(List.copyOf(resources)), instructionsEnabled);
    }

    private static Map<String, Object> decisions(ResponseEntity<Map<String, Object>> response) {
        Object decisions = response.getBody().get("decisions");
        assertThat(decisions).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) decisions;
        return map;
    }

    @Test
    void returnsEveryDecisionInTheModel() {
        ResponseEntity<Map<String, Object>> response = controller(true, "model-a")
                .evaluate(Map.of("evaluate", "all", "x", 5));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("evaluate", "all").containsEntry("model", "ModelA");
        assertThat(decisions(response)).containsOnlyKeys("Double", "Triple");
        assertThat((BigDecimal) decisions(response).get("Double")).isEqualByComparingTo("10");
        assertThat((BigDecimal) decisions(response).get("Triple")).isEqualByComparingTo("15");
        // result carries the same map, so clients can read either field
        assertThat(response.getBody().get("result")).isEqualTo(decisions(response));
    }

    @Test
    void missingInputIsUnprocessableAndReportsMessages() {
        ResponseEntity<Map<String, Object>> response = controller(true, "model-a").evaluate(Map.of("evaluate", "all"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat((List<?>) response.getBody().get("messages")).isNotEmpty();
    }

    @Test
    void severalModelsNeedAModelKey() {
        RulesController controller = controller(true, "model-a", "model-b");

        ResponseEntity<Map<String, Object>> ambiguous = controller.evaluate(Map.of("evaluate", "all", "x", 1));
        assertThat(ambiguous.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ambiguous.getBody().get("error").toString()).contains("'model'");

        ResponseEntity<Map<String, Object>> chosen = controller.evaluate(
                Map.of("evaluate", "all", "model", "ModelB", "y", 1));
        assertThat(chosen.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(decisions(chosen)).containsOnlyKeys("Inc");
        assertThat((BigDecimal) decisions(chosen).get("Inc")).isEqualByComparingTo("2");

        ResponseEntity<Map<String, Object>> unknown = controller.evaluate(
                Map.of("evaluate", "all", "model", "Nope", "y", 1));
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aRealDecisionCalledAllKeepsItsMeaning() {
        ResponseEntity<Map<String, Object>> response = controller(true, "model-all")
                .evaluate(Map.of("evaluate", "all", "n", 7));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // the real decision ran (a single value, not a map of every decision): the shortcut did not apply
        assertThat(response.getBody().get("result")).isNotInstanceOf(Map.class);
        assertThat((BigDecimal) response.getBody().get("result")).isEqualByComparingTo("7");
        assertThat((BigDecimal) decisions(response).get("all")).isEqualByComparingTo("7");
    }

    @Test
    void noModelsLoadedIsNotFound() {
        ResponseEntity<Map<String, Object>> response = controller(true).evaluate(Map.of("evaluate", "all"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("error")).isEqualTo("No models are loaded");
    }

    @Test
    void errorsRevealModelsOnlyWhenInstructionsAreEnabled() {
        Map<String, Object> request = Map.of("evaluate", "all", "x", 1);

        // two models loaded and no "model" key: with instructions on this is a 400 that lists the models
        assertThat(controller(true, "model-a", "model-b").evaluate(request).getBody()).containsKey("models");
        // with instructions off "all" does not exist at all, so nothing about the models can leak
        ResponseEntity<Map<String, Object>> quiet = controller(false, "model-a", "model-b").evaluate(request);
        assertThat(quiet.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(quiet.getBody()).doesNotContainKey("models");
    }
}
