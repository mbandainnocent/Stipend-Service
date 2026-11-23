BEGIN;

ALTER TABLE stipend_tiers
    ADD COLUMN IF NOT EXISTS minimum_attendance_percentage NUMERIC(5, 2);

DELETE FROM stipend_tiers;

ALTER TABLE stipend_tiers
    DROP COLUMN IF EXISTS minimum_absences,
    DROP COLUMN IF EXISTS maximum_absences;

INSERT INTO stipend_tiers (
    id,
    policy_id,
    minimum_attendance_percentage,
    payment_percentage
)
SELECT gen_random_uuid(), p.policy_id, tier.minimum_attendance_percentage, tier.payment_percentage
FROM stipend_policies p
CROSS JOIN (
    VALUES
        (0.00::NUMERIC(5, 2), 50.00::NUMERIC(5, 2)),
        (85.00::NUMERIC(5, 2), 75.00::NUMERIC(5, 2)),
        (90.00::NUMERIC(5, 2), 90.00::NUMERIC(5, 2)),
        (95.00::NUMERIC(5, 2), 100.00::NUMERIC(5, 2))
) AS tier(minimum_attendance_percentage, payment_percentage);

ALTER TABLE stipend_tiers
    ALTER COLUMN minimum_attendance_percentage SET NOT NULL;

ALTER TABLE monthly_stipends
    ADD COLUMN IF NOT EXISTS scheduled_training_days INTEGER,
    ADD COLUMN IF NOT EXISTS eligible_training_days INTEGER,
    ADD COLUMN IF NOT EXISTS credited_attendance_days NUMERIC(6, 2),
    ADD COLUMN IF NOT EXISTS attendance_percentage NUMERIC(6, 2),
    ADD COLUMN IF NOT EXISTS unauthorized_absence_days NUMERIC(6, 2);

COMMIT;
