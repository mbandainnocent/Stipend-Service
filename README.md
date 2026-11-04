# Stipend-Service

## Current implementation status

The service has Kafka consumers for student-registration, attendance, and student-status events. Registration events are validated, deduplicated, and used to create or update student records. Student Registration Service is the source of truth for cohort stipend eligibility, monthly base amount, and currency; Stipend Service stores these values on the student and derives current eligibility from the cohort setting and the student's enrollment status. The amount supplied by registration is a base amount, not the final monthly payment.

Student enrollment status is stored as `ACTIVE` or `DROPPED_OUT`. Before upgrading an existing database from the previous boolean `active` column, run `src/main/resources/db/manual-migrations/001_student_status_enum.sql` once; new databases created from the current entity do not need this migration.

Attendance status is stored as the enum name (`PRESENT`, `ABSENT`, `LATE`, or `EXCUSED`). Before upgrading an existing database whose `stipend_attendance.status` column stores ordinal numbers, run `src/main/resources/db/manual-migrations/002_attendance_status_enum.sql` once; new databases created from the current entity do not need this migration. Attendance events are validated, upserted by attendance ID, and deduplicated by processed event ID. The stored event version also prevents older or duplicate-version attendance events from overwriting newer records. Before upgrading an existing database to track attendance event versions, run `src/main/resources/db/manual-migrations/003_attendance_event_version.sql` once; new databases created from the current entity do not need this migration. Student status events are validated and deduplicated; stale status versions are ignored, and a `DROPPED_OUT` status disables future stipend eligibility without changing past stipend records. A later `ACTIVE` status restores eligibility only if the cohort is configured as stipend-eligible.

Kafka consumers use Spring Kafka's `ErrorHandlingDeserializer` and a bounded retry/dead-letter handler. Transient listener failures are retried twice, then published to the source topic's `.DLT` topic; invalid event data (`IllegalArgumentException`) and malformed JSON are not retried and are sent directly to the DLT. If the DLT producer send fails, recovery is reported as failed to the listener container rather than acknowledged as successful. Embedded-Kafka tests verify transient retries, invalid-event handling, malformed JSON preservation in the DLT, and failed DLT producer sends. Inspecting, alerting on, and safely replaying DLT records remain operational work.

`StipendPolicy` and `StipendTier` are JPA entities in `src/main/java/com/stipend/service/Model/`; their records are stored in the `stipend_policies` and `stipend_tiers` database tables, not in a policy file. When a student registration event arrives, `StudentRegistrationService` creates and saves a default enabled policy for the cohort if none exists. The policy starts on the cohort start date and has tiers 0–2 absences = 100%, 3 = 50%, and 4+ = 0%; an existing cohort policy is not overwritten. This currently happens for any newly processed cohort registration, whether or not that cohort is stipend-eligible. Existing cohorts are not backfilled automatically; they need another registration event or a future policy-management/seed operation.

`MonthlyStipendService.calculateMonthlyStipend(YearMonth)` calculates a month for active, stipend-eligible students whose cohort overlaps the period. It infers the cohort's class days from distinct attendance dates recorded for any student in the cohort. The daily allowance is the monthly base amount divided by those observed class days. A student's stipend activates on their first `PRESENT` record, inclusive; each distinct `PRESENT` date from activation through month-end earns one daily allowance. The attendance-based amount is then adjusted by the effective absence-policy tier, as requested. `ABSENT` and `LATE` records on or after activation count toward the tier but do not earn a daily allowance; missing student records do not count as absences or present days. `EXCUSED` does not count as an absence or a present day. Each absence count must match exactly one tier with a percentage from 0 to 100; missing or overlapping tiers fail the calculation. The inferred calendar cannot include a scheduled class date if nobody in the cohort has an attendance record for that date. When attendance is recorded or corrected, rerunning the calculation updates a `CALCULATED` stipend; approved, paid, and cancelled records are left unchanged. `MonthlyStipendScheduler` runs at 02:00 on the last calendar day of each month in `app.registration.business-time-zone` (default `Africa/Kigali`) and calculates that same stipend month using attendance available when the job runs. The cron is configurable with `app.stipend.calculation.cron`. Failures are logged and propagated; reruns are safe for existing records, and the database uniqueness constraint prevents duplicate student/month records if scheduler instances overlap. Runs missed during an outage are not automatically backfilled. Finance/policy read APIs, policy management/update path, and approval/payment endpoints are also not implemented. The finance team's requested view is one row per student and month with first name, last name, cohort, monthly stipend amount, and stipend-period start and end dates. Current models do not yet provide that complete view: monthly stipend records contain student/cohort IDs and year/month, while student names and cohort dates are stored separately; no finance query service or read endpoint exists.

