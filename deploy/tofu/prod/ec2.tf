data "aws_ssm_parameter" "al2023_arm64" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

# SSH goes through SSM (AWS-StartSSHSession); port 22 stays closed.
resource "aws_key_pair" "operator" {
  key_name   = "locatedo-prod-operator"
  public_key = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAICsJ2AqlfP/Y4wq7oa4qYR/a5+KCT0C2hosgma5KowE/"
}

resource "aws_instance" "app" {
  ami                    = data.aws_ssm_parameter.al2023_arm64.value
  instance_type          = "t4g.small"
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.app.id]
  iam_instance_profile   = aws_iam_instance_profile.app.name
  key_name               = aws_key_pair.operator.key_name

  user_data = templatefile("${path.module}/user_data.sh.tftpl", {
    data_volume_id = aws_ebs_volume.data.id
  })

  metadata_options {
    http_tokens = "required"
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = 20
    encrypted   = true
  }

  tags = {
    Name = "locatedo-prod-app"
  }

  # A newer AMI or user_data would replace the instance; that is done on purpose, not on every plan.
  lifecycle {
    ignore_changes = [ami, user_data]
  }
}

# Postgres lives here so that replacing the instance keeps the data. Resetting
# the database is done on the host (empty /var/lib/locatedo/postgres), never by
# destroying the volume; tearing prod down takes removing prevent_destroy first.
resource "aws_ebs_volume" "data" {
  availability_zone = aws_subnet.public.availability_zone
  type              = "gp3"
  size              = 20
  encrypted         = true
  final_snapshot    = true

  tags = {
    Name = "locatedo-prod-data"
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_volume_attachment" "data" {
  device_name                    = "/dev/sdf"
  volume_id                      = aws_ebs_volume.data.id
  instance_id                    = aws_instance.app.id
  stop_instance_before_detaching = true
}
