CREATE TABLE IF NOT EXISTS monthly_stipend_calculation_runs (
    id UUID PRIMARY KEY,
    period_year INTEGER NOT NULL,
    period_month INTEGER NOT NULL CHECK (period_month BETWEEN 1 AND 12),
    run_date DATE NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uk_stipend_calculation_period_run_date
        UNIQUE (period_year, period_month, run_date)
);
