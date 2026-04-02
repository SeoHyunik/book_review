package com.example.macronews.service.news.source;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;

final class ProviderPriorityResolver {

    private static final ZoneId DEFAULT_BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_DOMESTIC_START_HOUR = 5;
    private static final int DEFAULT_DOMESTIC_END_HOUR = 22;

    NewsFeedPriority currentPriority(Clock clock, String businessTimezone, int domesticStartHour, int domesticEndHour) {
        return isDomesticWindow(clock, businessTimezone, domesticStartHour, domesticEndHour)
                ? NewsFeedPriority.DOMESTIC
                : NewsFeedPriority.FOREIGN;
    }

    boolean isDomesticWindow(Clock clock, String businessTimezone, int domesticStartHour, int domesticEndHour) {
        int startHour = resolveHour(domesticStartHour, DEFAULT_DOMESTIC_START_HOUR);
        int endHour = resolveHour(domesticEndHour, DEFAULT_DOMESTIC_END_HOUR);
        int currentHour = ZonedDateTime.now(resolveBusinessClock(clock, businessTimezone)).getHour();

        if (startHour <= endHour) {
            return currentHour >= startHour && currentHour <= endHour;
        }
        return currentHour >= startHour || currentHour <= endHour;
    }

    NewsFeedPriority fallbackPriority(NewsFeedPriority priority) {
        return priority == NewsFeedPriority.DOMESTIC ? NewsFeedPriority.FOREIGN : NewsFeedPriority.DOMESTIC;
    }

    private int resolveHour(int configuredHour, int fallbackHour) {
        return configuredHour >= 0 && configuredHour <= 23 ? configuredHour : fallbackHour;
    }

    private Clock resolveBusinessClock(Clock clock, String businessTimezone) {
        return clock.withZone(resolveBusinessZone(businessTimezone));
    }

    private ZoneId resolveBusinessZone(String businessTimezone) {
        try {
            return ZoneId.of(businessTimezone);
        } catch (Exception ex) {
            return DEFAULT_BUSINESS_ZONE;
        }
    }
}
