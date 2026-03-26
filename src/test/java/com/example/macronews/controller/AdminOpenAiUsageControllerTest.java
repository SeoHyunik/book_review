package com.example.macronews.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.example.macronews.dto.OpenAiUsageDashboardDto;
import com.example.macronews.service.openai.OpenAiUsageReportService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;

@ExtendWith(MockitoExtension.class)
class AdminOpenAiUsageControllerTest {

    @Mock
    private OpenAiUsageReportService openAiUsageReportService;

    @InjectMocks
    private AdminOpenAiUsageController adminOpenAiUsageController;

    @Test
    @DisplayName("usage page should expose dashboard model")
    void usage_addsDashboardToModel() {
        OpenAiUsageDashboardDto dashboard = new OpenAiUsageDashboardDto(
                List.of(),
                0L,
                1,
                1,
                false,
                false,
                List.of(),
                List.of(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(1350d),
                "admin.openai.exchange.fallback",
                true,
                false
        );
        given(openAiUsageReportService.getDashboard(1)).willReturn(dashboard);

        ConcurrentModel model = new ConcurrentModel();
        String viewName = adminOpenAiUsageController.usage(null, model);

        assertThat(viewName).isEqualTo("admin/openai/usage");
        assertThat(model.getAttribute("usageDashboard")).isEqualTo(dashboard);
    }

    @Test
    @DisplayName("usage page should pass explicit page parameter to report service")
    void usage_passesExplicitPageParameter() {
        OpenAiUsageDashboardDto dashboard = new OpenAiUsageDashboardDto(
                List.of(),
                24L,
                2,
                3,
                true,
                true,
                List.of(),
                List.of(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(1350d),
                "admin.openai.exchange.fallback",
                true,
                false
        );
        given(openAiUsageReportService.getDashboard(2)).willReturn(dashboard);

        ConcurrentModel model = new ConcurrentModel();
        String viewName = adminOpenAiUsageController.usage(2, model);

        assertThat(viewName).isEqualTo("admin/openai/usage");
        assertThat(model.getAttribute("usageDashboard")).isEqualTo(dashboard);
    }

    @Test
    @DisplayName("usage page should normalize invalid page parameter to first page")
    void usage_normalizesInvalidPageParameter() {
        OpenAiUsageDashboardDto dashboard = new OpenAiUsageDashboardDto(
                List.of(),
                0L,
                1,
                1,
                false,
                false,
                List.of(),
                List.of(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(1350d),
                "admin.openai.exchange.fallback",
                true,
                false
        );
        given(openAiUsageReportService.getDashboard(1)).willReturn(dashboard);

        ConcurrentModel model = new ConcurrentModel();
        String viewName = adminOpenAiUsageController.usage(0, model);

        assertThat(viewName).isEqualTo("admin/openai/usage");
        assertThat(model.getAttribute("usageDashboard")).isEqualTo(dashboard);
    }
}
