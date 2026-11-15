package com.braintrustappliedresearch.example;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Endpoint is served at {context-path}{spring-boot-business-rules.path} = /api/business/rules. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RulesEndpointTest {

    @LocalServerPort
    int port;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String json) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/business/rules"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void getReturnsInstructions() throws Exception {
        HttpResponse<String> response = get("/api/business/rules");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("CustomerRules", "Eligibility", "firstName", "/api/business/rules");
        // the usage text is built from configuration, so it never contains the address this request was sent to
        assertThat(response.body()).contains("curl -X POST <base-url>/api/business/rules").doesNotContain("localhost");
    }

    @Test
    void getReportsEffectiveConfiguration() throws Exception {
        String body = get("/api/business/rules").body();
        assertThat(body).contains(
                "\"spring-boot-business-rules.enabled\":true",
                "\"spring-boot-business-rules.path\":\"/business/rules\"",
                "\"spring-boot-business-rules.rules-location\":\"classpath*:rules/**/*.dmn\"",
                "\"spring-boot-business-rules.instructions-enabled\":true");
    }

    @Test
    void defaultPathIsNotMapped() throws Exception {
        assertThat(get("/api/rules").statusCode()).isEqualTo(404);
        assertThat(get("/business/rules").statusCode()).isEqualTo(404);
    }

    @Test
    void approvesHighIncome() throws Exception {
        HttpResponse<String> response = post("""
                {"firstName":"Ada","lastName":"Lovelace","age":36,"income":120000,"zipCode":"94105","evaluate":"Eligibility"}""");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"result\":\"Approved\"");
    }

    @Test
    void eligibilityBoundaries() throws Exception {
        // age, income, expected. The table's rules are mutually exclusive, so each boundary matches exactly one rule.
        Object[][] cases = {
            {17, 75000, "Declined"}, {17, 0, "Declined"},
            {18, 75000, "Approved"}, {18, 74999, "Referred"},
            {18, 30000, "Referred"}, {18, 29999, "Declined"}, {18, 0, "Declined"},
            {100, 1000000, "Approved"},
        };
        for (Object[] c : cases) {
            HttpResponse<String> response = post("{\"age\":" + c[0] + ",\"income\":" + c[1] + ",\"evaluate\":\"Eligibility\"}");
            assertThat(response.statusCode()).as("age=%s income=%s", c[0], c[1]).isEqualTo(200);
            assertThat(response.body()).as("age=%s income=%s", c[0], c[1]).contains("\"result\":\"" + c[2] + "\"");
        }
    }

    @Test
    void referredAndDeclined() throws Exception {
        assertThat(post("""
                {"firstName":"A","lastName":"B","age":40,"income":50000,"zipCode":"10001","evaluate":"Eligibility"}""")
                .body()).contains("\"result\":\"Referred\"");
        assertThat(post("""
                {"firstName":"A","lastName":"B","age":17,"income":900000,"zipCode":"10001","evaluate":"Eligibility"}""")
                .body()).contains("\"result\":\"Declined\"");
    }

    @Test
    void dependentDecisionsAreEvaluated() throws Exception {
        HttpResponse<String> response = post("""
                {"firstName":"Ada","lastName":"Lovelace","age":36,"income":120000,"zipCode":"94105","evaluate":"Offer"}""");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Hello, Ada Lovelace: you are pre-approved (region 941)");
    }

    @Test
    void allReturnsEveryDecisionInOneCall() throws Exception {
        HttpResponse<String> response = post("""
                {"firstName":"Ada","lastName":"Lovelace","age":36,"income":120000,"zipCode":"94105","evaluate":"all"}""");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(
                "\"evaluate\":\"all\"",
                "\"model\":\"CustomerRules\"",
                "\"Greeting\":\"Hello, Ada Lovelace\"",
                "\"Eligibility\":\"Approved\"",
                "\"Offer\":\"Hello, Ada Lovelace: you are pre-approved (region 941)\"");
    }

    @Test
    void allWithMissingInputsIsUnprocessableButStillReturnsWhatItCould() throws Exception {
        // no names or zipCode: Greeting and Offer cannot be computed, Eligibility can
        HttpResponse<String> response = post("{\"age\":30,\"income\":50000,\"evaluate\":\"all\"}");
        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.body()).contains("\"Eligibility\":\"Referred\"");
    }

    @Test
    void aCallerCannotOverrideADecisionByNamingIt() throws Exception {
        // A 10-year-old with no income must be declined. Supplying "Eligibility" must not be able to change that.
        String minor = "\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"age\":10,\"income\":0,\"zipCode\":\"94105\"";
        HttpResponse<String> honest = post("{" + minor + ",\"evaluate\":\"Offer\"}");
        assertThat(honest.statusCode()).isEqualTo(200);
        assertThat(honest.body()).contains("we cannot make an offer at this time");

        for (String evaluate : new String[] {"Offer", "all"}) {
            HttpResponse<String> attack = post("{" + minor + ",\"Eligibility\":\"Approved\",\"evaluate\":\"" + evaluate + "\"}");
            assertThat(attack.statusCode()).as(evaluate).isEqualTo(400);
            assertThat(attack.body()).doesNotContain("pre-approved");
        }
    }

    @Test
    void wrongTypedInputsAreUnprocessableNotASuccessfulNull() throws Exception {
        HttpResponse<String> response = post("{\"age\":\"abc\",\"income\":\"x\",\"evaluate\":\"Eligibility\"}");
        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.body()).contains("\"result\":null", "No rule matched");
    }

    @Test
    void aWarningFromADependencyAlsoFailsTheRequest() throws Exception {
        // Offer falls through to "we cannot make an offer" because Eligibility came out null; that is not an answer
        HttpResponse<String> response = post("""
                {"firstName":"A","lastName":"B","age":"abc","income":50000,"zipCode":"10001","evaluate":"Offer"}""");
        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.body()).contains("No rule matched");
    }

    @Test
    void oversizedRequestsAreRejectedWithoutBeingEchoed() throws Exception {
        String manyKeys = "{\"evaluate\":\"Eligibility\"," + String.join(",",
                java.util.stream.IntStream.range(0, 20_000).mapToObj(i -> "\"k" + i + "\":1").toList()) + "}";
        HttpResponse<String> tooMany = post(manyKeys);
        assertThat(tooMany.statusCode()).isEqualTo(400);
        assertThat(tooMany.body()).contains("Too many inputs").hasSizeLessThan(300);

        String longModel = "M".repeat(1024 * 1024);
        HttpResponse<String> model = post("{\"evaluate\":\"all\",\"model\":\"" + longModel + "\"}");
        assertThat(model.statusCode()).isEqualTo(400);
        assertThat(model.body()).hasSizeLessThan(300).doesNotContain("MMMMMMMMMM");

        String longValue = "A".repeat(20_000);
        HttpResponse<String> value = post("{\"firstName\":\"" + longValue + "\",\"lastName\":\"x\",\"evaluate\":\"Greeting\"}");
        assertThat(value.statusCode()).isEqualTo(400);
        assertThat(value.body()).contains("'firstName'").hasSizeLessThan(300);
    }

    @Test
    void everyResponseCarriesTheDefensiveHeaders() throws Exception {
        String url = "http://localhost:" + port + "/api/business/rules";
        java.util.Map<String, HttpResponse<String>> responses = new java.util.LinkedHashMap<>();
        responses.put("200 evaluation", post("{\"age\":30,\"income\":50000,\"evaluate\":\"Eligibility\"}"));
        responses.put("400 over a limit", post("{\"evaluate\":\"" + "x".repeat(300) + "\"}"));
        responses.put("404 unknown decision", post("{\"evaluate\":\"Nope\"}"));
        responses.put("200 instructions", get("/api/business/rules"));
        // produced by Spring before any controller runs
        responses.put("405 wrong method", client.send(HttpRequest.newBuilder(URI.create(url))
                .PUT(HttpRequest.BodyPublishers.ofString("{}")).header("Content-Type", "application/json").build(),
                HttpResponse.BodyHandlers.ofString()));
        responses.put("415 wrong content type", client.send(HttpRequest.newBuilder(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString("{}")).header("Content-Type", "text/plain").build(),
                HttpResponse.BodyHandlers.ofString()));

        responses.forEach((what, response) -> {
            assertThat(response.headers().firstValue("X-Content-Type-Options")).as(what).contains("nosniff");
            assertThat(response.headers().firstValue("Cache-Control")).as(what).contains("no-store");
        });
        assertThat(responses.get("405 wrong method").statusCode()).isEqualTo(405);
        assertThat(responses.get("415 wrong content type").statusCode()).isEqualTo(415);
    }

    @Test
    void missingInputIsUnprocessable() throws Exception {
        assertThat(post("{\"age\":30,\"evaluate\":\"Eligibility\"}").statusCode()).isEqualTo(422);
    }

    @Test
    void missingOrUnknownEvaluateKey() throws Exception {
        assertThat(post("{\"age\":30}").statusCode()).isEqualTo(400);
        HttpResponse<String> unknown = post("{\"evaluate\":\"Nope\"}");
        assertThat(unknown.statusCode()).isEqualTo(404);
        // instructions are enabled in this app, so the error body helps the caller by listing the models
        assertThat(unknown.body()).contains("CustomerRules", "Eligibility");
    }
}
