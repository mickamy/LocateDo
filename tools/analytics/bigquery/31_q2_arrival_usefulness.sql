-- Q2: whether arrival reminders get opened and lead to to-dos being checked off, by month.
SELECT
  DATE_TRUNC(event_date, MONTH) AS month,
  COUNTIF(event_name = 'arrival_notified') AS reminders,
  COUNTIF(event_name = 'arrival_opened') AS reminders_opened,
  SAFE_DIVIDE(COUNTIF(event_name = 'arrival_opened'), COUNTIF(event_name = 'arrival_notified')) AS open_rate,
  APPROX_QUANTILES(IF(event_name = 'arrival_opened', latency_s, NULL), 2)[OFFSET(1)] AS median_open_latency_s,
  COUNTIF(event_name = 'todo_completed') AS todos_completed,
  COUNTIF(event_name = 'todo_completed' AND via = 'notification') AS todos_completed_from_reminders,
  SAFE_DIVIDE(
    COUNTIF(event_name = 'todo_completed' AND via = 'notification'),
    COUNTIF(event_name = 'todo_completed')
  ) AS completed_from_reminders_rate
FROM `__PROJECT__.__DATASET__.events`
GROUP BY month
