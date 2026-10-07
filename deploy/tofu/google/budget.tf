# One budget per billing account, mailed to that account's administrators as its projects' spend passes each mark.
locals {
  budget = {
    currency_code = "JPY"
    units         = "1500"
  }

  # Which account pays for each project; dev and prod share one, staging sits on its own.
  billing_accounts = {
    dev  = "01EFFA-73EF68-A84673"
    stg  = "012B29-7EEFEA-38C79E"
    prod = "01EFFA-73EF68-A84673"
  }

  projects_by_billing_account = {
    for env, account in local.billing_accounts : account => "projects/${data.google_project.each[env].number}"...
  }
}

resource "google_billing_budget" "locatedo" {
  for_each        = local.projects_by_billing_account
  billing_account = each.key
  display_name    = "LocateDo"

  budget_filter {
    projects        = each.value
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
