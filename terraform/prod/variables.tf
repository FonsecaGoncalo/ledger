variable "aws_region" {
  description = "AWS region for the stack."
  type        = string
  default     = "eu-west-3"
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

variable "signoz_instance_type" {
  description = "EC2 instance type for the self-hosted SigNoz host. t3.large is the documented minimum."
  type        = string
  default     = "t3.large"
}

variable "signoz_data_volume_size_gb" {
  description = "EBS gp3 volume size for ClickHouse data."
  type        = number
  default     = 100
}

variable "signoz_version" {
  description = "SigNoz git tag to check out on the host."
  type        = string
  default     = "v0.120.0"
}
