# App Store release — 2026-09-11

Quota 1.0.0 (1) builds and archives for iPhone, iPad and Apple Watch. The signed archive and
App Store export succeeded on 2026-09-11 with Apple Distribution team `5G3DZ3TH45`.
App Store Connect app: `6810831245` (Quota — AI usage at a glance).

## Signing

Four explicit bundle IDs and their App Group already existed. Four App Store distribution
profiles were created and installed during this pass:

| Bundle | Profile |
|---|---|
| `com.gorisse.thomas.quota` | Quota App Store |
| `com.gorisse.thomas.quota.widgets` | Quota Widgets App Store |
| `com.gorisse.thomas.quota.watchkitapp` | Quota Watch App Store |
| `com.gorisse.thomas.quota.watchkitapp.widgets` | Quota Watch Widgets App Store |

`project.yml` selects manual distribution signing for Release; Debug keeps automatic signing.
[`../Support/ExportOptions.plist`](../Support/ExportOptions.plist) maps all four profiles.
The generated Xcode project reflects the YAML. No credentials or profiles are committed.

Every signed bundle contains the privacy manifest declaring UserDefaults use for app settings
and App Group sharing (CA92.1 / 1C8F.1), with no tracking. See
[Apple's required reason API documentation](https://developer.apple.com/documentation/bundleresources/app-privacy-configuration/nsprivacyaccessedapitypes/nsprivacyaccessedapitype).

## Artifacts from this pass

- Archive: `/tmp/Quota-1.0.0-fixed.xcarchive` (the one exported and uploaded)
- Export: `/tmp/Quota-AppStore-fixed2/Quota.ipa`
- Build log: `/tmp/quota-ios-verified.log`
- Archive log: `/tmp/quota-ios-archive.log`
- Export log: `/tmp/quota-ios-export.log`

All four targets compile; `codesign --verify --deep --strict` passes on the archive.
Final store submission/upload state is maintained by the coordinating release task, not this
local build document.

## Reviewer access

On first launch choose **Try sample data**. No pairing, account or network is needed to
review the dashboard. Settings offers **Preview widgets**, **Demo data** and **Left / Used**.
The setup link is [the public collector setup page](https://quota.sceneview.dev/setup.html),
so reviewers do not need access to the private source repository.

## What was corrected

- WatchConnectivity now transfers pairing, display mode, demo state and snapshots from phone
  to watch. App Groups share data only between an app and extensions on the same device.
- Keychain access uses the signed team prefix. Release refuses insecure credential storage;
  the preferences fallback is limited to Debug.
- Changing collector or demo mode clears the old cache; an in-flight request cannot restore
  data after unpairing or from another pairing context.
- Fractional USED values decode correctly. Selection uses the original value; display rounds.
- Provider-specific `stale` and `measured_at` preserve freshness across phone, widgets and Watch.
- Cards adapt to iPad columns and accessibility text sizes. Ring motion respects Reduce Motion.

## Physical-device verification still outstanding

Simulator screenshots and compilation do not verify real iPhone-to-Watch delivery, camera
scanning, or iOS-scheduled background refresh. These require a paired physical iPhone and
Apple Watch. No claim is made that these device-only paths were exercised in this pass.
