package com.braintrustappliedresearch.example;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** The GET instructions route is opt-in: with it switched off, only POST exists and nothing reveals the models. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring-boot-business-rules.instructions-enabled=false")
class InstructionsDisabledTest {

    private static final String PATH = "/api/business/rules";

    @LocalServerPort
    int port;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> get() throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + PATH)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String json) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + PATH))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void getIsNotServed() throws Exception {
        HttpResponse<String> response = get();
        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.headers().firstValue("Allow").orElse("")).contains("POST").doesNotContain("GET");
        assertThat(response.body()).doesNotContain("CustomerRules", "Eligibility", "firstName");
    }

    @Test
    void postStillEvaluates() throws Exception {
        HttpResponse<String> response = post("""
                {"firstName":"Ada","lastName":"Lovelace","age":36,"income":120000,"zipCode":"94105","evaluate":"Eligibility"}""");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"result\":\"Approved\"");
    }

    @Test
    void aSuccessfulResponseRevealsNothingAboutTheModel() throws Exception {
        HttpResponse<String> response = post("""
                {"firstName":"A","lastName":"B","age":40,"income":50000,"zipCode":"10001","evaluate":"Eligibility"}""");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"decisions\":{\"Eligibility\":\"Referred\"}");
        assertThat(response.body()).doesNotContain("CustomerRules", "Offer", "Greeting");
    }

    @Test
    void anErrorMessageNamesNeitherInputsNorDecisionsNorDmnIds() throws Exception {
        HttpResponse<String> response = post("{\"evaluate\":\"Eligibility\"}");   // inputs missing
        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.body()).doesNotContain("Required dependency", "'age'", "'income'", "d_eligibility",
                "CustomerRules", "Offer", "Greeting");
        assertThat(response.body()).contains("instructions-enabled");
    }

    @Test
    void allIsNotAvailableWhenInstructionsAreOff() throws Exception {
        HttpResponse<String> response = post("""
                {"firstName":"A","lastName":"B","age":40,"income":50000,"zipCode":"10001","evaluate":"all"}""");
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).doesNotContain("Eligibility", "Greeting", "Offer", "CustomerRules");
    }

    @Test
    void unknownDecisionDoesNotLeakModels() throws Exception {
        HttpResponse<String> response = post("{\"evaluate\":\"Nope\"}");
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).doesNotContain("CustomerRules", "Eligibility", "models");
    }
}
