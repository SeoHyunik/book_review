package com.example.bookreview.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAiClientConfig {

    @Bean
    public com.theokanning.openai.service.OpenAiService openAiClient(
            @Value("${openai.api-key}") String openAiApiKey) {
        return new com.theokanning.openai.service.OpenAiService(openAiApiKey);
    }
}
