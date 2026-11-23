ALTER TABLE stipend_students
    RENAME COLUMN active TO status;

ALTER TABLE stipend_students
    ALTER COLUMN status TYPE VARCHAR(20)
    USING CASE
        WHEN status THEN 'ACTIVE'
        ELSE 'DROPPED_OUT'
    END;
