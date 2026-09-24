# Wealth Dashboard — the phone apps

Two clients for [Wealth Dashboard](https://github.com/halvar20000/wealth-dashboard),
the self-hosted household finance app: one Android, one iOS, kept
deliberately at feature parity. Neither is a wrapper around the
dashboard's website — that already works on a phone. These are for what
a browser cannot do: figures on the home screen, a statement shared
straight from the bank's app, the categorisation queue as a card stack
you clear with one thumb on a train, a notice when a sync fails.

```
android/     the Android app (Kotlin, Compose)      → android/README.md
ios/         the iOS app (Swift, SwiftUI)           → ios/README.md
CONTRACT.md  what the dashboard answers, and the rules both apps keep
PARITY.md    who has which feature, and what is next
```

## The two of us

- **Android** — halvar20000
- **iOS** — his son

**Parity is the point.** [CONTRACT.md](CONTRACT.md) is the single
source for anything that touches the dashboard: endpoints, JSON shapes,
error handling, and the seven rules both apps follow (the cache is the
screen; a verdict is written before it is sent; nothing leaves the
phone except to the paired dashboard; …). If the two apps disagree on
one of those, one of them is wrong — and the fix belongs in whichever
app drifted, not in the contract.

[PARITY.md](PARITY.md) is the score. Tick your box in the same commit
that ships the feature. A row where one column is `—` is the other
platform's next issue; open it as one.

## How we work

- `main` is always releasable. Work on a branch, open a pull request,
  and merge it yourself once CI is green — a review is welcome but
  never a gate between the two of us.
- **One tag ships both apps.** `v1.2.3` builds the Android release and
  should build the iOS one; see the end of CONTRACT.md. If a feature is
  only ready on one side, ship the same number anyway and say in
  PARITY.md what is missing where.
- A commit message says **why**, not what — the diff already says what.
- Neither app gets a dependency it does not need. The charts are drawn
  by hand rather than pulled in as a library; the Android app has no
  analytics, no crash reporter, and iOS should have none either.

## Getting a dashboard to test against

You need one running — a NAS, a Pi, a laptop. `docker run` and the rest
are in the [dashboard's README](https://github.com/halvar20000/wealth-dashboard#install).
Pair the app against it with the six-digit code from **Settings →
Assistants**. The dashboard's demo data is enough to see every screen.

## Licence

AGPL-3.0, the same as the dashboard.
