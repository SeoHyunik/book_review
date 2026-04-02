package com.example.macronews;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import com.example.macronews.config.policy.FeaturedMarketSummaryPolicyProperties;
import com.example.macronews.config.policy.ForecastPolicyProperties;

@EnableCaching
@SpringBootApplication
@EnableConfigurationProperties({
        FeaturedMarketSummaryPolicyProperties.class,
        ForecastPolicyProperties.class
})
public class MacroNewsApplication {

    public static void main(String[] args) {
        SpringApplication.run(MacroNewsApplication.class, args);
    }
}
