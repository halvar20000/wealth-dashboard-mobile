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

`.github/workflows/ios.yml` waits for a project: a Linux job decides in
about fifteen seconds whether a Mac is needed at all, and starts one
only for a tag, a manual run, or a push that actually touched `ios/`.
The repository is public, so the minutes are free either way — the
filter is there so a build you are waiting for is not queued behind one
nobody needed.

It expects a scheme called **`Wealth`**. Name yours differently and
change that one line, or say so and it will be changed for you.
