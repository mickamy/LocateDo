"""Where LocateDo's analytics live in BigQuery, and the calls the scripts share."""

import glob
import os

from google_token import call

PROJECT = "locatedo"
SOURCE_DATASET = "analytics_483038986"
DATASET = "locatedo_views"
LOCATION = "asia-northeast1"
API = f"https://bigquery.googleapis.com/bigquery/v2/projects/{PROJECT}"
SCOPE = "https://www.googleapis.com/auth/bigquery"
VIEWS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "bigquery")
# Views with a row per event, user, or user-day; the others are what reports read.
ROW_VIEWS = {"events", "daily_state", "users"}


def views():
    """(name, sql) for each view, in file-name order, with the project and source filled in."""
    found = []
    for path in sorted(glob.glob(os.path.join(VIEWS_DIR, "*.sql"))):
        name = os.path.basename(path)[:-len(".sql")].split("_", 1)[1]
        sql = open(path).read().replace("__PROJECT__", PROJECT).replace("__SOURCE__", SOURCE_DATASET)
        found.append((name, sql))
    return found


def view_sql(sql, dataset=DATASET):
    return sql.replace("__DATASET__", dataset)


def inline(sql, definitions):
    """The query with the views it reads replaced by their definitions, so it runs before they are applied."""
    for name, body in definitions.items():
        ref = f"`{PROJECT}.__DATASET__.{name}`"
        if ref in sql:
            sql = sql.replace(ref, f"(\n{inline(body, definitions)}\n)")
    return view_sql(sql)


def dry_run(token, sql):
    call(token, "POST", f"{API}/queries", {
        "query": sql, "useLegacySql": False, "dryRun": True, "location": LOCATION,
    })


def query(token, sql):
    """Every row, as dicts with typed values, paging through large results."""
    response = call(token, "POST", f"{API}/queries", {
        "query": sql, "useLegacySql": False, "location": LOCATION, "timeoutMs": 60000,
    })
    job = response["jobReference"]["jobId"]
    while not response.get("jobComplete"):
        response = call(token, "GET", f"{API}/queries/{job}?location={LOCATION}&timeoutMs=60000")
    fields = response["schema"]["fields"]
    rows = [_record(fields, row) for row in response.get("rows", [])]
    while response.get("pageToken"):
        response = call(token, "GET", f"{API}/queries/{job}?location={LOCATION}&pageToken={response['pageToken']}")
        rows += [_record(fields, row) for row in response.get("rows", [])]
    return rows


def _record(fields, row):
    return {field["name"]: _convert(cell["v"], field["type"]) for field, cell in zip(fields, row["f"])}


def _convert(value, field_type):
    if value is None:
        return None
    if field_type in ("INTEGER", "INT64"):
        return int(value)
    if field_type in ("FLOAT", "FLOAT64", "NUMERIC"):
        return float(value)
    if field_type in ("BOOLEAN", "BOOL"):
        return value == "true"
    return value
