# The Firebase projects, one per server environment, matching the Android build types.
locals {
  projects = {
    dev  = "locatedo-dev"
    stg  = "locatedo-stg"
    prod = "locatedo"
  }
}

data "google_project" "each" {
  for_each   = local.projects
  project_id = each.value
}

resource "google_project_service" "apikeys" {
  for_each           = local.projects
  project            = each.value
  service            = "apikeys.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "maps" {
  for_each           = local.projects
  project            = each.value
  service            = "maps-android-backend.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "places" {
  for_each           = local.projects
  project            = each.value
  service            = "places.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "billing_budgets" {
  project            = local.projects.prod
  service            = "billingbudgets.googleapis.com"
  disable_on_destroy = false
}
