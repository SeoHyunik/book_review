# Dynamic, Externally-Informed Keyword Refresh — Design Draft

> Status: **design draft / proposal only.** Nothing in this document is implemented yet.
> It introduces no code, no dependencies, no network calls, and no secret values.
> It describes a safe, incremental path; each rollout phase is a separate future slice.

## 1. Problem and Goal

News classification and scoring in this project currently lean on **fixed, hand-curated
keyword lists**. Fast-moving macro/financial issues (a new central-bank acronym, a newly
relevant ticker, a fresh geopolitical term) are not reflected until someone edits the lists
by hand. The standing request is to:

- reduce reliance on fixed/guessed keyword lists;
- use external search + AI analysis to surface recent high-impact macro issues;
- extract usable keywords and connect them to the existing classification/scoring;
- provide a structure for **periodic refresh**;
- **not break** existing collection/classification/scoring or tests;
- **never expose** API keys or sensitive config in code or logs.

The accepted completion form for this step is a *safe design draft*. A minimal code
skeleton (a `KeywordSource` interface with a static default) is deferred to a later slice
(KW-DYN-02) and is intentionally **not** done here.

## 2. Current State (as implemented today)

### 2.1 Shared matching seam: `KeywordMatcher`

`com.example.macronews.util.KeywordMatcher` is a stateless final utility
(`matches(String text, String keyword)`):

- Korean / non-ASCII keywords use case-insensitive **substring** matching (Korean does not
  separate words with whitespace).
- ASCII / English keywords match on **word boundaries** (`\bkeyword\b`, case-insensitive),
  so `"AI"` does not match inside `"RAID"`.

This utility is the single, unified place where "does this text contain this keyword?" is
decided. Both consumers below now route through it.

### 2.2 Fixed keyword lists — two consumers

1. **`com.example.macronews.service.news.NewsScoringPolicy`** (package-private `@Component`).
   Priority scoring uses fixed, in-class `static final` data:
   - `PRIORITY_WEIGHT_RULES` and `NOISE_DEMOTION_RULES` — lists of `KeywordWeightRule`
     records, each bundling a set of keywords with title/summary/source weights
     (e.g. `"fed"`, `"fomc"`, `"cpi"`, `"semiconductor"`, `"kospi"`, plus noise terms like
     `"giveaway"`, `"celebrity"`).
   - `TRUSTED_SOURCE_MARKERS` / `TRUSTED_DOMAIN_MARKERS` — source-reliability lists.
   - All membership tests funnel through private helpers `containsKeyword(...)` /
     `containsAnyKeyword(...)`, which delegate to `KeywordMatcher.matches(...)`.

2. **`com.example.macronews.controller.TopicKeywordPolicy`** (package-private final
   `@Component`). Topic tagging uses three fixed `static final List<String>`:
   `DOLLAR_KEYWORDS`, `RATES_KEYWORDS`, `OIL_KEYWORDS`. Its private `containsKeyword(...)`
   also delegates to `KeywordMatcher.matches(...)`.

### 2.3 Why this matters for the design

Because both consumers already share **one matching criterion** (`KeywordMatcher`), the only
remaining source of rigidity is **where the keyword *lists* come from**: today they are
compile-time constants. A dynamic source therefore needs to influence *the lists*, not the
matching logic. That is the seam this design targets.

> Note (honest current-state caveat): the keyword data lives inside each policy class as
> `private static final` constants, not behind an injected provider. Introducing a
> `KeywordSource` seam is itself a small structural change and is deferred to KW-DYN-02; this
> draft only specifies it.

## 3. Proposed Seam: `KeywordSource`

Introduce a neutral abstraction in the `util` package (sibling to `KeywordMatcher`):

```
com.example.macronews.util.KeywordSource   (interface — proposed, not yet created)
```

Conceptual shape (illustrative, not final API):

- `List<String> keywordsFor(KeywordGroup group)` — returns the active keyword list for a
  named group (e.g. `DOLLAR`, `RATES`, `OIL`, `PRIORITY_FED`, `NOISE`).
- A `KeywordGroup` enum (or string key) names each list the consumers need.

### 3.1 Static default = current behavior (fallback)

The default implementation, `StaticKeywordSource`, returns exactly today's hard-coded lists.
If no external/AI source is configured, behavior is **identical** to the current build. This
is the safety anchor: the dynamic path is purely additive, and the fallback is the existing
tested behavior.

### 3.2 How consumers would adopt it (later, incrementally)

