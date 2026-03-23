package com.example.macronews.service.news.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class NaverNewsSourceProviderBindingTest {

    @Test
    @DisplayName("System env should override yaml default for NAVER enabled flag")
    void systemEnvOverridesYamlDefaultForEnabledFlag() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addLast(new MapPropertySource("yamlDefaults", Map.of(
                    "app.news.naver.enabled", "false",
                    "app.news.naver.client-id", "yaml-client-id",
                    "app.news.naver.client-secret", "yaml-client-secret"
            )));
            context.getEnvironment().getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                    "systemEnvironment", Map.of("APP_NEWS_NAVER_ENABLED", "true")));

            context.registerBean(ExternalApiUtils.class, () -> mock(ExternalApiUtils.class));
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.registerBean(NaverNewsSourceProvider.class);
            context.refresh();

            NaverNewsSourceProvider provider = context.getBean(NaverNewsSourceProvider.class);

            assertThat(provider.isConfigured()).isTrue();
        }
    }
}
