package com.example.bookreview.service;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class CurrencyServiceImpl implements CurrencyService {

    @Override
    public BigDecimal convertUsdToKrw(BigDecimal usdAmount) {
        // TODO: 환율 API 호출 및 캐싱 로직 구현
        return usdAmount.multiply(BigDecimal.valueOf(1300));
    }
}
