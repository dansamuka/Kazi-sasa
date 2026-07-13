# Play Store readiness

What recommendations doc §35 asks to have prepared before moving from a debug
APK to internal/closed testing. This is a checklist, not filled-in content -
most of these are product/business decisions, not engineering ones.

## Store listing

- [ ] **App name** - "Kazi Sasa" fits Play Store's 30-character limit comfortably.
- [ ] **Short description** (80 chars max) - draft: *"Career opportunities, explained - not just listed."*
- [ ] **Full description** (4000 chars max) - should cover: what it is, the
      explainability angle (the actual differentiator per the original design
      spec), multiple profiles, offline support, and the honest limits section
      (spec §14) in plain language - Play Store descriptions that overclaim get
      flagged in review.
- [ ] **Screenshots** - minimum 2, Google recommends 4-8. Given the current UI
      is a functional skeleton (spec §11 leaves visual design to a designer),
      **do not submit screenshots until the visual design pass is done** -
      submitting placeholder-styled screenshots undersells the product and
      may need re-submission anyway once real design lands.
- [ ] **App icon** - the ladder mark in `res/drawable/ic_launcher_*.xml` is a
      literal placeholder, explicitly flagged as such in the spec. Needs a
      real design pass before store submission, not just before general use.
- [ ] **Feature graphic** (1024x500) - required for the store listing page.

## Privacy & data safety

- [ ] **Privacy policy URL** - Play Console requires a hosted, publicly
      reachable privacy policy URL, not just in-app text. The in-app privacy
      note (see below) is necessary but not sufficient - it needs a real URL
      you control (a GitHub Pages page off the `kazi-sasa-feed` repo, or
      similar, works fine for this).
- [ ] **Data safety declaration** - Play Console's data safety form asks what
      data the app collects/shares. Based on what's actually implemented:
      profiles, saved roles, and triage decisions are stored **locally only**
      (Room, on-device) - nothing is transmitted to a server the app controls,
      since there is no backend (spec's whole architecture is a static
      GitHub-hosted feed + on-device Room). The feed fetch itself sends no
      personal data - it's an unauthenticated GET against a public JSON file.
      This should make the data safety form straightforward (no analytics SDK
      is wired in - see recommendations doc §36 below), but fill it in
      against the actual shipped code at submission time, not this note.
- [ ] **Content rating questionnaire** - a career/jobs app with no
      user-generated content, no messaging between users, no mature content;
      should land in the lowest rating tier on every major regional system,
      but the questionnaire itself has to be completed in Play Console.

## Testing & rollout

- [ ] **Tester group** - set up an internal testing track in Play Console
      first (up to 100 testers via email list, no review wait), before
      closed or open testing tracks.
- [ ] **Versioning policy** - suggested: `versionName` follows `major.minor`
      (already at "2.0" per `app/build.gradle.kts`), `versionCode` increments
      by 1 on every Play Console upload regardless of how small the change.

## Analytics (recommendations doc §36's parenthetical: "only if privacy requirements are clear")

No analytics SDK is wired into this codebase, and none should be added
without a clear decision first on: what's collected, whether it's
per-installation or identifiable, and whether the privacy policy and data
safety declaration above are updated *before* the SDK ships, not after. Given
the app's entire value proposition includes "auditable, not a black box" (the
fit-engine explainability), adding opaque third-party analytics without
updating the user-facing privacy story would cut against the product's own
premise.
