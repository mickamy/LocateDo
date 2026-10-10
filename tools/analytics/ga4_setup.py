#!/usr/bin/env python3
"""Register LocateDo's GA4 custom definitions and set 14-month data retention.

Usage: python3 tools/analytics/ga4_setup.py [--archive-unused] [--property ID] [service_account_key.json]

The definitions come from ga4_definitions.json, generated from shared/analytics/catalog.yaml. The service account
needs the Editor role on the GA4 property, and the Google Analytics Admin API must be enabled in the key's Google
Cloud project. Safe to rerun: existing definitions are skipped. --archive-unused also archives definitions the catalog
no longer has, which GA4 cannot undo.
"""

import argparse
import json
import os
import sys
import urllib.error

from google_token import access_token, call, default_key_path

PROPERTY_ID = "483038986"
API = "https://analyticsadmin.googleapis.com/v1beta"
SCOPE = "https://www.googleapis.com/auth/analytics.edit"

DEFINITIONS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "ga4_definitions.json")


def list_all(token, path, field):
    items, page = [], ""
    while True:
        query = "pageSize=200"
        if page:
            query += f"&pageToken={page}"
        response = call(token, "GET", f"{API}/{path}?{query}")
        items += response.get(field, [])
        page = response.get("nextPageToken", "")
        if not page:
            return items


def archive_unused(token, dimensions, metrics, wanted_dimensions, wanted_metrics):
    failures = 0
    stale = [(d["name"], f"dimension {d['scope'].lower():5} {d['parameterName']}") for d in dimensions
             if (d["parameterName"], d["scope"]) not in wanted_dimensions]
    stale += [(m["name"], f"metric {m['parameterName']}") for m in metrics if m["parameterName"] not in wanted_metrics]
    for name, label in stale:
        try:
            call(token, "POST", f"{API}/{name}:archive", {})
            print(f"  archived {label}")
        except urllib.error.HTTPError as error:
            failures += 1
            print(f"  FAIL  archive {label}: {error.code} {error.read().decode()}")
    return failures


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--property", default=PROPERTY_ID)
    parser.add_argument("--archive-unused", action="store_true")
    parser.add_argument("key", nargs="?")
    args = parser.parse_args()
    definitions = json.load(open(DEFINITIONS))
    property_id = args.property
    token, email = access_token(args.key or default_key_path(), SCOPE)
    prop = f"properties/{property_id}"
    print(f"Using {email} on {prop}")

    failures = 0
    try:
        dimensions = list_all(token, f"{prop}/customDimensions", "customDimensions")
        metrics = list_all(token, f"{prop}/customMetrics", "customMetrics")
    except urllib.error.HTTPError as error:
        print(f"Could not read the property: {error.code} {error.read().decode()}")
        print("Check that the service account is an Editor on the property and the Admin API is enabled.")
        return 1

    existing_dimensions = {(d["parameterName"], d["scope"]) for d in dimensions}
    existing_metrics = {m["parameterName"] for m in metrics}

    wanted = [(name, "USER", f"User {name}") for name in definitions["user_dimensions"]]
    wanted += [(name, "EVENT", name) for name in definitions["event_dimensions"]]
    for name, scope, display in wanted:
        if (name, scope) in existing_dimensions:
            print(f"  skip  dimension {scope.lower():5} {name}")
            continue
        try:
            call(token, "POST", f"{API}/{prop}/customDimensions", {
                "parameterName": name, "displayName": display, "scope": scope,
            })
            print(f"  added dimension {scope.lower():5} {name}")
        except urllib.error.HTTPError as error:
            failures += 1
            print(f"  FAIL  dimension {scope.lower():5} {name}: {error.code} {error.read().decode()}")

    for metric in definitions["metrics"]:
        name, unit = metric["name"], metric["unit"]
        if name in existing_metrics:
            print(f"  skip  metric {name}")
            continue
        try:
            call(token, "POST", f"{API}/{prop}/customMetrics", {
                "parameterName": name, "displayName": name, "measurementUnit": unit, "scope": "EVENT",
            })
            print(f"  added metric {name} ({unit.lower()})")
        except urllib.error.HTTPError as error:
            failures += 1
            print(f"  FAIL  metric {name}: {error.code} {error.read().decode()}")

    if args.archive_unused:
        failures += archive_unused(
            token, dimensions, metrics,
            {(name, scope) for name, scope, _ in wanted},
            {metric["name"] for metric in definitions["metrics"]},
        )

    try:
        call(token, "PATCH", f"{API}/{prop}/dataRetentionSettings?updateMask=eventDataRetention", {
            "eventDataRetention": "FOURTEEN_MONTHS",
        })
        print("  set   event data retention to 14 months")
    except urllib.error.HTTPError as error:
        failures += 1
        print(f"  FAIL  data retention: {error.code} {error.read().decode()}")

    print(f"Done with {failures} failure(s)")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
