package com.example.macronews.dto.forecast;

import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.dto.market.FxSnapshotDto;
import com.example.macronews.dto.market.GoldSnapshotDto;
import com.example.macronews.dto.market.OilSnapshotDto;
import java.util.List;

public record MarketForecastDetailDto(
        MarketForecastSnapshotDto snapshot,
        List<NewsListItemDto> relatedNewsItems,
        FxSnapshotDto fxSnapshot,
        GoldSnapshotDto goldSnapshot,
        OilSnapshotDto oilSnapshot
) {
}
