package com.braintrustappliedresearch.businessrules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
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

    @Test
    void instructionsAreOffByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RulesEngine.class);
            assertThat(context).hasSingleBean(RulesController.class);
            assertThat(context).doesNotHaveBean(RulesInstructionsController.class);
        });
    }

    @Test
    void instructionsCanBeOptedInto() {
        runner.withPropertyValues("spring-boot-business-rules.instructions-enabled=true")
                .run(context -> assertThat(context).hasSingleBean(RulesInstructionsController.class));
    }

    @Test
    void instructionsStayOffWhenExplicitlyDisabled() {
        runner.withPropertyValues("spring-boot-business-rules.instructions-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(RulesInstructionsController.class));
    }

    @Test
    void disablingTheLibraryRemovesEverything() {
        runner.withPropertyValues("spring-boot-business-rules.enabled=false",
                        "spring-boot-business-rules.instructions-enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RulesController.class);
                    assertThat(context).doesNotHaveBean(RulesInstructionsController.class);
                });
    }

    @Test
    void warnsAtStartupWhenSpringSecurityIsAbsent(CapturedOutput output) {
        runner.withClassLoader(new FilteredClassLoader(SecurityFilterChain.class)).run(context -> {
            assertThat(context).hasSingleBean(UnprotectedEndpointWarning.class);
            assertThat(output).contains("UNAUTHENTICATED").contains("spring-boot-business-rules.enabled=false");
        });
    }

    @Test
    void staysQuietWhenSpringSecurityIsPresent(CapturedOutput output) {
        // spring-security-web is on this module's test classpath, as it would be in a secured host application
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(UnprotectedEndpointWarning.class);
            assertThat(output).doesNotContain("UNAUTHENTICATED");
        });
    }

    @Test
    void noWarningWhenTheEndpointIsDisabled(CapturedOutput output) {
        runner.withClassLoader(new FilteredClassLoader(SecurityFilterChain.class))
                .withPropertyValues("spring-boot-business-rules.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(UnprotectedEndpointWarning.class);
                    assertThat(output).doesNotContain("UNAUTHENTICATED");
                });
    }
}
