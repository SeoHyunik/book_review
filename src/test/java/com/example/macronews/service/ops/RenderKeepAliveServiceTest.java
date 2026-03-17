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

    private OpsFeatureToggleService opsFeatureToggleService;
    private RenderKeepAliveService renderKeepAliveService;

    @BeforeEach
    void setUp() {
        opsFeatureToggleService = new OpsFeatureToggleService(true, true);
        renderKeepAliveService = new RenderKeepAliveService(externalApiUtils, opsFeatureToggleService);
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
    @DisplayName("keep-alive should be effectively enabled only when config, runtime, and target are all ready")
    void isEffectivelyEnabled_requiresConfigRuntimeAndTarget() {
        ReflectionTestUtils.setField(renderKeepAliveService, "enabled", true);
        ReflectionTestUtils.setField(renderKeepAliveService, "targetUrl", "https://example.onrender.com/actuator/health");

        assertThatCode(() -> renderKeepAliveService.ping()).doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(renderKeepAliveService.isEffectivelyEnabled()).isTrue();
    }

    @Test
    @DisplayName("ping should skip when keep-alive runtime toggle is disabled")
    void ping_skipsWhenRuntimeDisabled() {
        ReflectionTestUtils.setField(renderKeepAliveService, "enabled", true);
        ReflectionTestUtils.setField(renderKeepAliveService, "targetUrl", "https://example.onrender.com/actuator/health");
        opsFeatureToggleService.disableKeepAlive();

        renderKeepAliveService.ping();

        org.assertj.core.api.Assertions.assertThat(renderKeepAliveService.isEffectivelyEnabled()).isFalse();
        verify(externalApiUtils, never()).callAPI(any());
    }

    @Test
    @DisplayName("ping should remain ineffective when keep-alive is globally disabled")
    void ping_skipsWhenConfigDisabledEvenIfRuntimeEnabled() {
        ReflectionTestUtils.setField(renderKeepAliveService, "enabled", false);
        ReflectionTestUtils.setField(renderKeepAliveService, "targetUrl", "https://example.onrender.com/actuator/health");
        opsFeatureToggleService.enableKeepAlive();

        renderKeepAliveService.ping();

        org.assertj.core.api.Assertions.assertThat(renderKeepAliveService.isEffectivelyEnabled()).isFalse();
        verify(externalApiUtils, never()).callAPI(any());
    }

    @Test
    @DisplayName("ping should skip when keep-alive target is blank")
    void ping_skipsWhenTargetBlank() {
        ReflectionTestUtils.setField(renderKeepAliveService, "enabled", true);
        ReflectionTestUtils.setField(renderKeepAliveService, "targetUrl", "   ");

        renderKeepAliveService.ping();

        org.assertj.core.api.Assertions.assertThat(renderKeepAliveService.isEffectivelyEnabled()).isFalse();
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
