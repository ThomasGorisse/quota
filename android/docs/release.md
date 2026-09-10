# Android release

Everything needed to ship to Google Play exists in this repo, except the Play Console app
itself — creating that is a manual, one-time step (below). Signing, the upload script and
the tag workflow are in place and verified: `:app:bundleRelease` produces an AAB signed by
the upload key, `jarsigner -verify` reports `jar verified`.

## 1. Upload keystore

`upload-keystore.jks`, RSA 2048, alias `upload`, valid 10 000 days (to 2054). It is **not
in this repo and never will be**. It lives with the other app credentials, in the private
profile clone, where `.gitignore` covers `credentials/**`:

```
~/Projects/ThomasGorisse/profile-private/credentials/quota/
  upload-keystore.jks          (600)
  upload-keystore.properties   (600)  — the four values, for recovery
```

`upload-keystore.properties` is a mirror, not the source: the build reads the same four
names from `~/.gradle/gradle.properties`, which already holds them:

| Property | Meaning |
|---|---|
| `QUOTA_UPLOAD_STORE_FILE` | absolute path to the `.jks` |
| `QUOTA_UPLOAD_STORE_PASSWORD` | keystore password |
| `QUOTA_UPLOAD_KEY_ALIAS` | `upload` |
| `QUOTA_UPLOAD_KEY_PASSWORD` | key password |

`app/build.gradle.kts` reads them through a `secret()` helper that falls back to the
environment (which is how CI supplies them). When **any** of the four is missing the
`release` signing config is simply not created and the release build stays unsigned, so a
fresh clone still builds. `sync-play.sh` and the workflow both refuse to upload an unsigned
bundle, so that fallback can never reach Play by accident.

Play App Signing holds the real app signing key; this is only the upload envelope. Losing
the upload key is recoverable through Play support. Losing an *unenrolled* app signing key
is not — which is why enrolling at app creation (step 4) is not optional.

Certificate SHA-256, for cross-checking against what the Play Console reports after the
first upload:

```
9B:04:1C:F7:AB:3C:91:76:86:BB:FF:73:D4:43:A1:F7:32:EA:AA:9C:BC:EB:0B:2A:2E:81:81:11:F7:0A:AF:0B
```

## 2. Versioning

`versionName` is `1.0.0`, overridable with `-PVERSION_NAME`. `versionCode` comes from
`-PVERSION_CODE` and defaults to `3` — the code of the first build carrying sample-data
mode, and the one the internal track serves. The workflow passes epoch-minutes
(`$(date +%s) / 60`), which is monotonic and cannot collide with a code Play has already
accepted — Play never forgets a versionCode, including one from a deleted release.

## 3. Shipping

### Locally

```bash
android/scripts/sync-play.sh --dry-run          # checks auth + versionCode, uploads nothing
android/scripts/sync-play.sh --draft            # the FIRST upload, see step 4
android/scripts/sync-play.sh --version-code 2   # subsequent uploads
```

`sync-play.sh` builds `:app:bundleRelease`, then hands the AAB to `scripts/play_upload.py`,
which talks to the Play Developer API with stdlib `urllib` and the system `openssl` for the
RS256 JWT — modelled on ar-model-viewer's `store/scripts/sync-play.py`, so there is nothing
to `pip install`. It refuses to run on a dirty tree, refuses a `versionCode` already on
Play, refuses an unsigned AAB, and refuses any track but `internal`: promoting to production
stays a click in the console.

The service-account JSON is read from `PLAY_SERVICE_ACCOUNT_JSON`, defaulting to the one
already on this machine:

```
~/Projects/ThomasGorisse/profile-private/credentials/arcamera/play-service-account.json
```

That account (`arcamera-publisher@arcamera-4421f.iam.gserviceaccount.com`) currently has
access to AR Model Viewer and Will It Fit only. It must be invited to this app too — see
step 4.

### From a tag

`.github/workflows/android-release.yml` runs on `android-v*`:

