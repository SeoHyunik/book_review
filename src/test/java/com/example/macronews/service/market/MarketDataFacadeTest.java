package com.example.macronews.service.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.example.macronews.dto.market.DxySnapshotDto;
import com.example.macronews.dto.market.FxSnapshotDto;
import com.example.macronews.dto.market.GoldSnapshotDto;
import com.example.macronews.dto.market.IndexSnapshotDto;
import com.example.macronews.dto.market.OilSnapshotDto;
import com.example.macronews.dto.market.Us10ySnapshotDto;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketDataFacadeTest {

    @Mock
    private ExchangeRateProvider exchangeRateProvider;

    @Mock
    private GoldPriceProvider goldPriceProvider;

    @Mock
    private OilPriceProvider oilPriceProvider;

    @Mock
    private IndexQuoteProvider indexQuoteProvider;

    @Mock
    private Us10yProvider us10yProvider;

    @Mock
    private DxyProvider dxyProvider;

    private MarketDataFacade marketDataFacade;

    @BeforeEach
    void setUp() {
        marketDataFacade = new MarketDataFacade(
                exchangeRateProvider,
                goldPriceProvider,
                oilPriceProvider,
                indexQuoteProvider,
                us10yProvider,
                dxyProvider
        );
    }

    @Test
    @DisplayName("getCurrentMarketSnapshot should combine available market data and keep missing values empty")
    void getCurrentMarketSnapshot_combinesAvailableValues() {
        given(exchangeRateProvider.getUsdKrw())
                .willReturn(Optional.of(new FxSnapshotDto("USD", "KRW", 1350.2d, Instant.parse("2026-03-17T00:00:00Z"))));
        given(goldPriceProvider.getGold()).willReturn(Optional.empty());
        given(oilPriceProvider.getOil())
                .willReturn(Optional.of(new OilSnapshotDto(78.3d, 82.1d, Instant.parse("2026-03-17T00:00:00Z"))));
        given(indexQuoteProvider.getQuote("KOSPI"))
                .willReturn(Optional.of(new IndexSnapshotDto("KOSPI", 2685.4d, Instant.parse("2026-03-17T00:00:00Z"))));
        given(us10yProvider.getUs10y())
                .willReturn(Optional.of(new Us10ySnapshotDto(4.21d, LocalDate.parse("2026-03-16"), "FRED", "DGS10")));
        given(dxyProvider.getDxy())
                .willReturn(Optional.of(new DxySnapshotDto(103.45d, Instant.parse("2026-03-17T00:00:00Z"),
                        "TWELVE_DATA_SYNTHETIC", "ICE_DXY_BASKET", true)));

        MarketDataFacade.MarketDataSnapshot snapshot = marketDataFacade.getCurrentMarketSnapshot();

        assertThat(snapshot.usdKrw()).isPresent();
        assertThat(snapshot.gold()).isEmpty();
        assertThat(snapshot.oil()).isPresent();
        assertThat(snapshot.kospi()).isPresent();
        assertThat(snapshot.us10y()).isPresent();
        assertThat(snapshot.dxy()).isPresent();
    }

    @Test
    @DisplayName("getCurrentMarketSnapshot should keep working when one provider throws")
    void getCurrentMarketSnapshot_keepsFailOpenWhenOneProviderThrows() {
        given(exchangeRateProvider.getUsdKrw())
                .willReturn(Optional.of(new FxSnapshotDto("USD", "KRW", 1350.2d, Instant.now())));
        given(goldPriceProvider.getGold()).willThrow(new RuntimeException("gold api down"));
        given(oilPriceProvider.getOil()).willReturn(Optional.empty());
        given(indexQuoteProvider.getQuote("KOSPI")).willReturn(Optional.empty());
        given(us10yProvider.getUs10y()).willReturn(Optional.empty());
        given(dxyProvider.getDxy()).willReturn(Optional.empty());

        MarketDataFacade.MarketDataSnapshot snapshot = marketDataFacade.getCurrentMarketSnapshot();

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.usdKrw()).isPresent();
        assertThat(snapshot.gold()).isEmpty();
    }
}
