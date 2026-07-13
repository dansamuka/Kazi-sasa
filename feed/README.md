# kazi-sasa-feed

The live data source for the Kazi Sasa app - a single `feed.json` file, refreshed
on a schedule by GitHub Actions and served over GitHub raw. The app
(`FeedApiService.DEFAULT_FEED_URL`) fetches this file directly; there is no
backend server.

This mirrors the pattern already proven on the Kenya Election Intelligence
Dashboard: static, source-transparent JSON, versioned in Git, rebuilt by a
scheduled workflow, no server to run or pay for.

## Files

- **`feed.json`** - the live feed, currently a copy of `seed.json` (both marked
  `is_sample_data: true`) so the app has something real to fetch on first
  setup. Replace with `refresh_feed.py`'s real output once collectors exist.
- **`seed.json`** - bundled fallback content, mirrored into the app at
  `app/src/main/assets/seed.json` (spec §7.7's offline/first-run fallback).
- **`SCHEMA.md`** - the field-for-field contract (spec §24).
- **`taxonomy.json`** - controlled category/skill IDs with aliases
  (recommendations doc §23) - `refresh_feed.py` should map every source term
  through this rather than writing raw source terms into `feed.json`.
- **`sources.json`** - which domains default to which `source.confidence`
  level (recommendations doc §21/§22's trust signal starts here).
- **`.github/workflows/refresh-feed.yml`** - runs three times a day, refreshes,
  validates, commits only if both succeed.
- **`scripts/refresh_feed.py`** - real, tested scaffolding (`FeedBuilder`,
  taxonomy mapping, source-confidence lookup) with one clearly-marked stub
  collector function (`build_from_reliefweb_stub`) left for you to fill in
  with an actual API call. It refuses to overwrite `feed.json` with an empty
  result rather than silently blanking the feed.
- **`scripts/validate_feed.py`** - a real, working validator (run it:
  `python3 scripts/validate_feed.py feed.json --taxonomy taxonomy.json`).
  Checks required fields, enum values, URL validity, ISO-8601 dates, deadline-
  after-posted ordering, and specifically that nothing marked `sample` also
  claims `source.confidence: official`.

## Why source-transparent matters here

Spec §14 is explicit that the app must not present aggregated/scraped info as
official unless it comes directly from an official source, and must never
fabricate data. `sources.json` is where that decision actually gets made -
every collector you add to `refresh_feed.py` should call
`builder.confidence_for_domain(...)` rather than deciding confidence ad hoc
per listing.

## Stable IDs matter

`id` must stay the same across regenerations for the same underlying listing
(spec §24.4: hash of source URL + title is a reasonable scheme) - it's what
lets a user's save, triage state, and reminder survive a feed refresh.
Changing the ID scheme is a breaking change for every device that has saved
anything.

## Setup

```bash
pip install -r requirements.txt
python3 scripts/validate_feed.py feed.json --taxonomy taxonomy.json   # should pass clean today
python3 scripts/refresh_feed.py --out feed.json                       # will exit 1 until a real collector is wired in - see the script's docstring
```
