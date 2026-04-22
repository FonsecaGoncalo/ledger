resource "aws_cloudwatch_log_group" "ledger" {
  name              = "/ecs/${local.name}"
  retention_in_days = var.log_retention_days
}
