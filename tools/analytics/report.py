#!/usr/bin/env python3
"""Read the BigQuery views and write an HTML report to tmp/analytics/report.html, then open it.

Usage: python3 tools/analytics/report.py [--no-open] [service_account_key.json]

Every view except the row-level ones (events, daily_state, users) goes into the page, so the report is the one place
to look; the page draws the charts and lets any view be read as a table.
"""

import argparse
import datetime
import json
import os
import sys
import webbrowser

import bigquery
from google_token import REPO_ROOT, access_token, default_key_path

OUTPUT = os.path.join(REPO_ROOT, "tmp", "analytics", "report.html")
TEMPLATE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "report_template.html")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--no-open", action="store_true", help="write the page without opening it")
    parser.add_argument("key", nargs="?", help="service account key")
    args = parser.parse_args()
    token, email = access_token(args.key or default_key_path(), bigquery.SCOPE)
    print(f"Using {email} on {bigquery.PROJECT}")
    data = {"generated_at": datetime.datetime.now(datetime.timezone.utc).isoformat(), "views": {}}
    for name, _ in bigquery.views():
        if name in bigquery.ROW_VIEWS:
            continue
        rows = bigquery.query(token, f"SELECT * FROM `{bigquery.PROJECT}.{bigquery.DATASET}.{name}`")
        data["views"][name] = rows
        print(f"  {name}: {len(rows)} rows")

    html = open(TEMPLATE).read().replace("__DATA__", json.dumps(data))
    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    with open(OUTPUT, "w") as output:
        output.write(html)
    print(f"Wrote {OUTPUT}")
    if not args.no_open:
        webbrowser.open("file://" + OUTPUT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
