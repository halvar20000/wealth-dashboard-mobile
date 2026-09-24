# Wealth Dashboard — the iOS app

Nothing here yet. This page is the brief.

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

## Signing and the store

Apple's side is yours: the developer account, the signing certificate
and the provisioning profile. Put what CI needs in the repository's
secrets, as the Android side does (`KEYSTORE_BASE64` and friends) — and
never in the repository itself.

`.github/workflows/ios.yml` is a stub that does nothing until there is
a project here. It runs on a macOS runner, which on a **private**
repository bills at ten times the Linux rate against the free monthly
minutes; that is why the stub exits early rather than building on every
push. Decide what it should do once there is something to build.
