# Play Store: what the listing says, and what is still needed

Everything here is text you can paste. What only you can do is marked
**you**.

## Listing

**App name** (30 characters)

```
Wealth Dashboard
```

**Short description** (80)

```
Your own Wealth Dashboard on your phone: figures, triage, statements.
```

**Full description** (4000)

```
Wealth Dashboard on your phone.

This app is the phone client for the Wealth Dashboard you run yourself —
on a NAS, a Raspberry Pi, a home server. It has no account, no cloud of
its own and no server in the middle: it talks to your dashboard and to
nothing else.

Pair once, with a six-digit code from the dashboard's settings, and the
phone has:

• Net worth, the day's and the month's return, your accounts and the
  rows behind them, with the day the figures were read printed on them.
• Triage — the queue of rows with no category, and the spending nobody
  in the household has claimed, as a stack of cards. Swipe right to
  take the app's own guess and remember it as a rule, left to leave it,
  tap to choose. It works with no network: what you decide is kept on
  the phone and sent when there is one.
• Share a statement into an account — from the bank's app, a file
  manager or Paperless. The dashboard reads it with the same readers
  the import page uses and answers with the same report.
• A home-screen widget with the net worth, drawn from what the app has
  already fetched, so it costs no battery of its own.
• A Sync now tile in Quick Settings, and Triage on a long-press of the
  icon.
• Optionally, a round every few hours that tells you when an account is
  about to run out, a bank link has stopped, or the queue has grown.

Your figures stay between your phone and your server. The app stores
the address, the token and the last figures in Android's encrypted
preferences; "Forget this dashboard" removes all of it. It never speaks
to a bank — your dashboard does that, on your own network.

The dashboard itself is free software (AGPL-3.0), and so is this app:
https://github.com/halvar20000/wealth-dashboard

You need a Wealth Dashboard to use this app. It is of no use on its
own.
```

**Category**: Finance. **Tags**: personal finance, budgeting.

**Privacy policy URL**

```
https://github.com/halvar20000/wealth-dashboard/blob/main/docs/PRIVACY-mobile.md
```

That link points at the **dashboard's** repository on purpose: it is
the address that will still be there when this repository is renamed or
reorganised, and Play does not like a policy URL that moves. The same
text lives here as `android/PRIVACY.md`, which is the copy we edit —
change it there, copy it across.

## Data safety form

- Does the app collect or share user data? **No.** Data stays on the
  device and goes only to the server the user runs.
- Is data encrypted in transit? **Yes** (whatever your dashboard's URL
  is — use https).
- Can users request deletion? **Yes** — *Forget this dashboard*, and
  the data is the user's own server's.

## Content rating

Everyone. No ads, no user-generated content, no purchases, no location.

## Before the first upload — **you**

1. **A keystore.** `keytool -genkeypair -v -keystore wealth.jks
   -keyalg RSA -keysize 4096 -validity 10000 -alias wealth`. Keep it and
   its passwords somewhere you will still have them in five years —
   Play Signing means losing it is survivable, but only just.
2. Put it in the repository's secrets, base64-encoded, as
   `KEYSTORE_BASE64`, with `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
   `KEY_PASSWORD`. Then a `v*` tag builds a signed release.
3. **Screenshots**: at least two phone screenshots, 16:9 or 9:16, at
   least 1080 px on the long side. Overview, Triage, an account, the
   widget on a home screen. Take them on your own phone — Play wants
   the real thing, and the figures are yours to blur or not.
4. A **feature graphic**, 1024 × 500.
5. In the Play Console: a new app, Finance, free, then the questionnaire
   above. First release goes to **internal testing**; install it on your
   own phone from there — that answers the "I cannot install an APK"
   problem without waiting for review.