There is also a `POST /api/students` endpoint, but it directly invokes the registration persistence service and bypasses the Kafka event validator and processed-event idempotency. It is not part of the finance API; decide whether to remove it or secure and align its validation/behavior with the supported registration flow. Kafka bootstrap/group settings should also be consolidated: `KafkaConsumerConfig` currently hard-codes `localhost:9092` and a group ID, while the application configuration and Compose environment provide configurable values.

The stories below track implementation status. A story is complete only when its acceptance criteria are implemented and tested.

## Status at a glance

- Core processing is implemented and validated: registration, attendance ingestion, policy validation, and monthly stipend calculation are complete.
- Remaining delivery is concentrated in the operational, finance, and security layers: scheduler, finance read APIs, approval/payment workflow, JWT authorization, and Kafka operational tooling.
- Progress: 5 of 14 user stories are complete; 9 stories remain in flight across US-6 through US-14.

## Remaining work

The current backlog that still needs implementation or verification is focused on the operational and finance layers that sit on top of the completed registration, attendance, and stipend-calculation foundations:

- US-4 and US-5: finance-facing repository queries, policy validation, and monthly stipend calculation are implemented and listed below under completed stories.
- US-6: add automatic backfill for missed runs; the last-day schedule and safe reruns are implemented.
- US-7 and US-8: add finance DTOs, services, and read endpoints for stipends and policies.
- US-9: implement approval and payment transitions with 409 handling and auditing.
- US-10: secure finance endpoints with JWT and role-based authorization.
- US-11: standardize API error contracts and validation responses.
- US-12: harden entity safety and align the OpenAPI contract with the persistence layer.
- US-13: document and automate Kafka DLT inspection, alerting, and safe replay.
- US-14: align the registration write path and Kafka configuration across local and container deployments.

## Completed user stories

### US-1: Verify application behavior and local startup
The project targets Java 17 and Maven 3.9.x. Run `mvn clean validate compile test` to validate and test the project. PostgreSQL integration tests use Testcontainers and require Docker; when Docker is unavailable, those tests are skipped. A Surefire message saying `Surefire is going to kill self fork JVM` may appear during shutdown even when Maven exits with code 0 and the Surefire reports show no failures; this indicates delayed test-JVM shutdown, not a failed assertion. PostgreSQL and Kafka startup instructions are below.

### US-2: Configure the PostgreSQL connection
PostgreSQL connection settings are supplied through environment variables.

- `DB_URL` defaults to `jdbc:postgresql://localhost:5538/stipenddb`.
- `DB_USERNAME` and `DB_PASSWORD` provide credentials without committing them.

### Implemented event processing
Registration, attendance, and student-status Kafka listeners and their persistence services are present. Registration validates required event fields and stores cohort-provided stipend settings. Attendance processing validates required fields, upserts by attendance ID, and records processed event IDs transactionally. Student-status processing ignores duplicate event IDs and stale status versions. Unit tests cover the principal service flows and selected duplicate/invalid-event cases.

### US-3: Consume and persist attendance events
As the stipend service, I want attendance events recorded idempotently so that monthly calculations use reliable attendance data.

The attendance consumer reads `AttendanceEvent` messages from the configured attendance topic. It validates every required field and requires a positive event version; records are created or updated by `attendanceId`, and processed event IDs prevent duplicate processing. Incoming events with a version less than or equal to the stored version are ignored, preventing stale or out-of-order updates. Attendance persistence and processed-event recording are transactional, so a failure to record the processed event rolls back the attendance change.

