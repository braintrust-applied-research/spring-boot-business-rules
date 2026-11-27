package com.braintrustappliedresearch.example;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.braintrustappliedresearch.businessrules.RulesEngine;

/**
 * Shows how application code evaluates a rule directly, with no HTTP call. The README's "Evaluating from Java" section
 * quotes this class, and {@code ProgrammaticEvaluationTest} runs it.
 */
@Service
class OfferService {

    private final RulesEngine rules;

    OfferService(RulesEngine rules) {
        this.rules = rules;
    }

    String eligibility(String firstName, String lastName, int age, int income, String zipCode) {
        RulesEngine.Evaluation e = rules.evaluate("CustomerRules", "Eligibility",
                Map.of("firstName", firstName, "lastName", lastName, "age", age, "income", income, "zipCode", zipCode));
        if (e.failed()) {
            throw new IllegalStateException("Rule did not decide: " + e.messages());
        }
        return (String) e.result();
    }
}
