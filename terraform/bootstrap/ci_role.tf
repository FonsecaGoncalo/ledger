locals {
  account_id = data.aws_caller_identity.current.account_id

  ecr_repo_arn       = "arn:aws:ecr:${var.aws_region}:${local.account_id}:repository/ledger"
  ecs_cluster_arn    = "arn:aws:ecs:${var.aws_region}:${local.account_id}:cluster/ledger"
  ecs_service_arn    = "arn:aws:ecs:${var.aws_region}:${local.account_id}:service/ledger/ledger"
  ecs_task_def_arn   = "arn:aws:ecs:${var.aws_region}:${local.account_id}:task-definition/ledger:*"
  task_exec_role_arn = "arn:aws:iam::${local.account_id}:role/ledger-task-execution"
  task_role_arn      = "arn:aws:iam::${local.account_id}:role/ledger-task"
  log_group_arn      = "arn:aws:logs:${var.aws_region}:${local.account_id}:log-group:/ecs/ledger:*"
}

data "aws_iam_policy_document" "ci_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_owner}/${var.github_repo}:ref:refs/heads/${var.github_branch}"]
    }
  }
}

resource "aws_iam_role" "ledger_ci" {
  name               = "ledger-ci"
  assume_role_policy = data.aws_iam_policy_document.ci_trust.json
  description        = "GitHub Actions OIDC role for ledger CI/CD"
}

data "aws_iam_policy_document" "ci_permissions" {
  statement {
    sid       = "EcrAuth"
    effect    = "Allow"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid    = "EcrPushPull"
    effect = "Allow"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:CompleteLayerUpload",
      "ecr:DescribeImages",
      "ecr:DescribeRepositories",
      "ecr:GetDownloadUrlForLayer",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
    ]
    resources = [local.ecr_repo_arn]
  }

  statement {
    sid    = "EcsDeploy"
    effect = "Allow"
    actions = [
      "ecs:DescribeServices",
      "ecs:UpdateService",
      "ecs:DescribeTasks",
      "ecs:ListTasks",
    ]
    resources = [
      local.ecs_service_arn,
      "${local.ecs_cluster_arn}/*",
      "arn:aws:ecs:${var.aws_region}:${local.account_id}:task/ledger/*",
    ]
  }

  statement {
    sid    = "EcsTaskDefinition"
    effect = "Allow"
    actions = [
      "ecs:RegisterTaskDefinition",
      "ecs:DescribeTaskDefinition",
      "ecs:DeregisterTaskDefinition",
    ]
    resources = ["*"]
  }

  statement {
    sid       = "PassRoleToEcs"
    effect    = "Allow"
    actions   = ["iam:PassRole"]
    resources = [local.task_exec_role_arn, local.task_role_arn]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }

  statement {
    sid    = "DeployLogs"
    effect = "Allow"
    actions = [
      "logs:DescribeLogGroups",
      "logs:DescribeLogStreams",
      "logs:GetLogEvents",
    ]
    resources = [local.log_group_arn]
  }
}

resource "aws_iam_role_policy" "ledger_ci" {
  name   = "ledger-ci"
  role   = aws_iam_role.ledger_ci.id
  policy = data.aws_iam_policy_document.ci_permissions.json
}
