package com.braintrustappliedresearch.businessrules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Requests over a limit are rejected with 400 and a message that names the rule without repeating the offending text,
 * so one caller cannot make the endpoint hold, evaluate or echo unbounded content.
 */
class RulesControllerLimitsTest {

    /** Small limits so the tests stay small: 3 inputs, 10-character names, 20-character text values. */
    private static final BusinessRulesProperties.Limits SMALL = new BusinessRulesProperties.Limits(3, 10, 20);

    private static RulesController controller(BusinessRulesProperties.Limits limits) {
        RulesEngine engine = new RulesEngine(List.of(new ClassPathResource("test-models/model-a.dmn")));
        return new RulesController(engine, true, limits);
    }

    private static Map<String, Object> request(Object... keysAndValues) {
        Map<String, Object> request = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            request.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return request;
    }

    private static String error(ResponseEntity<Map<String, Object>> response) {
        return String.valueOf(response.getBody().get("error"));
    }

    @Test
    void aRequestWithinTheLimitsIsUntouched() {
        ResponseEntity<Map<String, Object>> response = controller(SMALL).evaluate(request("evaluate", "Double", "x", 4));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void anEvaluateValueOverTheLimitIsRejectedWithoutBeingEchoed() {
        String longName = "D".repeat(200);
        ResponseEntity<Map<String, Object>> response = controller(SMALL).evaluate(request("evaluate", longName, "x", 4));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(error(response)).isEqualTo("'evaluate' is longer than 10 characters");
        assertThat(response.getBody().toString()).doesNotContain(longName);
    }

    @Test
    void aModelValueOverTheLimitOrNotAStringIsRejected() {
        String longModel = "M".repeat(200);
        ResponseEntity<Map<String, Object>> tooLong = controller(SMALL)
                .evaluate(request("evaluate", "Double", "model", longModel, "x", 4));
        assertThat(tooLong.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(error(tooLong)).isEqualTo("'model' is longer than 10 characters");
        assertThat(tooLong.getBody().toString()).doesNotContain(longModel);

        // a non-string would otherwise be turned into text with toString(), which can be arbitrarily large
        ResponseEntity<Map<String, Object>> notText = controller(SMALL)
                .evaluate(request("evaluate", "Double", "model", List.of(1, 2, 3), "x", 4));
        assertThat(notText.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(error(notText)).isEqualTo("'model' must be a string");
    }

    @Test
    void tooManyInputsAreRejected() {
        ResponseEntity<Map<String, Object>> response = controller(SMALL)
                .evaluate(request("evaluate", "Double", "a", 1, "b", 2, "c", 3, "d", 4));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(error(response)).isEqualTo("Too many inputs: at most 3 are accepted");
    }

    @Test
    void anInputNameOverTheLimitIsRejectedWithoutBeingEchoed() {
        String longKey = "k".repeat(200);
        ResponseEntity<Map<String, Object>> response = controller(SMALL).evaluate(request("evaluate", "Double", longKey, 1));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(error(response)).isEqualTo("An input name is longer than 10 characters");
        assertThat(response.getBody().toString()).doesNotContain(longKey);
    }

    @Test
    void aTextValueOverTheLimitIsRejectedWhereverItIsNested() {
        String longText = "v".repeat(500);
        List<Object> values = List.of(
                longText,
                List.of(1, List.of("ok", longText)),
                Map.of("inner", Map.of("deeper", longText)),
                Map.of("k".repeat(100), 1));          // a long NAME inside a value counts too
        for (Object value : values) {
            ResponseEntity<Map<String, Object>> response = controller(SMALL).evaluate(request("evaluate", "Double", "x", value));

            assertThat(response.getStatusCode()).as(String.valueOf(value).substring(0, 20)).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(error(response)).isEqualTo("The value of input 'x' contains text longer than 20 characters");
            assertThat(response.getBody().toString()).doesNotContain(longText);
        }
    }

    @Test
    void valuesExactlyAtTheLimitsPass() {
        // 20 characters is the text limit and 10 the name limit: both pass the limits, so any failure is a different one
        ResponseEntity<Map<String, Object>> response = controller(SMALL)
                .evaluate(request("evaluate", "Double", "x", "t".repeat(20)));
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.BAD_REQUEST);   // 422: the string is not a number

        ResponseEntity<Map<String, Object>> name = controller(SMALL).evaluate(request("evaluate", "D".repeat(10), "x", 4));
        assertThat(name.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);           // within the limit, just not a decision
    }

    @Test
    void aVeryLongDeeplyNestedValueDoesNotOverflowTheStack() {
        Object nested = "v".repeat(500);
        for (int i = 0; i < 20_000; i++) {
            nested = List.of(nested);
        }
        ResponseEntity<Map<String, Object>> response = controller(SMALL).evaluate(request("evaluate", "Double", "x", nested));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void theDefaultLimitsApplyWhenNoneAreGiven() {
        RulesEngine engine = new RulesEngine(List.of(new ClassPathResource("test-models/model-a.dmn")));
        RulesController controller = new RulesController(engine, true);   // the two-argument constructor
        Map<String, Object> many = new HashMap<>();
        many.put("evaluate", "Double");
        for (int i = 0; i < 600; i++) {
            many.put("k" + i, i);
        }

        ResponseEntity<Map<String, Object>> response = controller.evaluate(many);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(error(response)).isEqualTo("Too many inputs: at most 500 are accepted");
        assertThat(BusinessRulesProperties.Limits.defaults()).isEqualTo(new BusinessRulesProperties.Limits(500, 256, 10_000));
    }

    @Test
    void echoedNamesAreShortenedAndTheUnknownListIsCapped() {
        BusinessRulesProperties.Limits roomy = new BusinessRulesProperties.Limits(500, 256, 10_000);
        String longKey = "a".repeat(200);   // sorts before "unknown0...", so it is among the names that are listed
        Map<String, Object> request = request("evaluate", "Double", "x", 4, longKey, 1);
        for (int i = 0; i < 14; i++) {
            request.put("unknown" + i, i);
        }

        ResponseEntity<Map<String, Object>> response = controller(roomy).evaluate(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // 15 unknown names, sorted: ten are listed (the long one first, cut short) and the other five are counted
        assertThat(error(response)).contains("and 5 more").doesNotContain(longKey).contains("...");
        assertThat(error(response).length()).isLessThan(400);

        ResponseEntity<Map<String, Object>> notFound = controller(roomy).evaluate(request("evaluate", "n".repeat(200)));
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(error(notFound).length()).isLessThan(100);
    }
}
