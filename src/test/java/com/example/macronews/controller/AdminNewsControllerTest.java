package com.example.macronews.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.macronews.domain.NewsEvent;
import com.example.macronews.dto.AutoIngestionControlStatusDto;
import com.example.macronews.dto.AutoIngestionBatchStatusDto;
import com.example.macronews.dto.AutoIngestionRunOutcome;
import com.example.macronews.dto.request.AdminIngestionRequest;
import com.example.macronews.service.macro.MacroAiService;
import com.example.macronews.service.news.AutoIngestionControlService;
import com.example.macronews.service.news.AutoIngestionRunCommandResult;
import com.example.macronews.service.news.NewsIngestionService;
import com.example.macronews.service.news.NewsQueryService;
import com.example.macronews.service.news.source.NewsSourceProviderSelector;
import com.example.macronews.service.notification.AutoIngestionEmailNotificationService;
import com.example.macronews.service.ops.OpsFeatureToggleService;
import com.example.macronews.service.ops.RenderKeepAliveService;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

class AdminNewsControllerTest {

    @Test
    @DisplayName("manual external ingestion should keep the user requested count unchanged")
    void ingest_keepsManualRequestedCountBelowScheduledMinimum() {
        NewsIngestionService newsIngestionService = mock(NewsIngestionService.class);
        AdminNewsController controller = new AdminNewsController(
                newsIngestionService,
                mock(NewsSourceProviderSelector.class),
                mock(MacroAiService.class),
                mock(NewsQueryService.class),
                mock(AutoIngestionControlService.class),
                new OpsFeatureToggleService(false, false),
                mock(RenderKeepAliveService.class),
                mock(AutoIngestionEmailNotificationService.class),
                messageSource());
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        AdminIngestionRequest request = new AdminIngestionRequest(null, null, null, null, null, null, 3);

        when(newsIngestionService.ingestTopHeadlines(3)).thenReturn(List.of());

        String redirect = controller.ingest(request, redirectAttributes);

        assertThat(redirect).isEqualTo("redirect:/admin/news/manual");
        verify(newsIngestionService).ingestTopHeadlines(3);
    }

    @Test
    @DisplayName("user triggered auto ingestion should keep the user requested count unchanged")
    void ingestFromApi_keepsUserRequestedCountBelowScheduledMinimum() {
        NewsIngestionService newsIngestionService = mock(NewsIngestionService.class);
        NewsQueryService newsQueryService = mock(NewsQueryService.class);
        AutoIngestionControlService autoIngestionControlService = mock(AutoIngestionControlService.class);
        NewsSourceProviderSelector newsSourceProviderSelector = mock(NewsSourceProviderSelector.class);
        AdminNewsController controller = new AdminNewsController(
                newsIngestionService,
                newsSourceProviderSelector,
                mock(MacroAiService.class),
                newsQueryService,
                autoIngestionControlService,
                new OpsFeatureToggleService(false, false),
                mock(RenderKeepAliveService.class),
                mock(AutoIngestionEmailNotificationService.class),
                messageSource());
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        List<NewsEvent> ingested = List.of(sampleNewsEvent("event-1"));
        AutoIngestionBatchStatusDto batchStatus = new AutoIngestionBatchStatusDto(3, 1, 1, 0, 0, 1, false, List.of());

        when(newsSourceProviderSelector.isConfigured()).thenReturn(true);
        when(autoIngestionControlService.beginManualRun(3)).thenReturn(AutoIngestionRunCommandResult.STARTED);
        when(newsIngestionService.ingestTopHeadlines(3)).thenReturn(ingested);
        when(newsQueryService.getAutoIngestionBatchStatus(3, 1, List.of("event-1"))).thenReturn(batchStatus);

        String redirect = controller.ingestFromApi(3, redirectAttributes);

        assertThat(redirect).isEqualTo("redirect:/admin/news/auto");
        verify(autoIngestionControlService).beginManualRun(3);
        verify(newsIngestionService).ingestTopHeadlines(3);
    }

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

