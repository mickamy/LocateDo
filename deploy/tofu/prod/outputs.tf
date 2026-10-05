output "instance_id" {
  value = aws_instance.app.id
}

output "deploy_role_arn" {
  value = aws_iam_role.deploy.arn
}
