package com.example.bookreview.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyServiceImpl implements CurrencyService {

    private static final BigDecimal DEFAULT_RATE = BigDecimal.valueOf(1300);

    private final WebClient.Builder webClientBuilder;
    private final Gson gson = new Gson();

    @Value("${exchange-rate.api-key:${EXCHANGE_RATE_API_KEY:}}")
    private String apiKey;

    @Value("${exchange-rate.api-url:https://v6.exchangerate-api.com/v6/%s/latest/USD}")
    private String apiUrl;

    @Override
    public BigDecimal convertUsdToKrw(BigDecimal usdAmount) {
        Assert.notNull(usdAmount, "USD amount must not be null");
        log.info("[CURRENCY] Converting USD to KRW for amount={}", usdAmount);
        if (usdAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(0, RoundingMode.HALF_UP);
        }

        try {
            BigDecimal rate = getUsdToKrwRate();
            BigDecimal result = usdAmount.multiply(rate).setScale(0, RoundingMode.HALF_UP);
            log.debug("[CURRENCY] Converted USD {} to KRW {} using fetched rate {}", usdAmount, result, rate);
            return result;
        } catch (Exception ex) {
            log.warn("[CURRENCY] Failed to fetch exchange rate, falling back to default {}: {}", DEFAULT_RATE, ex.getMessage());
            return usdAmount.multiply(DEFAULT_RATE).setScale(0, RoundingMode.HALF_UP);
        }
    }

    @Cacheable(cacheNames = "exchangeRates", key = "'USD_KRW'")
    public BigDecimal getUsdToKrwRate() {
        Assert.hasText(apiKey, "Exchange rate API key is not configured");
        String resolvedUrl = apiUrl.formatted(apiKey);
        log.debug("[CURRENCY] Fetching USD->KRW rate from {}", resolvedUrl);

        try {
            String response = webClientBuilder.build()
                    .get()
                    .uri(resolvedUrl)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, ClientResponse::createException)
                    .bodyToMono(String.class)
                    .block();

            if (response == null) {
                throw new IllegalStateException("Empty exchange rate response");
            }

            JsonObject root = gson.fromJson(response, JsonObject.class);
            JsonObject rates = root.getAsJsonObject("conversion_rates");
            if (rates == null || !rates.has("KRW")) {
                throw new IllegalStateException("KRW rate not found in response");
            }

            BigDecimal rate = rates.get("KRW").getAsBigDecimal();
            log.info("[CURRENCY] Loaded USD->KRW rate: {}", rate);
            return rate;
        } catch (WebClientResponseException e) {
            log.error("[CURRENCY] Exchange rate API error: {}", e.getResponseBodyAsString(), e);
            throw e;
        }
    }
}
