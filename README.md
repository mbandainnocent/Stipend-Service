# Stipend-Service

## Service role and architecture

Stipend Service is one microservice in a larger system. It uses an event-driven architecture: upstream services publish student-registration, attendance, and student-status events to Kafka, and this service consumes those events, validates and deduplicates them, and maintains its own PostgreSQL data for stipend processing. Student Registration Service remains the source of truth for cohort eligibility, monthly base amount, and currency. This service derives attendance-adjusted monthly stipend records from the consumed data and cohort policy; registration is not performed through an HTTP endpoint here.

Kafka is the integration path for registration, attendance, and status changes. The finance REST API is a separate interface for reading stipend and policy data and recording approval/payment transitions; it does not replace Kafka ingestion. JWT authentication and finance-role authorization are not configured yet, so finance routes are not protected for production use. A development-only calculation endpoint is available under the `dev` profile for local/manual testing, not as the production integration path.

## Current implementation status

Kafka consumers process student-registration, attendance, and student-status events. Registration events are validated, deduplicated, and used to create or update local student records. Student Registration Service is the source of truth for cohort stipend eligibility, monthly base amount, and currency; Stipend Service stores these values on the student and derives current eligibility from the cohort setting and the student's enrollment status. The amount supplied by registration is a base amount, not the final monthly payment.

Student enrollment status is stored as `ACTIVE` or `DROPPED_OUT`. Before upgrading an existing database from the previous boolean `active` column, run `src/main/resources/db/manual-migrations/001_student_status_enum.sql` once; new databases created from the current entity do not need this migration.

Attendance status is stored as the enum name (`PRESENT`, `ABSENT`, `LATE`, `EXCUSED`, or `APPROVED_HALF_DAY`). `EXCUSED` represents a Program-Manager-approved full-day absence; `APPROVED_HALF_DAY` represents approved partial attendance. `LATE` is an unauthorized half-day loss under the Igire policy. Before upgrading an existing database whose `stipend_attendance.status` column stores ordinal numbers, run `src/main/resources/db/manual-migrations/002_attendance_status_enum.sql` once; new databases created from the current entity do not need this migration. Attendance events are validated, upserted by attendance ID, and deduplicated by processed event ID. The stored event version also prevents older or duplicate-version attendance events from overwriting newer records. Before upgrading an existing database to track attendance event versions, run `src/main/resources/db/manual-migrations/003_attendance_event_version.sql` once; new databases created from the current entity do not need this migration. Student status events are validated and deduplicated; stale status versions are ignored, and a `DROPPED_OUT` status disables future stipend eligibility without changing past stipend records. A later `ACTIVE` status restores eligibility only if the cohort is configured as stipend-eligible.

Kafka consumers use Spring Kafka's `ErrorHandlingDeserializer` and a bounded retry/dead-letter handler. Transient listener failures are retried twice, then published to the source topic's `.DLT` topic; invalid event data (`IllegalArgumentException`) and malformed JSON are not retried and are sent directly to the DLT. If the DLT producer send fails, recovery is reported as failed to the listener container rather than acknowledged as successful. Embedded-Kafka tests verify transient retries, invalid-event handling, malformed JSON preservation in the DLT, and failed DLT producer sends. Manual DLT inspection and replay guidance is provided below; automated alerting/volume monitoring and replay tooling remain operational work.

### Inspect and manually replay Kafka dead-letter records

The bundled Kafka container is named `stipend-kafka`. List the service's dead-letter topics:

```sh
docker exec stipend-kafka kafka-topics \
  --bootstrap-server localhost:29092 --list \
  | grep -E '^(registration-events|student-status-events|attendance-events)\.DLT$'
```

Inspect each topic without deleting or advancing a consumer group's offsets. The console consumer prints the DLT record's key, partition, and offset; Spring Kafka's dead-letter headers include the original topic, partition, offset, and exception details.

