output "state_bucket" {
  description = "S3 bucket holding terraform/prod remote state."
  value       = aws_s3_bucket.tf_state.id
}

output "lock_table" {
  description = "DynamoDB table used for terraform/prod state locking."
  value       = aws_dynamodb_table.tf_lock.name
}

output "aws_region" {
  description = "Region for the ledger stack. Set as a GitHub repo variable AWS_REGION."
  value       = var.aws_region
}

output "ci_role_arn" {
  description = "ARN of the GitHub Actions OIDC role. Set as a GitHub repo variable AWS_CI_ROLE_ARN."
  value       = aws_iam_role.ledger_ci.arn
}
