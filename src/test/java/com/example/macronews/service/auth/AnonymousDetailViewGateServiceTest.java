package com.example.macronews.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class AnonymousDetailViewGateServiceTest {

    @Test
    void allowsUniqueAnonymousDetailViewsUpToLimitAndKeepsPreviouslyViewedItemsAccessible() {
        AnonymousDetailViewGateService gateService = new AnonymousDetailViewGateService(2);
        MockHttpSession session = new MockHttpSession();

        assertThat(gateService.canAccess("news-1", session)).isTrue();
        gateService.recordAccess("news-1", session);

        assertThat(gateService.canAccess("news-2", session)).isTrue();
        gateService.recordAccess("news-2", session);

        assertThat(gateService.canAccess("news-1", session)).isTrue();
        assertThat(gateService.canAccess("news-3", session)).isFalse();
    }
}
