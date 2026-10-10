-- Who hits the free limits, how soon, and how far they get through the paywall.
WITH first_limit AS (
  SELECT
    user_pseudo_id,
    kind,
    MIN(days_since_install) AS days_since_install
  FROM `__PROJECT__.__DATASET__.events`
  WHERE event_name = 'limit_reached'
  GROUP BY user_pseudo_id, kind
),
funnel AS (
  SELECT
    user_pseudo_id,
    platform,
    paywall_trigger,
    LOGICAL_OR(event_name = 'paywall_shown') AS shown,
    LOGICAL_OR(event_name = 'purchase_started') AS started,
    LOGICAL_OR(event_name = 'purchase_canceled') AS canceled,
    LOGICAL_OR(event_name = 'purchase_failed') AS failed,
    LOGICAL_OR(event_name = 'paywall_purchased') AS purchased
  FROM `__PROJECT__.__DATASET__.events`
  WHERE paywall_trigger IS NOT NULL
  GROUP BY user_pseudo_id, platform, paywall_trigger
)
SELECT
  f.platform,
  f.paywall_trigger,
  COUNTIF(f.shown) AS users_shown,
  COUNTIF(f.started) AS users_started,
  COUNTIF(f.canceled) AS users_canceled,
  COUNTIF(f.failed) AS users_failed,
  COUNTIF(f.purchased) AS users_purchased,
  SAFE_DIVIDE(COUNTIF(f.purchased), COUNTIF(f.shown)) AS purchase_rate,
  APPROX_QUANTILES(l.days_since_install, 2)[OFFSET(1)] AS median_days_to_limit
FROM funnel AS f
LEFT JOIN first_limit AS l
  ON l.user_pseudo_id = f.user_pseudo_id
  AND (
    (f.paywall_trigger = 'place_limit' AND l.kind = 'place')
    OR (f.paywall_trigger = 'todo_limit' AND l.kind = 'todo')
  )
GROUP BY f.platform, f.paywall_trigger
