# One-time bootstrap. Creates the S3 state bucket + DynamoDB lock table that
# terraform/prod uses as its remote backend, the GitHub OIDC provider, and the
# IAM role (ledger-ci) that GitHub Actions assumes to push images and update
# the ECS service.
#
# State for THIS stack stays local — do not commit .tfstate. Losing it is
# recoverable (every resource is trivially re-creatable or importable).
#
# Runbook:
#   aws configure              # admin credentials, target account
#   terraform -chdir=terraform/bootstrap init
#   terraform -chdir=terraform/bootstrap apply
#
# Then, in GitHub: Settings → Secrets and variables → Actions → Variables:
#   AWS_REGION        = <aws_region output>
#   AWS_CI_ROLE_ARN   = <ci_role_arn output>
#
# Then bring up prod:
#   terraform -chdir=terraform/prod init \
#     -backend-config="bucket=<state_bucket output>" \
#     -backend-config="key=prod/terraform.tfstate" \
#     -backend-config="region=<aws_region output>" \
#     -backend-config="dynamodb_table=<lock_table output>" \
#     -backend-config="encrypt=true"
#   terraform -chdir=terraform/prod apply
#
# A push to main then runs the deploy workflow.

terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.70"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project   = "ledger"
      ManagedBy = "terraform"
      Stack     = "bootstrap"
    }
  }
}

data "aws_caller_identity" "current" {}
