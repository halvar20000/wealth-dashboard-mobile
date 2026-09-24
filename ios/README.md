# Wealth Dashboard — the iOS app

Phase 1 is here: pairing, the overview, accounts and their rows,
transactions with search. The rest is issue #1 and its sub-issues.

Read [CONTRACT.md](../CONTRACT.md) first: it is what the dashboard
answers and the rules both apps keep. Then [PARITY.md](../PARITY.md)
for what Android already does — every ✅ there is a description of
something that works, and the Android source next door is a reference
implementation you are free to ignore where Swift has a better way.

## Suggested shape

The Android app is about 2 500 lines; the same job in SwiftUI should be
similar. What it does, and the nearest platform equivalent:

| Android | iOS |
|---|---|
| Kotlin + Jetpack Compose, Material 3 | Swift + SwiftUI |
| `EncryptedSharedPreferences` for token, cache, pending verdicts | Keychain (token) + an encrypted file or `FileManager` with `.completeFileProtection` |
| OkHttp + kotlinx.serialization | `URLSession` + `Codable` |
| `Repo` — cache first, then network, keep what came back | the same; one type that owns the order |
| Glance widget drawn from the cache | WidgetKit, same rule: no network of its own |
| WorkManager, a round every three hours | `BGAppRefreshTask` |
| Share sheet → `POST /accounts/{id}/import` | Share Extension, same endpoint |
| `BiometricPrompt` | `LocalAuthentication` |
| C## Opening it

`ios/Wealth.xcodeproj`, Xcode 16 or newer, iOS 17 and up. No packages,
no CocoaPods. The folders `Wealth/` and `WealthTests/` are synchronised
groups: a file dropped into them is in the target, with no project edit.

To run it on a phone, pick your team under **Signing & Capabilities**.
The bundle id is `com.herbrig.wealthdashboard`, the same as on Play.

```
Wealth/
  Data/   Api (the HTTP surface), Models, Store (Keychain + cache), AppModel
  UI/     one view per screen, Format (money, days, gain/loss colours)
WealthTests/   the contract where it can be checked without a dashboard
Info.plist     only what build settings cannot say: local http, no export question
```

## CI and TestFlight

`.github/workflows/ios.yml` builds and tests on a simulator, unsigned,
whenever a push or pull request touched `ios/` — a Linux job decides
first, because macOS minutes bill at ten times the Linux rate here.

A `v1.2.3` tag also archives the app as marketing version 1.2.3, build
10203, and uploads it to TestFlight — once these four secrets exist
(Settings → Secrets and variables → Actions):

| Secret | What |
|---|---|
| `APP_STORE_CONNECT_KEY_ID` | the key's id, from App Store Connect → Users and Access → Integrations → App Store Connect API |
| `APP_STORE_CONNECT_ISSUER_ID` | the issuer id shown above the list of keys |
| `APP_STORE_CONNECT_KEY_BASE64` | the downloaded `AuthKey_….p8`, as `base64 -i AuthKey_XXXX.p8` |
| `APPLE_TEAM_ID` | the ten-character team id, from developer.apple.com → Membership |

The key needs the **Admin** role: Xcode signs in the cloud with it,
so no certificate or profile lives in the repository or its secrets.
The app itself must exist in App Store Connect with the bundle id
above. Without the secrets a tag still builds and tests, and simply
skips the upload.
