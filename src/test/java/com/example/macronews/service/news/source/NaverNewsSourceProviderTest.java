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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
        ReflectionTestUtils.setField(provider, "maxAgeHours", 12L);
        ReflectionTestUtils.setField(provider, "fallbackMaxAgeHours", 24L);
        ReflectionTestUtils.setField(provider, "maxPages", 3);
        ReflectionTestUtils.setField(provider, "clock",
                Clock.fixed(Instant.parse("2026-03-13T03:30:00Z"), ZoneId.of("Asia/Seoul")));
    }

    @Test
    @DisplayName("NAVER provider should be configured when enabled and credentials are present")
    void isConfigured_returnsTrueWhenEnabledAndCredentialsPresent() {
        assertThat(provider.isConfigured()).isTrue();
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
    @DisplayName("NAVER provider should parse alternate pubDate formats safely")
    void fetchTopHeadlines_parsesAlternatePubDateFormat() {
        ReflectionTestUtils.setField(provider, "rawQueries", "kospi");
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "items": [
                    {
                      "title": "KOSPI tone firms",
                      "description": "Alternate date format",
                      "originallink": "https://news.example.com/alternate-date",
                      "link": "https://search.naver.com/alternate-date",
                      "pubDate": "Fri, 13 Mar 2026 09:15:00 GMT"
                    }
                  ]
                }
                """));

        List<ExternalNewsItem> results = provider.fetchTopHeadlines(5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).publishedAt()).isEqualTo(Instant.parse("2026-03-13T09:15:00Z"));
    }

    @Test
    @DisplayName("NAVER provider should use link when original link is missing")
    void fetchTopHeadlines_usesLinkWhenOriginalLinkMissing() {
        ReflectionTestUtils.setField(provider, "rawQueries", "usd");
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "items": [
                    {
                      "title": "Dollar pulls back",
                      "description": "Fallback link should be used",
                      "originallink": "",
                      "link": "https://search.naver.com/usd-pullback",
                      "pubDate": "Fri, 13 Mar 2026 09:15:00 +0900"
                    }
                  ]
                }
                """));

        List<ExternalNewsItem> results = provider.fetchTopHeadlines(5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).url()).isEqualTo("https://search.naver.com/usd-pullback");
        assertThat(results.get(0).externalId()).isEqualTo("https://search.naver.com/usd-pullback");
    }

    @Test
    @DisplayName("NAVER provider should decode percent-encoded descriptions safely")
    void fetchTopHeadlines_decodesPercentEncodedDescription() {
        ReflectionTestUtils.setField(provider, "rawQueries", "bond");
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "items": [
                    {
                      "title": "Bond market steadies",
                      "description": "%EC%97%B0%EC%A4%80%20%EA%B8%88%EB%A6%AC%20%EC%9D%B8%ED%95%98%20%EA%B8%B0%EB%8C%80",
                      "originallink": "https://news.example.com/bond-market",
                      "link": "https://search.naver.com/bond-market",
                      "pubDate": "Fri, 13 Mar 2026 09:15:00 +0900"
                    }
                  ]
                }
                """));

        List<ExternalNewsItem> results = provider.fetchTopHeadlines(5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).summary()).isEqualTo("연준 금리 인하 기대");
    }

    @Test
    @DisplayName("NAVER provider should keep normal descriptions unchanged")
    void fetchTopHeadlines_keepsNormalDescriptionUnchanged() {
        ReflectionTestUtils.setField(provider, "rawQueries", "kosdaq");
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "items": [
                    {
                      "title": "KOSDAQ recovers",
                      "description": "기관 수급이 개선되고 있습니다.",
                      "originallink": "https://news.example.com/kosdaq",
                      "link": "https://search.naver.com/kosdaq",
                      "pubDate": "Fri, 13 Mar 2026 09:15:00 +0900"
                    }
                  ]
                }
                """));

        List<ExternalNewsItem> results = provider.fetchTopHeadlines(5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).summary()).isEqualTo("기관 수급이 개선되고 있습니다.");
    }

    @Test
    @DisplayName("NAVER provider should filter out fresh but irrelevant items")
    void fetchTopHeadlines_filtersFreshIrrelevantItems() {
        ReflectionTestUtils.setField(provider, "rawQueries", "\uCF54\uC2A4\uD53C");
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "items": [
                    {
                      "title": "KOSPI opens higher",
                      "description": "Fed signals patience on rates",
                      "originallink": "https://news.example.com/relevant",
                      "link": "https://search.naver.com/relevant",
                      "pubDate": "Fri, 13 Mar 2026 09:15:00 +0900"
                    },
                    {
                      "title": "Celebrity profile",
                      "description": "Entertainment roundup",
                      "originallink": "https://news.example.com/irrelevant",
                      "link": "https://search.naver.com/irrelevant",
                      "pubDate": "Fri, 13 Mar 2026 09:10:00 +0900"
                    }
                  ]
                }
                """));

        List<ExternalNewsItem> results = provider.fetchTopHeadlines(5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).url()).isEqualTo("https://news.example.com/relevant");
    }

    @Test
    @DisplayName("NAVER provider should not use links as summary text")
    void fetchTopHeadlines_doesNotUseLinkAsSummary() {
        ReflectionTestUtils.setField(provider, "rawQueries", "semiconductor");
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "items": [
                    {
                      "title": "KOSPI cycle improves",
                      "description": "https://news.example.com/chip-cycle",
                      "originallink": "https://news.example.com/chip-cycle",
                      "link": "https://search.naver.com/chip-cycle",
                      "pubDate": "Fri, 13 Mar 2026 09:15:00 +0900"
                    }
                  ]
                }
                """));

        List<ExternalNewsItem> results = provider.fetchTopHeadlines(5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).summary()).isEmpty();
    }

    @Test
    @DisplayName("NAVER provider should skip items when pubDate is unparseable")
    void fetchTopHeadlines_skipsItemsWhenPubDateIsUnparseable() {
        ReflectionTestUtils.setField(provider, "rawQueries", "oil");
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "items": [
                    {
                      "title": "Oil volatility returns",
                      "description": "Date is malformed but item should remain usable",
                      "originallink": "https://news.example.com/oil-volatility",
                      "link": "https://search.naver.com/oil-volatility",
                      "pubDate": "not-a-date"
                    }
                  ]
                }
                """));

        List<ExternalNewsItem> results = provider.fetchTopHeadlines(5);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("NAVER provider should skip stale items beyond configured max age")
    void fetchTopHeadlines_skipsStaleItems() {
        ReflectionTestUtils.setField(provider, "rawQueries", "fx");
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "items": [
                    {
                      "title": "FX headline",
                      "description": "Too old for real-time feed",
                      "originallink": "https://news.example.com/fx-old",
                      "link": "https://search.naver.com/fx-old",
                      "pubDate": "Thu, 12 Mar 2026 00:00:00 +0900"
                    }
                  ]
                }
                """));

        List<ExternalNewsItem> results = provider.fetchTopHeadlines(5);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("NAVER semi-fresh bucket should allow controlled fallback items")
    void fetchTopHeadlines_allowsSemiFreshItemsInsideFallbackWindow() {
        ReflectionTestUtils.setField(provider, "rawQueries", "fx");
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "items": [
                    {
                      "title": "KOSPI earlier wrap",
                      "description": "Inside fallback window",
                      "originallink": "https://news.example.com/fx-semi",
                      "link": "https://search.naver.com/fx-semi",
                      "pubDate": "Thu, 12 Mar 2026 18:00:00 +0900"
                    }
                  ]
                }
                """));

        assertThat(provider.fetchTopHeadlines(5)).isEmpty();
        assertThat(provider.fetchTopHeadlines(5, NewsFreshnessBucket.SEMI_FRESH))
                .extracting(ExternalNewsItem::url)
                .containsExactly("https://news.example.com/fx-semi");
    }

    @Test
    @DisplayName("NAVER provider should fetch a later page when page one is stale only")
    void fetchTopHeadlines_fetchesLaterPageWhenFirstPageIsStale() {
        ReflectionTestUtils.setField(provider, "rawQueries", "코스피");
        ReflectionTestUtils.setField(provider, "display", 5);
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(200, """
                        {
                          "items": [
                            {
                              "title": "Old market wrap",
                              "description": "Too old",
                              "originallink": "https://news.example.com/old-1",
                              "link": "https://search.naver.com/old-1",
                              "pubDate": "Thu, 12 Mar 2026 00:00:00 +0900"
                            }
                          ]
                        }
                        """))
                .willReturn(new ExternalApiResult(200, """
                        {
                          "items": [
                            {
                      "title": "Fresh KOSPI intraday move",
                              "description": "Fresh page two item",
                              "originallink": "https://news.example.com/fresh-2",
                              "link": "https://search.naver.com/fresh-2",
                              "pubDate": "Fri, 13 Mar 2026 11:45:00 +0900"
                            }
                          ]
                        }
                        """));

        List<ExternalNewsItem> results = provider.fetchTopHeadlines(5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).url()).isEqualTo("https://news.example.com/fresh-2");
        List<String> urls = capturedRequestUrls();
        assertThat(urls).anySatisfy(url -> assertThat(url).contains("start=1"));
        assertThat(urls).anySatisfy(url -> assertThat(url).contains("start=6"));
    }

    @Test
    @DisplayName("NAVER provider should stop paging once enough fresh items are found")
    void fetchTopHeadlines_stopsPagingWhenEnoughFreshItemsAreFound() {
        ReflectionTestUtils.setField(provider, "rawQueries", "코스피");
        ReflectionTestUtils.setField(provider, "display", 5);
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "items": [
                    {
                      "title": "Fresh KOSPI item one",
                      "description": "Fresh result",
                      "originallink": "https://news.example.com/fresh-1",
                      "link": "https://search.naver.com/fresh-1",
                      "pubDate": "Fri, 13 Mar 2026 11:50:00 +0900"
                    },
                    {
                      "title": "Fresh KOSPI item two",
                      "description": "Fresh result",
                      "originallink": "https://news.example.com/fresh-2",
                      "link": "https://search.naver.com/fresh-2",
                      "pubDate": "Fri, 13 Mar 2026 11:45:00 +0900"
                    }
                  ]
                }
                """));

        List<ExternalNewsItem> results = provider.fetchTopHeadlines(2);

        assertThat(results).hasSize(2);
        verify(externalApiUtils).callAPI(any());
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
    @DisplayName("NAVER provider should deduplicate fallback link variants by normalized title when original links are missing")
    void fetchTopHeadlines_deduplicatesFallbackLinkVariantsByNormalizedTitle() {
        ReflectionTestUtils.setField(provider, "rawQueries", "\uCF54\uC2A4\uD53C");
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "items": [
                    {
                      "title": "[속보] 코스피 급등",
                      "description": "Earlier version",
                      "originallink": "",
                      "link": "https://search.naver.com/read?oid=1",
                      "pubDate": "Fri, 13 Mar 2026 09:00:00 +0900"
                    },
                    {
                      "title": "코스피 급등",
                      "description": "Later version",
                      "originallink": "",
                      "link": "https://search.naver.com/read?oid=2",
                      "pubDate": "Fri, 13 Mar 2026 09:10:00 +0900"
                    }
                  ]
                }
                """));

        List<ExternalNewsItem> results = provider.fetchTopHeadlines(5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).summary()).isEqualTo("Later version");
        assertThat(results.get(0).url()).isEqualTo("https://search.naver.com/read?oid=2");
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
                .allSatisfy(url -> assertThat(url).contains("sort=date"))
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

        assertThat(decodedRequestUrls()).hasSize(15)
                .anySatisfy(url -> assertThat(url).contains("query=\uCF54\uC2A4\uD53C"))
                .anySatisfy(url -> assertThat(url).contains("query=\uCF54\uC2A4\uB2E5"))
                .anySatisfy(url -> assertThat(url).contains("query=\uC6D0\uB2EC\uB7EC \uD658\uC728"))
                .anySatisfy(url -> assertThat(url).contains("query=\uAE30\uC900\uAE08\uB9AC"))
                .anySatisfy(url -> assertThat(url).contains("query=\uBBF8\uAD6D\uCC44 \uAE08\uB9AC"))
                .anySatisfy(url -> assertThat(url).contains("query=\uCC44\uAD8C \uAE08\uB9AC"))
                .anySatisfy(url -> assertThat(url).contains("query=\uAD6D\uC81C\uC720\uAC00"))
                .anySatisfy(url -> assertThat(url).contains("query=\uBC18\uB3C4\uCCB4"))
                .anySatisfy(url -> assertThat(url).contains("query=\uC5F0\uC900"))
                .anySatisfy(url -> assertThat(url).contains("query=\uBBF8\uAD6D\uAE08\uB9AC"))
                .anySatisfy(url -> assertThat(url).contains("query=\uBB3C\uAC00 \uBC1C\uD45C"))
                .anySatisfy(url -> assertThat(url).contains("query=\uACE0\uC6A9 \uBC1C\uD45C"))
                .anySatisfy(url -> assertThat(url).contains("query=\uC99D\uC2DC"))
                .anySatisfy(url -> assertThat(url).contains("query=\uC8FC\uC2DD"))
                .allSatisfy(url -> assertThat(url)
                        .doesNotContain("query=\uC7A5\uC911")
                        .doesNotContain("query=\uB9C8\uAC10"));
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

        assertThat(decodedRequestUrls()).hasSize(15)
                .anySatisfy(url -> assertThat(url).contains("query=\uCF54\uC2A4\uD53C"))
                .anySatisfy(url -> assertThat(url).contains("query=\uC5F0\uC900"))
                .anySatisfy(url -> assertThat(url).contains("query=\uACE0\uC6A9 \uBC1C\uD45C"))
                .anySatisfy(url -> assertThat(url).contains("query=\uC99D\uC2DC"))
                .anySatisfy(url -> assertThat(url).contains("query=\uC8FC\uC2DD"))
                .allSatisfy(url -> assertThat(url)
                        .doesNotContain("query=\uBBF8\uC911\uAC08\uB4F1")
                        .doesNotContain("query=\uC6B0\uD06C\uB77C\uC774\uB098"));
    }

    @Test
    @DisplayName("NAVER provider should cap display safely at one hundred")
    void fetchTopHeadlines_capsDisplayAtOneHundred() {
        ReflectionTestUtils.setField(provider, "rawQueries", "\uCF54\uC2A4\uD53C");
        ReflectionTestUtils.setField(provider, "display", 150);
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "items": []
                }
                """));

        provider.fetchTopHeadlines(200);

        assertThat(capturedRequestUrls()).singleElement()
                .satisfies(url -> assertThat(url).contains("display=100"));
    }

    @Test
    @DisplayName("NAVER provider should cap configured start safely at one thousand")
    void fetchTopHeadlines_capsConfiguredStartAtOneThousand() {
        ReflectionTestUtils.setField(provider, "rawQueries", "\uCF54\uC2A4\uD53C");
        ReflectionTestUtils.setField(provider, "start", 2005);
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "items": []
                }
                """));

        provider.fetchTopHeadlines(5);

        assertThat(capturedRequestUrls()).singleElement()
                .satisfies(url -> assertThat(url).contains("start=1000"));
    }

    @Test
    @DisplayName("NAVER provider should stop paging before start exceeds one thousand")
    void fetchTopHeadlines_stopsPagingBeforeStartExceedsLimit() {
        ReflectionTestUtils.setField(provider, "rawQueries", "\uCF54\uC2A4\uD53C");
        ReflectionTestUtils.setField(provider, "display", 100);
        ReflectionTestUtils.setField(provider, "start", 950);
        ReflectionTestUtils.setField(provider, "maxPages", 5);
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "items": []
                }
                """));

        provider.fetchTopHeadlines(100);

        assertThat(capturedRequestUrls()).hasSize(1)
                .first()
                .satisfies(url -> assertThat(url).contains("start=950"));
    }

    @Test
    @DisplayName("NAVER provider should remain disabled when the provider flag is false")
    void fetchTopHeadlines_returnsEmptyWhenDisabled() {
        ReflectionTestUtils.setField(provider, "enabled", false);
        ReflectionTestUtils.setField(provider, "rawQueries", "");

        assertThat(provider.isConfigured()).isFalse();
        List<ExternalNewsItem> results = provider.fetchTopHeadlines(5);

        assertThat(results).isEmpty();
        verify(externalApiUtils, never()).callAPI(any());
    }

    @Test
    @DisplayName("NAVER provider should remain unconfigured when credentials are missing")
    void fetchTopHeadlines_returnsEmptyWhenCredentialsMissing() {
        ReflectionTestUtils.setField(provider, "clientId", "");
        ReflectionTestUtils.setField(provider, "rawQueries", "");

        assertThat(provider.isConfigured()).isFalse();
        List<ExternalNewsItem> results = provider.fetchTopHeadlines(5);

        assertThat(results).isEmpty();
        verify(externalApiUtils, never()).callAPI(any());
    }

    @Test
    @DisplayName("NAVER provider should remain unconfigured when client secret is missing")
    void fetchTopHeadlines_returnsEmptyWhenClientSecretMissing() {
        ReflectionTestUtils.setField(provider, "clientSecret", "");
        ReflectionTestUtils.setField(provider, "rawQueries", "");

        assertThat(provider.isConfigured()).isFalse();
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

    private List<String> decodedRequestUrls() {
        return capturedRequestUrls().stream()
                .map(url -> URLDecoder.decode(url, StandardCharsets.UTF_8))
                .toList();
    }
}
