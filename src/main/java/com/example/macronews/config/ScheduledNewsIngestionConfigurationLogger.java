package com.example.macronews.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ScheduledNewsIngestionConfigurationLogger {

    @Value("${app.ingestion.scheduler.enabled:false}")
    private boolean schedulerEnabled;

    @Value("${app.ingestion.scheduler.cron:0 0 * * * *}")
    private String schedulerCron;

    @Value("${app.ingestion.scheduler.page-size:10}")
    private int schedulerPageSize;

    @EventListener(ApplicationReadyEvent.class)
    public void logConfiguration() {
        if (!schedulerEnabled) {
            log.info("[SCHEDULER] automatic ingestion is disabled");
            return;
        }

        log.info("[SCHEDULER] automatic ingestion is enabled cron={} pageSize={}",
                schedulerCron, schedulerPageSize);
    }
}