```sh
for topic in registration-events.DLT student-status-events.DLT attendance-events.DLT; do
  echo "=== $topic ==="
  docker exec stipend-kafka kafka-console-consumer \
    --bootstrap-server localhost:29092 \
    --topic "$topic" \
    --from-beginning \
    --timeout-ms 5000 \
    --property print.headers=true \
    --property print.key=true \
    --property print.partition=true \
    --property print.offset=true
done
```

Treat DLT payloads and exception headers as potentially sensitive because registration events contain student details; restrict access to terminal logs and redact them before sharing.

There is no automated replay command/tool yet. For a manually approved replay, first capture the original key, value, headers, and DLT metadata; diagnose and correct the cause; then publish only that record's corrected event value to its original source topic (`registration-events`, `student-status-events`, or `attendance-events`). Keep the original DLT record intact. Preserve its `eventId` and event version (and key, if present); do not generate a new ID/version to bypass deduplication. The service records processed event IDs transactionally and rejects stale attendance/status versions, which helps prevent duplicate business effects, but operators must still verify the replay result in service logs/data before replaying another record. Never replay a whole DLT topic blindly.

`StipendPolicy` and `StipendTier` are JPA entities in `src/main/java/com/stipend/service/Model/`; their records are stored in the `stipend_policies` and `stipend_tiers` database tables, not in a policy file. When a student registration event arrives, `StudentRegistrationService` creates and saves a default enabled policy for the cohort if none exists. The policy starts on the cohort start date and applies attendance tiers: below 85% = 50%, 85%–89.99% = 75%, 90%–94.99% = 90%, and 95%–100% = 100%. Existing cohort policies are not overwritten on registration. Run `src/main/resources/db/manual-migrations/005_attendance_based_stipend_policy.sql` once on an existing database to replace the old absence-count tiers for all stored policies and add the attendance calculation fields; new databases created from the current entities do not need this migration. The migration intentionally replaces stored policy tiers with the approved Igire tiers. Existing cohorts without a policy are not backfilled automatically.

Stipend Service applies the monthly attendance tiers and payment formula only. The Attendance/Program Management service remains responsible for approving exceptions and publishing the appropriate statuses (`EXCUSED` for an approved full day and `APPROVED_HALF_DAY` for approved partial attendance); it must also publish attendance for every scheduled cohort date so missing participant records can be counted. Cumulative dismissal thresholds, approval workflows, transport payments, academic performance, and participant appeals are outside this service.

`MonthlyStipendService.calculateMonthlyStipend(YearMonth)` calculates a month for active, stipend-eligible students whose cohort overlaps the period. It infers scheduled cohort days from distinct attendance dates recorded for any student in the cohort; therefore, the event source must publish at least one attendance record for every scheduled class date. Each missing participant/date record is treated as an unauthorized full-day absence. A daily status contributes 1 credited day for `PRESENT`, 0.5 for `LATE` or `APPROVED_HALF_DAY`, 0 for `ABSENT`, and is excluded from eligible days for approved full-day `EXCUSED`. `LATE` contributes 0.5 unauthorized absence day, `ABSENT` and missing records contribute 1, and approved statuses contribute none. The persisted `absenceCount` and `unauthorizedAbsenceDays` both report this fractional unauthorized-absence total; for example, two full absences and one late arrival are reported as 2.5 days. For multiple records on a date, the latest updated record (then highest event version) is used. Monthly attendance is credited attendance days divided by eligible days. The stipend is monthly base amount × eligible days ÷ scheduled days × the effective attendance-tier percentage, rounded to two decimal places using `HALF_UP`. Each calculation persists the scheduled and eligible days, credited days, attendance percentage, and unauthorized absence days for review. On existing databases, apply `src/main/resources/db/manual-migrations/006_fractional_stipend_absence_count.sql` after `005_attendance_based_stipend_policy.sql` to store the fractional count and align it with existing unauthorized-absence totals. `MonthlyStipendScheduler` runs daily at 02:00 in `app.registration.business-time-zone` (default `Africa/Kigali`). It waits until the second day of the month before calculating the preceding month, giving the last day to finish; it retries unrecorded past periods on later runs and recalculates the most recently closed period daily during the following month to incorporate late Kafka events. A database run marker, unique by stipend period and scheduler date, makes overlapping instances claim the same run only once; the marker and stipend writes commit together, so failed calculations can be retried. The cron is configurable with `app.stipend.calculation.cron`. On existing databases, apply `src/main/resources/db/manual-migrations/004_monthly_stipend_calculation_runs.sql` before enabling this scheduler version. Finance read DTOs, services, and GET endpoints provide stipend and policy reads, including student names, cohort details, attendance inputs, adjusted amount, currency, and period boundaries. Approval and payment transitions are also implemented; JWT authorization and policy management/update are not yet implemented.

