/**
 * Drop-in DMN business rules for Spring Boot.
 *
 * <p>Add the {@code spring-boot-business-rules} artifact to a Spring Boot application and put DMN ({@code .dmn})
 * models under {@code src/main/resources/rules/}. {@link com.braintrustappliedresearch.businessrules.BusinessRulesAutoConfiguration}
 * then registers a {@link com.braintrustappliedresearch.businessrules.RulesEngine} (a thin wrapper over the KIE DMN
 * runtime) and a {@link com.braintrustappliedresearch.businessrules.RulesController} that exposes the models over REST.
 * No code is required.
 *
 * <p>Configuration lives under the {@code spring-boot-business-rules} prefix; see
 * {@link com.braintrustappliedresearch.businessrules.BusinessRulesProperties}.
 *
 * <p>A {@code GET} help route that lists the loaded models is available but <strong>off by default</strong>; enable it
 * with {@code spring-boot-business-rules.instructions-enabled=true} (see
 * {@link com.braintrustappliedresearch.businessrules.RulesInstructionsController}).
 *
 * <p>The endpoint takes a JSON {@code POST} whose keys are DMN inputs, plus an {@code evaluate} key naming the
 * decision to run:
 *
 * <pre>{@code
 * curl -X POST localhost:8080/api/business/rules -H 'Content-Type: application/json' \
 *   -d '{"age":36,"income":120000,"evaluate":"Eligibility"}'
 * }</pre>
 *
 * @see <a href="https://www.braintrustappliedresearch.com/spring-boot-business-rules/">Project documentation</a>
 */
package com.braintrustappliedresearch.businessrules;
