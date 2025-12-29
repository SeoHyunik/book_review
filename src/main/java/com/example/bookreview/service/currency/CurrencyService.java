package com.example.bookreview.service.currency;

import java.math.BigDecimal;

public interface CurrencyService {

    BigDecimal convertUsdToKrw(BigDecimal usdAmount);
}
