package com.velocity.api.security;

import com.velocity.api.AbstractApiIntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.velocity.api.security.SecurityTestHelper.asUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who can reach what: 401 without a token, 403 with the wrong role, both as ProblemDetail.
 */
public class SecurityAccessIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/me", "/api/v1/reservations/my", "/api/v1/admin/users", "/api/v1/admin/analytics"})
    void protectedEndpoint_withoutToken_returns401(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/admin/users", "/api/v1/admin/analytics"})
    void adminEndpoint_asClient_returns403(String path) throws Exception {
        mockMvc.perform(get(path).with(asUser(UUID.randomUUID().toString(), "CLIENT")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/admin/users", "/api/v1/admin/analytics"})
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
}
