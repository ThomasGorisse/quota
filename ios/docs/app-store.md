# Submitting Quota to the App Store

What is already done in this repository, and the exact list of what is not. Nothing below is
guesswork about which button to press: every step names the portal page or the API endpoint.

## Already done here

- **Team id.** `DEVELOPMENT_TEAM: 5G3DZ3TH45` in [`project.yml`](../project.yml) under
  `settings.base`, so all four targets inherit it. It is the same Apple Developer team the
  other apps on this account ship under (AR Model Viewer, the SceneView demo). Re-run
  `xcodegen generate` after any change to that file.
- **Signing style** is `Automatic`. See the caveat under *Provisioning* below — the two other
  apps on this team export with `signingStyle: manual`, because the App Store Connect API key
  in use has no certificate-or-profile management right.
- **App icon**: a single 1024 × 1024 asset in `Resources/App/Assets.xcassets` and
  `Resources/Watch/Assets.xcassets`. Xcode generates every other size from it.
- **`ITSAppUsesNonExemptEncryption: false`** and `NSCameraUsageDescription` are in
  `Support/Quota-Info.plist`, so neither is asked again at upload.
- **Listing copy and screenshots**: [`../store/en-US/`](../store/en-US) and
  [`../store/screenshots/6.9/`](../store/screenshots) — see *Screenshots* below for what is
  still missing there.

## 1. Register four App IDs

Developer portal → Certificates, Identifiers & Profiles → Identifiers → **explicit** App IDs,
not a wildcard, because they need capabilities:

| Bundle id | Target |
|---|---|
| `com.gorisse.thomas.quota` | the phone app |
| `com.gorisse.thomas.quota.widgets` | the WidgetKit extension |
| `com.gorisse.thomas.quota.watchkitapp` | the watch app |
| `com.gorisse.thomas.quota.watchkitapp.widgets` | the watch complications |

## 2. One App Group, enabled on all four

`group.com.gorisse.thomas.quota`, created once under Identifiers → App Groups, then ticked on
each of the four App IDs. It is what lets a widget draw before its first fetch.

**Keychain Sharing** on the three App IDs that read the device token — the phone app, the
widget extension and the watch app. Until both exist, `TokenStore` cannot resolve its keychain
access group, falls back to the App Group `UserDefaults`, and Settings → Storage says so out
loud. That fallback works, but it is not what should ship.

The entitlements files in `Support/` already declare both; the portal side is the missing half.

## 3. Provisioning

With automatic signing and the team set, Xcode generates the four profiles on first archive.
If it refuses — the usual cause is a missing capability on one of the four App IDs — the
worked-around path used by the other apps on this team is manual export: an
`ExportOptions.plist` with `signingStyle: manual` and each profile named or referenced by
UUID. That plist does not exist here yet and is only needed if automatic signing fails.

## 4. The App Store Connect record

**An API key already exists on this machine.** Yes:
`~/Projects/ThomasGorisse/profile-private/credentials/arcamera/AuthKey_<KEYID>.p8`, symlinked
into `~/.appstoreconnect/private_keys/`. Its key id, issuer id and path are recorded as
`ASC_KEY_ID`, `ASC_ISSUER_ID` and `ASC_KEY_PATH` in `credentials/arcamera/asc-api.env` in that
same private repo. Never print the key, never copy it into this repository, never commit it.

So the record can be created over the API rather than by clicking:

```bash
set -a; . ~/Projects/ThomasGorisse/profile-private/credentials/arcamera/asc-api.env; set +a
# POST https://api.appstoreconnect.apple.com/v1/apps with a JWT signed by that key:
#   bundleId: com.gorisse.thomas.quota
#   name: Quota            (must be unique across the whole store)
#   primaryLocale: en-US
#   sku: QUOTA-IOS-1
```

The watch app ships inside the iOS app and needs no record of its own.

Category: **Developer Tools**, secondary **Utilities**. Age rating: 4+, every question "None".

Two things that can only be settled by trying:
- The name **Quota** may already be taken on the store. The fallback the listing copy still
  works with is *Quota — AI limits*.
- Anything about a price or an in-app purchase: there is none, so the app is Free with no IAP.

## 5. Privacy answers

App Privacy → **Data Not Collected** is *not* strictly accurate and should not be claimed: the
app does send something. Answer it as follows.

- **Data collected: Usage Data → Other Usage Data.** What travels is a percentage of the
  Claude and Codex limits and the reset times, nothing else.
- Purpose: **App Functionality**. It is the entire product.
- **Linked to the user: No.** The pairing id is random and carries no account, no email, no
  device identifier.
- **Used for tracking: No.** No advertising identifier, no data broker, no cross-app or
  cross-site correlation, no third-party SDK at all.
- Everything else — contact info, identifiers, diagnostics, location, contacts, purchases,
  browsing, search history — **not collected**.
- Camera: used only to read the pairing QR, no image stored, no data derived from it leaves
  the device. That is a permission, not a privacy-label entry.

Review notes should say plainly that the app is useless without the open-source collector at
`github.com/ThomasGorisse/quota`, and give the reviewer a working pairing URL for a demo
backend, or point them at the **Demo data** switch in Settings, which fills every screen
without any network at all. The second is the safer answer: no credential in the review notes.

## 6. Screenshots

Required: the **6.9-inch iPhone** set, 1320 × 2868. Four are in
[`../store/screenshots/6.9/`](../store/screenshots/6.9): home dark, home light, the widget
preview, Settings. They come off the same iPhone 17 Pro Max simulator as the development
captures, at its native resolution, so they upload as-is.

Still missing, and they cannot be produced from this repository alone:
- **A watch set.** The app ships a watchOS app, so Apple asks for Apple Watch screenshots.
  `docs/screenshots/watch-dark.png` is 416 × 496, straight off the Series 11 46 mm simulator;
  check against the size Apple asks for at upload time before uploading it as-is.
- Apple no longer requires the 6.5-inch set when a 6.9-inch one is provided; if the upload
  form still asks, the same captures scale.

## 7. The two things that could come back from review

- **The provider marks.** The Claude spark and the OpenAI mark come from simple-icons and are
  drawn as monochrome templates in the provider accent, never in a brand's own palette — see
  `docs/brand/NOTICE.md`. Third-party use of another company's marks is a judgement call at
  review. Falling back to a plain accent dot is a one-line change in `BrandMark`.
- **"Minimum functionality"**, since the app is a viewer for a service the user runs. The
  widgets, the watch app and the complications are the counter-argument, and the Demo data
  switch lets a reviewer see all of it without setting anything up.

## 8. Before uploading anything

- **A physical device pass.** Everything here was validated on simulators, and
  `BGAppRefreshTask` never fires on one: the background refresh path has only been exercised by
  calling it directly. It has to run on a real phone and a real watch once.
- The keychain path only exists on a signed build — re-check Settings → Storage on the device
  and confirm it stops saying "App Group UserDefaults".
- Bump `CURRENT_PROJECT_VERSION` in `project.yml` for every upload; `MARKETING_VERSION` stays
  `1.0.0` for the first submission.
