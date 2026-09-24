# Parity

One table, two apps. A feature is **done** only when both columns say
so; until then the release notes say what is missing where, and nobody
has to read the other platform's code to find out.

**How to use this page.** Open a pull request that ticks your box in
the same commit that ships the feature. If you build something the
other platform does not have yet, add the row and leave the other
column at `—`; that row is then the other's next issue.

Legend: **✅** shipped · **🚧** in progress · **—** not started ·
**n/a** platform has no such thing.

| # | Feature | Android | iOS | Notes |
|---|---|---|---|---|
| 1 | Pairing with a six-digit code | ✅ 0.1.0 | 🚧 | `POST /api/v1/pair`; token to the encrypted store |
| 2 | Overview: net worth, its parts, return per period | ✅ 0.1.0 | 🚧 | one `snapshot` call |
| 3 | Net-worth line chart, six windows | ✅ 1.1.0 | — | `net_worth_history`, drawn by hand (Canvas / SwiftUI Path) |
| 4 | Accounts, and the rows behind one | ✅ 0.1.0 | 🚧 | inside the Portfolio tab |
| 5 | Transactions: list and search | ✅ 0.1.0 | 🚧 | `transactions` with `q` |
| 6 | Portfolio: holdings with value, gain and return | ✅ 1.1.0 | — | `holdings` + `performance` |
| 7 | Allocation ring by asset class | ✅ 1.1.0 | — | `allocation` |
| 8 | Swipe triage — categories | ✅ 0.2.0 | — | offline queue, flush in order |
| 9 | Swipe triage — whose spending | ✅ 0.2.0 | — | needs dashboard ≥ 0.72.4 |
| 10 | Share a statement into an account | ✅ 0.1.0 | — | Android share sheet / iOS share extension |
| 11 | Home-screen widget: net worth | ✅ 0.2.0 | — | Glance / WidgetKit, drawn from the cache |
| 12 | Background round + local notices | ✅ 0.2.0 | — | WorkManager / BGAppRefreshTask, every few hours |
| 13 | Quick tile "sync now" | ✅ 0.2.0 | n/a | iOS has no equivalent; a Control (iOS 18) is the nearest |
| 14 | Shortcut straight into triage | ✅ 0.2.0 | — | app shortcut / Home-screen quick action |
| 15 | Unlock with the device's own lock | ✅ 0.1.0 | — | BiometricPrompt / LocalAuthentication |
| 16 | German, French, Spanish | 🚧 partial | — | the dashboard has all three; the clients are mixed |
| 17 | Wear / watchOS complication | — | — | nobody has asked yet |

## What the dashboard needs for each

A client feature that needs a dashboard newer than the user's must hide
itself rather than fail. Current floors:

- row 9 (whose spending) — dashboard **0.72.4**
- everything else — **0.60** and up

## Where the numbers come from

Both apps are versioned by the same tag; see the end of
[CONTRACT.md](CONTRACT.md). "✅ 1.1.0" means it shipped in `v1.1.0`.
