package com.example.macronews.service.news;

import com.example.macronews.domain.SignalSentiment;
import com.example.macronews.dto.market.DxySnapshotDto;
import com.example.macronews.dto.market.FxSnapshotDto;
import com.example.macronews.dto.market.GoldSnapshotDto;
import com.example.macronews.dto.market.IndexSnapshotDto;
import com.example.macronews.dto.market.OilSnapshotDto;
import com.example.macronews.dto.market.Us10ySnapshotDto;
import com.example.macronews.service.market.MarketDataFacade;
import org.springframework.stereotype.Component;

@Component
class MarketPriceAdjustmentPolicy {

    private static final double MAX_TOTAL_CONFIDENCE_BOOST = 0.12d;
    private static final double MAX_MARKET_CONFIDENCE_BOOST = 0.12d;
    private static final double USD_KRW_NEGATIVE_LEVEL = 1380d;
    private static final double USD_KRW_POSITIVE_LEVEL = 1330d;
    private static final double GOLD_NEGATIVE_LEVEL = 3000d;
    private static final double OIL_NEGATIVE_LEVEL = 85d;
    private static final double KOSPI_NEGATIVE_LEVEL = 2500d;
    private static final double KOSPI_POSITIVE_LEVEL = 2600d;
    private static final double DXY_NEGATIVE_LEVEL = 104d;
    private static final double DXY_POSITIVE_LEVEL = 102d;
    private static final double US10Y_NEGATIVE_LEVEL = 4.4d;
    private static final double US10Y_POSITIVE_LEVEL = 4.0d;

    ConfidenceBreakdown buildConfidenceBreakdown(
            Double baseConfidence,
            Double boostedConfidence,
            SignalSentiment dominantSentiment,
            MarketDataFacade marketDataFacade
    ) {
        if (baseConfidence == null || boostedConfidence == null || dominantSentiment == SignalSentiment.NEUTRAL) {
            return new ConfidenceBreakdown(baseConfidence, 0d, 0d, boostedConfidence, false, false, false);
        }
        double crisisBoost = Math.max(0d, boostedConfidence - baseConfidence);
        try {
            FxSnapshotDto usdKrw = marketDataFacade.getUsdKrw().orElse(null);
            GoldSnapshotDto gold = marketDataFacade.getGold().orElse(null);
            OilSnapshotDto oil = marketDataFacade.getOil().orElse(null);
            IndexSnapshotDto kospi = marketDataFacade.getKospi().orElse(null);
            DxySnapshotDto dxy = marketDataFacade.getDxy().orElse(null);
            Us10ySnapshotDto us10y = marketDataFacade.getUs10y().orElse(null);

            int alignedSignals = resolveAlignedSignals(dominantSentiment, usdKrw, gold, oil, kospi, dxy, us10y);
            if (alignedSignals == 0) {
                return new ConfidenceBreakdown(
                        baseConfidence,
                        crisisBoost,
                        0d,
                        boostedConfidence,
                        crisisBoost > 0d,
                        false,
                        false
                );
            }

            double marketBoost = Math.min(MAX_MARKET_CONFIDENCE_BOOST, alignedSignals * 0.03d);
            double totalBoost = (boostedConfidence - baseConfidence) + marketBoost;
            double cappedBoost = Math.min(totalBoost, MAX_TOTAL_CONFIDENCE_BOOST);
            double finalConfidence = Math.min(1.0d, baseConfidence + cappedBoost);
            double effectiveMarketBoost = Math.max(0d, finalConfidence - boostedConfidence);
            boolean capApplied = totalBoost > cappedBoost || (baseConfidence + cappedBoost) > finalConfidence;
            return new ConfidenceBreakdown(
                    baseConfidence,
                    crisisBoost,
                    effectiveMarketBoost,
                    finalConfidence,
                    crisisBoost > 0d,
                    effectiveMarketBoost > 0d,
                    capApplied
            );
        } catch (RuntimeException ex) {
            return new ConfidenceBreakdown(
                    baseConfidence,
                    crisisBoost,
                    0d,
                    boostedConfidence,
                    crisisBoost > 0d,
                    false,
                    false
            );
        }
    }

    private int resolveAlignedSignals(
            SignalSentiment dominantSentiment,
            FxSnapshotDto usdKrw,
            GoldSnapshotDto gold,
            OilSnapshotDto oil,
            IndexSnapshotDto kospi,
            DxySnapshotDto dxy,
            Us10ySnapshotDto us10y
    ) {
        int alignedSignals = 0;
        if (dominantSentiment == SignalSentiment.NEGATIVE) {
            if (usdKrw != null && usdKrw.rate() >= USD_KRW_NEGATIVE_LEVEL) {
                alignedSignals++;
            }
            if (gold != null && gold.usdPerOunce() >= GOLD_NEGATIVE_LEVEL) {
                alignedSignals++;
            }
            Double oilPrice = oil == null ? null : (oil.wtiUsd() != null ? oil.wtiUsd() : oil.brentUsd());
            if (oilPrice != null && oilPrice >= OIL_NEGATIVE_LEVEL) {
                alignedSignals++;
            }
            if (kospi != null && kospi.price() != null && kospi.price() <= KOSPI_NEGATIVE_LEVEL) {
                alignedSignals++;
            }
            if (dxy != null && dxy.value() >= DXY_NEGATIVE_LEVEL) {
                alignedSignals++;
            }
            if (us10y != null && us10y.yield() >= US10Y_NEGATIVE_LEVEL) {
                alignedSignals++;
            }
        } else if (dominantSentiment == SignalSentiment.POSITIVE) {
            if (usdKrw != null && usdKrw.rate() <= USD_KRW_POSITIVE_LEVEL) {
                alignedSignals++;
            }
            if (kospi != null && kospi.price() != null && kospi.price() >= KOSPI_POSITIVE_LEVEL) {
                alignedSignals++;
            }
            if (dxy != null && dxy.value() <= DXY_POSITIVE_LEVEL) {
                alignedSignals++;
            }
            if (us10y != null && us10y.yield() <= US10Y_POSITIVE_LEVEL) {
                alignedSignals++;
            }
        }
        return alignedSignals;
    }

    record ConfidenceBreakdown(
            Double baseConfidence,
            double crisisBoost,
            double marketBoost,
            Double finalConfidence,
            boolean crisisApplied,
            boolean marketApplied,
            boolean capApplied
    ) {
    }
}
