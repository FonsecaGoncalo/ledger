variable "aws_region" {
  description = "AWS region for the stack."
  type        = string
  default     = "eu-west-1"
}

variable "image_tag" {
  description = "Image tag for the initial task-definition revision. Use 'bootstrap' to point at a public placeholder; after first apply, CI registers every subsequent revision."
  type        = string
  default     = "bootstrap"
}

variable "container_port" {
  description = "Port the Spring Boot app listens on inside the container."
  type        = number
  default     = 8080
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage" {
  description = "RDS allocated storage in GB."
  type        = number
  default     = 20
}

variable "task_cpu" {
  description = "Fargate task CPU units (256 = 0.25 vCPU)."
  type        = number
  default     = 512
}

variable "task_memory" {
  description = "Fargate task memory in MiB."
  type        = number
  default     = 1024
}

variable "desired_count" {
  description = "Initial desired count for the ECS service."
  type        = number
  default     = 1
}

variable "log_retention_days" {
  description = "CloudWatch log retention."
  type        = number
  default     = 14
}
