package com.example.macronews.service.news.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.macronews.dto.external.ExternalNewsItem;
import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.service.news.query.MarketIssueSeedService;
import com.example.macronews.service.news.query.NaverCuratedFallbackQueries;
import com.example.macronews.service.news.query.ResolvedMarketIssueQueries;
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

    @Mock
    private MarketIssueSeedService marketIssueSeedService;

    private NaverNewsSourceProvider provider;

    @BeforeEach
    void setUp() {
        provider = new NaverNewsSourceProvider(
                externalApiUtils, new ObjectMapper(), marketIssueSeedService);
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
    @DisplayName("NAVER provider should exclude ASCII keyword substring matches while keeping word-boundary matches")
    void fetchTopHeadlines_excludesAsciiKeywordSubstringMatchesButKeepsWordBoundaryMatches() {
        ReflectionTestUtils.setField(provider, "rawQueries", "kospi");
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "items": [
                    {
                      "title": "Bank of Korea rate decision",
                      "description": "Officials weigh the policy rate",
                      "originallink": "https://news.example.com/relevant-rate",
                      "link": "https://search.naver.com/relevant-rate",
                      "pubDate": "Fri, 13 Mar 2026 09:15:00 +0900"
                    },
                    {
                      "title": "Corporate restructuring plan",
                      "description": "Leadership operates separately",
                      "originallink": "https://news.example.com/substring-only",
                      "link": "https://search.naver.com/substring-only",
                      "pubDate": "Fri, 13 Mar 2026 09:10:00 +0900"
                    }
                  ]
                }
                """));

        List<ExternalNewsItem> results = provider.fetchTopHeadlines(5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).url()).isEqualTo("https://news.example.com/relevant-rate");
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
    @DisplayName("NAVER semi-fresh bucket should allow controlled fallback items inside the extended recovery window")
    void fetchTopHeadlines_allowsSemiFreshItemsInsideExtendedFallbackWindow() {
        ReflectionTestUtils.setField(provider, "rawQueries", "fx");
        ReflectionTestUtils.setField(provider, "maxAgeHours", 168L);
        ReflectionTestUtils.setField(provider, "fallbackMaxAgeHours", 336L);
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "items": [
                    {
                      "title": "KOSPI earlier wrap",
                      "description": "Inside fallback window",
                      "originallink": "https://news.example.com/fx-semi",
                      "link": "https://search.naver.com/fx-semi",
                      "pubDate": "Mon, 02 Mar 2026 12:00:00 +0900"
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
    @DisplayName("NAVER FRESH second-pass recovery should surface a fresh item from the leading queries")
    void fetchTopHeadlines_freshSecondPassRecoversFreshItem() {
        ReflectionTestUtils.setField(provider, "rawQueries", "코스피");
        // maxPages=1 removes page-level paging, so any recovery here must come from the pass-level
        // FRESH second pass rather than fetching a later page within the same query.
        ReflectionTestUtils.setField(provider, "maxPages", 1);
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(200, """
                        {
                          "items": [
                            {
                              "title": "Old KOSPI wrap",
                              "description": "Too old for the fresh window",
                              "originallink": "https://news.example.com/stale-first-pass",
                              "link": "https://search.naver.com/stale-first-pass",
                              "pubDate": "Thu, 12 Mar 2026 00:00:00 +0900"
                            }
                          ]
                        }
                        """))
                .willReturn(new ExternalApiResult(200, """
                        {
                          "items": [
                            {
                              "title": "Fresh KOSPI recovery move",
                              "description": "Within the fresh window",
                              "originallink": "https://news.example.com/fresh-recovery",
                              "link": "https://search.naver.com/fresh-recovery",
                              "pubDate": "Fri, 13 Mar 2026 11:00:00 +0900"
                            }
                          ]
                        }
                        """));

        List<ExternalNewsItem> results = provider.fetchTopHeadlines(5);

        assertThat(results)
                .extracting(ExternalNewsItem::url)
                .containsExactly("https://news.example.com/fresh-recovery");
        verify(externalApiUtils, times(2)).callAPI(any());
    }

    @Test
    @DisplayName("NAVER FRESH second-pass recovery should not widen the window to semi-fresh items")
    void fetchTopHeadlines_freshSecondPassDoesNotRecoverSemiFreshItems() {
        ReflectionTestUtils.setField(provider, "rawQueries", "코스피");
        // maxPages=1 isolates the pass-level recovery; maxAgeHours/fallbackMaxAgeHours keep a real gap
        // between the FRESH and SEMI_FRESH windows so the recovery item sits inside SEMI_FRESH only.
        ReflectionTestUtils.setField(provider, "maxPages", 1);
        ReflectionTestUtils.setField(provider, "maxAgeHours", 12L);
        ReflectionTestUtils.setField(provider, "fallbackMaxAgeHours", 24L);
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(200, """
                        {
                          "items": [
                            {
                              "title": "Old KOSPI wrap",
                              "description": "Too old for the fresh window",
                              "originallink": "https://news.example.com/stale-first-pass",
                              "link": "https://search.naver.com/stale-first-pass",
                              "pubDate": "Thu, 12 Mar 2026 00:00:00 +0900"
                            }
                          ]
                        }
                        """))
                .willReturn(new ExternalApiResult(200, """
                        {
                          "items": [
                            {
                              "title": "KOSPI afternoon recap",
                              "description": "코스피 흐름 정리",
                              "originallink": "https://news.example.com/semi-fresh-recovery",
                              "link": "https://search.naver.com/semi-fresh-recovery",
                              "pubDate": "Thu, 12 Mar 2026 19:00:00 +0900"
                            }
                          ]
                        }
                        """));

        // The recovery item is relevant and inside the SEMI_FRESH window, so an empty result proves the
        // FRESH second pass kept the FRESH window instead of falling back to the wider SEMI_FRESH window.
        List<ExternalNewsItem> results = provider.fetchTopHeadlines(5);

        assertThat(results).isEmpty();
        verify(externalApiUtils, times(2)).callAPI(any());
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
    @DisplayName("NAVER provider should use the third page when the first two pages are stale only")
    void fetchTopHeadlines_usesThirdPageWhenFirstTwoPagesAreStaleOnly() {
        ReflectionTestUtils.setField(provider, "rawQueries", "KOSPI");
        ReflectionTestUtils.setField(provider, "display", 5);
        ReflectionTestUtils.setField(provider, "maxPages", 0);
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(200, """
                        {
                          "items": [
                            {
                              "title": "Old KOSPI wrap",
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
                              "title": "Still old KOSPI wrap",
                              "description": "Still too old",
                              "originallink": "https://news.example.com/old-2",
                              "link": "https://search.naver.com/old-2",
                              "pubDate": "Thu, 12 Mar 2026 03:00:00 +0900"
                            }
                          ]
                        }
                        """))
                .willReturn(new ExternalApiResult(200, """
                        {
                          "items": [
                            {
                              "title": "Fresh KOSPI move",
                              "description": "Third page recovery item",
                              "originallink": "https://news.example.com/fresh-3",
                              "link": "https://search.naver.com/fresh-3",
                              "pubDate": "Fri, 13 Mar 2026 10:30:00 +0900"
                            }
                          ]
                        }
                        """));

        List<ExternalNewsItem> results = provider.fetchTopHeadlines(5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).url()).isEqualTo("https://news.example.com/fresh-3");
        List<String> urls = capturedRequestUrls();
        assertThat(urls).anySatisfy(url -> assertThat(url).contains("start=1"));
        assertThat(urls).anySatisfy(url -> assertThat(url).contains("start=6"));
        assertThat(urls).anySatisfy(url -> assertThat(url).contains("start=11"));
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

        assertThat(decodedRequestUrls()).hasSize(18)
                .anySatisfy(url -> assertThat(url).contains("query=\uCF54\uC2A4\uD53C \uC9C0\uC218"))
                .anySatisfy(url -> assertThat(url).contains("query=\uCF54\uC2A4\uD53C \uB9C8\uAC10"))
                .anySatisfy(url -> assertThat(url).contains("query=\uCF54\uC2A4\uB2E5 \uC9C0\uC218"))
                .anySatisfy(url -> assertThat(url).contains("query=\uCF54\uC2A4\uB2E5 \uB9C8\uAC10"))
                .anySatisfy(url -> assertThat(url).contains("query=\uC6D0\uB2EC\uB7EC \uD658\uC728"))
                .anySatisfy(url -> assertThat(url).contains("query=\uD55C\uAD6D\uC740\uD589 \uAE30\uC900\uAE08\uB9AC"))
                .anySatisfy(url -> assertThat(url).contains("query=\uBBF8\uAD6D \uC5F0\uC900 \uAE08\uB9AC"))
                .anySatisfy(url -> assertThat(url).contains("query=FOMC \uD68C\uC758 \uACB0\uACFC"))
                .anySatisfy(url -> assertThat(url).contains("query=\uD30C\uC6D4 \uC758\uC7A5 \uBC1C\uC5B8"))
                .anySatisfy(url -> assertThat(url).contains("query=\uBBF8\uAD6D CPI \uBB3C\uAC00"))
                .anySatisfy(url -> assertThat(url).contains("query=\uBBF8\uAD6D PPI \uBB3C\uAC00"))
                .anySatisfy(url -> assertThat(url).contains("query=\uBBF8\uAD6D \uACE0\uC6A9\uC9C0\uD45C \uBC1C\uD45C"))
                .anySatisfy(url -> assertThat(url).contains("query=\uAD6D\uC81C\uC720\uAC00 WTI"))
                .anySatisfy(url -> assertThat(url).contains("query=\uBE0C\uB80C\uD2B8\uC720 \uAC00\uACA9"))
                .anySatisfy(url -> assertThat(url).contains("query=\uBC18\uB3C4\uCCB4 \uC8FC\uAC00"))
                .anySatisfy(url -> assertThat(url).contains("query=\uB274\uC695\uC99D\uC2DC \uB9C8\uAC10"))
                .anySatisfy(url -> assertThat(url).contains("query=\uB2EC\uB7EC\uC778\uB371\uC2A4 \uD658\uC728"))
                .anySatisfy(url -> assertThat(url).contains("query=\uC778\uD50C\uB808\uC774\uC158 \uAE08\uB9AC"))
                .allSatisfy(url -> assertThat(url)
                        .doesNotContain("query=\uC8FC\uC2DD")
                        .doesNotContain("query=\uCC44\uAD8C \uAE08\uB9AC")
                        .doesNotContain("query=\uBB3C\uAC00 \uBC1C\uD45C")
                        .doesNotContain("query=\uACE0\uC6A9 \uBC1C\uD45C"));
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

        assertThat(decodedRequestUrls()).hasSize(18)
                .anySatisfy(url -> assertThat(url).contains("query=\uCF54\uC2A4\uD53C \uC9C0\uC218"))
                .anySatisfy(url -> assertThat(url).contains("query=\uBBF8\uAD6D \uC5F0\uC900 \uAE08\uB9AC"))
                .anySatisfy(url -> assertThat(url).contains("query=FOMC \uD68C\uC758 \uACB0\uACFC"))
                .anySatisfy(url -> assertThat(url).contains("query=\uD30C\uC6D4 \uC758\uC7A5 \uBC1C\uC5B8"))
                .anySatisfy(url -> assertThat(url).contains("query=\uBBF8\uAD6D PPI \uBB3C\uAC00"))
                .anySatisfy(url -> assertThat(url).contains("query=\uBBF8\uAD6D \uACE0\uC6A9\uC9C0\uD45C \uBC1C\uD45C"))
                .anySatisfy(url -> assertThat(url).contains("query=\uBE0C\uB80C\uD2B8\uC720 \uAC00\uACA9"))
                .anySatisfy(url -> assertThat(url).contains("query=\uB274\uC695\uC99D\uC2DC \uB9C8\uAC10"))
                .anySatisfy(url -> assertThat(url).contains("query=\uB2EC\uB7EC\uC778\uB371\uC2A4 \uD658\uC728"))
                .anySatisfy(url -> assertThat(url).contains("query=\uC778\uD50C\uB808\uC774\uC158 \uAE08\uB9AC"))
                .allSatisfy(url -> assertThat(url)
                        .doesNotContain("query=\uBBF8\uC911\uAC08\uB4F1")
                        .doesNotContain("query=\uC6B0\uD06C\uB77C\uC774\uB098")
                        .doesNotContain("query=\uC8FC\uC2DD"));
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

    @Test
    @DisplayName("NAVER provider should resolve configured queries and merge their results without consulting the GDELT seed or deterministic generator")
    void fetchTopHeadlines_resolvesConfiguredQueriesWithoutGeneratedQuerySource() {
        ReflectionTestUtils.setField(provider, "rawQueries", "코스피,환율");
        // maxPages=1 keeps one request per configured query so the captured URLs map 1:1 to the
        // resolved query list rather than fanning out into later pages of the sequential stub.
        ReflectionTestUtils.setField(provider, "maxPages", 1);
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(200, """
                        {
                          "items": [
                            {
                              "title": "코스피 강세",
                              "description": "기관 매수",
                              "originallink": "https://news.example.com/configured-kospi",
                              "link": "https://search.naver.com/configured-kospi",
                              "pubDate": "Fri, 13 Mar 2026 09:15:00 +0900"
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
                              "originallink": "https://news.example.com/configured-fx",
                              "link": "https://search.naver.com/configured-fx",
                              "pubDate": "Fri, 13 Mar 2026 10:00:00 +0900"
                            }
                          ]
                        }
                        """));

        List<ExternalNewsItem> results = provider.fetchTopHeadlines(5);

        // Characterization: the configured query source is the only one resolved here. Exactly the two
        // configured queries reach the upstream call and their candidates flow into the shared merge,
        // newest first, while the market-issue seed service stays untouched.
        assertThat(decodedRequestUrls())
                .hasSize(2)
                .anySatisfy(url -> assertThat(url).contains("query=코스피"))
                .anySatisfy(url -> assertThat(url).contains("query=환율"));
        assertThat(results).extracting(ExternalNewsItem::url)
                .containsExactly(
                        "https://news.example.com/configured-fx",
                        "https://news.example.com/configured-kospi");
        verifyNoInteractions(marketIssueSeedService);
    }

    @Test
    @DisplayName("NAVER provider should fall back to built-in default queries without consulting the seed service when dynamic is disabled")
    void fetchTopHeadlines_resolvesDefaultQueriesWithoutGeneratedQuerySource() {
        ReflectionTestUtils.setField(provider, "rawQueries", "");
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "items": []
                }
                """));

        provider.fetchTopHeadlines(5);

        // Characterization: with dynamic disabled (default) and blank configured queries, the provider
        // resolves its own built-in default query list and never consults the market-issue seed service.
        assertThat(decodedRequestUrls()).hasSize(18)
                .anySatisfy(url -> assertThat(url).contains("query=코스피 지수"))
                .anySatisfy(url -> assertThat(url).contains("query=미국 연준 금리"));
        verifyNoInteractions(marketIssueSeedService);
    }

    @Test
    @DisplayName("NAVER provider should issue the MarketIssueSeedService resolved queries in order when dynamic is enabled")
    void fetchTopHeadlines_dynamicQueriesEnabledUsesResolvedServiceQueries() {
        ReflectionTestUtils.setField(provider, "rawQueries", "");
        ReflectionTestUtils.setField(provider, "dynamicQueriesEnabled", true);
        ReflectionTestUtils.setField(provider, "maxPages", 1);
        given(marketIssueSeedService.resolve()).willReturn(resolved(
                List.of("연준 금리", "코스피 상승", "원달러"), "gdelt-dynamic", "GDELT_REMOTE", "ok", 3, 0, 0));
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                { "items": [] }
                """));

        provider.fetchTopHeadlines(5);

        List<String> urls = decodedRequestUrls();
        assertThat(urls).hasSize(3);
        assertThat(urls.get(0)).contains("query=연준 금리");
        assertThat(urls.get(1)).contains("query=코스피 상승");
        assertThat(urls.get(2)).contains("query=원달러");
        verify(marketIssueSeedService).resolve();
    }

    @Test
    @DisplayName("NAVER provider should issue OpenAI web-search dynamic queries resolved by the seed service")
    void fetchTopHeadlines_dynamicQueriesEnabledUsesOpenAiResolvedQueries() {
        ReflectionTestUtils.setField(provider, "rawQueries", "");
        ReflectionTestUtils.setField(provider, "dynamicQueriesEnabled", true);
        ReflectionTestUtils.setField(provider, "maxPages", 1);
        given(marketIssueSeedService.resolve()).willReturn(resolved(
                List.of("삼성전자 HBM", "SK하이닉스 실적"), "openai-web-search-dynamic", "OPENAI_WEB_SEARCH", "ok", 2, 0, 1));
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                { "items": [] }
                """));

        provider.fetchTopHeadlines(5);

        List<String> urls = decodedRequestUrls();
        assertThat(urls.get(0)).contains("query=삼성전자 HBM");
        assertThat(urls).anySatisfy(url -> assertThat(url).contains("query=SK하이닉스 실적"));
        verify(marketIssueSeedService).resolve();
    }

    // Polluted queries that repeatedly produced stale/lifestyle noise or rawItems=0 in production; the
    // curated Korea-market fallback pack must contain NONE of these.
    private static final List<String> POLLUTED_FALLBACK_QUERIES = List.of(
            "코스피 지수", "코스닥 지수", "코스피 마감", "코스닥 마감",
            "한국은행 기준금리", "미국 연준 금리", "미국 고용지표 발표", "파월 의장 발언",
            "브렌트유 가격", "FOMC 회의 결과", "미국 CPI 물가", "미국 PPI 물가");

    // Korea-market reaction / major domestic ticker queries that the curated fallback pack must include.
    private static final List<String> KOREA_MARKET_FALLBACK_QUERIES = List.of(
            "삼성전자", "SK하이닉스", "원달러", "뉴욕증시", "나스닥", "반도체");

    @Test
    @DisplayName("NAVER provider should issue the curated Korea-market fallback pack when the seed service resolves it")
    void fetchTopHeadlines_dynamicQueriesEnabledUsesCuratedFallback() {
        ReflectionTestUtils.setField(provider, "rawQueries", "");
        ReflectionTestUtils.setField(provider, "dynamicQueriesEnabled", true);
        given(marketIssueSeedService.resolve()).willReturn(new ResolvedMarketIssueQueries(
                NaverCuratedFallbackQueries.QUERIES, "naver-curated-fallback", "CURATED_FALLBACK", "disabled",
                0, NaverCuratedFallbackQueries.QUERIES.size(), 0, 0L,
                Instant.parse("2026-03-13T03:30:00Z")));
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                { "items": [] }
                """));

        provider.fetchTopHeadlines(5);

        List<String> urls = decodedRequestUrls();
        assertThat(urls.size()).isBetween(18, 24);
        for (String polluted : POLLUTED_FALLBACK_QUERIES) {
            assertThat(urls).noneSatisfy(url -> assertThat(url).contains("query=" + polluted));
        }
        for (String expected : KOREA_MARKET_FALLBACK_QUERIES) {
            assertThat(urls).anySatisfy(url -> assertThat(url).contains("query=" + expected));
        }
        verify(marketIssueSeedService).resolve();
    }

    private static ResolvedMarketIssueQueries resolved(List<String> queries, String source, String seedOrigin,
            String reason, int generatedQueryCount, int curatedQueryCount, int evidenceCount) {
        return new ResolvedMarketIssueQueries(queries, source, seedOrigin, reason, generatedQueryCount,
                curatedQueryCount, evidenceCount, 0L, Instant.parse("2026-03-13T03:30:00Z"));
    }

    @Test
    @DisplayName("parseItems should record a freshness x relevance breakdown without changing drops")
    void parseItems_recordsStaleRelevanceDiagnosticsWithoutChangingDrops() {
        // Four items spanning every freshness x relevance combination. Fixed clock = 2026-03-13T03:30Z,
        // maxAgeHours = 12 -> cutoff = 2026-03-12T15:30Z. Items dated 2026-03-13 are fresh; 2026-03-01 stale.
        String body = """
                {
                  "items": [
                    {
                      "title": "코스피 지수 상승",
                      "description": "기관 매수세 확대",
                      "originallink": "https://news.example.com/fresh-relevant",
                      "link": "https://search.naver.com/fresh-relevant",
                      "pubDate": "Fri, 13 Mar 2026 09:15:00 +0900"
                    },
                    {
                      "title": "연예인 화보 공개",
                      "description": "패션 트렌드 정리",
                      "originallink": "https://news.example.com/fresh-irrelevant",
                      "link": "https://search.naver.com/fresh-irrelevant",
                      "pubDate": "Fri, 13 Mar 2026 11:00:00 +0900"
                    },
                    {
                      "title": "한국은행 기준금리 동결",
                      "description": "통화정책 방향 점검",
                      "originallink": "https://news.example.com/stale-relevant",
                      "link": "https://search.naver.com/stale-relevant",
                      "pubDate": "Sun, 01 Mar 2026 09:00:00 +0900"
                    },
                    {
                      "title": "봄 여행 명소 추천",
                      "description": "라이프스타일 가이드",
                      "originallink": "https://news.example.com/stale-irrelevant",
                      "link": "https://search.naver.com/stale-irrelevant",
                      "pubDate": "Sun, 01 Mar 2026 10:00:00 +0900"
                    }
                  ]
                }
                """;

        NaverNewsSourceProvider.NaverParseResult result =
                provider.parseItems("진단", 1, body, 12L, NewsFreshnessBucket.FRESH);

        // Drop behavior unchanged: only the fresh+relevant item survives.
        assertThat(result.rawItemCount()).isEqualTo(4);
        assertThat(result.items()).hasSize(1);
        assertThat(result.staleItemCount()).isEqualTo(2);
        assertThat(result.filteredByRelevanceCount()).isEqualTo(1);

        // New simultaneous diagnostics surface the previously hidden stale-AND-irrelevant item.
        assertThat(result.staleAndIrrelevantCount()).isEqualTo(1);
        assertThat(result.staleButRelevantCount()).isEqualTo(1);
        assertThat(result.freshButIrrelevantCount()).isEqualTo(1);
        assertThat(result.freshAndRelevantCount()).isEqualTo(1);

        // Invariants tying the new counters back to the existing ones.
        assertThat(result.staleAndIrrelevantCount() + result.staleButRelevantCount())
                .isEqualTo(result.staleItemCount());
        assertThat(result.freshButIrrelevantCount()).isEqualTo(result.filteredByRelevanceCount());
        assertThat(result.freshAndRelevantCount()).isEqualTo(result.items().size());
    }

    @Test
    @DisplayName("Adding stale/relevance diagnostics does not change the returned item set")
    void fetchTopHeadlines_diagnosticsDoNotChangeReturnedItems() {
        ReflectionTestUtils.setField(provider, "rawQueries", "코스피");
        ReflectionTestUtils.setField(provider, "maxPages", 1);
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "items": [
                    {
                      "title": "코스피 지수 상승",
                      "description": "기관 매수세 확대",
                      "originallink": "https://news.example.com/fresh-relevant",
                      "link": "https://search.naver.com/fresh-relevant",
                      "pubDate": "Fri, 13 Mar 2026 09:15:00 +0900"
                    },
                    {
                      "title": "연예인 화보 공개",
                      "description": "패션 트렌드 정리",
                      "originallink": "https://news.example.com/fresh-irrelevant",
                      "link": "https://search.naver.com/fresh-irrelevant",
                      "pubDate": "Fri, 13 Mar 2026 11:00:00 +0900"
                    },
                    {
                      "title": "봄 여행 명소 추천",
                      "description": "라이프스타일 가이드",
                      "originallink": "https://news.example.com/stale-irrelevant",
                      "link": "https://search.naver.com/stale-irrelevant",
                      "pubDate": "Sun, 01 Mar 2026 10:00:00 +0900"
                    }
                  ]
                }
                """));

        List<ExternalNewsItem> results = provider.fetchTopHeadlines(5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).url()).isEqualTo("https://news.example.com/fresh-relevant");
    }

    @Test
    @DisplayName("extractDomain safely returns the lowercased host without www and tolerates bad input")
    void extractDomain_returnsSafeHostForDiagnostics() {
        assertThat(NaverNewsSourceProvider.extractDomain("https://www.mk.co.kr/news/123")).isEqualTo("mk.co.kr");
        assertThat(NaverNewsSourceProvider.extractDomain("https://news.naver.com/main/x")).isEqualTo("news.naver.com");
        assertThat(NaverNewsSourceProvider.extractDomain("HTTPS://News.Example.COM/a")).isEqualTo("news.example.com");
        assertThat(NaverNewsSourceProvider.extractDomain("")).isEmpty();
        assertThat(NaverNewsSourceProvider.extractDomain(null)).isEmpty();
        assertThat(NaverNewsSourceProvider.extractDomain("not a valid url")).isEmpty();
    }

    @Test
    @DisplayName("Source-domain diagnostics do not change which stale/irrelevant items are dropped")
    void parseItems_sourceDomainDiagnosticsDoNotChangeDrops() {
        // One fresh+relevant (kept), one fresh+irrelevant (dropped, sampled with domain), one stale
        // (dropped, sampled with domain). The returned item set and counters must be unchanged.
        String body = """
                {
                  "items": [
                    {
                      "title": "코스피 지수 상승",
                      "description": "기관 매수세 확대",
                      "originallink": "https://www.mk.co.kr/fresh-relevant",
                      "link": "https://search.naver.com/fresh-relevant",
                      "pubDate": "Fri, 13 Mar 2026 09:15:00 +0900"
                    },
                    {
                      "title": "연예인 화보 공개",
                      "description": "패션 트렌드 정리",
                      "originallink": "https://www.fashion.example.com/fresh-irrelevant",
                      "link": "https://search.naver.com/fresh-irrelevant",
                      "pubDate": "Fri, 13 Mar 2026 11:00:00 +0900"
                    },
                    {
                      "title": "봄 여행 명소 추천",
                      "description": "라이프스타일 가이드",
                      "originallink": "https://www.travel.example.com/stale-irrelevant",
                      "link": "https://search.naver.com/stale-irrelevant",
                      "pubDate": "Sun, 01 Mar 2026 10:00:00 +0900"
                    }
                  ]
                }
                """;

        NaverNewsSourceProvider.NaverParseResult result =
                provider.parseItems("진단", 1, body, 12L, NewsFreshnessBucket.FRESH);

        assertThat(result.rawItemCount()).isEqualTo(3);
        assertThat(result.items()).hasSize(1);
        assertThat(result.filteredByRelevanceCount()).isEqualTo(1);
        assertThat(result.staleItemCount()).isEqualTo(1);
        assertThat(result.freshButIrrelevantCount()).isEqualTo(1);
        assertThat(result.staleAndIrrelevantCount()).isEqualTo(1);
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
