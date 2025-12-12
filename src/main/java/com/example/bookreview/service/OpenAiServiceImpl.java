package com.example.bookreview.service;

import com.example.bookreview.service.model.AiReviewResult;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class OpenAiServiceImpl implements OpenAiService {

    @Override
    public AiReviewResult generateImprovedReview(String title, String originalContent) {
        // TODO: OpenAI API 호출 및 응답 파싱 로직 구현
        return new AiReviewResult("개선된 독후감 예시", 0L, BigDecimal.ZERO);
    }
}
