variable "aws_region" {
  description = "AWS region for the ledger stack."
  type        = string
  default     = "eu-west-1"
}

variable "github_owner" {
  description = "GitHub user or organization that owns the ledger repo."
  type        = string
  default     = "FonsecaGoncalo"
}

variable "github_repo" {
  description = "GitHub repository name."
  type        = string
  default     = "ledger"
}

variable "github_branch" {
  description = "Branch allowed to assume the CI role via OIDC."
  type        = string
  default     = "main"
}
