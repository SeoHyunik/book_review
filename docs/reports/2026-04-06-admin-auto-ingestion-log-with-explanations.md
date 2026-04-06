좋아. Codex가 “로그를 못 봐서 확신 못하는 상태”라서 흔들린 거니까,
**네가 가진 로그를 ‘반박 불가능한 증거 형태’로 구조화해서 던지는 게 핵심**이다.

아래는 **누락 없이 + 흐름 중심으로 + Codex가 그대로 읽고 판단할 수 있게 만든 md**야.
(그대로 복붙해서 전달하면 된다)

---

# 📊 Runtime Log Evidence – Auto Ingestion Failure Case

> 목적:
> 기존 조사 결과(provider 미구성 가설)를 검증하기 위해
> 실제 실행 로그를 기반으로 **팩트 중심 흐름 재구성**

---

# 1. Provider Selector 실행 여부 (Scheduler 진입 증거)

```log
[NEWS-SOURCE] provider plan preferredPriority=DOMESTIC preferredProviders=[naver] fallbackPriority=FOREIGN fallbackProviders=[newsapi-global, gnews-global] requested=10
```

➡️ 의미

* scheduler → selector까지 정상 진입
* provider plan 생성됨

👉 **결론: scheduler는 실제 실행됨**



---

# 2. Naver Provider 실제 호출 (Configured 증거)

```log
[NEWS-SOURCE] loading provider=naver priority=DOMESTIC preferred=true limit=10
```

```log
[HTTP] Calling external API: method=GET, url=https://openapi.naver.com/v1/search/news.json?... 
headers=[X-Naver-Client-Id:"****", X-Naver-Client-Secret:"****"]
```

➡️ 의미

* provider 로딩됨
* API 호출까지 수행됨
* 인증 헤더 존재

👉 **결론: Naver는 configured 상태 (isConfigured=true)**



---

# 3. Naver 결과 분석 (핵심 문제 1)

## 3.1 결과 패턴

```log
rawItems=5
parsedItems=0
staleItems=5
```

```log
merged usableItems=0 requestedLimit=10
```

➡️ 의미

* 데이터는 받아옴 (rawItems 존재)
* 하지만 전부 stale 처리됨
* 최종 usable = 0

👉 **결론: "configured but unusable (stale filtering)"**

---

## 3.2 실제 stale 샘플

```log
publishedAt=2025-07-27 ...
publishedAt=2024-12-04 ...
publishedAt=2024-07-27 ...
```

➡️ 의미

* 최신 뉴스가 아니라 오래된 콘텐츠 포함됨
* freshness cutoff 기준 초과

👉 **결론: freshness gate가 실제로 작동 중**



---

# 4. NewsAPI Provider 상태 (핵심 문제 2)

```log
[HTTP] Calling external API: https://newsapi.org/...
```

```log
[NEWSAPI] 429 received
```

```log
skipping call after prior 429
```

➡️ 의미

* API 호출은 시도됨 → configured 상태
* rate limit 초과
* 이후 fallback 요청도 차단됨

👉 **결론: configured but rate-limited**



---

# 5. GNews Provider 상태 (핵심 문제 3)

```log
[HTTP] Calling external API: https://gnews.io/api/v4/search...
```

```log
[GNEWS] status=400
```

➡️ 의미

* 호출은 수행됨 → configured 상태
* 요청 자체가 잘못됨 (400)

👉 **결론: configured but bad request**



---

# 6. Provider 결과 종합

```log
provider outcome status=EMPTY provider=naver
provider outcome status=EMPTY provider=newsapi-global
provider outcome status=EMPTY provider=gnews-global
```

➡️ 의미

* 모든 provider가 결과 0건 반환

👉 **결론: selector는 정상 동작했으나 결과가 모두 EMPTY**



---

# 7. 전체 흐름 요약 (팩트 기반)

## 실행 흐름

1. scheduler 실행됨
2. provider selector 실행됨
3. naver 호출 → stale로 전량 제거
4. newsapi 호출 → 429 rate limit
5. gnews 호출 → 400 bad request
6. 모든 provider 결과 EMPTY
7. 최종 ingestion 결과 = 0

---

# 8. 핵심 결론 (로그 기반 확정 사실)

## ❌ 반박되는 가설

* provider not configured ❌

    * 실제로 API 호출까지 수행됨

---

## ✅ 로그가 증명하는 사실

### 1. scheduler 정상 실행됨

### 2. provider 모두 configured 상태

### 3. 각 provider가 서로 다른 이유로 실패

| Provider | 상태                      |
| -------- | ----------------------- |
| Naver    | stale filtering으로 전량 제거 |
| NewsAPI  | rate limit (429)        |
| GNews    | bad request (400)       |

---

## 🎯 최종 Root Cause

```
compound EMPTY-result cascade
```

👉 단일 원인이 아니라:

* Naver → stale
* NewsAPI → rate limit
* GNews → bad request

👉 3개가 동시에 터지면서 최종 0건

---

# 9. Codex에게 던질 핵심 메시지

> This is NOT a "provider not configured" case.
> This is a "configured but ineffective + compound EMPTY cascade" case.

---

# 🔥 한 줄 요약

👉 **이건 설정 문제 아니고, 실행은 다 했는데 결과가 다 죽은 케이스다.**

---
