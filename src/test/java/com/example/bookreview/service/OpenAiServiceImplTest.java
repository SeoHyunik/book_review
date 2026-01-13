package com.example.bookreview.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bookreview.dto.internal.AiReviewResult;
import com.example.bookreview.service.openai.OpenAiService;
import com.example.bookreview.service.openai.OpenAiServiceImpl;
import com.example.bookreview.util.ExternalApiUtils;
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
        classes = {OpenAiServiceImpl.class, ExternalApiUtils.class, OpenAiServiceImplTest.TestConfig.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenAiServiceImplTest {

    private static MockWebServer mockWebServer;

    @org.springframework.beans.factory.annotation.Autowired
    private OpenAiService openAiService;

    @AfterAll
    void tearDown() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (mockWebServer == null) {
            mockWebServer = new MockWebServer();
            try {
                mockWebServer.start();
            } catch (IOException ex) {
                throw new RuntimeException("Failed to start MockWebServer", ex);
            }
        }
        registry.add("openai.api-key", () -> "test-key");
        registry.add("openai.api-url", () -> mockWebServer.url("/v1/chat/completions").toString());
        registry.add("openai.prompt-file", () -> "classpath:ai/prompts/improve_review_prompt.json");
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
        assertThat(response.fromAi()).isTrue();
        assertThat(response.reason()).isEqualTo("stop");

        RecordedRequest firstRequest = mockWebServer.takeRequest(5, TimeUnit.SECONDS);
        RecordedRequest secondRequest = mockWebServer.takeRequest(5, TimeUnit.SECONDS);

        assertThat(firstRequest).isNotNull();
        assertThat(firstRequest.getMethod()).isEqualTo("POST");
        assertThat(firstRequest.getHeader("Authorization")).isEqualTo("Bearer test-key");
        assertThat(firstRequest.getPath()).isEqualTo("/v1/chat/completions");

        assertThat(secondRequest).isNotNull();
        assertThat(secondRequest.getMethod()).isEqualTo("POST");
    }

    @Test
    void generateImprovedReview_returnsFallbackOnRateLimit() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(429).setBody("{\"error\":\"rate limit\"}"));

        AiReviewResult response = openAiService.generateImprovedReview("Title", "Original review content");

        assertThat(response).isNotNull();
        assertThat(response.fromAi()).isFalse();
        assertThat(response.reason()).isEqualTo("RATE_LIMITED");
        assertThat(response.improvedContent()).contains("[IMPROVEMENT_SKIPPED]");
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
