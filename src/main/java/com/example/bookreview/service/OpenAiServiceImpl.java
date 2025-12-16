package com.example.bookreview.service;

import com.example.bookreview.service.model.AiReviewResult;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OpenAiServiceImpl implements OpenAiService {

    @Override
    public AiReviewResult generateImprovedReview(String title, String originalContent) {
        log.info("Generating improved review using OpenAI mock for title='{}'", title);
        // TODO: OpenAI API 호출 및 응답 파싱 로직 구현
        AiReviewResult result = new AiReviewResult("개선된 독후감 예시", 0L, BigDecimal.ZERO);
        log.debug("Returning placeholder AI review result: {}", result);
        return result;
    }
}
