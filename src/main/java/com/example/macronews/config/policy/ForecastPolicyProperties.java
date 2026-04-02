package com.example.macronews.config.policy;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.forecast")
public class ForecastPolicyProperties {

    private boolean enabled = true;
    private int windowHours = 3;
    private int maxNewsItems = 20;
    private int cacheMinutes = 15;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getWindowHours() {
        return windowHours;
    }

    public void setWindowHours(int windowHours) {
        this.windowHours = windowHours;
    }

    public int getMaxNewsItems() {
        return maxNewsItems;
    }

    public void setMaxNewsItems(int maxNewsItems) {
        this.maxNewsItems = maxNewsItems;
    }

    public int getCacheMinutes() {
        return cacheMinutes;
    }

    public void setCacheMinutes(int cacheMinutes) {
        this.cacheMinutes = cacheMinutes;
    }
}
