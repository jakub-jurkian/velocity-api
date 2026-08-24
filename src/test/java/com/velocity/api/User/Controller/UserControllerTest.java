package com.velocity.api.User.Controller;

import com.velocity.api.config.SecurityConfig;
import com.velocity.api.security.JwtAuthenticationFilter;
import com.velocity.api.user.controller.UserController;
import com.velocity.api.user.service.UserService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.velocity.api.security.SecurityTestHelper.asUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
public class UserControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthFilter;


    @BeforeEach
    void allowFilterChain() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthFilter).doFilter(any(), any(), any());
    }

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
