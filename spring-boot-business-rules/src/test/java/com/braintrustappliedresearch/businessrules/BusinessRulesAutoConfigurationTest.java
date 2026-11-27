package com.braintrustappliedresearch.businessrules;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Which beans the auto-configuration registers. The GET instructions route must be opt-in, and the endpoint must warn
 * when nothing protects it.
 */
@ExtendWith(OutputCaptureExtension.class)
class BusinessRulesAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(BusinessRulesAutoConfiguration.class));

    /** The web side is opt-in, so every test of the endpoint turns it on explicitly. */
    private final WebApplicationContextRunner webRunner = runner
            .withPropertyValues("spring-boot-business-rules.web.enabled=true");

    @Test
    void theWebSideIsOffByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RulesEngine.class);
            assertThat(context).doesNotHaveBean(RulesController.class);
            assertThat(context).doesNotHaveBean(RulesInstructionsController.class);
            assertThat(context).doesNotHaveBean("rulesResponseHeadersFilter");
            assertThat(context).doesNotHaveBean(UnprotectedEndpointWarning.class);
        });
    }

    @Test
    void theWebSideStaysOffWhenExplicitlyFalse() {
        runner.withPropertyValues("spring-boot-business-rules.web.enabled=false",
                        "spring-boot-business-rules.instructions-enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(RulesController.class)
                        .doesNotHaveBean(RulesInstructionsController.class));
    }

    @Test
    void theWebSideIsServedOnceOptedInto() {
        webRunner.run(context -> {
            assertThat(context).hasSingleBean(RulesController.class);
            assertThat(context).hasBean("rulesResponseHeadersFilter");
        });
    }

    @Test
    void theWebSideDefaultIsFalseInTheBoundProperties() {
        runner.run(context -> assertThat(context.getBean(BusinessRulesProperties.class).web().enabled()).isFalse());
    }

    @Test
    void instructionsAreOffByDefault() {
        webRunner.run(context -> {
            assertThat(context).hasSingleBean(RulesEngine.class);
            assertThat(context).hasSingleBean(RulesController.class);
            assertThat(context).doesNotHaveBean(RulesInstructionsController.class);
        });
    }

    @Test
    void instructionsCanBeOptedInto() {
        webRunner.withPropertyValues("spring-boot-business-rules.instructions-enabled=true")
                .run(context -> assertThat(context).hasSingleBean(RulesInstructionsController.class));
    }

    @Test
    void instructionsStayOffWhenExplicitlyDisabled() {
        webRunner.withPropertyValues("spring-boot-business-rules.instructions-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(RulesInstructionsController.class));
    }

    @Test
    void disablingTheEndpointRemovesTheWebBeansButKeepsTheEngine() {
        webRunner.withPropertyValues("spring-boot-business-rules.web.enabled=false",
                        "spring-boot-business-rules.instructions-enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(RulesEngine.class);
                    assertThat(context).doesNotHaveBean(RulesController.class);
                    assertThat(context).doesNotHaveBean(RulesInstructionsController.class);
                });
    }

    @Test
    void theOldEnabledPropertyIsRefusedRatherThanSilentlyIgnored() {
        webRunner.withPropertyValues("spring-boot-business-rules.enabled=false")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .rootCause().hasMessageContaining("renamed to spring-boot-business-rules.web.enabled"));
    }

    @Test
    void theEngineIsAvailableInANonWebApplication() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(BusinessRulesAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(RulesEngine.class);
                    assertThat(context).doesNotHaveBean(RulesController.class);
                    assertThat(context).doesNotHaveBean("rulesResponseHeadersFilter");
                    assertThat(context).doesNotHaveBean(UnprotectedEndpointWarning.class);
                });
    }

    @Test
    void theEngineEvaluatesWithTheEndpointDisabled() {
        webRunner.withPropertyValues("spring-boot-business-rules.web.enabled=false",
                        "spring-boot-business-rules.rules-location=classpath:test-models/model-chain.dmn")
                .run(context -> {
                    RulesEngine.Evaluation evaluation = context.getBean(RulesEngine.class)
                            .evaluate("ModelChain", "Top", Map.of("n", 5));
                    assertThat(evaluation.failed()).isFalse();
                    assertThat((BigDecimal) evaluation.result()).isEqualByComparingTo("11");
                });
    }

    @Test
    void warnsAtStartupWhenSpringSecurityIsAbsent(CapturedOutput output) {
        webRunner.withClassLoader(new FilteredClassLoader(SecurityFilterChain.class)).run(context -> {
            assertThat(context).hasSingleBean(UnprotectedEndpointWarning.class);
            assertThat(output).contains("UNAUTHENTICATED").contains("removing spring-boot-business-rules.web.enabled=true");
        });
    }

    @Test
    void staysQuietWhenSpringSecurityIsPresent(CapturedOutput output) {
        // spring-security-web is on this module's test classpath, as it would be in a secured host application
        webRunner.run(context -> {
            assertThat(context).doesNotHaveBean(UnprotectedEndpointWarning.class);
            assertThat(output).doesNotContain("UNAUTHENTICATED");
        });
    }

    @Test
    void noWarningWhenTheEndpointIsDisabled(CapturedOutput output) {
        webRunner.withClassLoader(new FilteredClassLoader(SecurityFilterChain.class))
                .withPropertyValues("spring-boot-business-rules.web.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(UnprotectedEndpointWarning.class);
                    assertThat(output).doesNotContain("UNAUTHENTICATED");
                });
    }

    @Test
    void limitsBindToTheSameDefaultsTheControllerUses() {
        webRunner.run(context -> assertThat(context.getBean(BusinessRulesProperties.class).limits())
                .isEqualTo(BusinessRulesProperties.Limits.defaults()));
    }

    @Test
    void limitsCanBeConfigured() {
        webRunner.withPropertyValues("spring-boot-business-rules.limits.max-inputs=7",
                        "spring-boot-business-rules.limits.max-name-length=30",
                        "spring-boot-business-rules.limits.max-value-length=1234",
                        "spring-boot-business-rules.limits.max-body-bytes=4096")
                .run(context -> assertThat(context.getBean(BusinessRulesProperties.class).limits())
                        .isEqualTo(new BusinessRulesProperties.Limits(7, 30, 1234, 4096)));
    }

    @Test
    void theResponseHeadersFilterCoversExactlyTheEndpointPath() {
        webRunner.run(context -> {
            FilterRegistrationBean<?> registration = (FilterRegistrationBean<?>) context.getBean("rulesResponseHeadersFilter");
            assertThat(registration.getUrlPatterns()).containsExactly("/rules");
        });
    }

    @Test
    void theFilterPathFollowsTheConfiguredPathAndTheServletPrefix() {
        webRunner.withPropertyValues("spring-boot-business-rules.path=business/rules", "spring.mvc.servlet.path=/svc/")
                .run(context -> assertThat(((FilterRegistrationBean<?>) context.getBean("rulesResponseHeadersFilter"))
                        .getUrlPatterns()).containsExactly("/svc/business/rules"));
    }

    @Test
    void noFilterWhenTheEndpointIsDisabled() {
        webRunner.withPropertyValues("spring-boot-business-rules.web.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean("rulesResponseHeadersFilter"));
    }
}
