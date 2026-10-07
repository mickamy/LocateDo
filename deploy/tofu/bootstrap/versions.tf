terraform {
  required_version = "~> 1.13"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    google = {
      source  = "hashicorp/google"
      version = "~> 7.0"
    }
  }

  backend "s3" {
    bucket       = "locatedo-tofu-state"
    key          = "bootstrap/terraform.tfstate"
    region       = "us-west-2"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = "us-west-2"

  default_tags {
    tags = {
      Project     = "LocateDo"
      Environment = "prod"
      ManagedBy   = "opentofu"
    }
  }
}

# Application Default Credentials (`gcloud auth application-default login`); billing-account calls are billed to
# the production project.
provider "google" {
  user_project_override = true
  billing_project       = local.google_projects.prod
}
