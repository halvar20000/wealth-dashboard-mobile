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
| 1 | Pairing with a six-digit code | ✅ 0.1.0 | ✅ 1.2.1 | `POST /api/v1/pair`; token to the encrypted store |
| 2 | Overview: net worth, its parts, return per period | ✅ 0.1.0 | ✅ 1.2.1 | one `snapshot` call |
| 3 | Net-worth line chart, six windows | ✅ 1.1.0 | ✅ 1.2.2 | `net_worth_history`, drawn by hand (Canvas / SwiftUI Path) |
| 3a | Net worth without chosen asset classes | ✅ 1.2.0 | ✅ 1.2.1 | tick boxes over `by_class`, kept on the phone |
| 3b | Performance: all eight windows without scrolling | ✅ 1.2.0 | ✅ 1.2.1 | two rows of four |
| 3c | Refresh quotes and FX without a bank sync | ✅ 1.2.0 | ✅ 1.2.1 | `refresh_market`, dashboard ≥ 0.73.0 |
| 3d | Whose figures: everyone, or one person's accounts | ✅ 1.2.5 | ✅ 1.2.5 | `person` on every tool that takes it, the switch fed by `people`; hidden when the dashboard knows nobody |
| 4 | Accounts, and the rows behind one | ✅ 0.1.0 | ✅ 1.2.1 | inside the Portfolio tab |
| 5 | Transactions: list and search | ✅ 0.1.0 | ✅ 1.2.1 | `transactions` with `q` |
| 6 | Portfolio: holdings with value, gain and return | ✅ 1.1.0 | ✅ 1.2.2 | `holdings` + `performance` |
| 7 | Allocation ring by asset class | ✅ 1.1.0 | ✅ 1.2.2 | `allocation` |
| 8 | Swipe triage — categories | ✅ 0.2.0 | ✅ 1.2.2 | offline queue, flush in order |
| 8a | Edit the rule a swipe creates | ✅ 1.2.0 | ✅ 1.2.2 | the pattern, and a switch for "this row only" |
| 9 | Swipe triage — whose spending | ✅ 0.2.0 | ✅ 1.2.2 | needs dashboard ≥ 0.72.4 |
| 10 | Share a statement into an account | ✅ 0.1.0 | ✅ 1.2.2 | Android share sheet / iOS share extension |
| 10a | "Open with" a downloaded file, not only "share" | ✅ 1.2.3 | ✅ 1.2.4 | Android: ACTION_VIEW on content:// and file://. iOS: `CFBundleDocumentTypes` plus `LSSupportsOpeningDocumentsInPlace`, so a download in Safari or Files offers Wealth |
| 10b | Undo an import from the app | ✅ 1.2.4 | ✅ 1.2.4 | `undo_import`, dashboard ≥ 0.74.0; the button sits in the report after a share |
| 11 | Home-screen widget: net worth | ✅ 0.2.0 | ✅ 1.2.2 | Glance / WidgetKit, drawn from the cache |
| 12 | Background round + local notices | ✅ 0.2.0 | ✅ 1.2.2 | WorkManager / BGAppRefreshTask, every few hours |
| 13 | Quick tile "sync now" | ✅ 0.2.0 | n/a | iOS has no equivalent; a Control (iOS 18) is the nearest |
| 14 | Shortcut straight into triage | ✅ 0.2.0 | ✅ 1.2.2 | app shortcut / Home-screen quick action |
| 15 | Unlock with the device's own lock | ✅ 0.1.0 | ✅ 1.2.2 | BiometricPrompt / LocalAuthentication |
| 16 | German, French, Spanish | 🚧 German 1.2.6 | — | the dashboard has all three. The app follows the phone's language; a string added in English needs its German in the same pull request (Android: `values-de/strings.xml`). French and Spanish not started |
| 17 | Cash flow: months, categories, what is left | ✅ 1.2.0 | ✅ 1.2.1 | `cashflow`, dashboard ≥ 0.73.0 |
| 18 | Wear / watchOS complication | — | — | nobody has asked yet |

## What the dashboard needs for each

A client feature that needs a dashboard newer than the user's must hide
itself rather than fail. Current floors:

- row 10b (undo an import) — dashboard **0.74.0**
- rows 3c and 17 (market refresh, cash flow) — dashboard **0.73.0**
- row 9 (whose spending) — dashboard **0.72.4**
- everything else — **0.60** and up

## Where the numbers come from

Both apps are versioned by the same tag; see the end of
[CONTRACT.md](CONTRACT.md). "✅ 1.1.0" means it shipped in `v1.1.0`.
