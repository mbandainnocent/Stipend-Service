BEGIN;

ALTER TABLE monthly_stipends
    ALTER COLUMN absence_count TYPE NUMERIC(6, 2)
    USING absence_count::NUMERIC(6, 2);

UPDATE monthly_stipends
SET absence_count = unauthorized_absence_days
WHERE unauthorized_absence_days IS NOT NULL;

COMMIT;
