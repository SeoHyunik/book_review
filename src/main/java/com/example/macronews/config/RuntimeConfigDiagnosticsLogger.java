package com.example.macronews.config;

import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Logs a single concise, non-secret {@code [RUNTIME-CONFIG]} block at startup so that
 * deployment misconfiguration (missing env keys, disabled Naver, blank credentials) is
 * diagnosable from logs alone.
 *
 * <p>This logger only ever emits presence booleans, counts, and key names. It never logs
 * any secret value, credential, or query content.
 */
@Slf4j
@Component
public class RuntimeConfigDiagnosticsLogger {

    /** Effective default Naver query count applied when {@code app.news.naver.queries} is blank. */
    private static final int DEFAULT_NAVER_QUERY_COUNT = 14;

    /** Required environment variable key names whose presence is reported (never their values). */
    private static final String[] REQUIRED_ENV_KEYS = {
            "NAVER_CLIENT_ID",
            "NAVER_CLIENT_SECRET",
            "OPENAI_API_KEY",
            "APP_NEWS_NAVER_QUERIES",
            "APP_NEWS_NAVER_ENABLED",
    };

    @Value("${app.news.naver.enabled:false}")
    private boolean naverEnabled;

    @Value("${app.news.naver.client-id:}")
    private String naverClientId;

    @Value("${app.news.naver.client-secret:}")
    private String naverClientSecret;

    @Value("${app.news.naver.queries:}")
    private String naverQueries;

    @Value("${openai.api-key:}")
    private String openAiApiKey;

    @EventListener(ApplicationReadyEvent.class)
    public void logRuntimeConfig() {
        log.info(
                "[RUNTIME-CONFIG] {} naverEnabled={} naverClientIdPresent={} "
                        + "naverClientSecretPresent={} naverQueryCount={} openAiKeyPresent={}",
                envKeyPresenceBooleans(),
                naverEnabled,
                StringUtils.hasText(naverClientId),
                StringUtils.hasText(naverClientSecret),
                resolveNaverQueryCount(),
                StringUtils.hasText(openAiApiKey));
    }

    private String envKeyPresenceBooleans() {
        StringBuilder present = new StringBuilder();
        for (int i = 0; i < REQUIRED_ENV_KEYS.length; i++) {
            String key = REQUIRED_ENV_KEYS[i];
            if (i > 0) {
                present.append(' ');
            }
            present.append("env.").append(key).append("Present=")
                    .append(System.getenv().containsKey(key));
        }
        return present.toString();
    }

    private int resolveNaverQueryCount() {
        if (!StringUtils.hasText(naverQueries)) {
            return DEFAULT_NAVER_QUERY_COUNT;
        }
        return (int) Arrays.stream(naverQueries.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .count();
    }
}
