# Staging runs on a Raspberry Pi registered with SSM as a hybrid node, so GitHub-hosted jobs reach it the same way
# the prod deploy reaches its instance and the Pi keeps no inbound port or self-hosted runner. Applied from a
# laptop, like persistent and bootstrap.

data "aws_caller_identity" "current" {}

# The account already has GitHub's provider, shared with other projects.
data "aws_iam_openid_connect_provider" "github" {
  url = "https://token.actions.githubusercontent.com"
}

locals {
  account_id = data.aws_caller_identity.current.account_id
  # The repository's immutable subject (owner and repository ids), as in bootstrap.
  github_sub = "repo:mickamy@11856337/LocateDo@1402816584"
  node_name  = "locatedo-stg-pi"
}

# The role the Pi takes on through its activation.
resource "aws_iam_role" "pi" {
  name = "locatedo-stg-pi"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ssm.amazonaws.com" }
      Action    = "sts:AssumeRole"
      Condition = {
        StringEquals = { "aws:SourceAccount" = local.account_id }
        ArnLike      = { "aws:SourceArn" = "arn:aws:ssm:us-west-2:${local.account_id}:*" }
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "pi_ssm" {
  role       = aws_iam_role.pi.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy" "pi_parameters" {
  name = "read-parameters"
  role = aws_iam_role.pi.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = ["ssm:GetParametersByPath"]
      Resource = [
        "arn:aws:ssm:us-west-2:${local.account_id}:parameter/locatedo/stg",
        "arn:aws:ssm:us-west-2:${local.account_id}:parameter/locatedo/stg/*",
      ]
    }]
  })
}

# Only jobs in the repository's server-stg environment (limited to dev) can deploy and operate staging.
resource "aws_iam_role" "deploy" {
  name = "locatedo-stg-deploy"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = data.aws_iam_openid_connect_provider.github.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
          "token.actions.githubusercontent.com:sub" = "${local.github_sub}:environment:server-stg"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "deploy" {
  name = "deploy"
  role = aws_iam_role.deploy.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["ssm:PutParameter"]
        Resource = "arn:aws:ssm:us-west-2:${local.account_id}:parameter/locatedo/stg/app/*"
      },
      {
        # The node's id comes from its registration, so the tag the activation gave it picks it out.
        Effect   = "Allow"
        Action   = ["ssm:SendCommand"]
        Resource = "arn:aws:ssm:us-west-2:${local.account_id}:managed-instance/*"
        Condition = {
          StringEquals = { "ssm:resourceTag/Name" = local.node_name }
        }
      },
      {
        Effect   = "Allow"
        Action   = ["ssm:SendCommand"]
        Resource = "arn:aws:ssm:us-west-2::document/AWS-RunShellScript"
      },
      {
        Effect   = "Allow"
        Action   = ["ssm:GetCommandInvocation", "ssm:DescribeInstanceInformation"]
        Resource = "*"
      },
    ]
  })
}

output "pi_role_name" {
  value = aws_iam_role.pi.name
}

output "deploy_role_arn" {
  value = aws_iam_role.deploy.arn
}
