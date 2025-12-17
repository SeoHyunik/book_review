package com.example.bookreview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.example.bookreview.dto.OpenAiResult;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.usage.Usage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = "openai.api-key=test-key")
class OpenAiServiceTest {

    @Autowired
    private OpenAiService openAiService;

    @MockBean
    private com.theokanning.openai.service.OpenAiService openAiClient;

    @Test
    void improveReview_returnsParsedResult() {
        ChatMessage assistantMessage = new ChatMessage(ChatMessageRole.ASSISTANT.value(), "다듬어진 내용");
        ChatCompletionChoice choice = new ChatCompletionChoice();
        choice.setMessage(assistantMessage);

        Usage usage = new Usage();
        usage.setPromptTokens(11);
        usage.setCompletionTokens(7);

        ChatCompletionResult completionResult = new ChatCompletionResult();
        completionResult.setChoices(List.of(choice));
        completionResult.setUsage(usage);

        given(openAiClient.createChatCompletion(any(ChatCompletionRequest.class))).willReturn(completionResult);

        OpenAiResult openAiResult = openAiService.improveReview("원본 내용");

        assertThat(openAiResult.getImprovedContent()).isEqualTo("다듬어진 내용");
        assertThat(openAiResult.getPromptTokens()).isEqualTo(11);
        assertThat(openAiResult.getCompletionTokens()).isEqualTo(7);
    }
}