Student registration is accepted through the Kafka `registration-events` topic. There is no HTTP student-registration endpoint, avoiding a second write path that could bypass event validation and processed-event idempotency. Kafka bootstrap/group settings should also be consolidated: `KafkaConsumerConfig` currently hard-codes `localhost:9092` and a group ID, while the application configuration and Compose environment provide configurable values.

The stories below track implementation status. A story is complete only when its acceptance criteria are implemented and tested.

## Status at a glance

- Core processing is implemented and validated: registration, attendance ingestion, policy validation, and monthly stipend calculation are complete.
- Remaining delivery is concentrated in JWT authorization, API hardening, and Kafka operational tooling.
- Progress: 10 of 14 user stories are complete; the remaining stories are US-10, US-11, US-13, and US-14.

## Remaining work

The current backlog that still needs implementation or verification is focused on the operational and finance layers that sit on top of the completed registration, attendance, and stipend-calculation foundations:

- US-4 and US-5: finance-facing repository queries, policy validation, and monthly stipend calculation are implemented and listed below under completed stories.
- US-10: secure finance endpoints with JWT and role-based authorization.
- US-11: standardize API error contracts and validation responses.
- US-13: document and automate Kafka DLT inspection, alerting, and safe replay.
- US-14: align Kafka configuration across local and container deployments.

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

Repositories currently exist for students, attendance records, monthly stipends, processed events, and stipend policies. Monthly stipend searches support optional year, month, cohort, student, and status filters with pagination; a finance projection joins stipend rows with student names and cohort dates and derives the stipend period boundaries. Policy searches support optional program, cohort, and enabled filters with pagination and eagerly load tiers. A default policy with attendance-percentage tiers is created for a cohort when registration data arrives and no policy exists for that cohort. Student Registration Service provides cohort eligibility and the base amount in the registration event, so `StipendPolicy` contains attendance calculation rules rather than duplicating the cohort amount/currency.

Policy validation requires a start date, an end date no earlier than the start, and unique attendance thresholds from 0 through 100. The payment percentage is selected from the greatest threshold not exceeding monthly attendance. Registration validates new default policies before saving, and monthly calculation validates persisted policies before applying them. Unit tests cover invalid dates, thresholds, percentages, and missing tiers; PostgreSQL repository integration tests cover finance filters, joins, pagination, and tier loading.

Acceptance criteria:
- ✅ Add remaining query methods for finance API use cases.
- ✅ Validate policy effective dates and tier ranges/percentages; reject overlapping or ambiguous tiers.
- ✅ Add repository and policy-validation tests.

### US-5: Calculate monthly stipends
The monthly calculation service is implemented and its acceptance criteria are covered by unit and PostgreSQL concurrency integration tests. It calculates eligible students' stipends from attendance and the effective policy, and reruns safely update calculated records without changing approved, paid, or cancelled records.

