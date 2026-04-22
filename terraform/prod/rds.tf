resource "aws_db_subnet_group" "ledger" {
  name       = "${local.name}-db"
  subnet_ids = aws_subnet.public[*].id
  tags       = { Name = "${local.name}-db" }
}

resource "aws_db_instance" "ledger" {
  identifier     = local.name
  engine         = "postgres"
  engine_version = "17.4"
  instance_class = var.db_instance_class

  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = 100
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = "ledger"
  username = "ledger"
  password = random_password.db.result
  port     = 5432

  db_subnet_group_name   = aws_db_subnet_group.ledger.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false

  backup_retention_period    = 7
  skip_final_snapshot        = true
  deletion_protection        = false
  auto_minor_version_upgrade = true

  # Password also written to Secrets Manager (secrets.tf). RDS-managed rotation
  # would replace that; leaving it app-managed for now.
}
