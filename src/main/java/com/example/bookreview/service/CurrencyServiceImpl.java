package com.example.bookreview.service;

import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CurrencyServiceImpl implements CurrencyService {

    @Override
    public BigDecimal convertUsdToKrw(BigDecimal usdAmount) {
        log.info("[CURRENCY] Converting USD to KRW for amount={}", usdAmount);
        // TODO: 환율 API 호출 및 캐싱 로직 구현
        BigDecimal result = usdAmount.multiply(BigDecimal.valueOf(1300));
        log.debug("[CURRENCY] Converted USD {} to KRW {} using static rate", usdAmount, result);
        return result;
    }
}
