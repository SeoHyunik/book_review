package com.example.macronews.service.market;

import com.example.macronews.dto.market.IndexSnapshotDto;
import java.util.Optional;

public interface IndexQuoteProvider {

    Optional<IndexSnapshotDto> getQuote(String symbol);

    boolean isConfigured();
}
