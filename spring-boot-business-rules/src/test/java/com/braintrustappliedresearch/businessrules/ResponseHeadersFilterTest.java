package com.braintrustappliedresearch.businessrules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** The defensive headers are added to every response, but never override one that is already set. */
class ResponseHeadersFilterTest {

    private final ResponseHeadersFilter filter = new ResponseHeadersFilter();

    @Test
    void addsNosniffAndNoStore() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest("POST", "/rules"), response, chain);

        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(chain.getRequest()).as("the request must continue down the chain").isNotNull();
    }

    @Test
    void leavesHeadersThatAreAlreadySet() throws Exception {
        // for example Spring Security, which sets its own, stricter Cache-Control
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
        response.setHeader("X-Content-Type-Options", "nosniff");

        filter.doFilter(new MockHttpServletRequest("GET", "/rules"), response, new MockFilterChain());

        assertThat(response.getHeaders("Cache-Control")).containsExactly("no-cache, no-store, max-age=0, must-revalidate");
        assertThat(response.getHeaders("X-Content-Type-Options")).containsExactly("nosniff");
    }
}
