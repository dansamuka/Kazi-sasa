#!/usr/bin/env python3
"""Builds feed.json from whatever opportunity sources you plug in.

This is genuinely a starting scaffold, not a working scraper - I don't have a
way to build and maintain live scrapers against ReliefWeb/BrighterMonday/employer
career pages from inside the environment this was authored in, and a scraper
that isn't tested against the real live site is worse than no scraper (it fails
silently, or worse, produces plausible-looking garbage). What this script DOES
give you:

  - FeedBuilder: accumulates opportunities, applies taxonomy.json mapping,
    looks up source confidence from sources.json, and writes valid feed.json
    matching SCHEMA.md exactly.
  - A worked example (build_from_reliefweb_stub) showing the shape a real
    collector function should have - replace its body with an actual
    requests.get(...) call against ReliefWeb's API once you're ready.
  - main() wires everything together and always ends by calling
    validate_feed.py's logic on its own output before writing, so a broken
    collector fails loudly here rather than silently shipping bad data.

Usage:
    python3 refresh_feed.py --out feed.json
"""
import argparse
import json
import os
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from validate_feed import validate_feed  # noqa: E402


def load_json(path: Path) -> dict:
    with open(path, encoding="utf-8") as f:
        return json.load(f)


class FeedBuilder:
    """Accumulates opportunities and produces a schema-valid feed.json dict."""

    def __init__(self, taxonomy: dict, sources: dict):
        self.taxonomy = taxonomy
        self.sources = sources
        self._category_aliases = self._build_alias_map(taxonomy.get("categories", []))
        self._skill_aliases = self._build_alias_map(taxonomy.get("skills", []))
        self.opportunities: list[dict] = []

    @staticmethod
    def _build_alias_map(entries: list[dict]) -> dict:
        alias_map = {}
        for entry in entries:
            alias_map[entry["id"]] = entry["id"]
            for alias in entry.get("aliases", []):
                alias_map[alias.lower().strip()] = entry["id"]
        return alias_map

    def map_category(self, raw: str) -> str:
        """Maps a source's raw term to the taxonomy id, or returns it unchanged
        with a stderr warning if it isn't recognised - see taxonomy.json's
        _comment for why raw terms should never reach feed.json unmapped."""
        mapped = self._category_aliases.get(raw.lower().strip())
        if mapped is None:
            print(f"WARN: category '{raw}' not in taxonomy.json - add it or an alias for it", file=sys.stderr)
            return raw
        return mapped

    def map_skill(self, raw: str) -> str:
        mapped = self._skill_aliases.get(raw.lower().strip())
        if mapped is None:
            print(f"WARN: skill '{raw}' not in taxonomy.json - add it or an alias for it", file=sys.stderr)
            return raw
        return mapped

    def confidence_for_domain(self, domain: str) -> str:
        for source in self.sources.get("sources", []):
            if source.get("domain") == domain:
                return source["default_confidence"]
        return self.sources.get("default_for_unknown_source", "unverified")

    def add(self, opportunity: dict):
        self.opportunities.append(opportunity)

    def build(self, feed_version: str = "2.0", is_sample_data: bool = False) -> dict:
        now = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
        next_update = (datetime.now(timezone.utc) + timedelta(hours=6)).isoformat().replace("+00:00", "Z")
        return {
            "meta": {
                "feed_version": feed_version,
                "generated_at": now,
                "next_expected_update": next_update,
                "opportunity_count": len(self.opportunities),
                "source_count": len({o["source"]["name"] for o in self.opportunities if "source" in o}),
                "schema_url": "https://raw.githubusercontent.com/dansamuka/kazi-sasa-feed/main/SCHEMA.md",
                "is_sample_data": is_sample_data,
            },
            "opportunities": self.opportunities,
        }


