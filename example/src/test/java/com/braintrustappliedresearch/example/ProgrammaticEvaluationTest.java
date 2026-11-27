package com.braintrustappliedresearch.example;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.braintrustappliedresearch.businessrules.RulesEngine;

/**
 * Rules can be evaluated from Java, with no HTTP call, and that still works when the REST endpoint is switched off.
 * This runs the {@link OfferService} that the README quotes.
 */
@SpringBootTest(properties = "spring-boot-business-rules.web.enabled=false")
class ProgrammaticEvaluationTest {

    @Autowired
    OfferService offers;

    @Test
    void evaluatesANamedModelDirectly() {
        assertThat(offers.eligibility("Ada", "Lovelace", 36, 120000, "94105")).isEqualTo("Approved");
    }

    @Autowired
    RulesEngine rules;

    @Test
    void aMissingInputIsReportedAsFailedNotThrown() {
        assertThat(rules.evaluate("CustomerRules", "Eligibility", Map.of()).failed()).isTrue();
    }
}
