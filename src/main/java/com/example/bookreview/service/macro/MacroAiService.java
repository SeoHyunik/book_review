package com.example.bookreview.service.macro;

import com.example.bookreview.dto.domain.AnalysisResult;
import com.example.bookreview.dto.domain.NewsEvent;

public interface MacroAiService {

    AnalysisResult interpretNewsEvent(NewsEvent newsEvent);
}