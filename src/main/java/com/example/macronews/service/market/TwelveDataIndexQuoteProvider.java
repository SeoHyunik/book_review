package com.example.macronews.service.market;

import com.example.macronews.dto.market.IndexSnapshotDto;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TwelveDataIndexQuoteProvider implements IndexQuoteProvider {

    @Value("${app.market.index.enabled:false}")
    private boolean enabled;

    @Value("${app.market.index.api-key:}")
    private String apiKey;

    @Override
    public Optional<IndexSnapshotDto> getQuote(String symbol) {
        if (!enabled) {
            throw new UnsupportedOperationException("Index provider is disabled");
        }
        // TODO: Discover valid KOSPI/KOSDAQ symbols via Twelve Data symbol_search before live quote calls.
        // TODO: Use Twelve Data quote endpoint once symbol mapping is confirmed for domestic indices.
        throw new UnsupportedOperationException("Twelve Data index quote integration is not implemented yet");
    }

    @Override
    public boolean isConfigured() {
        return enabled && StringUtils.hasText(apiKey);
    }
}
