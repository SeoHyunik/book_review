package com.example.bookreview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.example.bookreview.config.GsonConfig;
import com.example.bookreview.dto.OpenAiResponse;
import com.example.bookreview.dto.ExternalApiRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
        classes = {OpenAiServiceImpl.class, GsonConfig.class},
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
    void improveReview_returnsParsedResult() {
        String responseBody = """
                {"id":"chatcmpl-2","model":"gpt-4o","choices":[{"index":0,"message":{"role":"assistant","content":"개선된 내용"},"finish_reason":"stop"}],"usage":{"prompt_tokens":13,"completion_tokens":9}}
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        given(externalApiUtils.callAPI(any(ExternalApiRequest.class)))
                .willReturn(ResponseEntity.ok().headers(headers).body(responseBody));

        OpenAiResponse openAiResult = openAiService.improveReview("원본 내용").block();

        assertThat(openAiResult).isNotNull();
        assertThat(openAiResult.improvedContent()).isEqualTo("개선된 내용");
        assertThat(openAiResult.inputTokens()).isEqualTo(13);
        assertThat(openAiResult.outputTokens()).isEqualTo(9);
    }
}
