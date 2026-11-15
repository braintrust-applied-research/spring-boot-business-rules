package com.braintrustappliedresearch.businessrules;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * The generated {@code spring-configuration-metadata.json} is what IDEs use for property completion and hover docs.
 * It silently disappears when the annotation processor does not run (as on JDK 23+ unless it is named explicitly),
 * and Spring copies Javadoc text into it verbatim, so markup there shows up as literal characters.
 */
class ConfigurationMetadataTest {

    private static String metadata() throws IOException {
        try (InputStream in = ConfigurationMetadataTest.class.getResourceAsStream(
                "/META-INF/spring-configuration-metadata.json")) {
            assertThat(in).as("spring-configuration-metadata.json was not generated").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void describesEveryProperty() throws IOException {
        assertThat(metadata()).contains(
                "\"name\": \"spring-boot-business-rules.enabled\"",
                "\"name\": \"spring-boot-business-rules.path\"",
                "\"name\": \"spring-boot-business-rules.rules-location\"",
                "\"name\": \"spring-boot-business-rules.instructions-enabled\"",
                "\"name\": \"spring-boot-business-rules.limits.max-inputs\"",
                "\"name\": \"spring-boot-business-rules.limits.max-name-length\"",
                "\"name\": \"spring-boot-business-rules.limits.max-value-length\"");
    }

    @Test
    void instructionsDefaultToOff() throws IOException {
        String json = metadata();
        int at = json.indexOf("spring-boot-business-rules.instructions-enabled");
        assertThat(json.substring(at, json.indexOf('}', at))).contains("\"defaultValue\": false");
    }

    @Test
    void descriptionsArePlainText() throws IOException {
        assertThat(metadata()).doesNotContain("{@", "<strong>", "<p>", "<code>");
    }
}
