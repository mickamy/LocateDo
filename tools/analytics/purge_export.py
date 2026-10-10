#!/usr/bin/env python3
"""List, and with --delete drop, the daily Analytics export tables up to a day.

Usage: python3 tools/analytics/purge_export.py --through YYYYMMDD [--delete] [service_account_key.json]

Asking GA4 to delete data does not touch what was already exported to BigQuery, so old events have to be dropped here
as well. Without --delete nothing changes.
"""

import argparse
import re
import sys

import bigquery
from google_token import access_token, call, default_key_path

API = f"{bigquery.API}/datasets/{bigquery.SOURCE_DATASET}/tables"
TABLE = re.compile(r"^events_(?:intraday_)?(\d{8})$")


def tables(token):
    names, page = [], ""
    while True:
        url = f"{API}?maxResults=1000"
        if page:
            url += f"&pageToken={page}"
        response = call(token, "GET", url)
        names += [table["tableReference"]["tableId"] for table in response.get("tables", [])]
        page = response.get("nextPageToken", "")
        if not page:
            return sorted(names)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--through", required=True, help="last day to drop, as YYYYMMDD")
    parser.add_argument("--delete", action="store_true", help="drop the tables instead of listing them")
    parser.add_argument("key", nargs="?", help="service account key")
    args = parser.parse_args()
    if not re.fullmatch(r"\d{8}", args.through):
        raise SystemExit("--through takes a day as YYYYMMDD")

    token, email = access_token(args.key or default_key_path(), bigquery.SCOPE)
    print(f"Using {email} on {bigquery.PROJECT}.{bigquery.SOURCE_DATASET}")
    doomed = []
    for name in tables(token):
        match = TABLE.match(name)
        if match and match.group(1) <= args.through:
            doomed.append(name)
    if not doomed:
        print("Nothing to drop")
        return 0
    for name in doomed:
        if args.delete:
            call(token, "DELETE", f"{API}/{name}")
            print(f"  dropped {name}")
        else:
            print(f"  would drop {name}")
    if not args.delete:
        print("Run again with --delete to drop them")
    return 0


if __name__ == "__main__":
    sys.exit(main())
