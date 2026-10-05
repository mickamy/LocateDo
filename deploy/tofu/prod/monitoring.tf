locals {
  alert_email = "dev@locatedo.com"
}

resource "aws_cloudwatch_log_group" "containers" {
  name              = "/locatedo/prod/containers"
  retention_in_days = 30
}

resource "aws_sns_topic" "alerts" {
  name = "locatedo-prod-alerts"
}

resource "aws_sns_topic_subscription" "alerts_email" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = local.alert_email
}

resource "aws_cloudwatch_metric_alarm" "system_status" {
  alarm_name          = "locatedo-prod-system-status"
  alarm_description   = "AWS-side failure of the prod host; the instance is recovered onto new hardware."
  namespace           = "AWS/EC2"
  metric_name         = "StatusCheckFailed_System"
  dimensions          = { InstanceId = aws_instance.app.id }
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 2
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  alarm_actions       = [aws_sns_topic.alerts.arn, "arn:aws:automate:us-west-2:ec2:recover"]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

resource "aws_cloudwatch_metric_alarm" "instance_status" {
  alarm_name          = "locatedo-prod-instance-status"
  alarm_description   = "The prod OS stopped responding; the instance is rebooted."
  namespace           = "AWS/EC2"
  metric_name         = "StatusCheckFailed_Instance"
  dimensions          = { InstanceId = aws_instance.app.id }
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 3
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  alarm_actions       = [aws_sns_topic.alerts.arn, "arn:aws:automate:us-west-2:ec2:reboot"]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

resource "aws_cloudwatch_metric_alarm" "attached_ebs_status" {
  alarm_name          = "locatedo-prod-attached-ebs-status"
  alarm_description   = "A volume attached to the prod instance is impaired."
  namespace           = "AWS/EC2"
  metric_name         = "StatusCheckFailed_AttachedEBS"
  dimensions          = { InstanceId = aws_instance.app.id }
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 2
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

resource "aws_cloudwatch_metric_alarm" "cpu" {
  alarm_name          = "locatedo-prod-cpu"
  alarm_description   = "Prod CPU above 80% for 15 minutes."
  namespace           = "AWS/EC2"
  metric_name         = "CPUUtilization"
  dimensions          = { InstanceId = aws_instance.app.id }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 3
  threshold           = 80
  comparison_operator = "GreaterThanThreshold"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

# EBS publishes this sparsely, so a gap is not a stall.
resource "aws_cloudwatch_metric_alarm" "data_volume_stalled" {
  alarm_name          = "locatedo-prod-data-volume-stalled"
  alarm_description   = "I/O to the Postgres volume stalled."
  namespace           = "AWS/EBS"
  metric_name         = "VolumeStalledIOCheck"
  dimensions          = { VolumeId = aws_ebs_volume.data.id }
  statistic           = "Maximum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

# The CloudWatch agent (server/cloudwatch/agent.json) reports these.
resource "aws_cloudwatch_metric_alarm" "memory" {
  alarm_name          = "locatedo-prod-memory"
  alarm_description   = "Prod memory above 90% for 15 minutes."
  namespace           = "LocateDo/Host"
  metric_name         = "mem_used_percent"
  dimensions          = { InstanceId = aws_instance.app.id }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 3
  threshold           = 90
  comparison_operator = "GreaterThanThreshold"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

resource "aws_cloudwatch_metric_alarm" "data_disk" {
  alarm_name          = "locatedo-prod-data-disk"
  alarm_description   = "The Postgres volume is more than 80% full."
  namespace           = "LocateDo/Host"
  metric_name         = "disk_used_percent"
  dimensions          = { InstanceId = aws_instance.app.id, path = "/var/lib/locatedo", fstype = "ext4" }
  statistic           = "Maximum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 80
  comparison_operator = "GreaterThanThreshold"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

resource "aws_cloudwatch_metric_alarm" "root_disk" {
  alarm_name          = "locatedo-prod-root-disk"
  alarm_description   = "The root volume (OS, images, local log copies) is more than 80% full."
  namespace           = "LocateDo/Host"
  metric_name         = "disk_used_percent"
  dimensions          = { InstanceId = aws_instance.app.id, path = "/", fstype = "xfs" }
  statistic           = "Maximum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 80
  comparison_operator = "GreaterThanThreshold"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

# server/scripts/backup-prod.sh reports Succeeded after each upload. Hourly
# buckets, so a backup at any time of day keeps the alarm quiet.
resource "aws_cloudwatch_metric_alarm" "backup_missing" {
  alarm_name          = "locatedo-prod-backup-missing"
  alarm_description   = "No successful database backup in the last 25 hours."
  namespace           = "LocateDo/Backup"
  metric_name         = "Succeeded"
  statistic           = "Sum"
  period              = 3600
  evaluation_periods  = 25
  datapoints_to_alarm = 25
  threshold           = 1
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "breaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

resource "aws_route53_health_check" "api" {
  fqdn              = "api.locatedo.com"
  type              = "HTTPS"
  port              = 443
  resource_path     = "/healthz"
  request_interval  = 30
  failure_threshold = 3

  tags = {
    Name = "locatedo-prod-api"
  }
}

resource "aws_sns_topic" "alerts_us_east_1" {
  provider = aws.us_east_1
  name     = "locatedo-prod-alerts"
}

resource "aws_sns_topic_subscription" "alerts_us_east_1_email" {
  provider  = aws.us_east_1
  topic_arn = aws_sns_topic.alerts_us_east_1.arn
  protocol  = "email"
  endpoint  = local.alert_email
}

resource "aws_cloudwatch_metric_alarm" "api_health" {
  provider            = aws.us_east_1
  alarm_name          = "locatedo-prod-api-health"
  alarm_description   = "https://api.locatedo.com/healthz is failing from Route 53's checkers."
  namespace           = "AWS/Route53"
  metric_name         = "HealthCheckStatus"
  dimensions          = { HealthCheckId = aws_route53_health_check.api.id }
  statistic           = "Minimum"
  period              = 60
  evaluation_periods  = 2
  threshold           = 1
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "breaching"
  alarm_actions       = [aws_sns_topic.alerts_us_east_1.arn]
  ok_actions          = [aws_sns_topic.alerts_us_east_1.arn]
}
