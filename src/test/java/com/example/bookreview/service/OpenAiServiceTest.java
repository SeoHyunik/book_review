package com.example.bookreview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.example.bookreview.config.GsonConfig;
import com.example.bookreview.dto.internal.AiReviewResult;
import com.example.bookreview.dto.request.ExternalApiRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
        classes = {OpenAiServiceImpl.class, GsonConfig.class, TokenCostCalculator.class},
        properties = {
                "openai.api-key=test-key",
                "openai.api-url=https://mock-openai.local/v1/chat/completions"
        })
class OpenAiServiceTest {

    @Autowired
    private OpenAiService openAiService;

    @MockBean
    private ExternalApiUtils externalApiUtils;

    @Test
    void generateImprovedReview_returnsParsedResultWithCost() {
        String responseBody = """
                {"id":"chatcmpl-2","model":"gpt-4o","choices":[{"index":0,"message":{"role":"assistant","content":"개선된 내용"},"finish_reason":"stop"}],"usage":{"prompt_tokens":13,"completion_tokens":9}}
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        given(externalApiUtils.callAPI(any(ExternalApiRequest.class)))
                .willReturn(ResponseEntity.ok().headers(headers).body(responseBody));

        AiReviewResult openAiResult = openAiService.generateImprovedReview("테스트 제목", "원본 내용");

        assertThat(openAiResult).isNotNull();
        assertThat(openAiResult.improvedContent()).isEqualTo("개선된 내용");
        assertThat(openAiResult.promptTokens()).isEqualTo(13);
        assertThat(openAiResult.completionTokens()).isEqualTo(9);
        assertThat(openAiResult.totalTokens()).isEqualTo(22);
        assertThat(openAiResult.usdCost()).isEqualByComparingTo("0.000220");
    }
}