Spring Kafka retries transient listener failures twice before publishing to the source topic's `.DLT`; invalid event data and malformed JSON go directly to the DLT without retries. A failed DLT producer send is not treated as successful recovery. Tests cover record creation and update, duplicate delivery, stale and equal versions, each missing required field, invalid versions, rollback, transient retries, malformed JSON, invalid-event DLT handling, and producer failure. The PostgreSQL rollback integration test requires Docker and is skipped when Docker is unavailable.

### US-4: Add stipend repositories and policy validation
As the calculation and finance services, I want queryable repositories and valid policies so that stipend rules can be selected predictably.

Repositories currently exist for students, attendance records, monthly stipends, processed events, and stipend policies. Monthly stipend searches support optional year, month, cohort, student, and status filters with pagination; a finance projection joins stipend rows with student names and cohort dates and derives the stipend period boundaries. Policy searches support optional program, cohort, and enabled filters with pagination and eagerly load tiers. A default policy with absence tiers is created for a cohort when registration data arrives and no policy exists for that cohort. Calculation queries retrieve eligible students, absences by student/date/status, and an existing monthly record by student and period. Student Registration Service provides cohort eligibility and the base amount in the registration event, so `StipendPolicy` contains attendance calculation rules rather than duplicating the cohort amount/currency.

Policy validation requires a start date, an end date no earlier than the start, and tiers that cover all nonnegative absence counts exactly once: tiers start at zero, are contiguous and non-overlapping, have valid inclusive bounds, and end with one unbounded tier. Payment percentages must be between 0 and 100. Registration validates new default policies before saving, and monthly calculation validates persisted policies before applying them. Unit tests cover invalid dates, bounds, gaps, overlaps, percentages, and missing/unbounded tiers; PostgreSQL repository integration tests cover finance filters, joins, pagination, and tier loading.

Acceptance criteria:
- ✅ Add remaining query methods for finance API use cases.
- ✅ Validate policy effective dates and tier ranges/percentages; reject overlapping or ambiguous tiers.
- ✅ Add repository and policy-validation tests.

### US-5: Calculate monthly stipends
The monthly calculation service is implemented and its acceptance criteria are covered by unit and PostgreSQL concurrency integration tests. It calculates eligible students' stipends from attendance and the effective policy, and reruns safely update calculated records without changing approved, paid, or cancelled records.

Acceptance criteria:
- ✅ Determine eligible students and stipend periods using cohort start/end dates.
- ✅ Count `ABSENT` and `LATE` records as absences; `PRESENT` and `EXCUSED` do not count.
- ✅ Derive a daily allowance by dividing the monthly base by observed cohort class dates; pay for each distinct student `PRESENT` date from their first `PRESENT` date (inclusive), then apply the effective absence-policy tier using `BigDecimal` and the cohort currency.
- ✅ Create or update a record per student and period on rerun, while leaving approved, paid, and cancelled records unchanged. The database unique constraint protects against duplicate period records.
- ✅ Late attendance and corrections are included when a `CALCULATED` stipend is rerun; approved, paid, and cancelled records remain unchanged.
- ✅ Add unit tests for cohort/month date boundaries, activation-based proration, all attendance statuses, `HALF_UP` rounding, and sequential recalculation of an existing student/month record.
- ✅ Add a PostgreSQL Testcontainers integration test for simultaneous calculations of the same student/month; it verifies the unique constraint leaves one complete record after the losing transaction rolls back. Run it with `mvn -Dtest=MonthlyStipendConcurrencyIntegrationTest test` and Docker running. Testcontainers skips it when Docker is unavailable.

## <mark>🟡 Remaining user stories</mark>

### <mark>🟡 US-6: Schedule monthly calculation</mark>
As an operator, I want monthly calculations to run automatically and safely so that finance receives each period's stipend records without manual processing.

The scheduler runs at 02:00 on the last calendar day of each month in the configured business timezone and calculates that same stipend month. The cron defaults to `0 0 2 L * *` and can be overridden with `app.stipend.calculation.cron`; the timezone defaults to `Africa/Kigali`. Calculation failures are logged and propagated. Existing `CALCULATED` records are safely recalculated, finalized records remain unchanged, and the unique student/year/month constraint protects against duplicate records. Runs missed during an outage are not automatically backfilled.

