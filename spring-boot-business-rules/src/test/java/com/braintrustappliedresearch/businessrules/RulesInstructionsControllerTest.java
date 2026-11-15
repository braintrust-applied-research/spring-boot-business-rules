package com.braintrustappliedresearch.businessrules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The usage text must be built from configuration, never from the request's own URL: a forged Host header (or a query
 * string or path parameter) must not be able to plant text or a hostile address in the response.
 */
class RulesInstructionsControllerTest {

    private static MockMvc mvc(String servletPath) {
        RulesEngine engine = new RulesEngine(List.of(new ClassPathResource("test-models/model-a.dmn")));
        BusinessRulesProperties properties = new BusinessRulesProperties(true, "/business/rules",
                "classpath*:rules/**/*.dmn", true, BusinessRulesProperties.Limits.defaults());
        return MockMvcBuilders.standaloneSetup(new RulesInstructionsController(engine, properties, servletPath))
                .addPlaceholderValue(BusinessRulesProperties.PREFIX + ".path", "/business/rules")
                .build();
    }

    @Test
    void pathsComeFromConfigurationNotFromTheRequest() throws Exception {
        String body = mvc("").perform(get("/api/business/rules?injected=evil-query")
                        .contextPath("/api")
                        .header("Host", "evil.example")
                        .header("X-Forwarded-Host", "evil-forwarded.example")
                        .with(request -> {
                            request.setServerName("evil.example");
                            return request;
                        }))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"GET /api/business/rules\"", "\"POST /api/business/rules\"");
        assertThat(body).contains("curl -X POST <base-url>/api/business/rules");
        assertThat(body).doesNotContain("evil", "http://", "https://");
    }

    @Test
    void theServletPrefixIsIncluded() throws Exception {
        String body = mvc("/svc").perform(get("/api/svc/business/rules").contextPath("/api").servletPath("/svc"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"POST /api/svc/business/rules\"");
    }

    @Test
    void pathParametersAreNotRepeated() throws Exception {
        // whether or not Spring matches ";evil=1", it must never come back in the text
        String body = mvc("").perform(get("/api/business/rules;evil=1").contextPath("/api"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("evil");
    }

    @Test
    void theServletPrefixIsReadFromTheEnvironmentAndNormalised() {
        assertThat(BusinessRulesAutoConfiguration.servletPrefix(new MockEnvironment())).isEmpty();
        assertThat(BusinessRulesAutoConfiguration.servletPrefix(env("/"))).isEmpty();
        assertThat(BusinessRulesAutoConfiguration.servletPrefix(env("/svc/"))).isEqualTo("/svc");
        assertThat(BusinessRulesAutoConfiguration.servletPrefix(env("svc"))).isEqualTo("/svc");
    }

    private static MockEnvironment env(String servletPath) {
        return new MockEnvironment().withProperty("spring.mvc.servlet.path", servletPath);
    }
}
