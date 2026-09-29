package com.velocity.api.user.controller;

import com.velocity.api.AbstractApiIntegrationTest;
import com.velocity.api.factory.TestDataFactory;
import com.velocity.api.user.User;
import com.velocity.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.velocity.api.security.SecurityTestHelper.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class UserControllerTest extends AbstractApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestDataFactory testDataFactory;
    @Autowired
    private UserRepository userRepository;

    @Test
    public void updateProfile_differentUser_returnsForbidden() throws Exception {
        User victim = testDataFactory.createAndSaveDefaultUser();
        UUID hackerId = UUID.randomUUID();

        mockMvc.perform(
                        patch("/api/v1/users/" + victim.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                         { "fullName": "Hacked Name" }
                                        """)
                                .with(asUser(hackerId.toString(), "CLIENT"))
                )
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(victim.getId()).orElseThrow().getFullName())
                .isEqualTo(victim.getFullName());
    }

    @Test
    public void updateProfile_sameUser_returnsNoContent() throws Exception {
        User user = testDataFactory.createAndSaveDefaultUser();

        mockMvc.perform(
                        patch("/api/v1/users/" + user.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                         { "fullName": "Changed Name" }
                                        """)
                                .with(asUser(user.getId().toString(), "CLIENT"))
                )
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(user.getId()).orElseThrow().getFullName())
                .isEqualTo("Changed Name");
    }
}
