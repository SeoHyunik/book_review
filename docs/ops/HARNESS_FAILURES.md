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