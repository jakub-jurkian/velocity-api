package com.velocity.api.security;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.Authentication;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import static com.velocity.api.security.SecurityTestHelper.asUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@WithMockCustomUser
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL) // Tells JUnit to let Spring inject
@RequiredArgsConstructor // creates constructor
public class ReservationIntegrationTest {

    private final MockMvc mockMvc;

    @Test
    void getAuthentication_customAnnotationPresent_returnsMockUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "auth should not be null");
        assertEquals("123e4567-e89b-12d3-a456-426614174000", auth.getName());
    }

    @Test
    void perform_asUserHelperInjected_authenticatesRequest() throws Exception {
        String testUserId = "999e4567-e89b-12d3-a456-426614174999";
        mockMvc.perform(
                        get("/v1/fake-endpoint-does-not-exist") // Fire at a fake URL
                                .with(asUser(testUserId, "CLIENT"))     // <-- Inject our helper!
                )
                .andExpect(status().isNotFound())
                .andExpect(authenticated().withUsername(testUserId))
                .andExpect(authenticated().withRoles("CLIENT"));
    }
}
