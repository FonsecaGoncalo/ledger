resource "aws_ecs_cluster" "main" {
  name = local.name
}

resource "aws_ecs_cluster_capacity_providers" "main" {
  cluster_name       = aws_ecs_cluster.main.name
  capacity_providers = ["FARGATE"]

  default_capacity_provider_strategy {
    capacity_provider = "FARGATE"
    weight            = 1
  }
}

locals {
  jdbc_url = "jdbc:postgresql://${aws_rds_cluster.ledger.endpoint}:${aws_rds_cluster.ledger.port}/ledger"

  container_definitions = [{
    name      = local.container_name
    image     = local.image
    essential = true

    portMappings = [{
      containerPort = var.container_port
      hostPort      = var.container_port
      protocol      = "tcp"
    }]

    environment = [
      { name = "SPRING_DATASOURCE_URL", value = local.jdbc_url },
      { name = "SPRING_DATASOURCE_USERNAME", value = "ledger" },

      { name = "JAVA_TOOL_OPTIONS", value = "-javaagent:/app/otel-agent.jar" },
      { name = "OTEL_SERVICE_NAME", value = local.name },
      { name = "OTEL_EXPORTER_OTLP_ENDPOINT", value = "http://${aws_instance.signoz.private_ip}:4317" },
      { name = "OTEL_EXPORTER_OTLP_PROTOCOL", value = "grpc" },
      { name = "OTEL_TRACES_EXPORTER", value = "otlp" },
      { name = "OTEL_METRICS_EXPORTER", value = "otlp" },
      { name = "OTEL_LOGS_EXPORTER", value = "otlp" },
      { name = "OTEL_RESOURCE_ATTRIBUTES", value = "deployment.environment=prod" },

      { name = "OTEL_INSTRUMENTATION_LOGBACK_APPENDER_EXPERIMENTAL_CAPTURE_KEY_VALUE_PAIR_ATTRIBUTES", value = "true" },
      { name = "OTEL_INSTRUMENTATION_LOGBACK_APPENDER_EXPERIMENTAL_CAPTURE_MDC_ATTRIBUTES", value = "*" },
      { name = "OTEL_INSTRUMENTATION_LOGBACK_APPENDER_EXPERIMENTAL_CAPTURE_CODE_ATTRIBUTES", value = "true" },
    ]

    secrets = [
      # Aurora stores the managed master secret as JSON {username, password};
      # `:password::` extracts just the password field.
      { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = "${aws_rds_cluster.ledger.master_user_secret[0].secret_arn}:password::" },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.ledger.name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "ecs"
      }
    }
  }]
}

resource "aws_ecs_task_definition" "ledger" {
  family                   = local.name
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = aws_iam_role.task_execution.arn
  task_role_arn            = aws_iam_role.task.arn

  container_definitions = jsonencode(local.container_definitions)

  # CI registers every later revision via amazon-ecs-render-task-definition.
  # Letting Terraform fight CI over container_definitions would thrash the
  # service; TF only owns the seed revision.
  lifecycle {
    ignore_changes = [container_definitions]
  }
}

resource "aws_ecs_service" "ledger" {
  name            = local.name
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.ledger.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  enable_execute_command = true

  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent         = 200

  # Spring Boot boot + Flyway migrations comfortably within 2 min.
  health_check_grace_period_seconds = 120

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.ledger.arn
    container_name   = local.container_name
    container_port   = var.container_port
  }

  # CI-driven deploys update task_definition; manual scaling may change desired_count.
  lifecycle {
    ignore_changes = [task_definition, desired_count]
  }

  depends_on = [aws_lb_listener.http]
}
