terraform {
  required_version = "~> 1.13"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 8.5"
    }
  }

  backend "s3" {
    bucket       = "locatedo-tofu-state"
    key          = "google/terraform.tfstate"
    region       = "us-west-2"
    encrypt      = true
    use_lockfile = true
  }
}

# Applied by infra-apply-google as the service account from deploy/tofu/bootstrap, or by hand with Application
# Default Credentials. Billing-account APIs need a project to bill the calls to; the production project takes that
# role.
provider "google" {
  user_project_override = true
  billing_project       = local.projects.prod
}
