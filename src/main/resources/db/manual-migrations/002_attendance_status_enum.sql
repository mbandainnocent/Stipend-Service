DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM stipend_attendance
        WHERE status NOT IN (0, 1, 2, 3)
    ) THEN
        RAISE EXCEPTION 'stipend_attendance contains an unknown ordinal attendance status';
    END IF;
END
$$;

ALTER TABLE stipend_attendance
    ALTER COLUMN status TYPE VARCHAR(20)
    USING CASE status
        WHEN 0 THEN 'PRESENT'
        WHEN 1 THEN 'ABSENT'
        WHEN 2 THEN 'LATE'
        WHEN 3 THEN 'EXCUSED'
    END;
