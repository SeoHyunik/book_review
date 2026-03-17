package com.example.macronews.service.ops;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OpsFeatureToggleService {

    private final AtomicBoolean keepAliveEnabled;
    private final AtomicBoolean emailNotificationEnabled;

    public OpsFeatureToggleService(
            @Value("${app.keep-alive.enabled:false}") boolean initialKeepAliveEnabled,
            @Value("${app.notification.email.enabled:false}") boolean initialEmailNotificationEnabled) {
        this.keepAliveEnabled = new AtomicBoolean(initialKeepAliveEnabled);
        this.emailNotificationEnabled = new AtomicBoolean(initialEmailNotificationEnabled);
    }

    public boolean isKeepAliveRuntimeEnabled() {
        return keepAliveEnabled.get();
    }

    public boolean enableKeepAlive() {
        return keepAliveEnabled.compareAndSet(false, true);
    }

    public boolean disableKeepAlive() {
        return keepAliveEnabled.compareAndSet(true, false);
    }

    public boolean isEmailNotificationRuntimeEnabled() {
        return emailNotificationEnabled.get();
    }

    public boolean enableEmailNotification() {
        return emailNotificationEnabled.compareAndSet(false, true);
    }

    public boolean disableEmailNotification() {
        return emailNotificationEnabled.compareAndSet(true, false);
    }
}