Acceptance criteria:
- ✅ Configure the calculation schedule and business timezone explicitly.
- <mark>Define automatic catch-up behavior for periods missed while the service is unavailable and whether a post-month-end recalculation is needed.</mark>
- ✅ Ensure concurrent runs cannot create duplicate monthly stipend records; failed uniqueness conflicts are logged and surfaced.
- ✅ Log completed, empty, and failed calculations.
- ✅ Add tests for scheduled dispatch and safe reruns.

### <mark>🟡 US-7: Implement finance and policy repositories, DTOs, and services</mark>
As a finance user, I want stipend and policy data exposed through validated API models and services so that the dashboard can safely review records and rules.

Acceptance criteria:
- <mark>Add stipend search repositories for student, cohort, period, and status, with pagination support.</mark>
- <mark>Add policy queries by program, cohort, and enabled state, including associated tiers.</mark>
- <mark>Implement validated request/response DTOs matching the OpenAPI contract; do not serialize JPA entities directly.</mark>
- <mark>Implement stipend search, student history, cohort-period lookup, and policy lookup.</mark>
- <mark>Return the finance team's required fields for each monthly row: `firstName`, `lastName`, cohort, monthly amount, stipend-period start date, and stipend-period end date. Resolve the student name and cohort information from the student record; derive period dates from the stipend year/month or persist them consistently.</mark>
- The displayed monthly amount is the final attendance-adjusted calculated amount; <mark>document the currency and rounding rules in the finance API contract.</mark>
- <mark>Return not-found errors for missing records and handle lazy-loaded policy tiers safely.</mark>

### <mark>🟡 US-8: Implement stipend and policy read endpoints</mark>
As a finance dashboard, I want authenticated endpoints to browse stipend records and policies so that finance staff can review calculations.

Acceptance criteria:
- <mark>Implement the GET stipend search, single stipend, student history, and cohort-period endpoints defined in `src/main/resources/stipend-service.yaml`.</mark>
- <mark>Implement the GET policy list and single-policy endpoints defined in the OpenAPI specification.</mark>
- <mark>Support the specified filters, pagination, response shapes, and HTTP status codes.</mark>
- <mark>Add controller tests for valid requests, invalid parameters, pagination, and missing records.</mark>
- <mark>Update `src/main/resources/stipend-service.yaml` to match the agreed finance response, including separate first/last names and stipend-period start/end dates if those remain required.</mark>

### <mark>🟡 US-9: Implement approval and payment workflow</mark>
As a finance user, I want to approve calculated stipends and record completed payments so that stipend processing has an auditable state transition.

Acceptance criteria:
- <mark>Implement `PATCH /api/v1/stipends/{stipendId}/approve` and `PATCH /api/v1/stipends/{stipendId}/mark-paid`.</mark>
- <mark>Allow only `CALCULATED → APPROVED` and `APPROVED → PAID` transitions.</mark>
- <mark>Record approver identity, approval comment/time, payment reference, and paid time.</mark>
- <mark>Reject invalid transitions with HTTP 409 and validate request bodies.</mark>
- <mark>Make updates transactional and add tests for allowed transitions, invalid transitions, and repeated requests.</mark>

### <mark>🟡 US-10: Secure finance endpoints with JWT</mark>
As an operator, I want finance endpoints protected by JWT authentication and role-based authorization so that stipend and payment data is only accessible to authorized users.

Acceptance criteria:
- <mark>Add Spring Security and configure JWT validation using the agreed identity provider and issuer.</mark>
- <mark>Require the finance role for stipend and policy API endpoints.</mark>
- <mark>Return consistent 401 and 403 responses.</mark>
- <mark>Configure CORS only for approved dashboard origins and test access-control rules.</mark>

### <mark>🟡 US-11: Standardize API error responses</mark>
As an API consumer, I want consistent error bodies so that the dashboard can handle failures predictably.

