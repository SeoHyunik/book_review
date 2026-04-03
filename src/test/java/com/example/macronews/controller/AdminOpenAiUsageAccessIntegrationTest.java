package com.example.macronews.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.macronews.service.openai.OpenAiUsageReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AdminOpenAiUsageAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OpenAiUsageReportService openAiUsageReportService;

    @Test
    @DisplayName("anonymous user should be redirected away from admin usage page")
    void givenAnonymousUser_whenRequestAdminOpenAiUsage_thenRedirectToLogin() throws Exception {
        mockMvc.perform(get("/admin/openai-usage"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