Acceptance criteria:
- ✅ Determine eligible students and stipend periods using cohort start/end dates.
- ✅ Apply the Igire attendance tiers and eligible-days stipend formula to the cohort's attendance events.
- ✅ Persist monthly attendance metrics with each calculated stipend for finance review.
- ✅ Create or update a record per student and period on rerun, while leaving approved, paid, and cancelled records unchanged. The database unique constraint protects against duplicate period records.
- ✅ Late attendance and corrections are included when a `CALCULATED` stipend is rerun; approved, paid, and cancelled records remain unchanged.
- ✅ Add unit tests for cohort/month date boundaries, activation-based proration, all attendance statuses, `HALF_UP` rounding, and sequential recalculation of an existing student/month record.
- ✅ Add a PostgreSQL Testcontainers integration test for simultaneous calculations of the same student/month; it verifies the unique constraint leaves one complete record after the losing transaction rolls back. Run it with `mvn -Dtest=MonthlyStipendConcurrencyIntegrationTest test` and Docker running. Testcontainers skips it when Docker is unavailable.

### US-6: Schedule monthly calculation
As an operator, I want monthly calculations to run automatically and safely so that finance receives each period's stipend records without manual processing.

The scheduler runs daily at 02:00 in the configured business timezone. It starts calculating the most recently closed month on the second calendar day, retries unrecorded past periods on later runs, and recalculates the latest closed period daily during the following month to incorporate late Kafka events. Persistent run markers are unique by stipend period and scheduler date, preventing concurrent instances from claiming the same run; the claim and stipend writes commit together so failed calculations can be retried. The cron defaults to `0 0 2 * * *` and can be overridden with `app.stipend.calculation.cron`; the timezone defaults to `Africa/Kigali`.

Acceptance criteria:
- ✅ Configure the calculation schedule and business timezone explicitly.
- ✅ Define the attendance cutoff and catch-up behavior for missed or failed periods.
- ✅ Protect the same period/date run across instances using a persistent unique claim.
- ✅ Log completed, empty, and failed calculations.
- ✅ Add tests for cutoff behavior, catch-up period selection, duplicate claims, and reruns.

### US-7: Implement finance and policy repositories, DTOs, and services
Finance stipend and policy read services map repository results into response DTOs without serializing JPA entities. Stipend search is paginated and supports year, month, cohort, student, and status filters; student history and cohort-period lookups are also available. Responses include student names and cohort details from the student record, final calculated amount, currency, and stipend-period bounds derived from `YearMonth`. Policy list and detail lookups map their tiers while the read-only transaction is active. US-8 implements the corresponding GET controllers; JWT authorization is tracked in US-10. The OpenAPI response schema documents separate names, period dates, currency, and `HALF_UP` two-decimal rounding.

Acceptance criteria:
- ✅ Reuse existing stipend and policy repository filters; the finance stipend projection includes fields required by the response DTO.
- ✅ Map finance and policy responses to DTOs rather than serializing entities.
- ✅ Implement paginated stipend search, stipend detail, student history, cohort-period lookup, and policy lookups.
- ✅ Include first/last names, cohort details, adjusted amount, currency, and stipend period start/end dates.
- ✅ Return not-found errors for missing stipend, student, or policy records and map policy tiers within the read transaction.
- ✅ Add service tests for filters, pagination, period boundaries, amount mapping, policy tiers, and missing records.

### US-8: Implement stipend and policy read endpoints
As a finance dashboard, I want read endpoints to browse stipend records and policies so that finance staff can review calculations. JWT authentication and authorization are tracked separately in US-10.

Implemented GET routes are `GET /api/v1/stipends` (filters and pagination), `GET /api/v1/stipends/{stipendId}`, `GET /api/v1/students/{studentId}/stipends`, `GET /api/v1/cohorts/{cohortId}/stipends`, `GET /api/v1/stipend-policies`, and `GET /api/v1/stipend-policies/{policyId}`. They map through the finance/policy read services into DTOs, validate request parameters, return `400` for invalid input, and return `404` for missing stipend, student, or policy records where applicable. JWT authentication/authorization is tracked separately in US-10.

Acceptance criteria:
- ✅ Implement the stipend search, detail, student-history, and cohort-period GET endpoints from `src/main/resources/stipend-service.yaml`.
- ✅ Implement policy list and detail GET endpoints.
- ✅ Support the documented filters, pagination, response shapes, and HTTP status codes.
- ✅ Add controller tests for valid requests, invalid parameters, pagination, and missing records.
- ✅ Align the OpenAPI response schema with the finance DTOs, including separate first/last names and stipend-period start/end dates.