def build_from_reliefweb(builder: FeedBuilder, appname: str, country_iso3: str = "KEN", limit: int = 50):
    """Real collector against the ReliefWeb API v2 jobs endpoint.

    IMPORTANT - not execution-tested against the live API: this environment's
    network access does not include api.reliefweb.int, so this has been
    written carefully against ReliefWeb's published API docs
    (https://apidoc.reliefweb.int/) and a confirmed real job-record field
    shape, but the first time it will actually run against live data is on
    a GitHub Actions runner. Test with workflow_dispatch and read the run's
    logs closely before trusting the cron schedule.

    Requires a pre-approved ReliefWeb `appname` (as of 1 Nov 2025, ReliefWeb
    requires this - request one at https://apidoc.reliefweb.int/, form linked
    from that page). Passed in via the RELIEFWEB_APPNAME environment variable
    (see .github/workflows/refresh-feed.yml), never hardcoded here.
    """
    import requests

    url = "https://api.reliefweb.int/v2/jobs"
    params = {
        "appname": appname,
        "limit": limit,
        "sort[]": "date.created:desc",
        "filter[operator]": "AND",
        "filter[conditions][0][field]": "country.iso3",
        "filter[conditions][0][value][]": country_iso3,
        "filter[conditions][1][field]": "status",
        "filter[conditions][1][value][]": "published",
        "fields[include][]": [
            "title",
            "body",
            "date.created",
            "date.closing",
            "country.name",
            "country.iso3",
            "city.name",
            "career_categories.name",
            "theme.name",
            "type.name",
            "source.name",
            "source.type.name",
            "how_to_apply",
        ],
    }

    resp = requests.get(url, params=params, timeout=30)
    resp.raise_for_status()
    payload = resp.json()

    for item in payload.get("data", []):
        fields = item.get("fields", {})
        rw_id = item.get("id")
        if rw_id is None or not fields.get("title"):
            continue  # skip anything too malformed to trust rather than guess

        detail_url = f"https://reliefweb.int/node/{rw_id}"

        # Job type -> opportunity_type. ReliefWeb's "type" taxonomy includes
        # values like "Job", "Internship", "Volunteer" - only map the ones
        # that have a real match in our own enum, default to "job" otherwise.
        rw_type_names = {t.get("name", "").lower() for t in fields.get("type", [])} if isinstance(fields.get("type"), list) else set()
        opportunity_type = "internship" if "internship" in rw_type_names else "job"

        source_orgs = fields.get("source", [])
        org_name = source_orgs[0].get("name", "ReliefWeb-listed organisation") if source_orgs else "ReliefWeb-listed organisation"
        org_type_names = {o.get("name", "").lower() for src in source_orgs for o in (src.get("type") or []) if isinstance(src.get("type"), list)} if source_orgs else set()
        if any("multilateral" in n or "united nations" in n for n in org_type_names):
            org_type = "multilateral"
        elif any("non-governmental" in n or "ngo" in n for n in org_type_names):
            org_type = "ngo"
        else:
            org_type = "unverified"

        country = fields.get("country", [])
        country_name = country[0].get("name") if country else None
        city = fields.get("city", [])
        city_name = city[0].get("name") if city else None
        location_raw = ", ".join(p for p in [city_name, country_name] if p) or None

        career_cats = fields.get("career_categories", [])
        raw_categories = [c.get("name", "") for c in career_cats if c.get("name")]
        categories = [builder.map_category(c) for c in raw_categories][:3]

        body = fields.get("body") or ""
        summary = (body[:300] + "...") if len(body) > 300 else body or None

        deadline = fields.get("date", {}).get("closing")
        posted_at = fields.get("date", {}).get("created")

        builder.add({
            "id": f"reliefweb-{rw_id}",
            "title": fields["title"],
            "opportunity_type": opportunity_type,
            "organisation": {
                "name": org_name,
                "type": org_type,
                "verified": True,  # ReliefWeb vets its posting sources - see sources.json
            },
            "location": {
                "raw": location_raw,
                "country": country_name,
                "region": None,
                "is_remote_from_kenya": False,
                "scope": "national" if country_name else None,
                "relocation_country": None,
            },
            "work_mode": None,  # ReliefWeb doesn't expose a structured work-mode field
            "seniority": None,  # left for a future pass - no reliable structured signal here
            "categories": categories,
            "skills_required": [],
            "skills_preferred": [],
            "posted_at": posted_at,
            "deadline": deadline,
            "deadline_confidence": "explicit" if deadline else "unknown",
            "source": {
                "name": "ReliefWeb",
                "url": "https://reliefweb.int",
                "confidence": builder.confidence_for_domain("reliefweb.int"),
                "last_seen_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
            },
            # detail_url, not a direct org apply link we can verify - so
            # apply_is_official stays False rather than assuming (spec §14).
            "apply_url": detail_url,
            "apply_is_official": False,
            "flags": [],
            "eligibility_notes": None,
            "summary": summary,
            "raw_description_url": detail_url,
        })


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", default="feed.json", help="Output path")
    parser.add_argument("--taxonomy", default="taxonomy.json")
    parser.add_argument("--sources", default="sources.json")
    args = parser.parse_args()

    here = Path(__file__).parent.parent  # feed/ - scripts/ is one level down
    taxonomy = load_json(here / args.taxonomy)
    sources = load_json(here / args.sources)

    builder = FeedBuilder(taxonomy, sources)

    appname = os.environ.get("RELIEFWEB_APPNAME")
    if not appname:
        print(
            "RELIEFWEB_APPNAME is not set - ReliefWeb requires a pre-approved "
            "appname as of 1 Nov 2025 (request one at https://apidoc.reliefweb.int/). "
            "Add it as a repo secret and pass it via the workflow's env block. "
            "Not writing an empty feed.json over a possibly-good existing one.",
            file=sys.stderr,
        )
        sys.exit(1)

    try:
        build_from_reliefweb(builder, appname=appname)
    except Exception as exc:  # noqa: BLE001 - any collector failure should be loud, not silent
        print(f"ERROR: ReliefWeb collector failed: {exc}", file=sys.stderr)
        sys.exit(1)

    if not builder.opportunities:
        print(
            "No opportunities collected - refresh_feed.py has no real collectors "
            "wired in yet (see build_from_reliefweb_stub's docstring). Not writing "
            "an empty feed.json over a possibly-good existing one.",
            file=sys.stderr,
        )
        sys.exit(1)

    feed = builder.build()

    result = validate_feed(feed, taxonomy)
    for w in result.warnings:
        print(f"WARN  {w}", file=sys.stderr)
    for e in result.errors:
        print(f"ERROR {e}", file=sys.stderr)
    if not result.ok:
        print("Generated feed failed validation - not writing output.", file=sys.stderr)
        sys.exit(1)

    out_path = here / args.out
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(feed, f, indent=2)
        f.write("\n")
    print(f"Wrote {len(builder.opportunities)} opportunities to {out_path}")


if __name__ == "__main__":
    main()
