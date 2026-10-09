-- Which kinds of places reminders fire at, and with what radius, per platform.
SELECT
  DATE_TRUNC(event_date, MONTH) AS month,
  platform,
  category,
  COUNT(*) AS reminders,
  APPROX_QUANTILES(radius_m, 2)[OFFSET(1)] AS median_radius_m,
  AVG(open_todos) AS average_open_todos
FROM `__PROJECT__.__DATASET__.events`
WHERE event_name = 'arrival_notified'
GROUP BY month, platform, category
