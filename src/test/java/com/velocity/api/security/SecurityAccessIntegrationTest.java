package com.velocity.api.security;

import com.velocity.api.AbstractApiIntegrationTest;
import com.velocity.api.bike.BikeInstance;
import com.velocity.api.bike.BikeStatus;
import com.velocity.api.bike.repository.BikeInstanceRepository;
import com.velocity.api.factory.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.velocity.api.security.SecurityTestHelper.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who can reach what: 401 without a token, 403 with the wrong role, both as ProblemDetail.
 */
public class SecurityAccessIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestDataFactory testDataFactory;
    @Autowired
    private BikeInstanceRepository bikeInstanceRepository;

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/me", "/api/v1/reservations/my", "/api/v1/admin/users", "/api/v1/admin/analytics", "/api/v1/admin/bikes"})
    void protectedEndpoint_withoutToken_returns401(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/admin/users", "/api/v1/admin/analytics", "/api/v1/admin/bikes"})
    void adminEndpoint_asClient_returns403(String path) throws Exception {
        mockMvc.perform(get(path).with(asUser(UUID.randomUUID().toString(), "CLIENT")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/admin/users", "/api/v1/admin/analytics", "/api/v1/admin/bikes"})
    void adminEndpoint_asAdmin_returns200(String path) throws Exception {
        mockMvc.perform(get(path).with(asUser(UUID.randomUUID().toString(), "ADMIN")))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"passwordHash", "doesNotExist"})
    void adminUserList_sortByFieldOutsideAllowList_returns400(String field) throws Exception {
        mockMvc.perform(get("/api/v1/admin/users?sort=" + field).with(asUser(UUID.randomUUID().toString(), "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Sort Parameter"));
    }

    @Test
    void bikeStatusChange_asClient_returns403AndLeavesBikeUnchanged() throws Exception {
        BikeInstance bike = testDataFactory.createAndSaveDefaultBike();

        mockMvc.perform(patch("/api/v1/admin/bikes/" + bike.getId() + "/status")
                        .with(asUser(UUID.randomUUID().toString(), "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "MAINTENANCE", "version": %d}
                                """.formatted(bike.getVersion())))
                .andExpect(status().isForbidden());

        assertThat(bikeInstanceRepository.findById(bike.getId()).orElseThrow().getStatus()).isEqualTo(BikeStatus.ACTIVE);
    }

    @Test
    void adminBikeList_withTheFrontendsSort_returns200() throws Exception {
        testDataFactory.createAndSaveDefaultBike();

        mockMvc.perform(get("/api/v1/admin/bikes?sort=city,bikeModel.name,id,asc")
                        .with(asUser(UUID.randomUUID().toString(), "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }
}
