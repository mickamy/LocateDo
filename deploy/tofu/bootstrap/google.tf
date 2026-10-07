# The identities the infra-apply-google workflow runs as: a Google Cloud service account that GitHub's OIDC
# tokens from main can impersonate, and AWS roles for that root's state in S3. Applied from a laptop, like the
# prod roles, so that nothing CI runs as can remove them.

locals {
  google_projects = {
    dev  = "locatedo-dev"
    stg  = "locatedo-stg"
    prod = "locatedo"
  }
  google_host_project = local.google_projects.prod
  # dev and prod share the first account; staging sits on its own.
  google_billing_accounts = toset(["01EFFA-73EF68-A84673", "012B29-7EEFEA-38C79E"])
  # The same immutable ids as local.github_sub, in the claims Google Cloud can see.
  github_owner_id      = "11856337"
  github_repository_id = "1402816584"
  google_state_arn     = "arn:aws:s3:::locatedo-tofu-state/google/terraform.tfstate"

  github_main_assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = data.aws_iam_openid_connect_provider.github.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
          "token.actions.githubusercontent.com:sub" = "${local.github_sub}:ref:refs/heads/main"
        }
      }
    }]
  })
}

data "google_project" "each" {
  for_each   = local.google_projects
  project_id = each.value
}

resource "google_project_service" "cloud_billing" {
  project            = local.google_host_project
  service            = "cloudbilling.googleapis.com"
  disable_on_destroy = false
}

resource "google_iam_workload_identity_pool" "github" {
  project                   = local.google_host_project
  workload_identity_pool_id = "github"
  display_name              = "GitHub Actions"
}

resource "google_iam_workload_identity_pool_provider" "github" {
  project                            = local.google_host_project
  workload_identity_pool_id          = google_iam_workload_identity_pool.github.workload_identity_pool_id
  workload_identity_pool_provider_id = "github"
  display_name                       = "GitHub Actions"

  attribute_mapping = {
    "google.subject"                = "assertion.sub"
    "attribute.repository_id"       = "assertion.repository_id"
    "attribute.repository_owner_id" = "assertion.repository_owner_id"
    "attribute.ref"                 = "assertion.ref"
  }
  attribute_condition = join(" && ", [
    "assertion.repository_owner_id == \"${local.github_owner_id}\"",
    "assertion.repository_id == \"${local.github_repository_id}\"",
    "assertion.ref == \"refs/heads/main\"",
  ])

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }
}

resource "google_service_account" "tofu_google" {
  project      = local.google_host_project
  account_id   = "tofu-google"
  display_name = "infra-apply-google"
}

resource "google_service_account_iam_member" "tofu_google_github" {
  service_account_id = google_service_account.tofu_google.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github.name}/attribute.repository_id/${local.github_repository_id}"
}

# What the google root does in each project: read it, enable APIs, and manage API keys.
resource "google_project_iam_member" "tofu_google_browser" {
  for_each = local.google_projects
  project  = each.value
  role     = "roles/browser"
  member   = "serviceAccount:${google_service_account.tofu_google.email}"
}

resource "google_project_iam_member" "tofu_google_service_usage" {
  for_each = local.google_projects
  project  = each.value
  role     = "roles/serviceusage.serviceUsageAdmin"
  member   = "serviceAccount:${google_service_account.tofu_google.email}"
}

resource "google_project_iam_member" "tofu_google_api_keys" {
  for_each = local.google_projects
  project  = each.value
  role     = "roles/serviceusage.apiKeysAdmin"
  member   = "serviceAccount:${google_service_account.tofu_google.email}"
}

# Budgets belong to the billing accounts.
resource "google_billing_account_iam_member" "tofu_google_budgets" {
  for_each           = local.google_billing_accounts
  billing_account_id = each.value
  role               = "roles/billing.costsManager"
  member             = "serviceAccount:${google_service_account.tofu_google.email}"

  depends_on = [google_project_service.cloud_billing]
}

resource "aws_iam_role" "google_plan" {
  name               = "locatedo-tofu-google-plan"
  assume_role_policy = local.github_main_assume_role_policy
}

resource "aws_iam_role_policy" "google_plan" {
  name = "state"
  role = aws_iam_role.google_plan.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = "s3:ListBucket"
        Resource = "arn:aws:s3:::locatedo-tofu-state"
      },
      {
        Effect   = "Allow"
        Action   = "s3:GetObject"
        Resource = local.google_state_arn
      },
      {
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:DeleteObject"]
        Resource = "${local.google_state_arn}.tflock"
      },
    ]
  })
}

resource "aws_iam_role" "google_apply" {
  name               = "locatedo-tofu-google"
  assume_role_policy = local.github_main_assume_role_policy
}

resource "aws_iam_role_policy" "google_apply" {
  name = "state"
  role = aws_iam_role.google_apply.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = "s3:ListBucket"
        Resource = "arn:aws:s3:::locatedo-tofu-state"
      },
      {
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:PutObject"]
        Resource = local.google_state_arn
      },
      {
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:DeleteObject"]
        Resource = "${local.google_state_arn}.tflock"
      },
    ]
  })
}

# The workflow reads these from the repository variables GCP_WORKLOAD_IDENTITY_PROVIDER and GCP_SERVICE_ACCOUNT.
output "google_workload_identity_provider" {
  value = google_iam_workload_identity_pool_provider.github.name
}

output "google_service_account" {
  value = google_service_account.tofu_google.email
}

output "google_plan_role_arn" {
  value = aws_iam_role.google_plan.arn
}

output "google_apply_role_arn" {
  value = aws_iam_role.google_apply.arn
}
