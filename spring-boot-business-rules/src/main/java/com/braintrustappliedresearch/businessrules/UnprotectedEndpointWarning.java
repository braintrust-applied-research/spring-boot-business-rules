package com.braintrustappliedresearch.businessrules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * Logs a warning, once at startup, that the rules endpoint is reachable without authentication.
 *
 * <p>The library adds no authentication of its own, so on its own it exposes {@code POST <path>} to anyone who can
 * reach the application. That also lets a caller work out the rules' thresholds by trying inputs. When Spring Security
 * is on the classpath Spring Boot protects every endpoint by default, so this is only registered when it is absent.
 * To silence it deliberately (for example when a gateway authenticates every request), set the log level of this class
 * to {@code ERROR}.
 */
final class UnprotectedEndpointWarning implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(UnprotectedEndpointWarning.class);

    private final String path;

    UnprotectedEndpointWarning(String path) {
        this.path = path;
    }

    @Override
    public void afterSingletonsInstantiated() {
        log.warn("The rules endpoint '{}' (relative to the server context path) is enabled and UNAUTHENTICATED: Spring "
                + "Security is not on the classpath, so anyone who can reach this application can evaluate your rules "
                + "and probe their thresholds. Add authentication (for example spring-boot-starter-security), or "
                + "disable the endpoint with {}.enabled=false.", path, BusinessRulesProperties.PREFIX);
    }
}