- `NewsScoringPolicy` and `TopicKeywordPolicy` would take a `KeywordSource` via
  **constructor injection** (the profile's Java rule), instead of reading `static final`
  constants directly.
- The matching call itself does **not** change — it still goes through
  `KeywordMatcher.matches(...)`. Only the *list provider* changes.
- Spring would inject `StaticKeywordSource` by default; a dynamic implementation becomes an
  opt-in `@Primary`/profile-gated bean in a later phase.

This keeps layer boundaries intact: `util` stays free of Spring/web/persistence concerns; the
policies remain the only orchestrators of scoring/tagging.

## 4. How AI / External-Search-Derived Keywords Merge In

The dynamic path produces **candidate keywords** that augment (never silently replace) the
curated baseline.

1. **Discovery (out of request path):** a background job asks an external search + AI step
   "what recent high-impact macro/financial terms are trending?" and gets back candidate
   terms with a group hint and a confidence/score.
2. **Normalization:** trim, lowercase-compare semantics already handled by `KeywordMatcher`;
   candidates are de-duplicated against the baseline.
3. **Merge policy (additive, bounded):**
   - `effectiveList(group) = curatedBaseline(group) ∪ acceptedDynamic(group)`.
   - Dynamic terms are **capped** (max N per group) and **filtered** (min confidence, allow/deny
     list) so the AI cannot inject noise or unbounded growth.
   - The curated baseline is always present, so removing the dynamic source can never drop a
     known-good keyword.
4. **Consumption:** consumers read the merged list through `KeywordSource`; matching is
   unchanged, so scoring math and topic tags behave the same for any term — curated or
   dynamic.

### 4.1 Not breaking existing tests

- `KeywordMatcherTest` is untouched — matching semantics do not change.
- `NewsScoringPolicy` / `TopicKeywordPolicy` regression tests pass because the **default**
  `KeywordSource` returns the existing lists; with no dynamic source configured, output is
  byte-for-byte the prior behavior.
- New behavior (dynamic merge) is covered by **new** tests against a fake/in-memory
  `KeywordSource`, never by weakening existing tests.
- Determinism: tests must inject a fixed `KeywordSource` (no live calls), so the suite never
  depends on the network or on AI output.

## 5. Periodic-Refresh Options and Trade-offs

The dynamic keyword set must be refreshed over time. Two viable structures:

### Option A — Cached snapshot (recommended)

A background process refreshes an in-memory (and optionally on-disk) **snapshot** of dynamic
keywords on an interval; consumers read the latest snapshot synchronously.

- **Pros:** no external latency or failure on the request path; bounded, predictable behavior;
  easy to make the request path fully offline-safe; trivial to disable (serve last snapshot or
  empty dynamic set → falls back to curated baseline).
- **Cons:** keywords are as fresh as the last successful refresh; needs a refresh trigger
  (scheduler or manual) and a staleness/empty-on-startup policy.

### Option B — Scheduled job writing a durable store

A scheduled job (e.g. Spring `@Scheduled`) periodically calls discovery and persists results;
consumers load from that store.

- **Pros:** survives restarts; refresh cadence is explicit and observable; decouples discovery
  from serving.
- **Cons:** adds a store/schema and a scheduler; more moving parts to operate and secure;
  still needs the same merge/cap/fallback guarantees as Option A.

### Anti-option — Live per-request lookup (not recommended)

Calling external search/AI inside each request would add latency, external-API coupling, cost,
and a new failure mode on the hot path. Rejected for the serving path.

**Recommendation:** Option A (cached snapshot), optionally backed by Option B's scheduled job
as the *refresh trigger*. The request path always reads a local snapshot and degrades to the
curated baseline if the snapshot is empty or stale.

## 6. Secret Safety (hard constraint)

Any external-search/AI integration needs credentials. These rules are non-negotiable and apply
to every later phase:

- **Source of secrets:** environment variables (e.g. on Render) **or** the project's
  SOPS-encrypted config (`application-secrets.enc.yaml`). Never inline literals, never a new
  plaintext config file.
- **Never in code:** no API keys, tokens, or endpoints-with-embedded-credentials in source,
  tests, or this document. Configuration is referenced by *property name only*
  (e.g. a Spring `@ConfigurationProperties`-bound value resolved from env/SOPS).
- **Never logged:** do not log key values, full auth headers, or full external request URLs
  that may carry credentials. Log only non-sensitive metadata (group, candidate count,
  refresh timestamp, success/failure).
- **Never committed:** the encrypted secrets file stays out of any commit produced by this
  work; the dynamic feature must not require committing any secret material.
- **Fail safe:** if credentials are absent, the dynamic source is simply **disabled** and the
  system runs on the curated baseline — missing secrets degrade gracefully, they do not crash
  classification/scoring.

This document handles secrets only as a *policy*; it introduces none.

## 7. Minimal-First Incremental Rollout

Each phase is a separate, independently shippable slice. Stop at any phase and the system is
still correct.

1. **Phase 0 — Seam, no behavior change (KW-DYN-02, next slice).**
   Add `KeywordSource` + `StaticKeywordSource` (returns today's lists). Wire consumers via
   constructor injection. Add a focused test. Run `./gradlew test`. Behavior identical;
   default fallback established.
2. **Phase 1 — Cached static dynamic source (still no network).**
   Add a `KeywordSource` implementation that reads an in-memory/config-provided extra list and
   merges it (capped) on top of the baseline. Exercised with a fake source in tests. Proves the
   merge path without any external dependency.
3. **Phase 2 — Scheduled refresh of the snapshot.**
   Add a background refresh (Option A/B) that updates the dynamic snapshot on an interval, with
   caps, fallback-to-baseline, and metadata-only logging. Discovery stub still offline/fake in
   tests.
4. **Phase 3 — Real external search + AI discovery.**
   Implement the discovery step against the real provider, with credentials from env/SOPS,
   secret-safe logging, and graceful disable when credentials are missing. Add integration
   coverage that does not run in the default unit suite.

Earlier phases must not be skipped to reach later ones; each adds one reviewable concern.

## 8. Scope Guards for This Draft

- No edits to `NewsScoringPolicy`, `TopicKeywordPolicy`, `KeywordMatcher`, collectors,
  resources, or any secret/config file.
- No new dependencies, no network calls, no real secret values.
- This is a proposal: it must not be read as describing existing behavior. The only things
  that exist today are `KeywordMatcher` and the two fixed-list consumers in §2.

## 9. Open Questions (carried for human decision)

1. Refresh model: confirm cached snapshot (Option A) over live per-request lookup
   (recommended: yes).
2. Secret-delivery path the dynamic source should assume — Render env vars vs. the in-flight
   SOPS config. Design stays compatible with both; no real keys ever embedded.
3. Caps/thresholds: acceptable max dynamic keywords per group and minimum AI confidence before
   a candidate is accepted.
4. Whether dynamic keywords may also feed *weights* (scoring) or only *membership* (topic tags)
   in the first real phase — recommended: membership only, weights later.
