# Staging runs on a Raspberry Pi registered with SSM as a hybrid node, so GitHub-hosted jobs reach it the same way
# the prod deploy reaches its instance and the Pi keeps no inbound port or self-hosted runner. Applied from a
# laptop, like the other roles here.

locals {
  stg_node_name = "locatedo-stg-pi"
  stg_tags      = { Environment = "stg" }
}

# The role the Pi takes on through its activation.
resource "aws_iam_role" "stg_pi" {
  name = "locatedo-stg-pi"
  tags = local.stg_tags

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

resource "aws_iam_role_policy_attachment" "stg_pi_ssm" {
  role       = aws_iam_role.stg_pi.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy" "stg_pi_parameters" {
  name = "read-parameters"
  role = aws_iam_role.stg_pi.id

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
resource "aws_iam_role" "stg_deploy" {
  name = "locatedo-stg-deploy"
  tags = local.stg_tags

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

resource "aws_iam_role_policy" "stg_deploy" {
  name = "deploy"
  role = aws_iam_role.stg_deploy.id

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
          StringEquals = { "ssm:resourceTag/Name" = local.stg_node_name }
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

output "stg_pi_role_name" {
  value = aws_iam_role.stg_pi.name
}

output "stg_deploy_role_arn" {
  value = aws_iam_role.stg_deploy.arn
}
