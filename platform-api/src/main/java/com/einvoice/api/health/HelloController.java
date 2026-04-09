package com.einvoice.api.health;

import java.time.Instant;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Temporary verification endpoint for end-to-end connectivity testing.
 */
@RestController
@RequestMapping("/api/health")
public class HelloController {

    private final Environment environment;

    public HelloController(Environment environment) {
        this.environment = environment;
    }

    /**
     * Returns a greeting with server timestamp and active profiles.
     *
     * @return health check response
     */
    @GetMapping("/hello")
    public HelloResponse hello() {
        return new HelloResponse(
                "Hello from E-Invoicing Platform",
                Instant.now(),
                List.of(environment.getActiveProfiles())
        );
    }

    /** Health check response payload. */
    public record HelloResponse(String message, Instant timestamp, List<String> profiles) {
    }
}
