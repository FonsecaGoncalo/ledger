resource "aws_db_subnet_group" "ledger" {
  name       = "${local.name}-db"
  subnet_ids = aws_subnet.public[*].id
  tags       = { Name = "${local.name}-db" }
}

resource "aws_rds_cluster" "ledger" {
  cluster_identifier = local.name
  engine             = "aurora-postgresql"
  engine_mode        = "provisioned"
  engine_version     = "17.4"

  database_name               = "ledger"
  master_username             = "ledger"
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.ledger.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  port                   = 5432

  # RDS Data API — powers the AWS Console Query Editor.
  enable_http_endpoint = true

  storage_encrypted       = true
  backup_retention_period = 1
  skip_final_snapshot     = true
  deletion_protection     = false

  serverlessv2_scaling_configuration {
    min_capacity             = 0
    max_capacity             = 1.0
    seconds_until_auto_pause = 300
  }
}

resource "aws_rds_cluster_instance" "ledger" {
  identifier         = "${local.name}-1"
  cluster_identifier = aws_rds_cluster.ledger.id
  engine             = aws_rds_cluster.ledger.engine
  engine_version     = aws_rds_cluster.ledger.engine_version
  instance_class     = "db.serverless"

  publicly_accessible        = false
  auto_minor_version_upgrade = true
}
