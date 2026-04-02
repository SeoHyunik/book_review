package com.example.macronews.service.macro;

import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
final class MacroAiClient {

    private final ExternalApiUtils externalApiUtils;

    ExternalApiResult call(String apiKey, String apiUrl, String payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        return externalApiUtils.callAPI(new ExternalApiRequest(
                HttpMethod.POST,
                headers,
                apiUrl,
                payload
        ));
    }
}
