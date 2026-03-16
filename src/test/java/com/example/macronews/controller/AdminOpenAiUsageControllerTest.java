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
                List.of(),
                List.of(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(1350d),
                "admin.openai.exchange.fallback",
                true
        );
        given(openAiUsageReportService.getDashboard()).willReturn(dashboard);

        ConcurrentModel model = new ConcurrentModel();
        String viewName = adminOpenAiUsageController.usage(model);

        assertThat(viewName).isEqualTo("admin/openai/usage");
        assertThat(model.getAttribute("usageDashboard")).isEqualTo(dashboard);
    }
}
