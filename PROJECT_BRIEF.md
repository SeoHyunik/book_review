# PROJECT_BRIEF

## 1. Product Definition
Macro News Interpretation (MNI) is a Korean-first macro/market interpretation platform built on a Spring Boot + MongoDB monolith.

The system ingests external macro/market news, interprets each article with AI, and synthesizes recent analyzed news into higher-level market read products such as:
- article-level macro interpretation
- near-term market forecast snapshot
- homepage-oriented featured market summary
- supporting market briefing views

This is not a pure news crawler and not yet a price-driven trading system.

Core identity:
**news-based macro interpretation + market narrative synthesis for Korean users**

---

## 2. Core Value
The service helps users quickly understand:
- what macro narrative currently dominates the market
- which drivers matter most (rates, USD, oil, inflation, geopolitics, risk sentiment)
- how recent news clusters affect market interpretation
- how multiple articles can be compressed into a structured market briefing

The goal is to reduce information overload and convert scattered headlines into structured meaning.

---

## 3. Target Users
Primary:
- Korean retail investors
- users searching for market explanations ("오늘 코스피 전망", "유가 영향")

Secondary:
- returning users consuming daily market summaries
- SEO-based traffic users landing on topic pages

---

## 4. Product Philosophy
This is a **production-first system**, not a demo.

Always prioritize:
- correctness
- stability
- observability
- rollback safety
- minimal safe changes
- explicit and traceable logic

The system should behave like a disciplined market interpreter, not a speculative predictor.

---

## 5. What the Product Is Good At
- article-level interpretation
- narrative summarization
- macro signal extraction
- Korean-language market explanation
- homepage-ready market briefing

---

## 6. What the Product Is NOT Yet
This system is NOT:
- a trading signal engine
- a portfolio advisor
- a high-confidence price prediction system
- a real-time market reaction model

When evidence is weak:
→ prefer ambiguity over false certainty

---

## 7. Core Flow
1. ingest external news
2. persist NewsEvent
3. generate AI interpretation (AnalysisResult)
4. aggregate recent analyzed news
5. generate forecast snapshot
6. generate featured market summary
7. render UI (Thymeleaf)

---

## 8. Strategic Direction
The next evolution is:

**from narrative-only → price-aware interpretation**

Minimum required indicators:
- USD/KRW
- US 10Y Treasury Yield
- DXY
- WTI / Brent
- KOSPI

Goal:
→ connect macro narrative to market reality

---

## 9. SEO / Content Direction
The system should evolve into a **searchable market interpretation platform**.

Target pages:
- market summary pages
- topic pages
- archive pages
- forecast review pages

Goal:
search → interpretation → navigation → retention

---

## 10. UI / UX Direction
- keep Spring Boot + Thymeleaf monolith
- avoid unnecessary frontend rewrite
- improve navigation and exploration
- support partial updates gradually
- maintain fast, structured UX

---

## 11. Architecture Direction
- maintain monolith structure
- enforce clear layer boundaries:
  controller → service → provider → repository
- keep provider-replaceable design
- avoid hidden side effects
- prefer explicit data flow

---

## 12. Non-Goals
Do NOT turn this into:
- a generic news portal
- a noisy content farm
- a trading bot
- a microservice-overengineered system
- a React rewrite for its own sake

---

## 13. Critical Constraints
- preserve Korean comments and user text
- prefer minimal safe changes
- do not mix refactoring with feature work
- public pages must fail gracefully
- avoid overconfidence in market interpretation

---

## 14. Current Development Focus
- stabilize ingestion pipeline
- ensure public page reliability (/news must not fail hard)
- improve interpretation consistency
- fix freshness logic issues
- introduce minimal market data context
- improve SEO structure
- incremental UX improvements

---

## 15. Product Tone
The system should sound:
- calm
- structured
- evidence-based
- macro-literate
- Korea-aware

---

## 16. Current Product Rule
This system should describe and interpret market conditions more confidently than it predicts exact price direction.