#!/usr/bin/env python3
"""Read the BigQuery views and write an HTML report to tmp/analytics/report.html, then open it.

Usage: python3 tools/analytics/report.py [service_account_key.json]   (from the repo root)
"""

import datetime
import json
import os
import sys
import webbrowser

from google_token import access_token, call, default_key_path

PROJECT = "locatedo"
DATASET = "locatedo_views"
LOCATION = "asia-northeast1"
API = f"https://bigquery.googleapis.com/bigquery/v2/projects/{PROJECT}"
SCOPE = "https://www.googleapis.com/auth/bigquery"
VIEWS = ["daily_overview", "kpi_monthly", "home_navigation", "adding_by_entry", "home_list_rank"]
OUTPUT = os.path.join("tmp", "analytics", "report.html")
TEMPLATE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "report_template.html")


def convert(value, field_type):
    if value is None:
        return None
    if field_type in ("INTEGER", "INT64"):
        return int(value)
    if field_type in ("FLOAT", "FLOAT64", "NUMERIC"):
        return float(value)
    if field_type in ("BOOLEAN", "BOOL"):
        return value == "true"
    return value


def fetch(token, view):
    response = call(token, "POST", f"{API}/queries", {
        "query": f"SELECT * FROM `{PROJECT}.{DATASET}.{view}`",
        "useLegacySql": False,
        "location": LOCATION,
        "timeoutMs": 60000,
    })
    if not response.get("jobComplete"):
        raise SystemExit(f"{view}: the query did not finish within 60 seconds")
    if response.get("pageToken"):
        raise SystemExit(f"{view}: more rows than one response holds; add paging to report.py")
    fields = response["schema"]["fields"]
    rows = []
    for row in response.get("rows", []):
        record = {}
        for field, cell in zip(fields, row["f"]):
            record[field["name"]] = convert(cell["v"], field["type"])
        rows.append(record)
    return rows


def main():
    key_path = sys.argv[1] if len(sys.argv) > 1 else default_key_path()
    token, email = access_token(key_path, SCOPE)
    print(f"Using {email} on {PROJECT}")
    data = {"generated_at": datetime.datetime.now(datetime.timezone.utc).isoformat()}
    for view in VIEWS:
        data[view] = fetch(token, view)
        print(f"  {view}: {len(data[view])} rows")

    html = open(TEMPLATE).read().replace("__DATA__", json.dumps(data))
    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    with open(OUTPUT, "w") as output:
        output.write(html)
    print(f"Wrote {OUTPUT}")
    webbrowser.open("file://" + os.path.abspath(OUTPUT))
    return 0


if __name__ == "__main__":
    sys.exit(main())