### US-9: Implement approval and payment workflow
The workflow supports approving a calculated stipend and marking an approved stipend as paid. `PATCH /api/v1/stipends/{stipendId}/approve` records the servlet principal name, optional approval comment, and approval timestamp. `PATCH /api/v1/stipends/{stipendId}/mark-paid` records the servlet principal name, required payment reference, and paid timestamp. Both transitions lock the stipend row and update it transactionally; only `CALCULATED → APPROVED` and `APPROVED → PAID` are allowed. Invalid or repeated transitions return `409 Conflict`, and invalid request bodies return `400 Bad Request`. JWT authentication and finance-role authorization are not configured yet (US-10); until then, these routes are not protected for production use and approval requires a principal supplied by the deployment.

Acceptance criteria:
- ✅ Implement the approval and mark-paid PATCH endpoints.
- ✅ Allow only `CALCULATED → APPROVED` and `APPROVED → PAID` transitions.
- ✅ Record approver identity, approval comment/time, payment reference, and paid time.
- ✅ Reject invalid transitions with HTTP 409 and validate request bodies.
- ✅ Make updates transactional and test successful, invalid, and repeated transitions.

### US-12: Maintain entity safety and API contract alignment
Policy and tier entities no longer generate Lombok `toString`, `equals`, or `hashCode` methods, avoiding traversal of their bidirectional lazy relationship. Tier's owning policy relationship is hidden from API schema generation; policy and stipend APIs return response DTOs rather than entities. Request behavior and response shapes are aligned with the OpenAPI contract and persistence constraints, including approval-comment and payment-reference lengths, nonblank payment references, nullable effective dates/tiers, and stipend calculation fields.

Acceptance criteria:
- ✅ Avoid generated entity methods traversing lazy policy/tier relationships.
- ✅ Keep OpenAPI schemas, DTOs, endpoint validation, and persisted fields aligned.
- ✅ Add regression coverage for policy/tier relationship safety and policy DTO serialization.

## <mark>🟡 Remaining user stories</mark>

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

### <mark>🟡 US-13: Operate and recover Kafka dead-letter records</mark>
As an operator, I want failed Kafka events to be inspectable and safely recoverable so that a bad message does not silently disappear from the registration workflow.

Acceptance criteria:
- ✅ Document commands to inspect each `.DLT` topic and identify the original topic, partition, offset, and failure cause.
- ✅ Document the manual replay procedure; automated replay tooling is still outstanding.
- ✅ Embedded-Kafka tests verify transient retries, malformed JSON delivery to the DLT, invalid event data sent without retries, and failed DLT sends not acknowledged as successful recovery.
- <mark>Alert on DLT publication and processing failures, and track DLT volume.</mark>
- <mark>Add coverage for repeated DLT publication failures and recovery after a broker outage.</mark>

### <mark>🟡 US-14: Align Kafka configuration</mark>
As an operator, I want the event-driven registration path and Kafka settings to behave consistently across local and container deployments.

Acceptance criteria:
- ✅ Use Kafka `registration-events` as the sole supported student-registration write path; the redundant `POST /api/students` endpoint has been removed.
- ✅ Keep the OpenAPI description explicit that student registration is not exposed as an HTTP write endpoint.
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

For scheduler catch-up and duplicate-run protection on an existing database, run:

```sh
PGPASSWORD="$DB_PASSWORD" psql \
  -h localhost -p 5538 -U "$DB_USERNAME" -d stipenddb \
  -v ON_ERROR_STOP=1 \
  -f src/main/resources/db/manual-migrations/004_monthly_stipend_calculation_runs.sql
```

Adjust the host, port, database, and credentials for your PostgreSQL setup. The enum migration converts `0`–`3` to `PRESENT`, `ABSENT`, `LATE`, and `EXCUSED`. It aborts if it encounters an unknown ordinal so unexpected data is not silently converted.

