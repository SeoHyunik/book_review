package com.example.macronews.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PublicNewsAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void newsListIsPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/news"))
                .andExpect(status().isOk());
    }

    @Test
    void newsDetailRouteNoLongerRedirectsAnonymousUserToLogin() throws Exception {
        mockMvc.perform(get("/news/non-existent-id"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/news"));
    }

    @Test
    void adminRouteStillRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/admin/news/manual"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }
}
