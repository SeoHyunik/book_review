package com.example.macronews.service.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.example.macronews.dto.market.DxySnapshotDto;
import com.example.macronews.dto.market.Us10ySnapshotDto;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
class MarketPriceProvidersTest {

    @Mock
    private ExternalApiUtils externalApiUtils;

    private FredUs10yProvider fredUs10yProvider;
    private TwelveDataDxyProvider twelveDataDxyProvider;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        fredUs10yProvider = new FredUs10yProvider(externalApiUtils, objectMapper);
        twelveDataDxyProvider = new TwelveDataDxyProvider(externalApiUtils, objectMapper);

        ReflectionTestUtils.setField(fredUs10yProvider, "enabled", true);
        ReflectionTestUtils.setField(fredUs10yProvider, "apiKey", "fred-key");
        ReflectionTestUtils.setField(fredUs10yProvider, "baseUrl", "https://api.stlouisfed.org/fred");

        ReflectionTestUtils.setField(twelveDataDxyProvider, "enabled", true);
        ReflectionTestUtils.setField(twelveDataDxyProvider, "apiKey", "twelve-key");
    }

    @Test
    @DisplayName("FRED provider should parse latest valid DGS10 observation")
    void givenFREDObservations_whenGetUs10y_thenParseLatestValidObservation() {
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "observations": [
                    { "date": "2026-04-02", "value": "." },
                    { "date": "2026-04-01", "value": "4.21" }
                  ]
                }
                """));

        var snapshot = fredUs10yProvider.getUs10y();

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().yield()).isEqualTo(4.21d);
        assertThat(snapshot.get().asOfDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(snapshot.get().source()).isEqualTo("FRED");
        assertThat(snapshot.get().sourceSeries()).isEqualTo("DGS10");
    }

    @Test
    @DisplayName("FRED provider should return empty when the latest observation is missing")
    void givenFredDotObservation_whenGetUs10y_thenReturnEmpty() {
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "observations": [
                    { "date": "2026-04-02", "value": "." }
                  ]
                }
                """));

        var snapshot = fredUs10yProvider.getUs10y();

        assertThat(snapshot).isEmpty();
    }

    @Test
    @DisplayName("DXY provider should use a verified direct Twelve Data symbol when available")
    void givenVerifiedDirectSymbol_whenGetDxy_thenUseDirectSymbolPath() {
        given(externalApiUtils.callAPI(any()))
                .willReturn(
                        new ExternalApiResult(200, """
                                {
                                  "data": [
                                    {
                                      "symbol": "DXY",
                                      "instrument_name": "U.S. Dollar Index",
                                      "instrument_type": "Index"
                                    }
                                  ]
                                }
                                """),
                        new ExternalApiResult(200, """
                                {
                                  "symbol": "DXY",
                                  "price": "103.45",
                                  "timestamp": "2026-04-02T00:00:00Z"
                                }
                                """));

        var snapshot = twelveDataDxyProvider.getDxy();

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().value()).isEqualTo(103.45d);
        assertThat(snapshot.get().source()).isEqualTo("TWELVE_DATA_DIRECT");
        assertThat(snapshot.get().sourceSeries()).isEqualTo("DXY");
        assertThat(snapshot.get().synthetic()).isFalse();
    }

    @Test
    @DisplayName("DXY provider should compute synthetic DXY from six verified FX pairs")
    void givenSyntheticFxQuotes_whenGetDxy_thenComputeSyntheticIndex() {
        given(externalApiUtils.callAPI(any()))
                .willReturn(
                        new ExternalApiResult(200, "{\"data\": []}"),
                        new ExternalApiResult(200, "{\"data\": []}"),
                        new ExternalApiResult(200, "{\"data\": []}"),
                        new ExternalApiResult(200, "{\"symbol\": \"EUR/USD\", \"price\": \"1.08\", \"timestamp\": \"2026-04-02T00:00:00Z\"}"),
                        new ExternalApiResult(200, "{\"symbol\": \"USD/JPY\", \"price\": \"151.2\", \"timestamp\": \"2026-04-02T00:00:00Z\"}"),
                        new ExternalApiResult(200, "{\"symbol\": \"GBP/USD\", \"price\": \"1.27\", \"timestamp\": \"2026-04-02T00:00:00Z\"}"),
                        new ExternalApiResult(200, "{\"symbol\": \"USD/CAD\", \"price\": \"1.36\", \"timestamp\": \"2026-04-02T00:00:00Z\"}"),
                        new ExternalApiResult(200, "{\"symbol\": \"USD/SEK\", \"price\": \"10.52\", \"timestamp\": \"2026-04-02T00:00:00Z\"}"),
                        new ExternalApiResult(200, "{\"symbol\": \"USD/CHF\", \"price\": \"0.91\", \"timestamp\": \"2026-04-02T00:00:00Z\"}")
                );

        var snapshot = twelveDataDxyProvider.getDxy();

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().source()).isEqualTo("TWELVE_DATA_SYNTHETIC");
        assertThat(snapshot.get().sourceSeries()).isEqualTo("FX_BASKET_6");
        assertThat(snapshot.get().synthetic()).isTrue();
        assertThat(snapshot.get().asOfDateTime()).isEqualTo(Instant.parse("2026-04-02T00:00:00Z"));
        assertThat(snapshot.get().value()).isCloseTo(Double.parseDouble(expectedSyntheticDxy()), within(0.000001d));
    }

    @Test
    @DisplayName("DXY provider should return empty when one synthetic component is missing")
    void givenMissingSyntheticComponent_whenGetDxy_thenReturnEmpty() {
        given(externalApiUtils.callAPI(any()))
                .willReturn(
                        new ExternalApiResult(200, "{\"data\": []}"),
                        new ExternalApiResult(200, "{\"data\": []}"),
                        new ExternalApiResult(200, "{\"data\": []}"),
                        new ExternalApiResult(200, "{\"symbol\": \"EUR/USD\", \"price\": \"1.08\", \"timestamp\": \"2026-04-02T00:00:00Z\"}"),
                        new ExternalApiResult(200, "{\"symbol\": \"USD/JPY\", \"price\": \"151.2\", \"timestamp\": \"2026-04-02T00:00:00Z\"}"),
                        new ExternalApiResult(200, "{\"symbol\": \"GBP/USD\", \"price\": \"1.27\", \"timestamp\": \"2026-04-02T00:00:00Z\"}"),
                        new ExternalApiResult(200, "{\"data\": []}")
                );

        var snapshot = twelveDataDxyProvider.getDxy();

        assertThat(snapshot).isEmpty();
    }

    private String expectedSyntheticDxy() {
        double value = 50.14348112d;
        value *= Math.pow(1.08d, -0.576d);
        value *= Math.pow(151.2d, 0.136d);
        value *= Math.pow(1.27d, -0.119d);
        value *= Math.pow(1.36d, 0.091d);
        value *= Math.pow(10.52d, 0.042d);
        value *= Math.pow(0.91d, 0.036d);
        return BigDecimal.valueOf(value).toPlainString();
    }
}
