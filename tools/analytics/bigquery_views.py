#!/usr/bin/env python3
"""Create or update the BigQuery views in tools/analytics/bigquery, in file-name order.

Usage: python3 tools/analytics/bigquery_views.py [--check] [service_account_key.json]

--check changes nothing: it checks that every event, screen, and entry the views name is in the catalog, and dry-runs
each view with the views it reads inlined, so new definitions are tried before they are applied.

The service account needs BigQuery User and BigQuery Data Editor on the project. Views are created in the same
location as the Analytics export, which has to exist first (the export writes its first table the day after linking).
"""

import argparse
import json
import os
import re
import sys
import urllib.error

import bigquery
from google_token import access_token, call, default_key_path

DATASET_API = f"{bigquery.API}/datasets/{bigquery.DATASET}"
CATALOG = os.path.join(os.path.dirname(os.path.abspath(__file__)), "catalog.json")
# Views whose SQL file was renamed or removed; they are dropped so they do not linger with old event names.
RETIRED_VIEWS = ["arrival_usefulness", "arrival_by_category", "arrivals", "sync_blocked_weekly"]
# Sent by Firebase itself rather than by the apps.
FIREBASE_EVENTS = {"first_open", "session_start", "user_engagement", "screen_view"}
NAMED = re.compile(r"\b(event_name|screen|entry)\s*(?:!=|=|NOT\s+IN|IN)\s*(\([^)]*\)|'[^']*')", re.IGNORECASE)


def ensure_dataset(token):
    try:
        call(token, "GET", DATASET_API)
        return
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
    call(token, "POST", f"{bigquery.API}/datasets", {
        "datasetReference": {"projectId": bigquery.PROJECT, "datasetId": bigquery.DATASET},
        "location": bigquery.LOCATION,
    })
    print(f"  created dataset {bigquery.DATASET} in {bigquery.LOCATION}")


def apply_view(token, name, query):
    table = {
        "tableReference": {"projectId": bigquery.PROJECT, "datasetId": bigquery.DATASET, "tableId": name},
        "view": {"query": query, "useLegacySql": False},
    }
    try:
        call(token, "POST", f"{DATASET_API}/tables", table)
        print(f"  created view {name}")
    except urllib.error.HTTPError as error:
        if error.code != 409:
            raise
        call(token, "PUT", f"{DATASET_API}/tables/{name}", table)
        print(f"  updated view {name}")


def drop_view(token, name):
    try:
        call(token, "DELETE", f"{DATASET_API}/tables/{name}")
        print(f"  dropped view {name}")
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise


# Names a view compares against that the apps never send, which would silently count nothing.
def unknown_names(sql, catalog):
    known = {
        "event_name": set(catalog["events"]) | FIREBASE_EVENTS,
        "screen": set(catalog["screens"]),
        "entry": set(catalog["entries"]),
    }
    unknown = []
    for column, values in NAMED.findall(sql):
        column = column.lower()
        for value in re.findall(r"'([^']*)'", values):
            if column == "event_name" and value.startswith("rc_"):
                continue
            if value not in known[column]:
                unknown.append(f"{column} '{value}'")
    return unknown


def check(token):
    catalog = json.load(open(CATALOG))
    views = bigquery.views()
    definitions = dict(views)
    failures = 0
    for name, sql in views:
        problems = unknown_names(sql, catalog)
        try:
            bigquery.dry_run(token, bigquery.inline(sql, definitions))
        except urllib.error.HTTPError as error:
            problems.append(json.loads(error.read()).get("error", {}).get("message", str(error.code)))
        if problems:
            failures += 1
            print(f"  FAIL  {name}: " + "; ".join(problems))
        else:
            print(f"  ok    {name}")
    return failures


def apply(token):
    ensure_dataset(token)
    failures = 0
    for name, sql in bigquery.views():
        try:
            apply_view(token, name, bigquery.view_sql(sql))
        except urllib.error.HTTPError as error:
            failures += 1
            print(f"  FAIL  view {name}: {error.code} {error.read().decode()}")
    for name in RETIRED_VIEWS:
        try:
            drop_view(token, name)
        except urllib.error.HTTPError as error:
            failures += 1
            print(f"  FAIL  drop view {name}: {error.code} {error.read().decode()}")
    return failures


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="check and dry-run the views without applying them")
    parser.add_argument("key", nargs="?", help="service account key")
    args = parser.parse_args()
    token, email = access_token(args.key or default_key_path(), bigquery.SCOPE)
    print(f"Using {email} on {bigquery.PROJECT}")
    if args.check:
        failures = check(token)
    else:
        failures = apply(token)
    print(f"Done with {failures} failure(s)")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
