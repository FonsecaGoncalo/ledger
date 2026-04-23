#   terraform -chdir=terraform/prod init \
#     -backend-config="bucket=ledger-tfstate-<account_id>" \
#     -backend-config="key=prod/terraform.tfstate" \
#     -backend-config="region=<aws_region>" \
#     -backend-config="dynamodb_table=ledger-tf-lock" \
#     -backend-config="encrypt=true"
terraform {
  backend "s3" {}
}
