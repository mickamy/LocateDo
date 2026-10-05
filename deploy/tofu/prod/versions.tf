terraform {
  required_version = "~> 1.13"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.7"
    }
  }

  backend "s3" {
    bucket       = "locatedo-tofu-state"
    key          = "prod/terraform.tfstate"
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

# Route 53 health check metrics exist only in us-east-1, so their alarm lives there.
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"

  default_tags {
    tags = {
      Project     = "LocateDo"
      Environment = "prod"
      ManagedBy   = "opentofu"
    }
  }
}