### Manually test registration, attendance, and stipend calculation

There is no stipend Kafka event: registration and attendance are consumed from Kafka, and the stipend record is calculated from those persisted events. The calculation trigger below is available only with the `dev` Spring profile; it is not enabled in the default or production profile.

For a complete local business-flow walkthrough with Postman requests and sample Kafka event data, see [POSTMAN_LOCAL_TESTING.md](POSTMAN_LOCAL_TESTING.md).

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

The response includes the student, period, attendance metrics, calculated amount, currency, and status. With the example policy, the calculated amount is `20000.00 RWF` (2 of 3 observed class dates present, 66.67% attendance, applying the 50% tier). Repeating the request recalculates the existing `CALCULATED` record rather than creating another one. The dev endpoint returns an empty list if no students qualify or no observed attendance is available for the period.

The dev profile also exposes GET routes for current-month stipend results. Both routes calculate the current month before reading the results:

```sh
curl 'http://localhost:8080/api/dev/stipends/stipends'
curl 'http://localhost:8080/api/dev/stipends/api/v1/studentId?studentId=20000000-0000-0000-0000-000000000001'
```

The first returns the current month's stipend rows; the second returns that student's current-month row or `404` when no row exists. These development routes delegate calculation-and-read orchestration to `DevelopmentStipendService`; production finance GET routes are documented under US-8.

## Student Registration integration handoff

Student Registration Service is the source of truth for student identity, program/cohort membership, registration dates, stipend eligibility, base amount, and currency. It must publish a complete registration snapshot to Kafka topic `registration-events` whenever a student is registered or one of those values changes. Stipend Service does not expose a student-registration HTTP write endpoint.

The JSON event shape is `StudentRegisteredEvent`:

| Field | Type | Required/meaning |
|---|---|---|
| `eventId` | UUID string | Required; unique for each published event. Reusing an already-processed ID causes the event to be ignored. |
| `studentId` | UUID string | Required; stable identifier used to update the same student. |
| `firstName`, `lastName` | string | Required and nonblank. |
| `programId`, `cohortId` | UUID strings | Required. |
| `registeredAt` | ISO-8601 instant | Required; for example `2025-01-01T08:00:00Z`. |
| `cohortStartDate`, `cohortEndDate` | ISO-8601 instants | Required; end must not precede start. These instants are converted to local dates using `Africa/Kigali` by default. |
| `registrationDate` | ISO-8601 instant | Required; used to determine stipend-period eligibility/proration. |
| `stipendEligible` | boolean | Required; whether the cohort is eligible for stipends. |
| `monthlyStipendAmount` | decimal number | Required and nonnegative when `stipendEligible` is `true`; this is the monthly base amount before attendance adjustment. |
| `stipendCurrency` | string | Required and nonblank when `stipendEligible` is `true`; use a currency code such as `RWF`. |
| `version` | integer | Required and at least 1; send the source record's revision for traceability. Registration events are deduplicated by `eventId`; unlike attendance/status events, the registration consumer currently does not reject stale versions, so publish changes in order. |

Example event (use real IDs and current values in integration):

```json
{
  "eventId": "10000000-0000-0000-0000-000000000001",
  "studentId": "20000000-0000-0000-0000-000000000001",
  "firstName": "Alex",
  "lastName": "Example",
  "programId": "30000000-0000-0000-0000-000000000001",
  "cohortId": "40000000-0000-0000-0000-000000000001",
  "registeredAt": "2025-01-01T08:00:00Z",
  "cohortStartDate": "2025-01-01T00:00:00Z",
  "cohortEndDate": "2025-12-31T00:00:00Z",
  "registrationDate": "2025-01-01T08:00:00Z",
  "stipendEligible": true,
  "monthlyStipendAmount": 40000.00,
  "stipendCurrency": "RWF",
  "version": 1
}
```

