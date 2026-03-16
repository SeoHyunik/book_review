package com.example.macronews.service.ops;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RenderKeepAliveServiceTest {

    @Mock
    private ExternalApiUtils externalApiUtils;

    private RenderKeepAliveService renderKeepAliveService;

    @BeforeEach
    void setUp() {
        renderKeepAliveService = new RenderKeepAliveService(externalApiUtils);
    }

    @Test
    @DisplayName("ping should call configured target when keep-alive is enabled")
    void ping_callsTargetWhenEnabled() {
        ReflectionTestUtils.setField(renderKeepAliveService, "enabled", true);
        ReflectionTestUtils.setField(renderKeepAliveService, "targetUrl", "https://example.onrender.com/actuator/health");
        when(externalApiUtils.callAPI(any(ExternalApiRequest.class))).thenReturn(new ExternalApiResult(200, "{}"));

        renderKeepAliveService.ping();

        ArgumentCaptor<ExternalApiRequest> captor = ArgumentCaptor.forClass(ExternalApiRequest.class);
        verify(externalApiUtils).callAPI(captor.capture());
        ExternalApiRequest request = captor.getValue();
        HttpHeaders headers = request.headers();
        org.assertj.core.api.Assertions.assertThat(request.method()).isEqualTo(HttpMethod.GET);
        org.assertj.core.api.Assertions.assertThat(request.url())
                .isEqualTo("https://example.onrender.com/actuator/health");
        org.assertj.core.api.Assertions.assertThat(headers.getFirst(HttpHeaders.USER_AGENT))
                .isEqualTo("MNI-KeepAlive");
    }

    @Test
    @DisplayName("ping should skip when keep-alive is disabled or target is blank")
    void ping_skipsWhenDisabledOrMissingTarget() {
        ReflectionTestUtils.setField(renderKeepAliveService, "enabled", false);
        ReflectionTestUtils.setField(renderKeepAliveService, "targetUrl", "https://example.onrender.com/actuator/health");

        renderKeepAliveService.ping();

        verify(externalApiUtils, never()).callAPI(any());

        ReflectionTestUtils.setField(renderKeepAliveService, "enabled", true);
        ReflectionTestUtils.setField(renderKeepAliveService, "targetUrl", "   ");

        renderKeepAliveService.ping();

        verify(externalApiUtils, never()).callAPI(any());
    }

    @Test
    @DisplayName("ping should not throw when remote keep-alive target fails")
    void ping_doesNotThrowOnFailureResponse() {
        ReflectionTestUtils.setField(renderKeepAliveService, "enabled", true);
        ReflectionTestUtils.setField(renderKeepAliveService, "targetUrl", "https://example.onrender.com/actuator/health");
        when(externalApiUtils.callAPI(any(ExternalApiRequest.class))).thenReturn(new ExternalApiResult(503, "unavailable"));

        assertThatCode(() -> renderKeepAliveService.ping()).doesNotThrowAnyException();
    }
}
