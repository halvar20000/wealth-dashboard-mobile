#!/usr/bin/env python3
"""Writes the "What to Test" text of a TestFlight build.

    testflight_whats_new.py BUNDLE_ID VERSION BUILD NOTES_FILE

The key comes from the environment as in ios.yml: ASC_KEY_ID,
ASC_ISSUER_ID and KEY_PATH (the .p8 file). An upload shows up in App
Store Connect a few minutes after xcodebuild returns, so the build is
looked for until it appears. The text goes to every language the build
already has, and to the app's primary language when it has none yet;
a tester sees their own language or, failing that, the primary one.
"""
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

import jwt

API = "https://api.appstoreconnect.apple.com/v1"
WAIT_SECONDS = 30 * 60
LIMIT = 4000  # Apple's limit on whatsNew


def token():
    with open(os.environ["KEY_PATH"]) as f:
        key = f.read()
    now = int(time.time())
    return jwt.encode(
        {"iss": os.environ["ASC_ISSUER_ID"], "iat": now, "exp": now + 15 * 60,
         "aud": "appstoreconnect-v1"},
        key, algorithm="ES256",
        headers={"kid": os.environ["ASC_KEY_ID"], "typ": "JWT"})


def call(method, path, query=None, body=None):
    url = API + path
    if query:
        url += "?" + urllib.parse.urlencode(query)
    request = urllib.request.Request(
        url, method=method,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Authorization": "Bearer " + token(),
                 "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request) as response:
            text = response.read()
    except urllib.error.HTTPError as e:
        sys.exit(f"{method} {path} failed: {e.code} {e.read().decode(errors='replace')}")
    return json.loads(text) if text else {}


def main():
    bundle_id, version, build, notes_file = sys.argv[1:5]
    with open(notes_file) as f:
        notes = f.read().strip()
    if not notes:
        print("No notes: nothing to write.")
        return
    if len(notes) > LIMIT:
        notes = notes[:LIMIT - 1].rstrip() + "…"

    apps = call("GET", "/apps", {"filter[bundleId]": bundle_id})["data"]
    if not apps:
        sys.exit(f"No app with bundle ID {bundle_id} in App Store Connect.")
    app = apps[0]

    deadline = time.time() + WAIT_SECONDS
    while True:
        builds = call("GET", "/builds", {
            "filter[app]": app["id"],
            "filter[version]": build,
            "filter[preReleaseVersion.version]": version,
            "limit": 1})["data"]
        if builds:
            break
        if time.time() > deadline:
            sys.exit(f"Build {version} ({build}) did not show up in App Store Connect.")
        print(f"Build {version} ({build}) not in App Store Connect yet; waiting.")
        time.sleep(30)
    build_id = builds[0]["id"]

    localizations = call("GET", f"/builds/{build_id}/betaBuildLocalizations")["data"]
    for loc in localizations:
        call("PATCH", f"/betaBuildLocalizations/{loc['id']}", body={"data": {
            "type": "betaBuildLocalizations", "id": loc["id"],
            "attributes": {"whatsNew": notes}}})
        print(f"What to Test written for {loc['attributes']['locale']}.")
    if not localizations:
        locale = app["attributes"]["primaryLocale"]
        call("POST", "/betaBuildLocalizations", body={"data": {
            "type": "betaBuildLocalizations",
            "attributes": {"locale": locale, "whatsNew": notes},
            "relationships": {"build": {"data": {"type": "builds", "id": build_id}}}}})
        print(f"What to Test written for {locale}.")
    print(notes)


if __name__ == "__main__":
    main()