For a change, publish the full event again with the same `studentId`, a new `eventId`, updated fields, and the next source version. Send it to `registration-events` using the broker address for the producer's network (`localhost:9092` from the host or `kafka:29092` from the shared Compose network). If no stipend policy exists for the cohort, the first registration event creates the enabled default policy. Later registration events do not overwrite an existing policy.

### Test after Student Registration changes

1. Start PostgreSQL and Kafka, then run Stipend Service with the `dev` profile as described in [Local development setup](#local-development-setup). Wait for the application startup log before publishing events. Set `DB_USERNAME`, `DB_PASSWORD`, and `DB_URL` to match the local database.
2. Have Student Registration publish the complete event to `registration-events`. For a local smoke test from the host, the Kafka console producer can also publish the JSON directly:

   ```sh
   docker exec -i stipend-kafka kafka-console-producer \
     --bootstrap-server localhost:29092 \
     --topic registration-events <<'EOF'
   {"eventId":"10000000-0000-0000-0000-000000000001","studentId":"20000000-0000-0000-0000-000000000001","firstName":"Alex","lastName":"Example","programId":"30000000-0000-0000-0000-000000000001","cohortId":"40000000-0000-0000-0000-000000000001","registeredAt":"2025-01-01T08:00:00Z","cohortStartDate":"2025-01-01T00:00:00Z","cohortEndDate":"2025-12-31T00:00:00Z","registrationDate":"2025-01-01T08:00:00Z","stipendEligible":true,"monthlyStipendAmount":40000.00,"stipendCurrency":"RWF","version":1}
   EOF
   ```

   Use a fresh `eventId` and appropriate next version for every repeat/change. A successful consume is logged with the event and student IDs. Invalid events are sent to `registration-events.DLT`; see [Inspect and manually replay Kafka dead-letter records](#inspect-and-manually-replay-kafka-dead-letter-records).
3. Confirm the policy exists with `GET http://localhost:8080/api/v1/stipend-policies?cohortId=<cohortId>`. If the cohort already has a policy, registration updates do not replace its tiers.
4. Ensure the Attendance/Program Management service has published one attendance record for every scheduled cohort class date in the target month to `attendance-events`. The sample attendance payloads and statuses are in [POSTMAN_LOCAL_TESTING.md](POSTMAN_LOCAL_TESTING.md). The scheduled-day denominator is inferred from distinct cohort attendance dates; a missing student/date is treated as an unauthorized full-day absence.
5. Trigger a development calculation for the month:

   ```sh
   curl -sS -X POST \
     'http://localhost:8080/api/dev/stipends/calculate?period=2025-01'
   ```

   Replace `2025-01` with the target `YYYY-MM`. Inspect the response and then query the persisted finance record:

   ```sh
   curl -sS -G 'http://localhost:8080/api/v1/stipends' \
     --data-urlencode 'studentId=20000000-0000-0000-0000-000000000001' \
     --data-urlencode 'year=2025' \
     --data-urlencode 'month=1'
   ```

   Check that the student/cohort/base amount/currency reflect the registration snapshot, and that attendance percentage, `absenceCount`, `unauthorizedAbsenceDays`, `paymentPercentage`, and `calculatedAmount` agree with the events and policy. `absenceCount` includes half-day unauthorized losses and matches `unauthorizedAbsenceDays`. Repeating calculation updates a `CALCULATED` row; it does not change `APPROVED`, `PAID`, or `CANCELLED` rows.
6. To test a registration update, publish a new complete event for the same `studentId` with a fresh `eventId`, next version, and changed source-of-truth fields. Verify the consumed event in application logs and repeat the calculation/read for an affected period. The current service has no student-detail GET route; use the stipend response for calculation data or inspect `stipend_students` locally for registration fields.

The finance GET endpoints return stipend snapshots, not live recalculations. Only the `dev`-profile calculation endpoint triggers a calculation; the production scheduler calculates automatically according to its schedule.

## Frontend implementation handoff

The frontend can begin a finance stipend/policy read UI against the existing API. Base URL is `http://localhost:8080` for local Compose testing. JSON dates are ISO-8601, IDs are UUIDs, and decimal values (including `absenceCount`, attendance/payment percentages, amounts, and credited days) should be treated as numbers with decimal precision. Display stipend amounts with the response's `currency`; do not recalculate policy tiers or amounts in the browser.

### Available API routes

| Method and route | Use |
|---|---|
| `GET /api/v1/stipends?year=&month=&cohortId=&studentId=&status=&page=0&size=20` | Filtered, paginated stipend list. All filters are optional; `size` is 1–100. Response has `content`, `page`, `size`, `totalElements`, and `totalPages`. |
| `GET /api/v1/stipends/{stipendId}` | One stipend record. |
| `GET /api/v1/students/{studentId}/stipends?year=` | Student stipend history; optional year. Returns an array. |
| `GET /api/v1/cohorts/{cohortId}/stipends?year=&month=&status=` | Cohort records for a required year/month; optional status. Returns an array. |
| `GET /api/v1/stipend-policies?programId=&cohortId=&enabled=` | Policy list; all filters optional. Returns an array with tiers. |
| `GET /api/v1/stipend-policies/{policyId}` | One policy with its tiers. |
| `PATCH /api/v1/stipends/{stipendId}/approve` | Approval transition; optional JSON body `{"comment":"..."}`. Requires an authenticated servlet principal. |
| `PATCH /api/v1/stipends/{stipendId}/mark-paid` | Payment transition; JSON body such as `{"paymentReference":"PAY-123","paidAt":"2025-02-01T12:00:00Z"}`. Requires an authenticated servlet principal. |

Stipend list response fields include `id`, `studentId`, `firstName`, `lastName`, `cohortId`, `cohortStartDate`, `cohortEndDate`, `policyId`, `stipendYear`, `stipendMonth`, `stipendPeriodStartDate`, `stipendPeriodEndDate`, `absenceCount`, `scheduledTrainingDays`, `eligibleTrainingDays`, `creditedAttendanceDays`, `attendancePercentage`, `unauthorizedAbsenceDays`, `baseAmount`, `paymentPercentage`, `calculatedAmount`, `currency`, `status`, approval/payment metadata, `calculatedAt`, and `updatedAt`. Policy responses contain `id`, `programId`, `cohortId`, effective dates, `enabled`, timestamps, and `tiers`; each tier has `id`, `minimumAttendancePercentage`, and `paymentPercentage`.

Stipend statuses are `CALCULATED`, `NEEDS_RECALCULATION`, `APPROVED`, `PAID`, and `CANCELLED`. The supported transitions are `CALCULATED → APPROVED → PAID`; invalid transitions return `409 Conflict`. Missing resources return `404`, invalid query/body data returns `400`, and the approval/payment APIs return `401` if no servlet principal is supplied. Current error responses contain `timestamp`, `status`, `error`, `message`, `path`, and `validationErrors`.

### Current UI integration limitations

- Student registration and attendance are Kafka write integrations, not browser APIs. There is no student-list/detail endpoint, attendance-edit endpoint, policy-write endpoint, or production calculation-trigger endpoint yet.
- The manual calculation route `POST /api/dev/stipends/calculate?period=YYYY-MM` is development-profile only; the UI should use persisted finance GET routes rather than rely on it in production.
- JWT authentication, finance-role authorization, and CORS are not configured. Finance read routes are currently open, while approval/payment routes cannot be exercised locally without middleware that supplies a servlet principal. Do not expose this service directly to an untrusted browser or deploy this configuration as production security.
- Policy GET routes expose rules for display, but policy editing is not implemented. Registration events create a cohort's default policy only when one does not already exist.
- The current API has no bulk cohort/student lookup for filter controls. Until an upstream directory API is available, the UI needs cohort/student IDs supplied by its parent system or another agreed lookup service.

Coordinate any additional UI requirements (student/cohort directory, attendance details, policy editing, manual recalculation, authentication, and CORS origins) with the backend before assuming those APIs exist.
