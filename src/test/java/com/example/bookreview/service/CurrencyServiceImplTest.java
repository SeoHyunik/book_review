package com.example.bookreview.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bookreview.service.currency.CurrencyService;
import com.example.bookreview.service.currency.CurrencyServiceImpl;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = {CurrencyServiceImpl.class, CurrencyServiceImplTest.TestConfig.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CurrencyServiceImplTest {

    private static MockWebServer mockWebServer;

    @Autowired
    private CurrencyService currencyService;

    @AfterAll
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        try {
            if (mockWebServer == null) {
                mockWebServer = new MockWebServer();
                mockWebServer.start();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        registry.add("exchange-rate.api-key", () -> "test-key");
        registry.add("exchange-rate.api-url", () -> mockWebServer.url("/v6/%s/latest/USD").toString());
    }

    @Test
    void convertUsdToKrw_fetchesRateAndRounds() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"conversion_rates\":{\"KRW\":1333.5}}"));

        BigDecimal converted = currencyService.convertUsdToKrw(new BigDecimal("1.50"));

        assertThat(converted).isEqualByComparingTo("2000");
        RecordedRequest recordedRequest = mockWebServer.takeRequest(5, TimeUnit.SECONDS);
        assertThat(recordedRequest).isNotNull();
        assertThat(recordedRequest.getPath()).isEqualTo("/v6/test-key/latest/USD");
    }

    @Test
    void convertUsdToKrw_returnsFallbackWhenApiFails() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        BigDecimal converted = currencyService.convertUsdToKrw(BigDecimal.ONE);

        assertThat(converted).isEqualByComparingTo("1300");
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        WebClient.Builder webClientBuilder() {
            return WebClient.builder();
        }
    }
}
