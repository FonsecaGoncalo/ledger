locals {
  name           = "ledger"
  container_name = "ledger"
  account_id     = data.aws_caller_identity.current.account_id
  azs            = slice(data.aws_availability_zones.available.names, 0, 2)

  # Initial task definition needs a valid image. `terraform/prod` creates the ECR
  # repository but not the app image — that's CI's job. Use a public placeholder
  # for the seed revision; every later revision is registered by GitHub Actions.
  image = var.image_tag == "bootstrap" ? "public.ecr.aws/nginx/nginx:latest" : "${aws_ecr_repository.ledger.repository_url}:${var.image_tag}"
}