```bash
git tag android-v1.0.0 && git push origin android-v1.0.0
```

It decodes the keystore from a secret, builds, verifies the bundle is signed, uploads the
AAB as a run artifact, and pushes to the **internal** track. `workflow_dispatch` is
available with a `dry_run` input (default true) to exercise the build and signing without
uploading. Concurrency group `android-release`, never cancelled in flight: Play issues one
edit at a time per app, and two racing runs both lose.

Repository secrets it expects:

| Secret | How to produce it |
|---|---|
| `QUOTA_UPLOAD_KEYSTORE_BASE64` | `base64 -i <path>/upload-keystore.jks \| pbcopy` |
| `QUOTA_UPLOAD_STORE_PASSWORD` | from `upload-keystore.properties` |
| `QUOTA_UPLOAD_KEY_ALIAS` | `upload` |
| `QUOTA_UPLOAD_KEY_PASSWORD` | from `upload-keystore.properties` |
| `PLAY_SERVICE_ACCOUNT_JSON` | the whole service-account JSON, pasted as plain text |

## 4. What is still manual — the Play Console

The API cannot create an app, and cannot make the first release of a never-published one.
In order:

1. **Play Console → All apps → Create app.** Name "Quota", English (United States), App,
   Free. Package `com.gorisse.thomas.quota` — this is fixed at creation and can never be
   changed.
2. **Enrol in Play App Signing** (offered during setup; accept the default, "let Google
   generate the app signing key"). Not reversible later without a new listing.
3. **Upload the first AAB by hand**, from
   `android/app/build/outputs/bundle/release/app-release.aab`, under
   Testing → Internal testing → Create new release. Play refuses any status but `draft`
   from the API on an app that has never been published, and the first country selection
   and review can only be sent from the console.
4. **Grant the service account access:** Users and permissions → Invite new user →
   `arcamera-publisher@arcamera-4421f.iam.gserviceaccount.com` → select this app → grant
   *View app information* and *Release to testing tracks*. Until this is done every API
   call returns 403, and `sync-play.sh` says so explicitly.
5. Fill the store listing from `android/play/` (below), plus content rating and the data
   safety form. Data safety must match the flow, which is not "nothing leaves the
   device": the collector sends a percentage per provider and a reset time to the relay
   at `quota.sceneview.dev`, keyed by a random pairing identifier, and the app reads them
   back with a pairing token. Declare that as app activity / other in-app actions,
   transferred and not linked to an identity — no prompts, no file contents, no token
   counts, no account identifiers, and nothing that names the user or the machine. No
   analytics, no advertising ID, no crash SDK, no third-party tracker. The pairing token
   lives in Android's encrypted storage. Answer "yes" to data in transit encryption and
   to deletion on request: unpairing drops it.

After that, `sync-play.sh` and the tag workflow both work unattended for the internal track.

## 5. Listing assets

`android/play/` holds everything the listing needs, in a fastlane-ish layout:

```
play/
  listing/en-US/
    title.txt              "Quota — AI usage at a glance"   (28 / 30 chars)
    short-description.txt                                    (78 / 80)
    full-description.txt                                     (2 467 / 4 000)
    icon.png               512x512
    feature-graphic.png    1024x500
  screenshots/phone/
    01-home-light.png  02-home-dark.png  03-home-error.png   (1080x2400, 9:16)
```

The icon and the feature graphic are generated, not hand-drawn:

```bash
python3 android/scripts/generate-play-assets.py
```

That script is the single source of the mark — a ring three-quarters full, severity `ok`
green on background dark, straight from `docs/DESIGN.md`. It also writes
`app/src/main/res/mipmap-*/ic_launcher_foreground.png`, so the launcher icon and the store
icon are literally the same drawing and cannot drift. Re-run it after any palette change.

Play wants at least two phone screenshots, 16:9 or 9:16, 320–3840 px; the three committed
1080×2400 shots qualify as they are.
