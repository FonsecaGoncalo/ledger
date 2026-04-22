output "alb_dns_name" {
  description = "ALB DNS name — hit /actuator/health, /swagger-ui.html on :80."
  value       = aws_lb.main.dns_name
}

output "ecr_repository_url" {
  description = "ECR repository URL for the ledger image."
  value       = aws_ecr_repository.ledger.repository_url
}

output "rds_endpoint" {
  description = "RDS endpoint (host:port)."
  value       = aws_db_instance.ledger.endpoint
}

output "jdbc_url" {
  description = "JDBC URL for the app — paste into .github/aws/task-definition.json."
  value       = local.jdbc_url
}

output "db_secret_arn" {
  description = "Secrets Manager ARN for the DB password — paste into .github/aws/task-definition.json."
  value       = aws_secretsmanager_secret.db_password.arn
}

output "cluster_name" {
  value = aws_ecs_cluster.main.name
}

output "service_name" {
  value = aws_ecs_service.ledger.name
}

output "log_group" {
  value = aws_cloudwatch_log_group.ledger.name
}
