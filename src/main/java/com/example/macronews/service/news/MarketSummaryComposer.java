package com.example.macronews.service.news;

import com.example.macronews.domain.SignalSentiment;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class MarketSummaryComposer {

    String buildHeadlineKo(SignalSentiment dominantSentiment) {
        return switch (dominantSentiment) {
            case POSITIVE -> "\uCD5C\uADFC \uAC70\uC2DC \uC2E0\uD638\uB294 \uAE0D\uC815 \uCABD\uC73C\uB85C \uAE30\uC6B8\uACE0 \uC788\uC2B5\uB2C8\uB2E4";
            case NEGATIVE -> "\uCD5C\uADFC \uAC70\uC2DC \uC2E0\uD638\uB294 \uBC29\uC5B4\uC801\uC73C\uB85C \uAE30\uC6B8\uACE0 \uC788\uC2B5\uB2C8\uB2E4";
            case NEUTRAL -> "\uCD5C\uADFC \uAC70\uC2DC \uC2E0\uD638\uB294 \uD63C\uC870 \uD750\uB984\uC785\uB2C8\uB2E4";
        };
    }

    String buildHeadlineEn(SignalSentiment dominantSentiment) {
        return switch (dominantSentiment) {
            case POSITIVE -> "Recent macro signals lean positive";
            case NEGATIVE -> "Recent macro signals lean defensive";
            case NEUTRAL -> "Recent macro signals remain mixed";
        };
    }

    String buildSummaryKo(int windowHours, int sourceCount, List<MarketDriverExtractor.DriverCount> topDrivers) {
        String base = "\uCD5C\uADFC " + windowHours
                + "\uC2DC\uAC04 \uB3D9\uC548 \uBD84\uC11D\uB41C \uAE30\uC0AC " + sourceCount
                + "\uAC74\uC744 \uBC14\uD0D5\uC73C\uB85C \uC815\uB9AC\uD588\uC2B5\uB2C8\uB2E4";
        if (topDrivers.isEmpty()) {
            return base + ".";
        }
        return base + ". " + joinDriverLabels(topDrivers, true)
                + " \uC774(\uAC00) \uAC00\uC7A5 \uC790\uC8FC \uB4F1\uC7A5\uD588\uC2B5\uB2C8\uB2E4.";
    }

    String buildSummaryEn(int windowHours, int sourceCount, List<MarketDriverExtractor.DriverCount> topDrivers) {
        String base = "Built from " + sourceCount + " analyzed headlines over the last "
                + windowHours + " hours";
        if (topDrivers.isEmpty()) {
            return base + ".";
        }
        return base + ", with " + joinDriverLabels(topDrivers, false) + " appearing most often.";
    }

    private String joinDriverLabels(List<MarketDriverExtractor.DriverCount> topDrivers, boolean korean) {
        List<String> labels = new ArrayList<>();
        for (MarketDriverExtractor.DriverCount driver : topDrivers) {
            labels.add(korean ? driver.labelKo() : driver.labelEn());
        }
        return String.join(" / ", labels);
    }
}
