package com.example.macronews.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.macronews.service.macro.MacroAiService;
import com.example.macronews.service.news.AutoIngestionControlService;
import com.example.macronews.service.news.NewsIngestionService;
import com.example.macronews.service.news.NewsQueryService;
import com.example.macronews.service.news.source.NewsSourceProviderSelector;
import com.example.macronews.service.notification.AutoIngestionEmailNotificationService;
import com.example.macronews.service.ops.OpsFeatureToggleService;
import com.example.macronews.service.ops.RenderKeepAliveService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

class AdminNewsControllerTest {

    @Test
    @DisplayName("keep-alive runtime endpoints should toggle state and redirect to the admin auto page")
    void keepAliveRuntimeEndpoints_toggleStateAndRedirect() {
        OpsFeatureToggleService opsFeatureToggleService = new OpsFeatureToggleService(false, false);
        RenderKeepAliveService renderKeepAliveService = mock(RenderKeepAliveService.class);
        when(renderKeepAliveService.isConfigured()).thenReturn(true);

        AdminNewsController controller = controller(opsFeatureToggleService, renderKeepAliveService,
                mock(AutoIngestionEmailNotificationService.class));
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String startRedirect = controller.startKeepAlive(redirectAttributes);

        assertThat(startRedirect).isEqualTo("redirect:/admin/news/auto");
        assertThat(opsFeatureToggleService.isKeepAliveRuntimeEnabled()).isTrue();

        RedirectAttributesModelMap stopRedirectAttributes = new RedirectAttributesModelMap();
        String stopRedirect = controller.stopKeepAlive(stopRedirectAttributes);

        assertThat(stopRedirect).isEqualTo("redirect:/admin/news/auto");
        assertThat(opsFeatureToggleService.isKeepAliveRuntimeEnabled()).isFalse();
    }

    @Test
    @DisplayName("email runtime endpoints should toggle state and redirect to the admin auto page")
    void emailRuntimeEndpoints_toggleStateAndRedirect() {
        OpsFeatureToggleService opsFeatureToggleService = new OpsFeatureToggleService(false, false);
        AutoIngestionEmailNotificationService emailService = mock(AutoIngestionEmailNotificationService.class);
        when(emailService.isConfigured()).thenReturn(true);

        AdminNewsController controller = controller(opsFeatureToggleService, mock(RenderKeepAliveService.class),
                emailService);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String startRedirect = controller.startEmailNotification(redirectAttributes);

        assertThat(startRedirect).isEqualTo("redirect:/admin/news/auto");
        assertThat(opsFeatureToggleService.isEmailNotificationRuntimeEnabled()).isTrue();

        RedirectAttributesModelMap stopRedirectAttributes = new RedirectAttributesModelMap();
        String stopRedirect = controller.stopEmailNotification(stopRedirectAttributes);

        assertThat(stopRedirect).isEqualTo("redirect:/admin/news/auto");
        assertThat(opsFeatureToggleService.isEmailNotificationRuntimeEnabled()).isFalse();
    }

    @Test
    @DisplayName("globally disabled features should remain runtime disabled when start endpoints are hit")
    void startEndpoints_doNotEnableGloballyDisabledFeatures() {
        OpsFeatureToggleService opsFeatureToggleService = new OpsFeatureToggleService(false, false);
        RenderKeepAliveService renderKeepAliveService = mock(RenderKeepAliveService.class);
        AutoIngestionEmailNotificationService emailService = mock(AutoIngestionEmailNotificationService.class);
        when(renderKeepAliveService.isConfigured()).thenReturn(false);
        when(emailService.isConfigured()).thenReturn(false);

        AdminNewsController controller = controller(opsFeatureToggleService, renderKeepAliveService, emailService);

        String keepAliveRedirect = controller.startKeepAlive(new RedirectAttributesModelMap());
        String emailRedirect = controller.startEmailNotification(new RedirectAttributesModelMap());

        assertThat(keepAliveRedirect).isEqualTo("redirect:/admin/news/auto");
        assertThat(emailRedirect).isEqualTo("redirect:/admin/news/auto");
        assertThat(opsFeatureToggleService.isKeepAliveRuntimeEnabled()).isFalse();
        assertThat(opsFeatureToggleService.isEmailNotificationRuntimeEnabled()).isFalse();
    }

    private AdminNewsController controller(
            OpsFeatureToggleService opsFeatureToggleService,
            RenderKeepAliveService renderKeepAliveService,
            AutoIngestionEmailNotificationService emailService) {
        return new AdminNewsController(
                mock(NewsIngestionService.class),
                mock(NewsSourceProviderSelector.class),
                mock(MacroAiService.class),
                mock(NewsQueryService.class),
                mock(AutoIngestionControlService.class),
                opsFeatureToggleService,
                renderKeepAliveService,
                emailService,
                messageSource());
    }

    private StaticMessageSource messageSource() {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.setUseCodeAsDefaultMessage(true);
        messageSource.addMessage("admin.news.auto.keepAlive.started", java.util.Locale.ENGLISH,
                "Keep-alive runtime toggle has been enabled.");
        messageSource.addMessage("admin.news.auto.keepAlive.stopped", java.util.Locale.ENGLISH,
                "Keep-alive runtime toggle has been disabled.");
        messageSource.addMessage("admin.news.auto.keepAlive.unavailable", java.util.Locale.ENGLISH,
                "Keep-alive is globally disabled by static configuration.");
        messageSource.addMessage("admin.news.auto.keepAlive.alreadyStarted", java.util.Locale.ENGLISH,
                "Keep-alive runtime toggle is already enabled.");
        messageSource.addMessage("admin.news.auto.keepAlive.alreadyStopped", java.util.Locale.ENGLISH,
                "Keep-alive runtime toggle is already disabled.");
        messageSource.addMessage("admin.news.auto.email.started", java.util.Locale.ENGLISH,
                "Email notification runtime toggle has been enabled.");
        messageSource.addMessage("admin.news.auto.email.stopped", java.util.Locale.ENGLISH,
                "Email notification runtime toggle has been disabled.");
        messageSource.addMessage("admin.news.auto.email.unavailable", java.util.Locale.ENGLISH,
                "Email notification is globally disabled by static configuration.");
        messageSource.addMessage("admin.news.auto.email.alreadyStarted", java.util.Locale.ENGLISH,
                "Email notification runtime toggle is already enabled.");
        messageSource.addMessage("admin.news.auto.email.alreadyStopped", java.util.Locale.ENGLISH,
                "Email notification runtime toggle is already disabled.");
        return messageSource;
    }
}
