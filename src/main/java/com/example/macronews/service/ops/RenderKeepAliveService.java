package com.example.macronews.service.ops;

import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class RenderKeepAliveService {

    private final ExternalApiUtils externalApiUtils;
    private final OpsFeatureToggleService opsFeatureToggleService;

    @Value("${app.keep-alive.enabled:false}")
    private boolean enabled;

    @Value("${app.keep-alive.target-url:}")
    private String targetUrl;

    public boolean isConfigured() {
        return enabled;
    }

    public boolean isRuntimeEnabled() {
        return opsFeatureToggleService.isKeepAliveRuntimeEnabled();
    }

    public boolean hasTargetUrl() {
        return StringUtils.hasText(targetUrl);
    }

    public boolean isEffectivelyEnabled() {
        return isConfigured() && isRuntimeEnabled() && hasTargetUrl();
    }

    public boolean isEnabled() {
        return isEffectivelyEnabled();
    }

    @Scheduled(cron = "${app.keep-alive.cron:0 */10 * * * *}")
    public void ping() {
        if (!isConfigured()) {
            return;
        }
        if (!isRuntimeEnabled()) {
            log.debug("[KEEP-ALIVE] skipped reason=runtime-disabled");
            return;
        }
        if (!hasTargetUrl()) {
            log.debug("[KEEP-ALIVE] skipped reason=missing-target-url");
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.USER_AGENT, "MNI-KeepAlive");
        ExternalApiResult result = externalApiUtils.callAPI(new ExternalApiRequest(
                HttpMethod.GET,
                headers,
                targetUrl.trim(),
                null
        ));
        if (result == null || result.statusCode() < 200 || result.statusCode() >= 300) {
            log.warn("[KEEP-ALIVE] ping failed url={} status={}",
                    targetUrl, result == null ? -1 : result.statusCode());
            return;
        }
        log.debug("[KEEP-ALIVE] ping success url={} status={}", targetUrl, result.statusCode());
    }
}
