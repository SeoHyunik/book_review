package com.example.bookreview.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CurrencyFormatter {

    private static final NumberFormat KOREAN_FORMATTER = NumberFormat.getCurrencyInstance(Locale.KOREA);
    private static final NumberFormat USD_FORMATTER = NumberFormat.getCurrencyInstance(Locale.US);

    public static String formatKrw(BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        BigDecimal rounded = amount.setScale(0, RoundingMode.HALF_UP);
        synchronized (KOREAN_FORMATTER) {
            return KOREAN_FORMATTER.format(rounded).replace("₩", "").trim() + "원";
        }
    }

    public static String formatUsd(BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        synchronized (USD_FORMATTER) {
            return USD_FORMATTER.format(amount);
        }
    }
}
