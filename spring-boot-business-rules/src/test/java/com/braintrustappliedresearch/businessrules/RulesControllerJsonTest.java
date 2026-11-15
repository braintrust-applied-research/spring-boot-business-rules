package com.braintrustappliedresearch.businessrules;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The body is read strictly: a duplicate key is an error, not a silent last-one-wins that a gateway checking the first
 * occurrence would disagree with, and every body problem gets the endpoint's own error shape.
 */
class RulesControllerJsonTest {

    private final RulesController controller = new RulesController(
            new RulesEngine(List.of(new ClassPathResource("test-models/model-table.dmn"))), true);

    private ResponseEntity<Map<String, Object>> post(String json) {
        return controller.post(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String error(ResponseEntity<Map<String, Object>> response) {
        return String.valueOf(response.getBody().get("error"));
    }

    @Test
    void aDuplicateKeyIsRejected() {
        ResponseEntity<Map<String, Object>> response = post("{\"evaluate\":\"Band\",\"n\":5,\"n\":50}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(error(response)).isEqualTo("Duplicate keys are not allowed in the request body");
        assertThat(response.getBody()).containsOnlyKeys("error");
    }

    @Test
    void aDuplicateEvaluateKeyIsRejected() {
        // which decision runs must not depend on which duplicate a parser prefers
        ResponseEntity<Map<String, Object>> response = post("{\"evaluate\":\"Band\",\"evaluate\":\"Label\",\"n\":5}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(error(response)).startsWith("Duplicate keys");
    }

    @Test
    void aDuplicateKeyInsideAnInputValueIsRejectedToo() {
        ResponseEntity<Map<String, Object>> response = post("{\"evaluate\":\"Band\",\"n\":{\"a\":1,\"a\":2}}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(error(response)).startsWith("Duplicate keys");
    }

    @Test
    void theSameKeyInDifferentObjectsIsFine() {
        // not a duplicate: the two "a" keys belong to two separate objects. It fails later (a list is not a number),
        // but it must get past the parser.
        ResponseEntity<Map<String, Object>> response = post("{\"evaluate\":\"Band\",\"n\":[{\"a\":1},{\"a\":2}]}");

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void malformedEmptyOrNonObjectBodiesGetTheEndpointsOwnErrorShape() {
        for (String body : List.of("{\"evaluate\":", "not json", "[1,2,3]", "\"text\"", "42", "{\"evaluate\":\"Band\"} trailing")) {
            ResponseEntity<Map<String, Object>> response = post(body);

            assertThat(response.getStatusCode()).as(body).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).as(body).containsOnlyKeys("error");
            assertThat(error(response)).as(body).isEqualTo("The request body is not a valid JSON object");
        }
        assertThat(error(controller.post(new byte[0]))).isEqualTo("The request body is empty");
        assertThat(error(controller.post(null))).isEqualTo("The request body is empty");
    }

    @Test
    void aBodyNestedBeyondTheParserLimitIsRejected() {
        String deep = "{\"evaluate\":\"Band\",\"n\":" + "[".repeat(5000) + "]".repeat(5000) + "}";

        assertThat(post(deep).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void numbersAreReadAsBefore() {
        // The strict mapper is a different instance from Spring's, so check that numbers mean the same thing
        // (n < 10 is "low", n >= 10 is "high"): decimals, exponents and integers.
        assertThat(post("{\"evaluate\":\"Band\",\"n\":9.99}").getBody()).containsEntry("result", "low");
        assertThat(post("{\"evaluate\":\"Band\",\"n\":10.0}").getBody()).containsEntry("result", "high");
        assertThat(post("{\"evaluate\":\"Band\",\"n\":1e1}").getBody()).containsEntry("result", "high");
        assertThat(post("{\"evaluate\":\"Band\",\"n\":15}").getBody()).containsEntry("result", "high");
        assertThat(post("{\"evaluate\":\"Band\",\"n\":9999999999999999999999}").getBody()).containsEntry("result", "high");
        assertThat(post("{\"evaluate\":\"Band\",\"n\":-3}").getBody()).containsEntry("result", "low");
    }

    @Test
    void aValidRequestIsUnchanged() {
        ResponseEntity<Map<String, Object>> response = post("{\"evaluate\":\"Label\",\"n\":15}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("result", "H");
    }
}
