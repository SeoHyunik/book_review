package com.example.macronews.service.macro;

import com.example.macronews.domain.AnalysisResult;
import com.example.macronews.domain.NewsEvent;

public interface MacroAiService {

    AnalysisResult interpret(NewsEvent event);

    NewsEvent interpretAndSave(String newsEventId);
}