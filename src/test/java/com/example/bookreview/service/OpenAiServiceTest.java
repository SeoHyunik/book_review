package com.example.bookreview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.example.bookreview.config.GsonConfig;
import com.example.bookreview.dto.internal.AiReviewResult;
import com.example.bookreview.dto.request.ExternalApiRequest;
import com.example.bookreview.service.openai.OpenAiService;
import com.example.bookreview.service.openai.OpenAiServiceImpl;
import com.example.bookreview.util.ExternalApiResult;
import com.example.bookreview.util.ExternalApiUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
        classes = {OpenAiServiceImpl.class, GsonConfig.class},
        properties = {
                "openai.api-key=test-key",
                "openai.api-url=https://mock-openai.local/v1/chat/completions"
        })
class OpenAiServiceTest {

    @Autowired
    private OpenAiService openAiService;

    @MockitoBean
    private ExternalApiUtils externalApiUtils;

    @Test
    void generateImprovedReview_returnsParsedResult() {
        String responseBody = """
                {"id":"chatcmpl-2","model":"gpt-4o","choices":[{"index":0,"message":{"role":"assistant","content":"개선된 내용"},"finish_reason":"stop"}],"usage":{"prompt_tokens":13,"completion_tokens":9}}
                """;

        ExternalApiResult statusResult = new ExternalApiResult(200, "{\"data\":[{\"id\":\"gpt-4o\"}]}");
        ExternalApiResult chatResult = new ExternalApiResult(200, responseBody);
        given(externalApiUtils.callAPI(any(ExternalApiRequest.class)))
                .willReturn(statusResult, chatResult);

        AiReviewResult openAiResult = openAiService.generateImprovedReview("테스트 제목", "원본 내용");

        assertThat(openAiResult).isNotNull();
        assertThat(openAiResult.improvedContent()).isEqualTo("개선된 내용");
        assertThat(openAiResult.fromAi()).isTrue();
        assertThat(openAiResult.reason()).isEqualTo("stop");
    }
}
