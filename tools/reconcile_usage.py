#!/usr/bin/env python3
"""
Generate a reconciliation dataset between internal OpenAI usage records
and an external OpenAI usage export.

Usage:
  python tools/reconcile_usage.py \
    --internal internal.csv \
    --external external.csv \
    --output-dir /tmp/reconcile
    [--internal-timestamp ts --external-timestamp ts ...]
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import os
from collections import defaultdict
from datetime import datetime, timedelta, timezone
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple

PRICING_PATH = Path("src/main/resources/pricing/openai-pricing.json")
ONE_MILLION = Decimal("1000000")


def parse_decimal(value: str) -> Decimal:
    if value is None or value == "":
        return Decimal("0")
    return Decimal(value)


def load_csv(path: Path) -> List[Dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as fh:
        reader = csv.DictReader(fh)
        return [dict(row) for row in reader]


def parse_timestamp(value: str) -> datetime:
    if value is None or value.strip() == "":
        raise ValueError("timestamp is required")
    if value.endswith("Z"):
        value = value[:-1] + "+00:00"
    dt = datetime.fromisoformat(value)
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)


def minute_bucket(dt: datetime) -> str:
    return dt.strftime("%Y-%m-%dT%H:%M")


def load_pricing() -> Dict:
    with PRICING_PATH.open() as fh:
        data = json.load(fh)
    return data


def normalize_model(model: Optional[str]) -> str:
    if not model:
        return "unknown"
    if "-" in model:
        parts = model.split("-")
        if len(parts) > 2 and _looks_like_date(parts[-1]):
            return "-".join(parts[:-1])
    return model


def _looks_like_date(segment: str) -> bool:
    if len(segment) not in (8, 10):
        return False
    return segment.replace("-", "").isdigit()


def resolve_pricing(
    pricing: Dict,
    feature: Optional[str],
    normalized_model: str,
) -> Tuple[Decimal, Decimal, bool]:
    feature_profiles = pricing.get("feature_profiles", {})
    models = pricing.get("models", {})

    if feature:
        profile = feature_profiles.get(feature)
        if profile and profile.get("model") == normalized_model:
            return Decimal(str(profile["input"])), Decimal(str(profile["output"])), True

    model_entry = models.get(normalized_model)
    if model_entry:
        return Decimal(str(model_entry["input"])), Decimal(str(model_entry["output"])), False

    fallback = feature_profiles.get("default_fallback")
    if fallback:
        return Decimal(str(fallback["input"])), Decimal(str(fallback["output"])), True

    # Fallback to model entry if names mismatch
    if models:
        first = next(iter(models.values()))
        return Decimal(str(first["input"])), Decimal(str(first["output"])), False

    raise RuntimeError("pricing snapshot contains no models")


def compute_cost(input_tokens: Decimal, output_tokens: Decimal, pricing_pair: Tuple[Decimal, Decimal]) -> Decimal:
    input_price, output_price = pricing_pair
    result = (input_tokens / ONE_MILLION) * input_price + (output_tokens / ONE_MILLION) * output_price
    return result.quantize(Decimal("0.00001"), rounding=ROUND_HALF_UP)


def build_records(
    rows: Iterable[Dict[str, str]],
    mapping: Dict[str, str],
    is_internal: bool = True,
) -> List[Dict]:
    result = []
    for row in rows:
        ts = parse_timestamp(row.get(mapping["timestamp"], ""))
        model = normalize_model(row.get(mapping["model"], "unknown"))
        prompt = parse_decimal(row.get(mapping["prompt_tokens"], "0"))
        completion = parse_decimal(row.get(mapping["completion_tokens"], "0"))
        total = parse_decimal(row.get(mapping["total_tokens"], str(prompt + completion)))
        feature = row.get(mapping.get("feature", ""), "").strip() or None
        record = {
            "timestamp": ts,
            "bucket": minute_bucket(ts),
            "model": model,
            "prompt": prompt,
            "completion": completion,
            "total": total,
            "feature": feature,
            "request_id": row.get(mapping.get("request_id", ""), "").strip() or None,
        }
        if not is_internal:
            record["cached_tokens"] = parse_decimal(row.get(mapping.get("cached_tokens", ""), "0"))
            record["billed_cost"] = parse_decimal(row.get(mapping.get("billed_cost", ""), "0"))
        result.append(record)
    return result


def match_records(
    internal: List[Dict],
    external: List[Dict],
    pricing: Dict,
) -> Tuple[List[Dict], List[Dict], List[Dict]]:
    internal_index = {rec["request_id"]: rec for rec in internal if rec["request_id"]}
    external_index = {rec["request_id"]: rec for rec in external if rec["request_id"]}
    matches = []
    used_internal = set()
    used_external = set()

    for request_id in internal_index:
        if request_id in external_index:
            int_rec = internal_index[request_id]
            ext_rec = external_index[request_id]
            matches.append(_build_match(int_rec, ext_rec, pricing))
            used_internal.add(id(int_rec))
            used_external.add(id(ext_rec))

    unmatched_internal = [rec for rec in internal if id(rec) not in used_internal]
    unmatched_external = [rec for rec in external if id(rec) not in used_external]

    bucket_index: Dict[str, List[Dict]] = defaultdict(list)
    external_buckets: Dict[str, List[Dict]] = defaultdict(list)
    for rec in unmatched_external:
        external_buckets[rec["bucket"]].append(rec)

    for rec in unmatched_internal:
        bucket_index[rec["bucket"]].append(rec)

    for bucket, ints in bucket_index.items():
        candidates = []
        for delta in (0, -1, 1):
            target = _shift_bucket(bucket, delta)
            candidates.extend(external_buckets.get(target, []))
        for int_rec in ints:
            candidate = _find_best_candidate(int_rec, candidates)
            if candidate:
                matches.append(_build_match(int_rec, candidate, pricing))
                external_buckets[candidate["bucket"]].remove(candidate)
                used_external.add(id(candidate))
                used_internal.add(id(int_rec))

    remaining_internal = [rec for rec in internal if id(rec) not in used_internal]
    remaining_external = [rec for rec in external if id(rec) not in used_external]

    return matches, remaining_internal, remaining_external


def _shift_bucket(bucket: str, delta: int) -> str:
    dt = datetime.fromisoformat(bucket + ":00")
    shifted = dt + timedelta(minutes=delta)
    return shifted.strftime("%Y-%m-%dT%H:%M")


def _find_best_candidate(internal: Dict, candidates: List[Dict]) -> Optional[Dict]:
    filtered = [c for c in candidates if c["model"] == internal["model"]]
    if not filtered:
        return None
    filtered.sort(key=lambda c: (
        abs((internal["total"] - c["total"]).copy_abs()),
        abs((internal["timestamp"] - c["timestamp"]).total_seconds()),
    ))
    return filtered[0]


def _build_match(internal: Dict, external: Dict, pricing: Dict) -> Dict:
    feature = internal["feature"] or "unknown"
    pricing_pair, using_feature = _pricing_pair(pricing, feature, internal["model"])
    internal_cost = compute_cost(internal["prompt"], internal["completion"], pricing_pair)
    diff = internal_cost - external["billed_cost"]
    confidence = "HIGH" if _token_confidence(internal, external) else "LOW"
    if external.get("cached_tokens", Decimal("0")) > 0:
        match_type = "MATCHED_CACHED_DISCOUNT_SUSPECTED"
    elif using_feature:
        match_type = "MATCHED_FEATURE_PRICE_SUSPECTED"
    else:
        match_type = "MATCHED_CLOSE"
    return {
        "time_bucket": internal["bucket"],
        "internal_timestamp": internal["timestamp"].isoformat(),
        "external_timestamp": external["timestamp"].isoformat(),
        "model": internal["model"],
        "feature": feature,
        "internal_prompt": str(internal["prompt"]),
        "internal_completion": str(internal["completion"]),
        "external_input": str(external["total"] - external.get("cached_tokens", Decimal("0"))),
        "external_output": str(external["total"] - (external["total"] - external.get("cached_tokens", Decimal("0")))),
        "cached_tokens": str(external.get("cached_tokens", Decimal("0"))),
        "internal_cost": str(internal_cost),
        "billed_cost": str(external["billed_cost"]),
        "diff": str(diff),
        "match_type": match_type,
        "match_confidence": confidence,
        "notes": "",
    }


def _pricing_pair(pricing: Dict, feature: str, model: str) -> Tuple[Tuple[Decimal, Decimal], bool]:
    input_price, output_price, used_feature = resolve_pricing(pricing, feature, model)
    return (input_price, output_price), used_feature


def _token_confidence(internal: Dict, external: Dict) -> bool:
    internal_total = internal["total"]
    external_total = external["total"]
    if external_total == 0:
        return False
    relative_diff = abs(internal_total - external_total) / external_total
    return relative_diff <= Decimal("0.1")


def summarize(matches: List[Dict], internal_only: List[Dict], external_only: List[Dict]) -> Dict:
    total_internal = sum(Decimal(row["internal_cost"]) for row in matches)
    total_external = sum(Decimal(row["billed_cost"]) for row in matches)
    total_diff = total_internal - total_external
    return {
        "total_internal_estimated_cost": str(total_internal),
        "total_external_billed_cost": str(total_external),
        "total_diff": str(total_diff),
        "internal_only_count": len(internal_only),
        "external_only_count": len(external_only),
        "matched_count": len(matches),
    }


def write_csv(records: List[Dict], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = [
        "time_bucket",
        "internal_timestamp",
        "external_timestamp",
        "model",
        "feature",
        "internal_prompt",
        "internal_completion",
        "external_input",
        "external_output",
        "cached_tokens",
        "internal_cost",
        "billed_cost",
        "diff",
        "match_type",
        "match_confidence",
        "notes",
    ]
    with path.open("w", newline="", encoding="utf-8") as fh:
        writer = csv.DictWriter(fh, fieldnames=fieldnames)
        writer.writeheader()
        for row in records:
            writer.writerow(row)


def main() -> None:
    parser = argparse.ArgumentParser(description="Reconcile OpenAI usage records")
    parser.add_argument("--internal", required=True, type=Path, help="Internal usage export CSV file")
    parser.add_argument("--external", required=True, type=Path, help="External OpenAI usage CSV file")
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--internal-timestamp", default="timestamp")
    parser.add_argument("--external-timestamp", default="timestamp")
    parser.add_argument("--internal-model", default="model")
    parser.add_argument("--external-model", default="model")
    parser.add_argument("--internal-prompt", default="prompt_tokens")
    parser.add_argument("--internal-completion", default="completion_tokens")
    parser.add_argument("--external-input", default="input_tokens")
    parser.add_argument("--external-output", default="output_tokens")
    parser.add_argument("--internal-total", default="total_tokens")
    parser.add_argument("--external-total", default="total_tokens")
    parser.add_argument("--internal-feature", default="feature")
    parser.add_argument("--internal-request-id", default="request_id")
    parser.add_argument("--external-request-id", default="request_id")
    parser.add_argument("--external-cached", default="cached_tokens")
    parser.add_argument("--external-billed", default="billed_cost_usd")
    args = parser.parse_args()

    pricing = load_pricing()
    internal_rows = load_csv(args.internal)
    external_rows = load_csv(args.external)

    internal_mapping = {
        "timestamp": args.internal_timestamp,
        "model": args.internal_model,
        "prompt_tokens": args.internal_prompt,
        "completion_tokens": args.internal_completion,
        "total_tokens": args.internal_total,
        "feature": args.internal_feature,
        "request_id": args.internal_request_id,
    }
    external_mapping = {
        "timestamp": args.external_timestamp,
        "model": args.external_model,
        "prompt_tokens": args.external_input,
        "completion_tokens": args.external_output,
        "total_tokens": args.external_total,
        "feature": "",
        "request_id": args.external_request_id,
        "cached_tokens": args.external_cached,
        "billed_cost": args.external_billed,
    }

    internals = build_records(internal_rows, internal_mapping, is_internal=True)
    externals = build_records(external_rows, external_mapping, is_internal=False)

    matches, remaining_internal, remaining_external = match_records(internals, externals, pricing)

    output_dir = args.output_dir
    csv_path = output_dir / "reconciliation.csv"
    summary_path = output_dir / "reconciliation_summary.json"

    write_csv(matches, csv_path)
    summary = summarize(matches, remaining_internal, remaining_external)
    summary["pricing_version"] = pricing.get("version")
    summary["internal_only"] = [
        {
            "model": rec["model"],
            "feature": rec["feature"],
            "timestamp": rec["timestamp"].isoformat(),
            "prompt": str(rec["prompt"]),
            "completion": str(rec["completion"]),
        }
        for rec in remaining_internal
    ]
    summary["external_only"] = [
        {
            "model": rec["model"],
            "timestamp": rec["timestamp"].isoformat(),
            "total": str(rec["total"]),
            "cached_tokens": str(rec.get("cached_tokens", Decimal('0'))),
        }
        for rec in remaining_external
    ]

    output_dir.mkdir(parents=True, exist_ok=True)
    with summary_path.open("w", encoding="utf-8") as fh:
        json.dump(summary, fh, indent=2)

    print(f"Reconciliation CSV: {csv_path}")
    print(f"Summary JSON: {summary_path}")


if __name__ == "__main__":
    main()
