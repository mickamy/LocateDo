#!/usr/bin/env python3
"""Create or update the BigQuery views in tools/analytics/bigquery, in file-name order.

Usage: python3 tools/analytics/bigquery_views.py [service_account_key.json]

The service account needs BigQuery User and BigQuery Data Editor on the project. Views are created in
the same location as the Analytics export, which has to exist first (the export writes its first table
the day after linking).
"""

import glob
import os
import sys
import urllib.error

from google_token import access_token, call, default_key_path

PROJECT = "locatedo"
SOURCE_DATASET = "analytics_483038986"
DATASET = "locatedo_views"
LOCATION = "asia-northeast1"
API = f"https://bigquery.googleapis.com/bigquery/v2/projects/{PROJECT}"
SCOPE = "https://www.googleapis.com/auth/bigquery"


def ensure_dataset(token):
    try:
        call(token, "GET", f"{API}/datasets/{DATASET}")
        return
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
    call(token, "POST", f"{API}/datasets", {
        "datasetReference": {"projectId": PROJECT, "datasetId": DATASET},
        "location": LOCATION,
    })
    print(f"  created dataset {DATASET} in {LOCATION}")


def apply_view(token, name, query):
    table = {
        "tableReference": {"projectId": PROJECT, "datasetId": DATASET, "tableId": name},
        "view": {"query": query, "useLegacySql": False},
    }
    try:
        call(token, "POST", f"{API}/datasets/{DATASET}/tables", table)
        print(f"  created view {name}")
    except urllib.error.HTTPError as error:
        if error.code != 409:
            raise
        call(token, "PUT", f"{API}/datasets/{DATASET}/tables/{name}", table)
        print(f"  updated view {name}")


def main():
    key_path = sys.argv[1] if len(sys.argv) > 1 else default_key_path()
    token, email = access_token(key_path, SCOPE)
    print(f"Using {email} on {PROJECT}")
    ensure_dataset(token)

    failures = 0
    directory = os.path.join(os.path.dirname(os.path.abspath(__file__)), "bigquery")
    for path in sorted(glob.glob(os.path.join(directory, "*.sql"))):
        name = os.path.basename(path)[:-len(".sql")].split("_", 1)[1]
        query = (open(path).read()
                 .replace("__PROJECT__", PROJECT)
                 .replace("__SOURCE__", SOURCE_DATASET)
                 .replace("__DATASET__", DATASET))
        try:
            apply_view(token, name, query)
        except urllib.error.HTTPError as error:
            failures += 1
            print(f"  FAIL  view {name}: {error.code} {error.read().decode()}")

    print(f"Done with {failures} failure(s)")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
