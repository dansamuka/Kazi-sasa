#!/usr/bin/env python3
"""Validates feed.json against the Kazi Sasa feed contract (SCHEMA.md / spec §24).

Usage:
    python3 validate_feed.py feed.json
    python3 validate_feed.py feed.json --taxonomy taxonomy.json

Exits non-zero on any error, so the GitHub Actions workflow (§24 recommendation:
"add feed validation in GitHub Actions... invalid feed updates fail before they
reach the app") can gate the commit step on this. Prints every problem found
rather than stopping at the first one, since a batch feed regeneration is more
useful to debug all at once.
"""
import json
import re
import sys
import argparse
from datetime import datetime, timezone
from urllib.parse import urlparse

VALID_OPPORTUNITY_TYPES = {"job", "fellowship", "grant", "internship", "programme"}
VALID_ORG_TYPES = {"employer", "ngo", "multilateral", "private", "unverified"}
VALID_WORK_MODES = {"onsite", "hybrid", "remote_kenya", "remote_regional", "remote_global"}
VALID_SENIORITY = {"entry", "mid", "senior", "leadership"}
VALID_LOCATION_SCOPE = {"local", "national", "regional", "international"}
VALID_DEADLINE_CONFIDENCE = {"explicit", "inferred", "unknown"}
VALID_SOURCE_CONFIDENCE = {"official", "aggregated", "community", "unverified"}
VALID_FLAGS = {"urgent", "relocation_worthy", "ai_relevant", "hidden_gem", "eligibility_review", "sample"}

REQUIRED_OPPORTUNITY_FIELDS = ["id", "title", "opportunity_type", "organisation", "location", "source"]


class ValidationErrors:
    def __init__(self):
        self.errors: list[str] = []
        self.warnings: list[str] = []

    def error(self, msg: str):
        self.errors.append(msg)

    def warn(self, msg: str):
        self.warnings.append(msg)

    @property
    def ok(self) -> bool:
        return not self.errors


def is_iso8601(value) -> bool:
    if not isinstance(value, str):
        return False
    try:
        # Python's fromisoformat wants +00:00 not Z pre-3.11; normalise defensively
        # so this validator behaves the same on older Python in CI as it does here.
        datetime.fromisoformat(value.replace("Z", "+00:00"))
        return True
    except ValueError:
        return False


