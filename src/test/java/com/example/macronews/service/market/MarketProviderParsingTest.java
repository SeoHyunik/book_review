package com.example.macronews.service.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MarketProviderParsingTest {

    @Mock
    private ExternalApiUtils externalApiUtils;

    private ExchangeRateApiProvider exchangeRateApiProvider;
    private MetalPriceApiProvider metalPriceApiProvider;
    private OilPriceApiProvider oilPriceApiProvider;
    private TwelveDataIndexQuoteProvider twelveDataIndexQuoteProvider;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        exchangeRateApiProvider = new ExchangeRateApiProvider(externalApiUtils, objectMapper);
        metalPriceApiProvider = new MetalPriceApiProvider(externalApiUtils, objectMapper);
        oilPriceApiProvider = new OilPriceApiProvider(externalApiUtils, objectMapper);
        twelveDataIndexQuoteProvider = new TwelveDataIndexQuoteProvider(externalApiUtils, objectMapper);

        ReflectionTestUtils.setField(exchangeRateApiProvider, "enabled", true);
        ReflectionTestUtils.setField(exchangeRateApiProvider, "baseUrl", "https://v6.exchangerate-api.com");
        ReflectionTestUtils.setField(exchangeRateApiProvider, "apiKey", "fx-key");

        ReflectionTestUtils.setField(metalPriceApiProvider, "enabled", true);
        ReflectionTestUtils.setField(metalPriceApiProvider, "baseUrl", "https://api.metalpriceapi.com/v1/latest");
        ReflectionTestUtils.setField(metalPriceApiProvider, "apiKey", "gold-key");

        ReflectionTestUtils.setField(oilPriceApiProvider, "enabled", true);
        ReflectionTestUtils.setField(oilPriceApiProvider, "url", "https://api.oilpriceapi.com/v1/prices/latest");
        ReflectionTestUtils.setField(oilPriceApiProvider, "apiKey", "oil-key");

        ReflectionTestUtils.setField(twelveDataIndexQuoteProvider, "enabled", true);
        ReflectionTestUtils.setField(twelveDataIndexQuoteProvider, "apiKey", "index-key");
    }

    @Test
    @DisplayName("FX provider should parse USD KRW rate from mocked response")
    void getUsdKrw_parsesRate() {
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "time_last_update_unix": 1710000000,
                  "conversion_rates": {
                    "KRW": 1337.42
                  }
                }
                """));

        var snapshot = exchangeRateApiProvider.getUsdKrw();

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().rate()).isEqualTo(1337.42d);
        assertThat(snapshot.get().quoteCurrency()).isEqualTo("KRW");
    }

    @Test
    @DisplayName("Gold provider should normalize USD per ounce from mocked response")
    void getGold_parsesUsdPerOunce() {
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "timestamp": 1710000000,
                  "base": "USD",
                  "rates": {
                    "XAU": 0.0005
                  }
                }
                """));

        var snapshot = metalPriceApiProvider.getGold();

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().usdPerOunce()).isEqualTo(2000.0d);
        assertThat(snapshot.get().baseCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("Oil provider should parse WTI and Brent from mocked response")
    void getOil_parsesWtiAndBrent() {
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "data": {
                    "wti": 78.12,
                    "brent": 82.45,
                    "timestamp": "2026-03-13T00:00:00Z"
                  }
                }
                """));

        var snapshot = oilPriceApiProvider.getOil();

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().wtiUsd()).isEqualTo(78.12d);
        assertThat(snapshot.get().brentUsd()).isEqualTo(82.45d);
    }

    @Test
    @DisplayName("Index provider should parse KOSPI quote from mocked response")
    void getQuote_parsesKospiPrice() {
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "symbol": "KOSPI",
                  "price": "2685.40",
                  "timestamp": 1710000000
                }
                """));

        var snapshot = twelveDataIndexQuoteProvider.getQuote("KOSPI");

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().symbol()).isEqualTo("KOSPI");
        assertThat(snapshot.get().price()).isEqualTo(2685.40d);
    }
}
