package com.example.macronews.service.news.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NaverNewsSourceProviderTest {

    @Mock
    private ExternalApiUtils externalApiUtils;

    private NaverNewsSourceProvider provider;

    @BeforeEach
    void setUp() {
        provider = new NaverNewsSourceProvider(externalApiUtils, new ObjectMapper());
        ReflectionTestUtils.setField(provider, "enabled", true);
        ReflectionTestUtils.setField(provider, "baseUrl", "https://openapi.naver.com");
        ReflectionTestUtils.setField(provider, "clientId", "client-id");
        ReflectionTestUtils.setField(provider, "clientSecret", "client-secret");
        ReflectionTestUtils.setField(provider, "display", 10);
        ReflectionTestUtils.setField(provider, "start", 1);
    }

    @Test
    @DisplayName("NAVER provider should strip bold tags and prefer original links")
    void fetchTopHeadlines_stripsBoldTagsAndPrefersOriginalLink() {
        ReflectionTestUtils.setField(provider, "rawQueries", "코스피");
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "items": [
                    {
                      "title": "<b>코스피</b> 급등",
                      "description": "외국인 <b>매수</b>세 확대",
                      "originallink": "https://news.example.com/original",
                      "link": "https://news.example.com/naver",
                      "pubDate": "Fri, 13 Mar 2026 09:15:00 +0900"
                    }
                  ]
                }
                """));

        var results = provider.fetchTopHeadlines(5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("코스피 급등");
        assertThat(results.get(0).summary()).isEqualTo("외국인 매수세 확대");
        assertThat(results.get(0).url()).isEqualTo("https://news.example.com/original");
        assertThat(results.get(0).publishedAt()).isEqualTo(Instant.parse("2026-03-13T00:15:00Z"));
    }

    @Test
    @DisplayName("NAVER provider should merge multi-query results and deduplicate by link newest first")
    void fetchTopHeadlines_mergesQueriesAndDeduplicatesNewestFirst() {
        ReflectionTestUtils.setField(provider, "rawQueries", "코스피,환율");
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(200, """
                        {
                          "items": [
                            {
                              "title": "코스피 보합",
                              "description": "장 초반 혼조",
                              "originallink": "https://news.example.com/shared",
                              "link": "https://search.naver.com/shared",
                              "pubDate": "Fri, 13 Mar 2026 08:00:00 +0900"
                            }
                          ]
                        }
                        """))
                .willReturn(new ExternalApiResult(200, """
                        {
                          "items": [
                            {
                              "title": "환율 급등",
                              "description": "달러 강세",
                              "originallink": "https://news.example.com/shared",
                              "link": "https://search.naver.com/shared-newer",
                              "pubDate": "Fri, 13 Mar 2026 10:00:00 +0900"
                            },
                            {
                              "title": "반도체 수출 개선",
                              "description": "코스닥 강세",
                              "originallink": "https://news.example.com/unique",
                              "link": "https://search.naver.com/unique",
                              "pubDate": "Fri, 13 Mar 2026 09:30:00 +0900"
                            }
                          ]
                        }
                        """));

        List<com.example.macronews.dto.external.ExternalNewsItem> results = provider.fetchTopHeadlines(5);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(item -> item.url())
                .containsExactly("https://news.example.com/shared", "https://news.example.com/unique");
        assertThat(results.get(0).title()).isEqualTo("환율 급등");
    }
}
