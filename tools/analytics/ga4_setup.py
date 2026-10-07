#!/usr/bin/env python3
"""Register LocateDo's GA4 custom definitions and set 14-month data retention.

Usage: python3 tools/analytics/ga4_setup.py [property_id] [service_account_key.json]

The service account needs the Editor role on the GA4 property, and the Google Analytics Admin API
must be enabled in the key's Google Cloud project. Safe to rerun: existing definitions are skipped.
"""

import sys
import urllib.error

from google_token import access_token, call, default_key_path

PROPERTY_ID = "483038986"
API = "https://analyticsadmin.googleapis.com/v1beta"
SCOPE = "https://www.googleapis.com/auth/analytics.edit"

USER_DIMENSIONS = [
    "plan", "location_auth", "household_members", "place_count", "open_todo_count", "signed_in", "app_build",
    "promotions_consent",
]
EVENT_DIMENSIONS = [
    "source", "category", "via", "kind", "trigger", "from", "to", "notification_auth", "mode", "step", "reason", "result",
    "household_plan", "campaign_id", "action",
]
METRICS = {
    "place_count": "STANDARD",
    "open_todo_count": "STANDARD",
    "completed_todo_count_7d": "STANDARD",
    "places_with_open_todos": "STANDARD",
    "custom_category_count": "STANDARD",
    "household_members": "STANDARD",
    "days_since_install": "STANDARD",
    "open_todos": "STANDARD",
    "place_open_todos": "STANDARD",
    "age_days": "STANDARD",
    "assigned": "STANDARD",
    "signed_in": "STANDARD",
    "precise_location": "STANDARD",
    "promotions_consent": "STANDARD",
    "count": "STANDARD",
    "radius_m": "METERS",
    "age_hours": "HOURS",
    "duration_s": "SECONDS",
    "latency_s": "SECONDS",
}


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


def main():
    property_id = sys.argv[1] if len(sys.argv) > 1 else PROPERTY_ID
    key_path = sys.argv[2] if len(sys.argv) > 2 else default_key_path()
    token, email = access_token(key_path, SCOPE)
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

    wanted = [(name, "USER", f"User {name}") for name in USER_DIMENSIONS]
    wanted += [(name, "EVENT", name) for name in EVENT_DIMENSIONS]
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

    for name, unit in METRICS.items():
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
