package com.einvoice.core.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the shared {@link Clock} bean used for UTC date/time boundaries
 * (Wave 9 dashboard KPIs and certificate-expiry evaluation). Defaulting to
 * {@link Clock#systemUTC()} keeps production on the wall clock while tests can
 * override it with a fixed clock via {@code @Primary} test configuration.
 */
@Configuration
public class ClockConfiguration {

    /**
     * Registers a UTC system clock as the primary {@link Clock} bean.
     *
     * @return the system UTC clock
     */
    @Bean
    public Clock systemUtcClock() {
        return Clock.systemUTC();
    }
}