Acceptance criteria:
- <mark>Extend the existing global exception handler and `ErrorResponse` to match the OpenAPI schema; current handling covers not-found and illegal-argument errors only.</mark>
- <mark>Map validation, authentication, authorization, not-found, and invalid-state errors to 400, 401, 403, 404, and 409 respectively.</mark>
- <mark>Include timestamp, status, error, message, path, and field validation details where applicable.</mark>
- <mark>Add tests that verify response status and body for each error category.</mark>

### <mark>🟡 US-12: Maintain entity safety and API contract alignment</mark>
As a developer, I want persistence models and the OpenAPI contract to stay safe and consistent so that serialization and schema changes do not introduce runtime errors.

Acceptance criteria:
- <mark>Avoid generated `toString`, `equals`, or `hashCode` traversing bidirectional or lazy JPA relationships; current Lombok `@Data` on policy/tier entities includes relationship fields and needs review.</mark>
- <mark>Keep OpenAPI schemas, DTO validation, API behavior, and database fields aligned.</mark>
- <mark>Add regression tests for policy/tier serialization and entity relationship behavior.</mark>

### <mark>🟡 US-13: Operate and recover Kafka dead-letter records</mark>
As an operator, I want failed Kafka events to be inspectable and safely recoverable so that a bad message does not silently disappear from the registration workflow.

Acceptance criteria:
- <mark>Document commands or provide tooling to inspect each `.DLT` topic and identify the source topic, partition, offset, and failure cause.</mark>
- <mark>Alert on DLT publication and processing failures, and track DLT volume.</mark>
- <mark>Define a safe replay procedure that preserves the original record and avoids duplicate business effects.</mark>
- ✅ Embedded-Kafka tests verify transient retries, malformed JSON delivery to the DLT, invalid event data sent without retries, and failed DLT sends not acknowledged as successful recovery. Repeated DLT publication failures and recovery after a broker outage still need coverage.

### <mark>🟡 US-14: Align registration write paths and Kafka configuration</mark>
As an operator, I want one documented and consistently validated registration path so that local and container deployments behave the same way.

Acceptance criteria:
- <mark>Decide whether `POST /api/students` remains supported; if retained, validate events and preserve idempotency, and secure it according to the intended access model.</mark>
- <mark>Align the OpenAPI description with the actual registration endpoint and supported write paths.</mark>
- <mark>Remove hard-coded Kafka bootstrap servers and consumer group IDs; use Spring configuration for both local and Compose deployments.</mark>
- <mark>Add a startup/configuration test that verifies the effective Kafka settings and listener container wiring.</mark>

## Local development setup

### Prerequisites

- Java 17 is the project target (`maven.compiler.source` and `maven.compiler.target`). The current Maven build has been run with Maven 3.9.9; use Maven 3.9.x.
- Run `mvn -Dtest=KafkaRetryConfigTest,AttendanceKafkaRetryIntegrationTest test` to verify Kafka retry and dead-letter behavior using the embedded Kafka test broker.
- PostgreSQL must be available with a database named `stipenddb` and a user that can create/update tables. The bundled Docker Compose setup publishes PostgreSQL on `localhost:5538`; a separately installed PostgreSQL can use the default `localhost:5432`.
- Kafka must be available at `localhost:9092`. The consumers read registration, status, and attendance events from `registration-events`, `student-status-events`, and `attendance-events`, respectively.

### Configure and run

Start the bundled PostgreSQL and Kafka services:

```sh
docker compose up -d postgres kafka
```

For the bundled PostgreSQL container, set its default local credentials and published host port before starting the service. Override the example password for any non-local use; do not commit credentials:

```sh
export DB_USERNAME=stipend_app
export DB_PASSWORD='password'
export DB_URL=jdbc:postgresql://localhost:5538/stipenddb
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

For a separately installed PostgreSQL on its default port, use `DB_URL=jdbc:postgresql://localhost:5432/stipenddb`. The application defaults to `JPA_DDL_AUTO=update` for local development. For a separately managed schema, set `JPA_DDL_AUTO=validate`; production deployments should use versioned migrations. Kafka's bootstrap address is configured as `localhost:9092` in `src/main/resources/application.yaml`.

When upgrading a local database, stop the running service and execute any required manual migration before restarting with the current code. To convert the previous attendance enum-ordinal mapping, run:

