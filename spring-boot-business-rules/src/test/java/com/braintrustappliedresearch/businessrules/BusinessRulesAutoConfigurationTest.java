package com.braintrustappliedresearch.businessrules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

/** Which beans the auto-configuration registers. The GET instructions route must be opt-in. */
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
}
