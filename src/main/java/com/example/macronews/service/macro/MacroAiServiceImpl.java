package com.example.macronews.service.macro;

import com.example.macronews.domain.AnalysisResult;
import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.domain.OpenAiUsageFeatureType;
import com.example.macronews.repository.NewsEventRepository;
import com.example.macronews.service.openai.OpenAiUsageLoggingService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
@RequiredArgsConstructor
public class MacroAiServiceImpl implements MacroAiService {

    private final MacroAiPromptBuilder macroAiPromptBuilder;
    private final MacroAiClient macroAiClient;
    private final MacroAiResponseParser macroAiResponseParser;
    private final NewsEventRepository newsEventRepository;
    private final OpenAiUsageLoggingService openAiUsageLoggingService;

    @Value("${openai.api-key:}")
    private String openAiApiKey;

    @Value("${openai.api-url:}")
    private String openAiUrl;

    @Value("${openai.interpretation-model:${openai.model:gpt-4o-mini}}")
    private String interpretationModel;

    @Value("${openai.max-tokens:800}")
    private int openAiMaxTokens;

    @Value("${openai.temperature:0.2}")
    private double openAiTemperature;

    @Value("${openai.macro-prompt-file:classpath:ai/prompts/macro_interpretation_prompt.json}")
    private Resource macroPromptFile;

    @Override
    public AnalysisResult interpret(NewsEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        log.info("[INTERPRET] start id={} status={}", event.id(), event.status());

        validateConfig();

        String payload = macroAiPromptBuilder.buildPayload(
                event, interpretationModel, openAiMaxTokens, openAiTemperature, macroPromptFile);
        var apiResult = macroAiClient.call(openAiApiKey, openAiUrl, payload);

        if (apiResult == null) {
            throw new IllegalStateException("OpenAI interpretation response was null");
        }
        if (apiResult.statusCode() < 200 || apiResult.statusCode() >= 300) {
            throw new IllegalStateException(
                    "OpenAI interpretation failed with status=" + apiResult.statusCode());
        }
        openAiUsageLoggingService.recordUsage(
                OpenAiUsageFeatureType.MACRO_INTERPRETATION,
                interpretationModel,
                apiResult.body());

        AnalysisResult result = macroAiResponseParser.parseAnalysisResult(apiResult.body(), interpretationModel);
        log.info("[INTERPRET] success id={} macroImpacts={} marketImpacts={}", event.id(),
                result.macroImpacts() == null ? 0 : result.macroImpacts().size(),
                result.marketImpacts() == null ? 0 : result.marketImpacts().size());
        return result;
    }

    @Override
    @CacheEvict(cacheNames = "newsDetail", key = "#newsEventId", beforeInvocation = true)
    public NewsEvent interpretAndSave(String newsEventId) {
        log.info("[INTERPRET] persist-start id={}", newsEventId);
        NewsEvent event = newsEventRepository.findById(newsEventId)
                .orElseThrow(() -> new IllegalArgumentException("NewsEvent not found: " + newsEventId));
        Instant attemptedAt = Instant.now();

        try {
            AnalysisResult analysisResult = interpret(event);
            NewsEvent analyzed = copyWithStatusAndResult(event, NewsStatus.ANALYZED, analysisResult, attemptedAt);
            NewsEvent saved = newsEventRepository.save(analyzed);
            log.info("[INTERPRET] persist-success id={} status={}", saved.id(), saved.status());
            return saved;
        } catch (Exception ex) {
            log.error("[INTERPRET] persist-failure id={}", newsEventId, ex);
            NewsEvent failed = copyWithStatusAndResult(event, NewsStatus.FAILED, null, attemptedAt);
            NewsEvent saved = newsEventRepository.save(failed);
            log.info("[INTERPRET] persisted-failed id={} status={}", saved.id(), saved.status());
            return saved;
        }
    }

    private NewsEvent copyWithStatusAndResult(NewsEvent base, NewsStatus status,
            AnalysisResult result, Instant attemptedAt) {
        return new NewsEvent(
                base.id(),
                base.externalId(),
                base.title(),
                base.summary(),
                base.source(),
                base.url(),
                base.publishedAt(),
                base.ingestedAt(),
                status,
                result,
                resolveRetryCount(base),
                resolveAttemptedAt(base, attemptedAt)
        );
    }

    private int resolveRetryCount(NewsEvent event) {
        return event.analysisRetryCount() == null ? 0 : event.analysisRetryCount();
    }

    private Instant resolveAttemptedAt(NewsEvent event, Instant attemptedAt) {
        return event.analysisLastAttemptAt() != null ? event.analysisLastAttemptAt() : attemptedAt;
    }

    private void validateConfig() {
        if (!StringUtils.hasText(openAiApiKey)) {
            throw new IllegalStateException("openai.api-key is not configured");
        }
        if (!StringUtils.hasText(openAiUrl)) {
            throw new IllegalStateException("openai.api-url is not configured");
        }
        if (!StringUtils.hasText(interpretationModel)) {
            throw new IllegalStateException("openai.interpretation-model is not configured");
        }
    }

}
