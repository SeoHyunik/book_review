package com.example.macronews.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.macronews.dto.request.ExternalApiRequest;
import java.lang.reflect.Method;
import java.io.IOException;
import java.time.Duration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExternalApiUtilsTest {

    private MockWebServer mockWebServer;
    private ExternalApiUtils externalApiUtils;

    @BeforeAll
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        externalApiUtils = new ExternalApiUtils(WebClient.builder());
    }

    @AfterAll
    void tearDown() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    @Test
    void callApi_returnsStatusForServerError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("server error"));

        ExternalApiRequest request = new ExternalApiRequest(
                HttpMethod.GET,
                null,
                mockWebServer.url("/error").toString(),
                null);

        ExternalApiResult response = externalApiUtils.callAPI(request);

        assertThat(response).isNotNull();
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).contains("server error");
    }

    @Test
    void givenSuccessfulResponse_whenCallApiAsync_thenReturnResult() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        ExternalApiRequest request = new ExternalApiRequest(
                HttpMethod.GET,
                null,
                mockWebServer.url("/async").toString(),
                null);

        ExternalApiResult response = externalApiUtils.callAPIAsync(request).block();

        assertThat(response).isNotNull();
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("ok");
    }

    @Test
    void givenNeverRespondingExternalApi_whenCallApi_thenReturnGatewayTimeout() {
        externalApiUtils = new ExternalApiUtils(
                WebClient.builder().exchangeFunction(request -> Mono.never()));
        ReflectionTestUtils.setField(externalApiUtils, "timeout", Duration.ofMillis(50));

        ExternalApiRequest request = new ExternalApiRequest(
                HttpMethod.GET,
                null,
                "https://example.com/timeout",
                null);

        ExternalApiResult response = externalApiUtils.callAPI(request);

        assertThat(response).isNotNull();
        assertThat(response.statusCode()).isEqualTo(504);
        assertThat(response.body()).contains("timed out");
    }

    @Test
    void sanitizeUrl_masksServiceKeyQueryParameter() throws Exception {
        Method sanitizeUrl = ExternalApiUtils.class.getDeclaredMethod("sanitizeUrl", String.class);
        sanitizeUrl.setAccessible(true);

        String sanitized = (String) sanitizeUrl.invoke(
                externalApiUtils,
                "https://apis.data.go.kr/test?serviceKey=secret-value&resultType=json");

        assertThat(sanitized).contains("serviceKey=****(masked)");
        assertThat(sanitized).doesNotContain("secret-value");
    }
}
