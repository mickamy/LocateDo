# One budget over the three projects, mailed to the billing account's administrators as spend passes each mark.
locals {
  budget = {
    currency_code = "USD"
    units         = "10"
  }
}

resource "google_billing_budget" "locatedo" {
  billing_account = data.google_project.each["prod"].billing_account
  display_name    = "LocateDo"

  budget_filter {
    projects        = [for project in data.google_project.each : "projects/${project.number}"]
    calendar_period = "MONTH"
  }

  amount {
    specified_amount {
      currency_code = local.budget.currency_code
      units         = local.budget.units
    }
  }

  threshold_rules {
    threshold_percent = 0.5
  }
  threshold_rules {
    threshold_percent = 0.9
  }
  threshold_rules {
    threshold_percent = 1.0
  }

  depends_on = [google_project_service.billing_budgets]
}
