# The contract

Both clients talk to one thing: a **Wealth Dashboard** the user runs
themselves. There is no server of ours in between, no account, no push
service. Everything below is what the dashboard already answers — the
Android client uses it today, and an iOS client that follows this page
will behave identically without either of us reading the other's code.

The dashboard is at <https://github.com/halvar20000/wealth-dashboard>;
its README documents the same endpoints from the server's side.

## Pairing

The user opens **Settings → Assistants** on their dashboard and reads a
six-digit code. The client posts it:

```http
POST {base}/api/v1/pair
Content-Type: application/json

{"code": "123456"}
```

```json
{"ok": true, "token": "…", "base_currency": "EUR",
 "server": {"name": "Zuhause", "version": "0.72.7"}, "mcp_url": "…"}
```

Codes last five minutes and one exchange. The token is a bearer token
with no expiry; **losing it must be survivable** — "forget this
dashboard" wipes it and pairing again costs six digits.

`{base}` is whatever the user typed (`http://nas.local:8000`,
`https://dashboard.example.com`). Accept both schemes; do not assume a
path prefix, but do not strip one either — a dashboard behind an
ingress lives at `/wealth/`.

## Every other call

```http
GET  {base}/api/v1/tools/{name}?arg=value
POST {base}/api/v1/tools/{name}      # JSON object of arguments
Authorization: Bearer {token}
```

The reply is always the same envelope:

```json
{"ok": true,  "result": {…}}
{"ok": false, "error": "A sentence a person can read."}
```

Status codes worth handling apart: **401** (token gone — offer to pair
again), **404** (tool unknown — the dashboard is older than the client;
degrade, do not crash), **422** (the tool refused: the row was deleted,
the category no longer exists — drop the request rather than retry
forever), **503** (the dashboard has no user yet).

`GET /api/v1/tools` lists every tool with its JSON schema. That listing
is the authority; the table below is what the clients actually use.

## The tools the clients use

| Tool | Gives | Used for |
|---|---|---|
| `snapshot` | net worth and its parts, return per period, accounts, what is due, how many rows wait, sync health | the overview, the widget, one round trip on start |
| `net_worth_history` | `points[]` of `{date, net_worth}` over `1m,3m,6m,ytd,1y,all` | the line chart |
| `holdings` | per security: isin, name, symbol, quantity, `net_invested`, price, `value_base`, accounts | the portfolio list |
| `allocation` | `dimensions.asset_class.rows[]` of `{key, value, share, target, drift}`, plus `cash`, `total` | the ring |
| `performance` | TWR/MWR for `all`, `ytd`, `1y`, and per holding under its ISIN | the return beside each position |
| `transactions` | rows, newest first; filters `q`, `account_id`, `category`, `kind`, dates, `limit` | the rows tab, search |
| `uncategorised` | `{remaining, transactions[]}`, each with the app's own `suggestion` and the `pattern` a rule would remember | the triage stack |
| `categories` | a **bare list** of `{slug, label, group, colour, transactions}` | the category sheet |
| `set_category` | files one row; `remember` (default true) also stores a rule from `pattern` | a swipe |
| `people` / `unowned_spending` | the household; the spending nobody claimed | the "whose" stack (dashboard ≥ 0.72.4) |
| `set_owner` | files a row to a person id or `"shared"` | a swipe |
| `apply_rules` | re-runs every rule over everything (dashboard ≥ 0.72.6) | after a fix |
| `cashflow` | income, spending and investment per month, the categories behind them, the average (dashboard ≥ 0.73.0) | the cash-flow page |
| `refresh_market` | quotes every holding again and refetches the ECB rates — no bank touched — and answers with the fresh net worth (dashboard ≥ 0.73.0) | the "did the markets move" button |
| `imports` / `undo_import` | what went into an account, and putting one file's rows back (dashboard ≥ 0.74.0) | the Undo after a share |

Import from the share sheet is not a tool but its own endpoint:

```http
POST {base}/api/v1/accounts/{id}/import    # multipart, field name "file"
```

It answers with the same report the web import page shows: how many
rows were new, how many were already there, and why any were left out.

## Rules both clients keep

These are decisions, not preferences. Where the two apps differ here,
one of them is wrong.

1. **The cache is the screen.** Draw the last snapshot immediately,
   then fetch. A refresh that fails leaves the figures alone and says
   when they were read — never an empty screen, never a spinner over
   yesterday's truth.
2. **A verdict is written down before it is sent.** Triage decisions go
   to encrypted storage first and flush in order when there is a
   network; a 4xx drops the verdict rather than blocking the queue.
   The thumb never waits for the radio.
3. **Money is formatted by the device's locale, in the dashboard's
   base currency**, with the day the figures were read shown next to
   them. Two decimals under 1 000, none above.
4. **Nothing leaves the phone except to the paired dashboard.** No
   analytics, no crash reporter, no advertising id. The token, the last
   figures and the pending verdicts live in the platform's encrypted
   store (EncryptedSharedPreferences / Keychain) and nowhere else.
5. **Unknown JSON fields are ignored, missing ones have defaults.** The
   two sides are updated separately; a client must start against a
   dashboard both older and newer than itself.
6. **A feature that needs a newer dashboard degrades quietly** — hide
   the tab, do not show an error. `server.version` from pairing and
   `snapshot` says what is there.
7. **Green is a gain, red is a loss**, and the same six windows appear
   in the same order everywhere: 1 M, 3 M, 6 M, YTD, 1 Y, all.
8. **What the figure at the top leaves out is the viewer's choice, and
   the phone's alone.** The dashboard has no opinion about whether a
   house counts; the client keeps the unticked classes locally and says
   what the total would be with everything in.
9. **Two refreshes, never confused.** `refresh_market` is quotes and
   rates and costs seconds; a bank sync is the dashboard's own job and
   costs minutes. A phone offers the first and does not pretend to
   offer the second.

## Versions

One tag ships both apps: `v1.2.3` builds Android `versionName 1.2.3`
(`versionCode 10203`) and should build the iOS marketing version 1.2.3.
Parity is the point — if only one platform is ready, the other ships
the same number with the feature missing from [PARITY.md](PARITY.md),
not a number of its own.
