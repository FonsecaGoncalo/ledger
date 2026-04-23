locals {
  signoz_name       = "${local.name}-signoz"
  signoz_private_ip = "10.20.1.50"
}

data "aws_ssm_parameter" "al2023_ami" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-6.1-x86_64"
}

resource "aws_security_group" "signoz" {
  name        = local.signoz_name
  description = "SigNoz host: OTLP from ECS, UI from ops CIDR"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "OTLP gRPC from ledger ECS tasks"
    from_port       = 4317
    to_port         = 4317
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }

  ingress {
    description     = "OTLP HTTP from ledger ECS tasks"
    from_port       = 4318
    to_port         = 4318
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }

  ingress {
    description = "SigNoz UI"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "All egress"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = local.signoz_name }
}

data "aws_iam_policy_document" "signoz_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "signoz" {
  name               = "${local.signoz_name}-host"
  assume_role_policy = data.aws_iam_policy_document.signoz_assume.json
}

resource "aws_iam_role_policy_attachment" "signoz_ssm" {
  role       = aws_iam_role.signoz.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy_attachment" "signoz_cw" {
  role       = aws_iam_role.signoz.name
  policy_arn = "arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy"
}

resource "aws_iam_instance_profile" "signoz" {
  name = "${local.signoz_name}-host"
  role = aws_iam_role.signoz.name
}

resource "aws_ebs_volume" "signoz_data" {
  availability_zone = local.azs[0]
  size              = var.signoz_data_volume_size_gb
  type              = "gp3"
  encrypted         = true

  tags = { Name = "${local.signoz_name}-data" }
}

resource "aws_instance" "signoz" {
  ami                         = data.aws_ssm_parameter.al2023_ami.value
  instance_type               = var.signoz_instance_type
  subnet_id                   = aws_subnet.public[0].id
  private_ip                  = local.signoz_private_ip
  associate_public_ip_address = true
  vpc_security_group_ids      = [aws_security_group.signoz.id]
  iam_instance_profile        = aws_iam_instance_profile.signoz.name

  root_block_device {
    volume_size = 20
    volume_type = "gp3"
    encrypted   = true
  }

  user_data = templatefile("${path.module}/signoz-user-data.sh.tftpl", {
    signoz_version = var.signoz_version
  })

  # Replacing the instance would re-run user_data against a fresh root disk;
  # data persists on the attached EBS regardless.
  user_data_replace_on_change = true

  tags = { Name = local.signoz_name }

  depends_on = [aws_internet_gateway.main]
}

resource "aws_volume_attachment" "signoz_data" {
  device_name = "/dev/sdh"
  volume_id   = aws_ebs_volume.signoz_data.id
  instance_id = aws_instance.signoz.id
}

resource "aws_cloudwatch_log_group" "signoz_host" {
  name              = "/ec2/${local.signoz_name}"
  retention_in_days = var.log_retention_days
}
