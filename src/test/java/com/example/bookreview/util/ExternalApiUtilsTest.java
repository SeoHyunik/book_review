package com.example.bookreview.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bookreview.dto.request.ExternalApiRequest;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

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

        ResponseEntity<String> response = externalApiUtils.callAPI(request);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).contains("server error");
    }
}
