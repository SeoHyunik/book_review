package com.example.bookreview.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bookreview.dto.AiReviewResult;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = {OpenAiServiceImpl.class, ExternalApiUtils.class, TokenCostCalculator.class, OpenAiServiceImplTest.TestConfig.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenAiServiceImplTest {

    private static MockWebServer mockWebServer;

    private final OpenAiService openAiService;

    OpenAiServiceImplTest(OpenAiService openAiService) {
        this.openAiService = openAiService;
    }

    @BeforeAll
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("openai.api-key", () -> "test-key");
        registry.add("openai.api-url", () -> mockWebServer.url("/v1/chat/completions").toString());
    }

    @Test
    void generateImprovedReviewParsesResponse() throws Exception {
        String firstResponse = """
                {"id":"chatcmpl-1","model":"gpt-4o","choices":[{"index":0,"message":{"role":"assistant","content":"Partial"},"finish_reason":"length"}],"usage":{"prompt_tokens":11,"completion_tokens":7}}
                """;
        String secondResponse = """
                {"id":"chatcmpl-2","model":"gpt-4o","choices":[{"index":0,"message":{"role":"assistant","content":"Improved review text"},"finish_reason":"stop"}],"usage":{"prompt_tokens":13,"completion_tokens":9}}
                """;

        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(firstResponse));
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(secondResponse));

        AiReviewResult response = openAiService.generateImprovedReview("Title", "Original review content");

        assertThat(response).isNotNull();
        assertThat(response.improvedContent()).isEqualTo("Improved review text");
        assertThat(response.model()).isEqualTo("gpt-4o");
        assertThat(response.promptTokens()).isEqualTo(13);
        assertThat(response.completionTokens()).isEqualTo(9);

        RecordedRequest firstRequest = mockWebServer.takeRequest(5, TimeUnit.SECONDS);
        RecordedRequest secondRequest = mockWebServer.takeRequest(5, TimeUnit.SECONDS);

        assertThat(firstRequest).isNotNull();
        assertThat(firstRequest.getMethod()).isEqualTo("POST");
        assertThat(firstRequest.getHeader("Authorization")).isEqualTo("Bearer test-key");
        assertThat(firstRequest.getPath()).isEqualTo("/v1/chat/completions");

        assertThat(secondRequest).isNotNull();
        assertThat(secondRequest.getMethod()).isEqualTo("POST");
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        WebClient.Builder webClientBuilder() {
            return WebClient.builder();
        }

        @Bean
        Gson gson() {
            return new Gson();
        }
    }
}
