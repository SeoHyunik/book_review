package com.example.bookreview.service;

import java.math.BigDecimal;

public interface CurrencyService {

    BigDecimal convertUsdToKrw(BigDecimal usdAmount);
}
