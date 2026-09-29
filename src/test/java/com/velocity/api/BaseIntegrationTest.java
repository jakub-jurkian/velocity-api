package com.velocity.api;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Real PostgreSQL and Redis for every test that touches them.

// The containers are started once per test run and never stopped by hand (Testcontainers
// removes them when the JVM exits). Deliberately not @Testcontainers/@Container -
// those restart the containers for every test class on a new port, while Spring keeps a cached
// context pointing at the old one - the next class to reuse that context fails with
// "connection refused".

@ActiveProfiles("test")
public abstract class BaseIntegrationTest {
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> redis = new GenericContainer<>("redis:8.10.1-alpine").withExposedPorts(6379);

    static {
        postgres.start();
        redis.start();
    }
}
