package com.example.bookreview.service.macro;

import com.example.bookreview.dto.domain.AnalysisResult;
import com.example.bookreview.dto.domain.NewsEvent;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MacroAiServiceImpl implements MacroAiService {

    @Override
    public AnalysisResult interpretNewsEvent(NewsEvent newsEvent) {
        // TODO: Replace placeholder with real macro interpretation in next phase.
        if (newsEvent != null) {
            log.debug("Macro AI placeholder invoked for newsEventId={}", newsEvent.id());
        }
        return AnalysisResult.builder()
                .summary("PENDING_AI_INTERPRETATION")
                .macroImpacts(List.of())
                .marketImpacts(List.of())
                .build();
    }
}