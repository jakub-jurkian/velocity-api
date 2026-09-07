package com.velocity.api.user.controller;

import com.velocity.api.BaseIntegrationTest;
import com.velocity.api.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.velocity.api.security.SecurityTestHelper.asUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class UserControllerTest extends BaseIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UserService userService;

    @Test
    public void updateProfile_differentUser_returnsForbidden() throws Exception {
        UUID hackerId = UUID.randomUUID();
        UUID victimId = UUID.randomUUID();

        mockMvc.perform(
                        patch("/api/v1/users/" + victimId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                         { "fullName": "Hacked Name" }
                                        """)
                                .with(asUser(hackerId.toString(), "CLIENT"))
                )
                .andExpect(status().isForbidden());

    }

    @Test
    public void updateProfile_sameUser_returnsNoContent() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(
                        patch("/api/v1/users/" + userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                         { "fullName": "Changed Name" }
                                        """)
                                .with(asUser(userId.toString(), "CLIENT"))
                )
                .andExpect(status().isNoContent());

    }
}
