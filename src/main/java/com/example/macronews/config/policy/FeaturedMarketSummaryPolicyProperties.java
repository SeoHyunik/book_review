package com.example.macronews.config.policy;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.featured.market-summary")
public class FeaturedMarketSummaryPolicyProperties {

    private boolean enabled = true;
    private int windowHours = 3;
    private int maxItems = 10;
    private int minItems = 3;
    private boolean aiEnabled = true;
    private int aiWindowHours = 3;
    private int aiMaxItems = 10;
    private int aiMinItems = 3;
    private int aiMaxInputChars = 12000;
    private int aiCacheMinutes = 15;

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

    public int getMaxItems() {
        return maxItems;
    }

    public void setMaxItems(int maxItems) {
        this.maxItems = maxItems;
    }

    public int getMinItems() {
        return minItems;
    }

    public void setMinItems(int minItems) {
        this.minItems = minItems;
    }

    public boolean isAiEnabled() {
        return aiEnabled;
    }

    public void setAiEnabled(boolean aiEnabled) {
        this.aiEnabled = aiEnabled;
    }

    public int getAiWindowHours() {
        return aiWindowHours;
    }

    public void setAiWindowHours(int aiWindowHours) {
        this.aiWindowHours = aiWindowHours;
    }

    public int getAiMaxItems() {
        return aiMaxItems;
    }

    public void setAiMaxItems(int aiMaxItems) {
        this.aiMaxItems = aiMaxItems;
    }

    public int getAiMinItems() {
        return aiMinItems;
    }

    public void setAiMinItems(int aiMinItems) {
        this.aiMinItems = aiMinItems;
    }

    public int getAiMaxInputChars() {
        return aiMaxInputChars;
    }

    public void setAiMaxInputChars(int aiMaxInputChars) {
        this.aiMaxInputChars = aiMaxInputChars;
    }

    public int getAiCacheMinutes() {
        return aiCacheMinutes;
    }

    public void setAiCacheMinutes(int aiCacheMinutes) {
        this.aiCacheMinutes = aiCacheMinutes;
    }
}
