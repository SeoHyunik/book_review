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

---

### [2026-04-06] Stale Ops Context Is Not Being Revalidated

**Type**
harness failure

**What Happened**
Daily ops notes can carry forward stale or contradictory claims about current-file state, so handoff context may describe missing or unreadable artifacts even after the files exist.

**Root Cause**
There is no required revalidation pass that compares ops notes against the live date-scoped files before the handoff is trusted.

**Impact**
The team can inherit an unreliable working context, which slows planning and makes repeated doc issues harder to spot.

**Rule / Fix**
Before handoff, recheck the current date-scoped ops files and reject notes that still claim missing, empty, or corrupted artifacts when the live files do not match.

**Prevention**
Add a pre-handoff consistency check that compares summary notes with the actual `docs/ops/YYYY-MM-DD/` files and flags stale claims immediately.

---

### [2026-04-07] Daily Ops Consistency Checks Still Too Weak

**Type**
harness failure

**What Happened**
Today’s date-scoped ops chain still shows inconsistent handoff state: the raw QA notes, structured QA, strategy, and handoff artifacts do not have a hard enforced consistency gate, and `DAILY_HANDOFF.md` is still empty at the day boundary.

**Root Cause**
There is no mandatory final validation that checks the current date-scoped ops files for existence, readability, and mutual alignment before the session is considered complete.

**Impact**
The next session can inherit incomplete or contradictory working context, which repeats the same planning and handoff friction.

**Rule / Fix**
Before closing a day, verify that `QA_INBOX.md`, `QA_STRUCTURED.md`, `TODAY_STRATEGY.md`, and `DAILY_HANDOFF.md` all exist, are readable, and agree on the current work state.

**Prevention**
Add a pre-handoff gate that fails fast when the date-scoped ops set is missing, empty, corrupted, or internally inconsistent.

---

### [2026-04-08] Planning Chain Still Ends Without a Reusable Handoff

**Type**
harness failure

**What Happened**
Today's QA and strategy files stay aligned on the selected carry-over work, but `DAILY_HANDOFF.md` remains empty, so the session still ends without a reusable next-step artifact.

**Root Cause**
The harness validates planning consistency more reliably than it validates handoff completion.

**Impact**
The next session must reconstruct context from QA and strategy again, which repeats the same continuity friction even when the work selection is correct.

**Rule / Fix**
Do not treat a day as closed until the date-scoped handoff file contains the selected carry-over state and next-step record.

**Prevention**
Add a required final check that fails when QA, strategy, and handoff are aligned in intent but the handoff file is empty or missing.

---

### [2026-04-09] Day Closed With Empty Handoff and Incomplete Workday-State

**Type**
harness failure

**What Happened**
The 2026-04-09 QA and strategy chain identified carry-over work correctly, but `DAILY_HANDOFF.md` was still empty at the day boundary and `.workday-state.json` showed `handoff_done=false` and `qa_structurer_done=false`.

**Root Cause**
The harness records planning progress more reliably than it records closure progress, so an unfinished handoff can survive into the end-of-day state.

**Impact**
The next session can still inherit a non-reusable context, even when the selected work was already clear.

**Rule / Fix**
Do not allow a day to close when the date-scoped handoff file is empty or the workday-state file says the session is incomplete.

**Prevention**
Add a closure gate that checks `QA_INBOX.md`, `QA_STRUCTURED.md`, `TODAY_STRATEGY.md`, `DAILY_HANDOFF.md`, and `.workday-state.json` together before marking the day complete.