```sh
PGPASSWORD="$DB_PASSWORD" psql \
  -h localhost -p 5538 -U "$DB_USERNAME" -d stipenddb \
  -v ON_ERROR_STOP=1 \
  -f src/main/resources/db/manual-migrations/002_attendance_status_enum.sql
```

For attendance event-version tracking, also run:

```sh
PGPASSWORD="$DB_PASSWORD" psql \
  -h localhost -p 5538 -U "$DB_USERNAME" -d stipenddb \
  -v ON_ERROR_STOP=1 \
  -f src/main/resources/db/manual-migrations/003_attendance_event_version.sql
```

Adjust the host, port, database, and credentials for your PostgreSQL setup. The enum migration converts `0`–`3` to `PRESENT`, `ABSENT`, `LATE`, and `EXCUSED`. It aborts if it encounters an unknown ordinal so unexpected data is not silently converted.

### Manually test registration, attendance, and stipend calculation

There is no stipend Kafka event: registration and attendance are consumed from Kafka, and the stipend record is calculated from those persisted events. The calculation trigger below is available only with the `dev` Spring profile; it is not enabled in the default or production profile.

With the services and app running, publish a registration event (the service creates the default cohort policy if one does not exist):

```sh
docker exec -i stipend-kafka kafka-console-producer \
  --bootstrap-server localhost:29092 --topic registration-events <<'EOF'
{"eventId":"10000000-0000-0000-0000-000000000001","studentId":"20000000-0000-0000-0000-000000000001","firstName":"Alex","lastName":"Example","programId":"30000000-0000-0000-0000-000000000001","cohortId":"40000000-0000-0000-0000-000000000001","registeredAt":"2025-01-01T08:00:00Z","cohortStartDate":"2025-01-01T00:00:00Z","cohortEndDate":"2025-12-31T00:00:00Z","registrationDate":"2025-01-01T08:00:00Z","stipendEligible":true,"monthlyStipendAmount":40000.00,"stipendCurrency":"RWF","version":1}
EOF
```

After registration is consumed, publish three attendance records. These create three observed cohort class dates; two are present and one is absent:

```sh
docker exec -i stipend-kafka kafka-console-producer \
  --bootstrap-server localhost:29092 --topic attendance-events <<'EOF'
{"eventId":"50000000-0000-0000-0000-000000000001","attendanceId":"60000000-0000-0000-0000-000000000001","studentId":"20000000-0000-0000-0000-000000000001","programId":"30000000-0000-0000-0000-000000000001","cohortId":"40000000-0000-0000-0000-000000000001","attendanceStatus":"PRESENT","attendanceDate":"2025-01-02","timestamp":"2025-01-02T16:00:00Z","version":1}
{"eventId":"50000000-0000-0000-0000-000000000002","attendanceId":"60000000-0000-0000-0000-000000000002","studentId":"20000000-0000-0000-0000-000000000001","programId":"30000000-0000-0000-0000-000000000001","cohortId":"40000000-0000-0000-0000-000000000001","attendanceStatus":"ABSENT","attendanceDate":"2025-01-03","timestamp":"2025-01-03T16:00:00Z","version":1}
{"eventId":"50000000-0000-0000-0000-000000000003","attendanceId":"60000000-0000-0000-0000-000000000003","studentId":"20000000-0000-0000-0000-000000000001","programId":"30000000-0000-0000-0000-000000000001","cohortId":"40000000-0000-0000-0000-000000000001","attendanceStatus":"PRESENT","attendanceDate":"2025-01-06","timestamp":"2025-01-06T16:00:00Z","version":1}
EOF
```

Once Kafka has delivered the events, trigger the calculation for that month:

```sh
curl -X POST 'http://localhost:8080/api/dev/stipends/calculate?period=2025-01'
```

The response includes the student, period, calculated amount, currency, and status. With the example policy, the calculated amount is `26666.67 RWF` (2 of 3 observed class dates present, with 1 absence in the 100% tier). Repeating the request recalculates the existing `CALCULATED` record rather than creating another one. The dev endpoint returns an empty list if no students qualify or no observed attendance is available for the period.
