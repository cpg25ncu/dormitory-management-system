package io.github.cpg25ncu.dormitory;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the application clock with one that tests can pin to an instant. */
@TestConfiguration(proxyBeanMethods = false)
class TestClockConfiguration {
    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(ApiIntegrationTest.RESIDENCE_ZONE);
    }
}
