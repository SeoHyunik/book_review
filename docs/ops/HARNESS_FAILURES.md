# HARNESS_FAILURES

---

### [2026-04-03] Scope Expansion in Provider Fix

**Type**
execution failure

**What Happened**
Provider query tuning step modified unrelated fallback logic

**Root Cause**
Step boundary was not strictly enforced

**Impact**
Increased regression risk

**Rule / Fix**
Every step must define and enforce forbidden scope

**Prevention**
Reviewer must explicitly check for unrelated diff

---

### [2026-04-03] Ops Document Encoding Noise

**Type**
documentation failure

**What Happened**
Generated daily ops docs showed garbled Korean text and formatting noise across multiple files

**Root Cause**
No harness check enforced clean UTF-8 output and readable ops-document rendering

**Impact**
Daily strategy and handoff context became harder to trust and reuse

**Rule / Fix**
Daily ops documents must be validated for readable UTF-8 text before handoff

**Prevention**
Reviewer must reject ops output with mojibake, broken bullets, or corrupted headings

---

### [2026-04-06] Repeated Ops-Document Corruption and Missing Handoff

**Type**
harness failure

**What Happened**
Today's strategy document still contains garbled Korean text, and the required `DAILY_HANDOFF.md` is missing for the current date

**Root Cause**
Daily ops generation does not appear to enforce readable UTF-8 output and required-file presence checks before handoff

**Impact**
Current working context is harder to trust, and carry-over state cannot be handed off cleanly

**Rule / Fix**
Reject daily ops output unless all required files exist and are readable, with no mojibake or broken formatting

**Prevention**
Add a pre-handoff validation that checks daily ops file completeness and flags encoding corruption immediately

---

### [2026-04-06] QA Structure Not Enforced Before Planning

**Type**
harness failure

**What Happened**
`QA_INBOX.md` contains actionable user issues, but `QA_STRUCTURED.md` is empty, so the daily planning flow has to rely on raw notes instead of a normalized issue set.

**Root Cause**
No enforced step converts raw QA into a structured summary before strategy generation.

**Impact**
Priority selection becomes noisier, and repeated user issues are easier to miss or mis-rank.

**Rule / Fix**
Daily QA must be normalized into a structured review artifact before strategy planning begins.

**Prevention**
Require a non-empty `QA_STRUCTURED.md` whenever `QA_INBOX.md` has actionable items.
