# Database passwords are generated here and written once to Parameter Store
# through write-only arguments, so they never reach the state or a plan. Bump
# value_wo_version to rotate one.
locals {
  db_passwords = toset(["DB_ADMIN_PASSWORD", "DB_WRITER_PASSWORD", "DB_READER_PASSWORD"])
}

ephemeral "random_password" "db" {
  for_each = local.db_passwords

  length  = 40
  special = false
}

resource "aws_ssm_parameter" "db_password" {
  for_each = local.db_passwords

  name             = "/locatedo/prod/db/${each.key}"
  type             = "SecureString"
  value_wo         = ephemeral.random_password.db[each.key].result
  value_wo_version = 1
}
