# Android release — what is still missing

Signing and Play upload are deliberately **out of scope for v1**. Nothing in this repo can
publish anything today: there is no keystore, no signing config, and no upload script. This
note lists what has to exist before the first internal-testing build.

## 1. Upload keystore

- One `upload-keystore.jks`, RSA 2048, 25 years, generated once and **never committed**.
- Kept in the private profile repo, not here; referenced from `~/.gradle/gradle.properties`
  (`QUOTA_UPLOAD_STORE_FILE`, `…_STORE_PASSWORD`, `…_KEY_ALIAS`, `…_KEY_PASSWORD`), so the
  build file stays secret-free.
- Play App Signing holds the app signing key; the upload key is only the envelope. Losing it
  is recoverable through Play support — losing an unenrolled app signing key is not.
- `app/build.gradle.kts` then gains a `signingConfigs.release` that reads those properties and
  silently falls back to unsigned when they are absent, so a clone still builds.

## 2. Play Console app

- Package `com.gorisse.thomas.quota`, a third listing next to AR Model Viewer and Will It Fit.
- Store listing, content rating, data-safety form. Data safety is short and true: the app
  sends nothing anywhere except the paired host, collects no analytics, ships no crash SDK.
- Internal testing track first; the app is only useful to someone who runs the collector.

## 3. `sync-play`-style script

Modelled on the existing sceneview one, and kept in this repo:

- Reads `PLAY_PACKAGE_NAME` so a single script can serve several apps.
- `./gradlew :app:bundleRelease`, then uploads the `.aab` to the internal track through the
  Play Developer API with a service-account JSON (again from outside the repo).
- Refuses to run on a dirty tree, and refuses to upload a `versionCode` already on the track.
- Never rolls out to production on its own: promotion stays a click in the console.

## 4. Before any of this is worth doing

The backend has to be deployed. Until `GET /v1/status` answers for real, the only way to see
this app work is the debug "demo data" toggle, and a store listing would be premature.