    @Test
    @DisplayName("auto ingest form should expose keep-alive and email control state for rendering")
    void autoIngestForm_exposesOpsFeatureStatuses() {
        OpsFeatureToggleService opsFeatureToggleService = new OpsFeatureToggleService(false, false);
        RenderKeepAliveService renderKeepAliveService = mock(RenderKeepAliveService.class);
        AutoIngestionEmailNotificationService emailService = mock(AutoIngestionEmailNotificationService.class);
        NewsQueryService newsQueryService = mock(NewsQueryService.class);
        AutoIngestionControlService autoIngestionControlService = mock(AutoIngestionControlService.class);
        NewsSourceProviderSelector newsSourceProviderSelector = mock(NewsSourceProviderSelector.class);

        when(newsQueryService.getRecentNews(null)).thenReturn(List.of());
        when(autoIngestionControlService.getStatus()).thenReturn(new AutoIngestionControlStatusDto(
                false, false, AutoIngestionRunOutcome.IDLE, null, null, null, null, null, null, null));
        when(newsSourceProviderSelector.isConfigured()).thenReturn(true);
        when(renderKeepAliveService.isConfigured()).thenReturn(false);
        when(renderKeepAliveService.isRuntimeEnabled()).thenReturn(false);
        when(renderKeepAliveService.isEffectivelyEnabled()).thenReturn(false);
        when(renderKeepAliveService.hasTargetUrl()).thenReturn(false);
        when(emailService.isConfigured()).thenReturn(false);
        when(emailService.isRuntimeEnabled()).thenReturn(false);
        when(emailService.isEffectivelyEnabled()).thenReturn(false);
        when(emailService.hasRecipient()).thenReturn(false);
        when(emailService.hasMailSender()).thenReturn(false);

        AdminNewsController controller = new AdminNewsController(
                mock(NewsIngestionService.class),
                newsSourceProviderSelector,
                mock(MacroAiService.class),
                newsQueryService,
                autoIngestionControlService,
                opsFeatureToggleService,
                renderKeepAliveService,
                emailService,
                messageSource());
        ExtendedModelMap model = new ExtendedModelMap();

        String viewName = controller.autoIngestForm(null, model);

        assertThat(viewName).isEqualTo("admin/news/ingest-api");
        assertThat(model.getAttribute("keepAliveStatus")).isNotNull();
        assertThat(model.getAttribute("emailNotificationStatus")).isNotNull();
        assertThat(model.getAttribute("autoIngestionControlStatus")).isNotNull();
    }

    @Test
    @DisplayName("korean message bundle should include keep-alive and email operation labels")
    void koreanMessageBundle_containsOpsPanelKeys() {
        ResourceBundle bundle = ResourceBundle.getBundle("messages", Locale.KOREAN);

        assertThat(bundle.getString("admin.news.auto.ops.config.enabled")).isNotBlank();
        assertThat(bundle.getString("admin.news.auto.ops.runtime.enabled")).isNotBlank();
        assertThat(bundle.getString("admin.news.auto.ops.effective.enabled")).isNotBlank();
        assertThat(bundle.getString("admin.news.auto.keepAlive.heading")).isNotBlank();
        assertThat(bundle.getString("admin.news.auto.keepAlive.configHint.disabled")).isNotBlank();
        assertThat(bundle.getString("admin.news.auto.email.heading")).isNotBlank();
        assertThat(bundle.getString("admin.news.auto.email.configHint.disabled")).isNotBlank();
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

    private NewsEvent sampleNewsEvent(String id) {
        return new NewsEvent(
                id,
                "external-" + id,
                "Title " + id,
                "Summary",
                "Reuters",
                "https://example.com/" + id,
                Instant.parse("2026-03-13T00:00:00Z"),
                Instant.parse("2026-03-13T00:01:00Z"),
                null,
                null
        );
    }
}