def parse_iso8601(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def is_valid_url(value) -> bool:
    if not isinstance(value, str):
        return False
    parsed = urlparse(value)
    return parsed.scheme in ("http", "https") and bool(parsed.netloc)


def validate_meta(meta: dict, errs: ValidationErrors):
    for field in ["feed_version", "generated_at"]:
        if field not in meta:
            errs.error(f"meta.{field} is required")
    if "generated_at" in meta and not is_iso8601(meta["generated_at"]):
        errs.error(f"meta.generated_at is not valid ISO-8601: {meta.get('generated_at')!r}")
    if "next_expected_update" in meta and meta["next_expected_update"] is not None:
        if not is_iso8601(meta["next_expected_update"]):
            errs.error(f"meta.next_expected_update is not valid ISO-8601: {meta['next_expected_update']!r}")


def validate_opportunity(opp: dict, index: int, seen_ids: set, taxonomy: dict | None, errs: ValidationErrors):
    label = f"opportunities[{index}] (id={opp.get('id', '?')!r})"

    for field in REQUIRED_OPPORTUNITY_FIELDS:
        if field not in opp or opp[field] in (None, ""):
            errs.error(f"{label}: missing required field '{field}'")

    opp_id = opp.get("id")
    if opp_id:
        if not re.match(r"^[a-zA-Z0-9_.-]+$", opp_id):
            errs.error(f"{label}: id contains characters that risk breaking stability across regenerations")
        if opp_id in seen_ids:
            errs.error(f"{label}: duplicate id '{opp_id}' - ids must be stable AND unique")
        seen_ids.add(opp_id)

    opp_type = opp.get("opportunity_type")
    if opp_type is not None and opp_type not in VALID_OPPORTUNITY_TYPES:
        errs.error(f"{label}: invalid opportunity_type '{opp_type}' (expected one of {sorted(VALID_OPPORTUNITY_TYPES)})")

    org = opp.get("organisation", {})
    if isinstance(org, dict):
        if "name" not in org or not org["name"]:
            errs.error(f"{label}: organisation.name is required")
        if org.get("type") is not None and org.get("type") not in VALID_ORG_TYPES:
            errs.error(f"{label}: invalid organisation.type '{org.get('type')}'")
    else:
        errs.error(f"{label}: organisation must be an object")

    work_mode = opp.get("work_mode")
    if work_mode is not None and work_mode not in VALID_WORK_MODES:
        errs.error(f"{label}: invalid work_mode '{work_mode}'")

    seniority = opp.get("seniority")
    if seniority is not None and seniority not in VALID_SENIORITY:
        errs.error(f"{label}: invalid seniority '{seniority}'")

    location = opp.get("location", {})
    if isinstance(location, dict):
        scope = location.get("scope")
        if scope is not None and scope not in VALID_LOCATION_SCOPE:
            errs.error(f"{label}: invalid location.scope '{scope}'")
    else:
        errs.error(f"{label}: location must be an object")

    deadline_confidence = opp.get("deadline_confidence", "unknown")
    if deadline_confidence not in VALID_DEADLINE_CONFIDENCE:
        errs.error(f"{label}: invalid deadline_confidence '{deadline_confidence}'")

    posted_at = opp.get("posted_at")
    deadline = opp.get("deadline")
    for field_name, value in [("posted_at", posted_at), ("deadline", deadline)]:
        if value is not None and not is_iso8601(value):
            errs.error(f"{label}: {field_name} is not valid ISO-8601: {value!r}")

    if posted_at and deadline and is_iso8601(posted_at) and is_iso8601(deadline):
        if parse_iso8601(deadline) < parse_iso8601(posted_at):
            errs.error(f"{label}: deadline ({deadline}) is before posted_at ({posted_at})")

    source = opp.get("source", {})
    if isinstance(source, dict):
        if "name" not in source or not source["name"]:
            errs.error(f"{label}: source.name is required")
        if "url" not in source or not is_valid_url(source.get("url")):
            errs.error(f"{label}: source.url is missing or not a valid http(s) URL")
        confidence = source.get("confidence")
        if confidence not in VALID_SOURCE_CONFIDENCE:
            errs.error(f"{label}: invalid or missing source.confidence '{confidence}'")
    else:
        errs.error(f"{label}: source must be an object")

    apply_url = opp.get("apply_url")
    if apply_url is None:
        errs.warn(f"{label}: no apply_url - the app will show 'no application link available'")
    elif not is_valid_url(apply_url):
        errs.error(f"{label}: apply_url is not a valid http(s) URL: {apply_url!r}")

    flags = opp.get("flags", [])
    if not isinstance(flags, list):
        errs.error(f"{label}: flags must be a list")
    else:
        for flag in flags:
            if flag not in VALID_FLAGS:
                errs.error(f"{label}: unknown flag '{flag}' (expected one of {sorted(VALID_FLAGS)})")

    # recommendations doc §24: "sample listings not marked official"
    is_sample = "sample" in flags
    if is_sample and isinstance(source, dict) and source.get("confidence") == "official":
        errs.error(f"{label}: flagged 'sample' but source.confidence is 'official' - sample data must never claim official confidence")

    if taxonomy is not None:
        valid_categories = {c["id"] for c in taxonomy.get("categories", [])}
        valid_skills = {s["id"] for s in taxonomy.get("skills", [])}
        for cat in opp.get("categories", []):
            if cat not in valid_categories:
                errs.warn(f"{label}: category '{cat}' is not in taxonomy.json - either add it there or map it to an existing id")
        for skill in opp.get("skills_required", []) + opp.get("skills_preferred", []):
            if skill not in valid_skills:
                errs.warn(f"{label}: skill '{skill}' is not in taxonomy.json - either add it there or map it to an existing id")


def validate_feed(feed: dict, taxonomy: dict | None) -> ValidationErrors:
    errs = ValidationErrors()

    if "meta" not in feed:
        errs.error("top-level 'meta' object is required")
    else:
        validate_meta(feed["meta"], errs)

    opportunities = feed.get("opportunities")
    if not isinstance(opportunities, list):
        errs.error("top-level 'opportunities' must be a list")
        return errs

    if len(opportunities) == 0:
        errs.warn("opportunities list is empty - the app will fall back to cache or bundled seed data")

    seen_ids: set = set()
    for i, opp in enumerate(opportunities):
        validate_opportunity(opp, i, seen_ids, taxonomy, errs)

    declared_count = feed.get("meta", {}).get("opportunity_count")
    if declared_count is not None and declared_count != len(opportunities):
        errs.warn(f"meta.opportunity_count ({declared_count}) does not match actual count ({len(opportunities)})")

    return errs


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("feed_path", help="Path to feed.json")
    parser.add_argument("--taxonomy", help="Path to taxonomy.json (optional - enables category/skill checks)")
    args = parser.parse_args()

    with open(args.feed_path, encoding="utf-8") as f:
        feed = json.load(f)

    taxonomy = None
    if args.taxonomy:
        with open(args.taxonomy, encoding="utf-8") as f:
            taxonomy = json.load(f)

    result = validate_feed(feed, taxonomy)

    for w in result.warnings:
        print(f"WARN  {w}")
    for e in result.errors:
        print(f"ERROR {e}")

    print()
    print(f"{len(result.errors)} error(s), {len(result.warnings)} warning(s)")

    if not result.ok:
        sys.exit(1)


if __name__ == "__main__":
    main()
