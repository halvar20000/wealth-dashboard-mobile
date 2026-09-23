# Wealth Dashboard — Android

A phone client for [Wealth Dashboard](https://github.com/halvar20000/wealth-dashboard),
the self-hosted household finance app. Not a wrapper around its website:
the website already works on a phone. This is for the things a browser
cannot do — the figures on the home screen, a statement shared straight
from the bank's app, the categorisation queue as a card stack, a
notification when a sync fails.

**Where it is:** phase 1, plus the share sheet. Pairing, the overview
with the performance strip, the accounts, the transactions, a lock,
figures that survive a tunnel going down — and a statement shared from
any app straight into an account. The rest is listed below.

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

## Sharing a statement into an account

Open the statement in the bank's app, in a file manager or in
Paperless, **Share → Import into Wealth**, and pick the account. The
file goes to the dashboard as it is; the dashboard reads it with the
same readers the import page uses and answers with the same report —
how many rows were new, how many it already had, and, where it applies,
why some were left out or that the file is a scan with no text in it.

Several files at once work too. Nothing is stored on the phone: the
bytes are read while the share is open and sent.

## Triage: the queue, one thumb

**Triage** is the tab in the middle. It deals with the two queues the
dashboard keeps — the rows with no category, and the spending nobody in
the household has claimed — as a stack of cards, one at a time:

- **swipe right** takes the app's own guess (and remembers it as a rule,
  the same rule the dashboard would have made),
- **swipe left** leaves the row in the queue for another day,
- **tap** opens the sheet: every category, the guess first and the ones
  you actually use next; or the household plus *Shared*.

The switch at the top turns the stack from *Category* to *Whose*.

A verdict is written to the phone's encrypted store **before** it is
sent, so the thumb never waits for the network — which is the point of
doing this on a train. What is waiting goes out in order the next time
the app has a network, oldest first; **Undo last** takes back a verdict
that has not left the phone yet. The header counts both: *N left · M to
send*.

The *Whose* queue needs a dashboard on **0.72.4 or newer** (the `people`
and `unowned_spending` tools); the category queue works with any.

## The home screen

Long-press the home screen, **Widgets → Wealth**: net worth, today's
and the month's return, and how many rows are waiting to be filed.

It draws the app's own cache and asks the dashboard for nothing of its
own — a widget that wakes the radio every half hour costs battery for a
figure that moves once a day. The app redraws it after each refresh;
tapping the line at the bottom fetches a fresh one, and tapping the
figure opens the app.

## Still to come

- **Notifications** through your own ntfy: the dashboard's webhooks can
  already speak it.
- Quick-Settings tile for *sync now*, app shortcuts, Wear complication.

## Licence

AGPL-3.0, the same as the dashboard.
