# Wealth Dashboard — Android

A phone client for [Wealth Dashboard](https://github.com/halvar20000/wealth-dashboard),
the self-hosted household finance app. Not a wrapper around its website:
the website already works on a phone. This is for the things a browser
cannot do — the figures on the home screen, a statement shared straight
from the bank's app, the categorisation queue as a card stack, a
notification when a sync fails.

**Where it is:** phase 1. Pairing, the overview with the performance
strip, the accounts, the transactions, a lock, and figures that survive
a tunnel going down. The rest is listed below.

## Pairing

Open the dashboard in a browser → **Settings → Assistants → Pair a
phone or a tablet** → a six-digit code appears, good for five minutes
and for one device. Type the dashboard's address and that code into the
app. The code is exchanged once for a token, which lives in the phone's
keystore; the dashboard can revoke it at any time under the same page.

A token is never shown on a screen or put in a QR, because a photograph
of either is the token.

## What it needs of the server

Version **0.71.0** or newer, which added:

| | |
|---|---|
| `POST /api/v1/pair` | a code for a token |
| `GET /api/v1/tools/snapshot` | the whole home screen in one round trip |
| `POST /api/v1/accounts/<id>/import` | a statement, read as the page reads it |

Everything else is the existing tool API, behind the same bearer token.

## Building

No Gradle wrapper is committed — a binary nobody can read is a binary
nobody can check. With Gradle 8.9 and JDK 17 on your machine:

```bash
gradle :app:assembleDebug        # app/build/outputs/apk/debug/
gradle wrapper                   # if you would rather have ./gradlew
```

Every push builds the debug APK in CI and hangs it off the run; a `v*`
tag builds a release APK and makes a GitHub release of it. To have CI
sign it, put a keystore in the repository's secrets as
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`;
without them the release APK is unsigned and you sign it yourself.

## Still to come

- **Share sheet**: receive a PDF or CSV from any app and post it to an
  account's import (the server endpoint is already there).
- **Home-screen widgets** (Glance): net worth, the performance strip,
  what is due next.
- **Swipe triage**: the uncategorised queue and *Who spent*, one thumb,
  offline, flushed when the phone is back on the network.
- **Notifications** through your own ntfy: the dashboard's webhooks can
  already speak it.
- Quick-Settings tile for *sync now*, app shortcuts, Wear complication.

## Licence

AGPL-3.0, the same as the dashboard.
