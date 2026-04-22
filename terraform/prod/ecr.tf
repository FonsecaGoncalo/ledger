resource "aws_ecr_repository" "ledger" {
  name                 = local.name
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }
}

resource "aws_ecr_lifecycle_policy" "ledger" {
  repository = aws_ecr_repository.ledger.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Retain the 10 most recent images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}
