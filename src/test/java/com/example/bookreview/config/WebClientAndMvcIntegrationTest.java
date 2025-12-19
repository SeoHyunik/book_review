package com.example.bookreview.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest
@AutoConfigureMockMvc
class WebClientAndMvcIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebClient webClient;

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Test
    void contextLoadsWithWebClientAndMockMvc() throws Exception {
        assertThat(mockMvc).isNotNull();
        assertThat(webClient).isNotNull();
        assertThat(webClientBuilder).isNotNull();

        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reviews"));

        WebClient localClient = webClientBuilder.build();
        assertThat(localClient).isNotSameAs(webClient);
    }
}
