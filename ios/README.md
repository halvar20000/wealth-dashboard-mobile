# Wealth Dashboard — the iOS app

Everything on the parity list: pairing, the overview (with the classes
that count, eight return windows and the quotes-only refresh), the
portfolio with its chart and ring, accounts and their rows, the swipe
triage for categories and for whose spending, cash flow, transactions
with search, the Home-screen widget, the share extension, the
background round with its notices, the quick action and the unlock.

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
| Charts on a `Canvas`, ~50 lines | SwiftUI `Path`, or Swift Charts if it earns its place |

## Opening it

`ios/Wealth.xcodeproj`, Xcode 16 or newer, iOS 17 and up. No packages,
no CocoaPods. Every folder is a synchronised group: a file dropped into
one is in its target, with no project edit. `Shared/` belongs to the
app, the widget and the share extension alike.

To run it on a phone, pick your team under **Signing & Capabilities**
for all three targets. The bundle ids are `com.herbrig.wealthdashboard`
and, for the extensions, `….widget` and `….share`.

```
Shared/        Api (the HTTP surface), Models, Store (Keychain + cache), Format
Wealth/
  Data/        AppModel (the order of things), Round (the background round)
  UI/          one view per screen, Charts (a line and a ring, by hand)
WealthWidget/  the net worth on the Home screen, drawn from the cache
WealthShare/   a statement from the share sheet into an account
WealthTests/   the contract where it can be checked without a dashboard
Info.plist     only what build settings cannot say: local http, Face ID,
               the quick action, the background round, no export question
Wealth.entitlements   one Keychain group for all three targets
```

The widget and the share extension read the app's Keychain through one
shared access group (`Wealth.entitlements`). That is Keychain sharing,
not an App Group, so nothing has to be registered in the developer
portal: the extensions' bundle ids are created by Xcode's cloud signing
on the first archive, like the app's was.

## CI and TestFlight

`.github/workflows/ios.yml` builds and tests on a simulator, unsigned,
whenever a push or pull request touched `ios/`. A Linux job decides in
about fifteen seconds whether a Mac is needed at all. The repository is
public, so the minutes are free either way — the filter is there so a
build you are waiting for is not queued behind one nobody needed. It
expects the scheme **`Wealth`**, which is shared in the project.

A `v1.2.3` tag also archives the app as marketing version 1.2.3, build
10203, and uploads it to TestFlight — once these four secrets exist
(Settings → Secrets and variables → Actions), and never in the
repository itself:

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
