package com.example.macronews.service.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.example.macronews.dto.market.DxySnapshotDto;
import com.example.macronews.dto.market.Us10ySnapshotDto;
import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private FredUs10yProvider fredUs10yProvider;
    private TwelveDataDxyProvider twelveDataDxyProvider;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        exchangeRateApiProvider = new ExchangeRateApiProvider(externalApiUtils, objectMapper);
        metalPriceApiProvider = new MetalPriceApiProvider(externalApiUtils, objectMapper);
        oilPriceApiProvider = new OilPriceApiProvider(externalApiUtils, objectMapper);
        twelveDataIndexQuoteProvider = new TwelveDataIndexQuoteProvider(externalApiUtils, objectMapper);
        fredUs10yProvider = new FredUs10yProvider(externalApiUtils, objectMapper);
        twelveDataDxyProvider = new TwelveDataDxyProvider(externalApiUtils, objectMapper);

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

        ReflectionTestUtils.setField(fredUs10yProvider, "enabled", true);
        ReflectionTestUtils.setField(fredUs10yProvider, "baseUrl", "https://api.stlouisfed.org/fred");
        ReflectionTestUtils.setField(fredUs10yProvider, "apiKey", "fred-key");

        ReflectionTestUtils.setField(twelveDataDxyProvider, "enabled", true);
        ReflectionTestUtils.setField(twelveDataDxyProvider, "apiKey", "dxy-key");
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
    @DisplayName("FX provider should fail open when the external request times out")
    void givenTimeoutResponse_whenGetUsdKrw_thenReturnEmpty() {
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(504, "External API request timed out"));

        var snapshot = exchangeRateApiProvider.getUsdKrw();

        assertThat(snapshot).isEmpty();
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
    @DisplayName("Index provider should parse KOSPI quote from public-data response")
    void getQuote_parsesKospiPriceFromPublicData() {
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "response": {
                    "body": {
                      "items": {
                        "item": [
                          {
                            "idxNm": "코스피",
                            "clpr": "2685.40",
                            "basDt": "20260323"
                          }
                        ]
                      }
                    }
                  }
                }
                """));
        ArgumentCaptor<ExternalApiRequest> requestCaptor = ArgumentCaptor.forClass(ExternalApiRequest.class);

        var snapshot = twelveDataIndexQuoteProvider.getQuote("KOSPI");

        assertThat(snapshot).isPresent();
        verify(externalApiUtils).callAPI(requestCaptor.capture());
        assertThat(requestCaptor.getValue().url()).contains("apis.data.go.kr/1160100/service/GetMarketIndexInfoService/getStockMarketIndex");
        assertThat(requestCaptor.getValue().url()).contains("resultType=json");
        assertThat(requestCaptor.getValue().url()).contains("numOfRows=1");
        assertThat(requestCaptor.getValue().url()).contains("pageNo=1");
        assertThat(requestCaptor.getValue().url()).contains("idxNm=%EC%BD%94%EC%8A%A4%ED%94%BC");
        assertThat(snapshot.get().symbol()).isEqualTo("코스피");
        assertThat(snapshot.get().price()).isEqualTo(2685.40d);
    }

    @Test
    @DisplayName("Index provider should fail open on empty public-data response")
    void getQuote_returnsEmptyWhenPublicDataResponseHasNoItem() {
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "response": {
                    "body": {
                      "items": {
                        "item": []
                      }
                    }
                  }
                }
                """));

        var snapshot = twelveDataIndexQuoteProvider.getQuote("KOSPI");

        assertThat(snapshot).isEmpty();
    }

    @Test
    @DisplayName("US 10Y provider should parse the latest valid FRED observation")
    void getUs10y_parsesLatestValidObservation() {
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "observations": [
                    {"date": "2026-03-17", "value": "."},
                    {"date": "2026-03-16", "value": "4.27"}
                  ]
                }
                """));

        var snapshot = fredUs10yProvider.getUs10y();

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().yield()).isEqualTo(4.27d);
        assertThat(snapshot.get().asOfDate()).isEqualTo(LocalDate.parse("2026-03-16"));
        assertThat(snapshot.get().source()).isEqualTo("FRED");
        assertThat(snapshot.get().sourceSeries()).isEqualTo("DGS10");
    }

    @Test
    @DisplayName("US 10Y provider should fail open when only missing observations are returned")
    void getUs10y_returnsEmptyWhenOnlyMissingObservationsExist() {
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "observations": [
                    {"date": "2026-03-17", "value": "."},
                    {"date": "2026-03-16", "value": "."}
                  ]
                }
                """));

        var snapshot = fredUs10yProvider.getUs10y();

        assertThat(snapshot).isEmpty();
    }

    @Test
    @DisplayName("DXY provider should use a verified direct symbol when Twelve Data discovers one")
    void getDxy_usesVerifiedDirectSymbolWhenAvailable() {
        given(externalApiUtils.callAPI(any()))
                .willReturn(
                        new ExternalApiResult(200, """
                                {
                                  "data": [
                                    {
                                      "symbol": "DXY",
                                      "instrument_name": "US Dollar Index",
                                      "instrument_type": "Index"
                                    }
                                  ]
                                }
                                """),
                        new ExternalApiResult(200, """
                                {
                                  "symbol": "DXY",
                                  "price": "104.125",
                                  "timestamp": "2026-03-17T03:00:00Z"
                                }
                                """));

        var snapshot = twelveDataDxyProvider.getDxy();

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().value()).isEqualTo(104.125d);
        assertThat(snapshot.get().asOfDateTime()).isEqualTo(Instant.parse("2026-03-17T03:00:00Z"));
        assertThat(snapshot.get().source()).isEqualTo("TWELVE_DATA_DIRECT");
        assertThat(snapshot.get().synthetic()).isFalse();
        assertThat(snapshot.get().sourceSeries()).isEqualTo("DXY");
    }

    @Test
    @DisplayName("DXY provider should compute the synthetic ICE basket when no direct symbol is verified")
    void getDxy_computesSyntheticBasketWhenDirectSymbolMissing() {
        given(externalApiUtils.callAPI(any()))
                .willReturn(
                        new ExternalApiResult(200, "{\"data\": []}"),
                        new ExternalApiResult(200, "{\"data\": []}"),
                        new ExternalApiResult(200, "{\"data\": []}"),
                        new ExternalApiResult(200, "{\"symbol\": \"EUR/USD\", \"price\": \"1.0800\", \"timestamp\": \"2026-03-17T03:00:00Z\"}"),
                        new ExternalApiResult(200, "{\"symbol\": \"USD/JPY\", \"price\": \"149.50\", \"timestamp\": \"2026-03-17T02:59:00Z\"}"),
                        new ExternalApiResult(200, "{\"symbol\": \"GBP/USD\", \"price\": \"1.2650\", \"timestamp\": \"2026-03-17T02:58:00Z\"}"),
                        new ExternalApiResult(200, "{\"symbol\": \"USD/CAD\", \"price\": \"1.3550\", \"timestamp\": \"2026-03-17T02:57:00Z\"}"),
                        new ExternalApiResult(200, "{\"symbol\": \"USD/SEK\", \"price\": \"10.1500\", \"timestamp\": \"2026-03-17T02:56:00Z\"}"),
                        new ExternalApiResult(200, "{\"symbol\": \"USD/CHF\", \"price\": \"0.8850\", \"timestamp\": \"2026-03-17T02:55:00Z\"}")
                );

        var snapshot = twelveDataDxyProvider.getDxy();

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().synthetic()).isTrue();
        assertThat(snapshot.get().source()).isEqualTo("TWELVE_DATA_SYNTHETIC");
        assertThat(snapshot.get().sourceSeries()).isEqualTo("FX_BASKET_6");
        assertThat(snapshot.get().asOfDateTime()).isEqualTo(Instant.parse("2026-03-17T02:55:00Z"));
        assertThat(snapshot.get().value()).isCloseTo(103.97593505507736d, within(0.000001d));
    }

    @Test
    @DisplayName("DXY provider should fail open when a basket component is unavailable")
    void getDxy_returnsEmptyWhenBasketComponentIsUnavailable() {
        given(externalApiUtils.callAPI(any()))
                .willReturn(
                        new ExternalApiResult(200, "{\"data\": []}"),
                        new ExternalApiResult(200, "{\"data\": []}"),
                        new ExternalApiResult(200, "{\"data\": []}"),
                        new ExternalApiResult(503, "service unavailable"));

        var snapshot = twelveDataDxyProvider.getDxy();

        assertThat(snapshot).isEmpty();
    }
}
