package com.example.macronews.service.macro;

import com.example.macronews.dto.domain.AnalysisResult;
import com.example.macronews.dto.domain.NewsEvent;

public interface MacroAiService {

    AnalysisResult interpretNewsEvent(NewsEvent newsEvent);
}