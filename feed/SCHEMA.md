# feed.json schema

Source of truth: `app/src/main/java/com/kazisasa/app/data/remote/dto/FeedDtos.kt`
in the main Kazi Sasa app repo, and `scripts/validate_feed.py` in this repo
(which enforces this document programmatically). If any of the three
disagree, the Kotlin DTOs win for what the app actually accepts, but
validate_feed.py should be updated to match immediately - a schema doc and a
validator that silently drift apart from the real parser is worse than
having neither.

## Top level

```json
{
  "meta": { ... },
  "profiles": [ ... ],
  "opportunities": [ ... ]
}
```

`profiles` is optional (defaults to `[]`) - only include it if you want to ship
default career profiles with the feed; the app will not overwrite a profile a
user has already customised with the same `id`.

## meta

| field | type | notes |
|---|---|---|
| `feed_version` | string | bump on any breaking schema change |
| `generated_at` | ISO-8601 string | when this file was built |
| `next_expected_update` | ISO-8601 string, nullable | drives the app's staleness UI |
| `opportunity_count` | int | informational; validator warns if it drifts from the real count |
| `source_count` | int | informational |
| `schema_url` | string, nullable | link back to this file |
| `is_sample_data` | bool, default `false` | recommendations doc §20 - set `true` if this *entire* feed is demo/sample content, independent of how the app fetched it |

## opportunities[]

Required: `id`, `title`, `opportunity_type`, `organisation`, `location`, `source`.
Everything else is optional and the app renders around a missing value rather
than breaking (spec §17).

| field | type | allowed values |
|---|---|---|
| `id` | string | stable across regenerations - see README |
| `opportunity_type` | string | `job`, `fellowship`, `grant`, `internship`, `programme` |
| `organisation.type` | string | `employer`, `ngo`, `multilateral`, `private`, `unverified` |
| `location.scope` | string, nullable | `local`, `national`, `regional`, `international` |
| `work_mode` | string, nullable | `onsite`, `hybrid`, `remote_kenya`, `remote_regional`, `remote_global` |
| `seniority` | string, nullable | `entry`, `mid`, `senior`, `leadership` |
| `deadline_confidence` | string | `explicit`, `inferred`, `unknown` |
| `source.confidence` | string | `official`, `aggregated`, `community`, `unverified` |
| `flags[]` | string[] | any of `urgent`, `relocation_worthy`, `ai_relevant`, `hidden_gem`, `eligibility_review`, `sample` |

`flags: ["sample"]` marks one *individual* opportunity as demo content within
an otherwise-live feed - different from `meta.is_sample_data`, which marks the
whole response. **A single opportunity can never carry both `sample` and
`source.confidence: "official"`** - validate_feed.py enforces this; sample
content claiming official provenance is exactly the kind of misleading signal
spec §14 exists to prevent, and it's caught two real instances of this during
this repo's own setup.

`categories` and `skills_required`/`skills_preferred` should use the
canonical `id`s from `taxonomy.json`, not raw source terms - validate_feed.py
warns (doesn't yet hard-fail) when it sees an id that isn't in the taxonomy.

All timestamps are ISO-8601 UTC (`2026-07-10T06:00:00Z`). Unrecognised enum
values are not fatal on the app side - it maps them to a safe default and
keeps going (see `enumOrNull` in `OpportunityMappers.kt`) - but
validate_feed.py treats them as hard errors here, because letting a bad enum
value reach the app silently loses a signal the fit engine would otherwise use.

## profiles[]

See spec §25 / `ProfileDto` in `FeedDtos.kt` - `weights` fields are all floats,
nominally in `[0, 1]`, one per fit dimension listed in spec §8.2.
