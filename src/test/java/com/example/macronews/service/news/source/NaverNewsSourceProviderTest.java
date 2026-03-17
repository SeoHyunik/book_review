package com.example.macronews.service.news.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.macronews.dto.external.ExternalNewsItem;
import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
        ReflectionTestUtils.setField(provider, "rawQueries", "\uCF54\uC2A4\uD53C");
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "items": [
                    {
                      "title": "<b>코스피</b> 급등",
                      "description": "기관 <b>매수</b> 확대",
                      "originallink": "https://news.example.com/original",
                      "link": "https://news.example.com/naver",
                      "pubDate": "Fri, 13 Mar 2026 09:15:00 +0900"
                    }
                  ]
                }
                """));

        var results = provider.fetchTopHeadlines(5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("\uCF54\uC2A4\uD53C \uAE09\uB4F1");
        assertThat(results.get(0).summary()).isEqualTo("\uAE30\uAD00 \uB9E4\uC218 \uD655\uB300");
        assertThat(results.get(0).url()).isEqualTo("https://news.example.com/original");
        assertThat(results.get(0).publishedAt()).isEqualTo(Instant.parse("2026-03-13T00:15:00Z"));
    }

    @Test
    @DisplayName("NAVER provider should merge multi-query results and deduplicate by link newest first")
    void fetchTopHeadlines_mergesQueriesAndDeduplicatesNewestFirst() {
        ReflectionTestUtils.setField(provider, "rawQueries", "\uCF54\uC2A4\uD53C,\uD658\uC728");
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
                              "description": "코스피 강세",
                              "originallink": "https://news.example.com/unique",
                              "link": "https://search.naver.com/unique",
                              "pubDate": "Fri, 13 Mar 2026 09:30:00 +0900"
                            }
                          ]
                        }
                        """));

        List<ExternalNewsItem> results = provider.fetchTopHeadlines(5);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(ExternalNewsItem::url)
                .containsExactly("https://news.example.com/shared", "https://news.example.com/unique");
        assertThat(results.get(0).title()).isEqualTo("\uD658\uC728 \uAE09\uB4F1");
    }

    @Test
    @DisplayName("NAVER provider should collapse breaking markers when deduplicating title-only items")
    void fetchTopHeadlines_deduplicatesBreakingTitleVariants() {
        ReflectionTestUtils.setField(provider, "rawQueries", "\uCF54\uC2A4\uD53C");
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "items": [
                    {
                      "title": "[속보] 코스피 상승 전환",
                      "description": "오전 흐름",
                      "originallink": "",
                      "link": "",
                      "pubDate": "Fri, 13 Mar 2026 09:00:00 +0900"
                    },
                    {
                      "title": "코스피 상승 전환",
                      "description": "오전 흐름 업데이트",
                      "originallink": "",
                      "link": "",
                      "pubDate": "Fri, 13 Mar 2026 09:10:00 +0900"
                    }
                  ]
                }
                """));

        List<ExternalNewsItem> results = provider.fetchTopHeadlines(5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("\uCF54\uC2A4\uD53C \uC0C1\uC2B9 \uC804\uD658");
        assertThat(results.get(0).summary()).isEqualTo("\uC624\uC804 \uD750\uB984 \uC5C5\uB370\uC774\uD2B8");
        assertThat(results.get(0).publishedAt()).isEqualTo(Instant.parse("2026-03-13T00:10:00Z"));
    }

    @Test
    @DisplayName("NAVER provider should use configured queries as-is when present")
    void fetchTopHeadlines_usesConfiguredQueriesWhenPresent() {
        ReflectionTestUtils.setField(provider, "rawQueries", "\uCF54\uC2A4\uD53C,\uD658\uC728");
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "items": []
                }
                """));

        provider.fetchTopHeadlines(5);

        ArgumentCaptor<ExternalApiRequest> requestCaptor = ArgumentCaptor.forClass(ExternalApiRequest.class);
        verify(externalApiUtils, atLeastOnce()).callAPI(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues())
                .extracting(ExternalApiRequest::url)
                .hasSize(2)
                .anySatisfy(url -> assertThat(url).contains("query=%EC%BD%94%EC%8A%A4%ED%94%BC"))
                .anySatisfy(url -> assertThat(url).contains("query=%ED%99%98%EC%9C%A8"))
                .allSatisfy(url -> assertThat(url)
                        .doesNotContain("query=%EC%BD%94%EC%8A%A4%EB%8B%A5")
                        .doesNotContain("query=%EA%B8%88%EB%A6%AC")
                        .doesNotContain("query=%EC%9C%A0%EA%B0%80")
                        .doesNotContain("query=%EB%B0%98%EB%8F%84%EC%B2%B4")
                        .doesNotContain("query=%EC%97%B0%EC%A4%80"));
    }

    @Test
    @DisplayName("NAVER provider should use built-in default queries when configured queries are blank")
    void fetchTopHeadlines_usesDefaultQueriesWhenConfiguredQueriesBlank() {
        ReflectionTestUtils.setField(provider, "rawQueries", "");
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "items": []
                }
                """));

        provider.fetchTopHeadlines(5);

        assertThat(capturedRequestUrls()).hasSize(7)
                .anySatisfy(url -> assertThat(url).contains("query=%EC%BD%94%EC%8A%A4%ED%94%BC"))
                .anySatisfy(url -> assertThat(url).contains("query=%EC%BD%94%EC%8A%A4%EB%8B%A5"))
                .anySatisfy(url -> assertThat(url).contains("query=%ED%99%98%EC%9C%A8"))
                .anySatisfy(url -> assertThat(url).contains("query=%EA%B8%88%EB%A6%AC"))
                .anySatisfy(url -> assertThat(url).contains("query=%EC%9C%A0%EA%B0%80"))
                .anySatisfy(url -> assertThat(url).contains("query=%EB%B0%98%EB%8F%84%EC%B2%B4"))
                .anySatisfy(url -> assertThat(url).contains("query=%EC%97%B0%EC%A4%80"));
    }

    @Test
    @DisplayName("NAVER provider should use built-in default queries when configured queries are whitespace only")
    void fetchTopHeadlines_usesDefaultQueriesWhenConfiguredQueriesWhitespaceOnly() {
        ReflectionTestUtils.setField(provider, "rawQueries", " ,  , ");
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "items": []
                }
                """));

        provider.fetchTopHeadlines(5);

        assertThat(capturedRequestUrls()).hasSize(7)
                .anySatisfy(url -> assertThat(url).contains("query=%EC%BD%94%EC%8A%A4%ED%94%BC"))
                .anySatisfy(url -> assertThat(url).contains("query=%EC%97%B0%EC%A4%80"));
    }

    @Test
    @DisplayName("NAVER provider should remain disabled when the provider flag is false")
    void fetchTopHeadlines_returnsEmptyWhenDisabled() {
        ReflectionTestUtils.setField(provider, "enabled", false);
        ReflectionTestUtils.setField(provider, "rawQueries", "");

        List<ExternalNewsItem> results = provider.fetchTopHeadlines(5);

        assertThat(results).isEmpty();
        verify(externalApiUtils, never()).callAPI(any());
    }

    @Test
    @DisplayName("NAVER provider should remain unconfigured when credentials are missing")
    void fetchTopHeadlines_returnsEmptyWhenCredentialsMissing() {
        ReflectionTestUtils.setField(provider, "clientId", "");
        ReflectionTestUtils.setField(provider, "rawQueries", "");

        List<ExternalNewsItem> results = provider.fetchTopHeadlines(5);

        assertThat(results).isEmpty();
        verify(externalApiUtils, never()).callAPI(any());
    }

    private List<String> capturedRequestUrls() {
        ArgumentCaptor<ExternalApiRequest> requestCaptor = ArgumentCaptor.forClass(ExternalApiRequest.class);
        verify(externalApiUtils, atLeastOnce()).callAPI(requestCaptor.capture());
        return requestCaptor.getAllValues().stream()
                .map(ExternalApiRequest::url)
                .toList();
    }
}
