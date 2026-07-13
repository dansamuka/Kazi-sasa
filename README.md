# Kazi Sasa - Implementation

A full Android Studio project implementing `KAZI_SASA_APP_DESIGN_SPEC_v2.md`,
subsequently patched against a full engineering review
(`KAZI_SASA_ANDROID_IMPLEMENTATION_RECOMMENDATIONS.md`) covering build
readiness, product-state correctness, reminders, feed trust, fit-engine
rigor, UX, and release prep. Kotlin + Jetpack Compose, Room, WorkManager,
DataStore, Retrofit.

## What's verified vs. not - read this first

This was built in a sandboxed environment with a JDK and a downloaded Kotlin
compiler, but **no Android SDK and no access to Google's Maven repository**
(`google()` / `dl.google.com` aren't reachable here). That means:

**Actually compiled and run, with real output, in this environment:**
- The entire `domain/` package (`FitEngine`, `FitEngineImpl`, all domain
  models) - zero Android dependencies by design, so plain `kotlinc` could
  build and run it.
- `app/src/test/.../FitEngineTest.kt` - the *exact* file that ships in the
  project, 13 scenarios, compiled against a local JUnit4-API shim and run via
  reflection (real JUnit lives on Maven Central, also unreachable here). All
  13 pass. Re-run for yourself with `./gradlew test` in Android Studio - no
  shim needed there.
- `feed/scripts/validate_feed.py` and `refresh_feed.py` - both real, run
  against real data. The validator caught two genuine inconsistencies in this
  repo's own bundled sample data during setup (see "Things the tooling
  actually caught" below) - not hypothetical bugs, ones it found and I fixed.
- The **Gradle wrapper is genuine**, not a placeholder: generated via an
  apt-installed Gradle 4.4.1 targeting version 8.7. Confirmed working by
  running it and watching it correctly attempt to download the real Gradle
  8.7 distribution from `services.gradle.org` - it was blocked only by this
  sandbox's own network whitelist, which is exactly what you'd expect it to
  try on a machine with normal internet access.

**Written carefully but *not* compiled here** (needs Android Studio's Gradle
sync, which needs `google()`): everything touching Room, Compose,
WorkManager, DataStore, or Retrofit. I did a full static sweep after every
major change (package/directory consistency, brace/paren balance, every
internal import resolving to a real declaration) across all 54 Kotlin files,
and caught several real issues that sweep couldn't catch by pattern-matching
alone - see below.

**First thing to do:** open this in Android Studio and let Gradle sync.
Accept any AGP/Gradle version-bump prompts it offers.

## Things the tooling actually caught (not hypothetical)

Kept here because "I wrote a validator" is a weaker claim than "here's what
it found":

1. **`validate_feed.py` caught sample data claiming official provenance.**
   Several bundled opportunities were flagged `sample` and *also* carried
   `source.confidence: official` - exactly the misleading-trust-signal
   pattern spec §14 exists to prevent. Fixed by relying on the feed-level
   `is_sample_data` flag instead of contradictory per-opportunity flags.
2. **`validate_feed.py` caught a taxonomy drift.** One opportunity used the
   raw category `customer_service` instead of the canonical taxonomy id
   `customer_ops`. Fixed at the source.
3. **A real Kotlin bug caught by inspection, not compilation:** `flatMapLatest`
   needs `@OptIn(ExperimentalCoroutinesApi::class)` in kotlinx.coroutines, and
   the original code was missing it - would have been a compile error on
   first Android Studio sync.
4. **Another real bug caught by inspection:** the `viewModelFactory` DSL lives
   in `androidx.lifecycle.viewmodel`, not `androidx.lifecycle.viewmodel.compose`
   - four screens had the wrong import path.
5. **A test I wrote was itself wrong.** An early version of the
   "remote-from-Kenya" fit-engine test asserted `LOCATION_FIT` would appear in
   `topReasons` (top 3 only) - but with a full skill+seniority+sector match,
   those three legitimately outrank location for the display slots, which is
   *correct* engine behaviour, not a bug. Rewrote the test as a differential
   comparison instead of an absolute-position check.

## Building the APK on GitHub instead of locally

`.github/workflows/build-apk.yml` builds the debug APK on GitHub's own
runners, which - unlike the sandbox this project was authored in - have the
real Android SDK and `google()` Maven access. It also runs
`FitEngineTest.kt` with real JUnit (not the local shim used during
development). This is the actual, real compile check this project has been
missing.

**To use it:** push this repo to GitHub (see "Setup" below), then either:
- push to `main` again (any commit triggers it), or
- go to the repo's **Actions** tab -> **Build debug APK** -> **Run workflow**
  for an on-demand build without committing anything.

Once it finishes (a few minutes), open the completed run and download
`kazi-sasa-debug-apk` from the **Artifacts** section at the bottom of the
page - that's `app-debug.apk`, installable directly on a device
(`adb install app-debug.apk`, or just copy it over and open it with
"install from unknown sources" enabled).

If the very first run fails, that's genuinely useful signal: it means there's
a real Gradle/Android issue this sandbox couldn't have caught, not a fabricated one.

## Setup

1. Open the `kazi-sasa/` folder in Android Studio (Ladybug+). Let Gradle sync.
2. Windows users: `01-check-environment.cmd` first, then
   `02-build-debug.cmd`, then `03-install-device.cmd` with a device connected.
3. The package is `com.kazisasa.app` throughout - rename via Android Studio's
   refactor tool if you want your own applicationId.
4. Point the feed at a real URL: `FeedApiService.DEFAULT_FEED_URL` in
   `data/remote/FeedApiService.kt` has a placeholder GitHub path. Stand up
   the `feed/` folder as its own repo (see `feed/README.md`) and point the
   constant there.
5. Until you do #4, the app is fully usable on the bundled
   `assets/seed.json` - both it and `feed/feed.json` are explicitly marked
   `is_sample_data: true` and the app tells the user so (recommendations
   doc §20).
6. Run on a device/emulator with API 26+.

## What changed in the recommendations-doc pass

Full list is in the doc itself; the structural changes worth knowing before
reading the code:

- **`SavedOpportunityEntity` and `ReminderEntity` are now keyed by
  `(profileId, opportunityId)`, not `opportunityId` alone.** Two profiles on
  one device no longer share saved favourites or step on each other's
  reminders.
- **`TriageAction.SAVED` is gone.** Saving now always implies `KEPT`;
  unsaving reverts to `KEPT`, not `UNSEEN` (recommendations doc §12/§13) -
  saved and triage state can no longer drift out of sync with each other.
- **"Keep" is now "Shortlist", and it has a real screen** (`ui/shortlist/`) -
  previously tapping Keep just hid a card with no way to find it again.
- **Skipped items are recoverable** via a dedicated screen with per-item
  restore and restore-all, reachable from Shortlist.
- **Notification permission is requested inline, on first "Remind me" tap**,
  not on app launch - `MainActivity` no longer requests it at all.
- **A weekly digest banner** on the main feed surfaces top untriaged matches
  as a fallback for anyone who denies notification permission, with a
  persistent on/off toggle on the Profiles screen.
- **Navigation is now a real bottom nav** (For You / Shortlist / Saved /
  Profile), not a chain of text-button links.
- **`FitEngine` gained score/band caps**: an unverified source can never
  present as a top (STRONG) recommendation; matching zero required skills
  caps at STRETCH; an expired deadline caps the numeric score at 30; any
  STRETCH-band item caps at 45 - so the displayed number never contradicts
  the band next to it.
- **The `feed/` companion repo is now real**: `taxonomy.json` (controlled
  category/skill vocabulary), `sources.json` (per-domain trust defaults),
  a working `validate_feed.py`, and `refresh_feed.py` scaffolding with one
  clearly-stubbed collector function.
- **`docs/RELEASE_BUILD.md` and `docs/PLAY_STORE_READINESS.md`** cover
  signing and store-listing prep; an in-app privacy note lives on the
  Profiles screen.

## Mapping back to the design spec

| Spec section | Where it lives |
|---|---|
| §8 Explainability model | `domain/fit/FitEngineImpl.kt` (scoring + caps), `domain/model/FitBreakdown.kt` |
| §9 Triage model | `data/local/entity/SupportingEntities.kt`, `data/repository/TriageRepository.kt`, `ui/shortlist/` |
| §7.11 Source confidence / anti-scam | `ui/components/SourceConfidenceBadge.kt`, the pre-exit caution dialog in `OpportunityDetailScreen.kt` |
| §7.6 Reminders, honestly | `work/ReminderScheduler.kt`, permission request inline in `OpportunityDetailScreen.kt` |
| §7.7 Live/cache/bundled fallback | `data/repository/FeedRepository.kt` |
| §7.10 Weekly digest | `FeedUiState.digestItems` in `ui/feed/FeedViewModel.kt`, `DigestBanner` in `FeedScreen.kt` |
| §15 Kenyan location granularity | `LocationInfo`/`LocationEmbedded`/`LocationDto` |
| §24 Feed contract | `data/remote/dto/FeedDtos.kt`, `feed/SCHEMA.md` |
| §26 Room schema | `data/local/entity/*.kt`, `data/local/dao/*.kt` |

## Architecture at a glance

```
domain/            <- framework-free: models + FitEngine. Compiles standalone.
data/
  local/            Room: entities, DAOs, TypeConverters, AppDatabase
  datastore/        active profile + prefs (DataStore)
  remote/           feed DTOs + Retrofit service
  mapper/           DTO<->Entity<->Domain conversions
  repository/       what ViewModels actually talk to
work/               WorkManager: reminders + periodic feed refresh
di/                 AppContainer (manual service locator)
ui/                 Compose: navigation (bottom nav), theme, components,
                    feed/triage, detail, saved, shortlist, skipped, profile
docs/               RELEASE_BUILD.md, PLAY_STORE_READINESS.md
feed/               companion data-pipeline repo scaffold
```

Data flows one direction: `remote/dto` -(mapper)-> `local/entity` (Room)
-(mapper)-> `domain/model` (what everything above the repository layer
actually sees). Nothing in `ui/` or `domain/` imports a Room or Retrofit type.

## Known gaps, stated plainly

- **`app/schemas/` starts empty.** Room populates it on first real build;
  nothing to do manually.
- **`fallbackToDestructiveMigration()` is on** in `AppDatabase.kt` - correct
  for a pre-release app whose schema has changed repeatedly with no real user
  data at stake, wrong once real users exist. Flagged in that file's own
  comment.
- **`refresh_feed.py` has no real collector wired in** - it's tested,
  working scaffolding (taxonomy mapping, source-confidence lookup, schema-
  valid output) around one deliberately-empty stub function. See that file's
  docstring for why building a real, untested scraper wasn't attempted here.
- **Reminder delivery across Android OEM battery-optimisation variants is
  untested** - can't be tested without physical devices. The copy in
  `OpportunityDetailScreen.kt` reflects this honestly rather than promising
  reliable delivery.
- **Release signing isn't wired into `app/build.gradle.kts`** - deliberately,
  per `docs/RELEASE_BUILD.md` - `isMinifyEnabled` should stay `false` until a
  real device test confirms R8 isn't stripping something it shouldn't.

## Suggested next step

Wire a real collector into `feed/scripts/refresh_feed.py` (ReliefWeb's API is
the easiest first target - `sources.json` already has its trust level set),
point `FeedApiService.DEFAULT_FEED_URL` at the standalone `feed/` repo once
it exists, and do a first real Android Studio build to work through whatever
Gradle sync surfaces that this sandbox couldn't check.
