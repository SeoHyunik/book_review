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
