package com.velocity.api;

import com.velocity.api.factory.TestDataFactory;
import com.velocity.api.support.MutableClock;
import com.velocity.api.support.TestClockConfig;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;


// Base for every full-application test.
// All of them share one configuration, so Spring builds a single context for the whole
// suite. Keep it that way: an extra @MockitoBean, @Import or property in a
// subclass creates a second context and a second application start. Move time with
// clock instead of mocking Clock.

// The database is shared across test classes, so every test leaves it empty.

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@AutoConfigureTestRestTemplate
@Import({TestClockConfig.class, TestDataFactory.class})
public abstract class AbstractApiIntegrationTest extends BaseIntegrationTest {
    @Autowired
    protected MutableClock clock;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @AfterEach
    void resetSharedState() {
        jdbcTemplate.execute("TRUNCATE TABLE reservations, bike_instances, bike_models, users CASCADE");
        clock.reset();
    }
}
