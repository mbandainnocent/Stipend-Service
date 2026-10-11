# Stipend-Service

## Current implementation status

The service currently consumes student-registration events, validates and deduplicates them transactionally, and creates or updates student records. Student registration stores cohort membership and student details only; stipend rules belong to the cohort policy and are not copied from registration events. Persistence models exist for attendance, policies, tiers, monthly stipends, and processed events. Attendance persistence and monthly stipend calculation are not implemented yet.

The following stories describe remaining work, the implementation order. A story is complete only when its acceptance criteria are implemented and tested.

## Completed user stories

### US-1: Verify application behavior and local startup
Tests pass with Java 17 and Maven 3.9.9 (10 tests: 8 service tests and 2 consumer tests). Unit tests cover skipping already-processed events. Local startup with PostgreSQL and Kafka has been verified. Supported Java/Maven versions and local service setup are documented below.

### US-2: Configure the PostgreSQL connection
PostgreSQL connection settings are supplied through environment variables, and the service has been verified to connect and persist entity data.

- `DB_URL` defaults to `jdbc:postgresql://localhost:5432/stipenddb`.
- `DB_USERNAME` and `DB_PASSWORD` provide credentials without committing them.
- Local PostgreSQL setup and required environment variables are documented below.

## Remaining user stories

### US-3: Consume and persist attendance events
As the stipend service, I want attendance events recorded idempotently so that monthly calculations use reliable attendance data.

The `AttendanceEvent` message type, `AttendanceRecord` entity, and `AttendanceRecordRepository` are in place. The Kafka consumer, event validation, persistence flow, and deduplication are still pending.

Acceptance criteria:
- Consume the configured attendance topic and deserialize `AttendanceEvent`.
- Validate required identifiers, status, attendance date, and event version before persistence.
- Create or update attendance records consistently and make duplicate delivery safe using the processed-event uniqueness constraint.
- Configure bounded retries and a dead-letter topic for invalid or repeatedly failing messages.
- Add tests for duplicate events, updates, invalid messages, and database rollback behavior.

### US-4: Add stipend repositories and policy validation
As the calculation and finance services, I want queryable repositories and valid policies so that stipend rules can be selected predictably.

`StipendPolicyRepository` exists with a cohort lookup. Monthly-stipend and tier repositories, effective-date selection, and policy validation are still pending.

Acceptance criteria:
- Add repositories for monthly stipends, policies, and tiers with query methods required by calculation and API use cases.
- Select policies by program, cohort, enabled state, and effective date.
- Validate policy amounts, currency, effective dates, and tier ranges/percentages; reject overlapping or ambiguous tiers.
- Add repository and policy-validation tests.

### US-5: Calculate monthly stipends
As the stipend service, I want to calculate each eligible student's stipend from attendance and the effective policy so that monthly stipend records are produced consistently.

Acceptance criteria:
- Determine eligible students and stipend periods using cohort start/end dates and an agreed attendance cutoff.
- Count absences according to documented rules for `PRESENT`, `ABSENT`, `LATE`, and `EXCUSED`.
- Select the applicable policy and absence tier, then calculate amounts using `BigDecimal` and the policy currency.
- Create at most one record per student, year, and month; safely handle retries and existing records.
- Define how late attendance changes affect calculated or already approved stipends.
- Add tests for date boundaries, attendance statuses, tier boundaries, rounding, and duplicate calculation.

### US-6: Schedule monthly calculation
As an operator, I want monthly calculations to run automatically and safely so that finance receives each period's stipend records without manual processing.

Acceptance criteria:
- Configure the calculation schedule and business timezone explicitly.
- Define the calculation cutoff and behavior for missed or delayed scheduler runs.
- Ensure concurrent instances and retries cannot create duplicate monthly stipend records.
- Provide useful logs and operational visibility for completed, skipped, and failed runs.
- Add tests for scheduled calculation and reruns.

### US-7: Implement finance and policy repositories, DTOs, and services
As a finance user, I want stipend and policy data exposed through validated API models and services so that the dashboard can safely review records and rules.

Acceptance criteria:
- Add stipend search repositories for student, cohort, period, and status, with pagination support.
- Add policy queries by program, cohort, and enabled state, including associated tiers.
- Implement validated request/response DTOs matching the OpenAPI contract; do not serialize JPA entities directly.
- Implement stipend search, student history, cohort-period lookup, and policy lookup.
- Return not-found errors for missing records and handle lazy-loaded policy tiers safely.

### US-8: Implement stipend and policy read endpoints
As a finance dashboard, I want authenticated endpoints to browse stipend records and policies so that finance staff can review calculations.

Acceptance criteria:
- Implement the GET stipend search, single stipend, student history, and cohort-period endpoints defined in `src/main/resources/stipend-service.yaml`.
- Implement the GET policy list and single-policy endpoints defined in the OpenAPI specification.
- Support the specified filters, pagination, response shapes, and HTTP status codes.
- Add controller tests for valid requests, invalid parameters, pagination, and missing records.

### US-9: Implement approval and payment workflow
As a finance user, I want to approve calculated stipends and record completed payments so that stipend processing has an auditable state transition.

Acceptance criteria:
- Implement `PATCH /api/v1/stipends/{stipendId}/approve` and `PATCH /api/v1/stipends/{stipendId}/mark-paid`.
- Allow only `CALCULATED → APPROVED` and `APPROVED → PAID` transitions.
- Record approver identity, approval comment/time, payment reference, and paid time.
- Reject invalid transitions with HTTP 409 and validate request bodies.
- Make updates transactional and add tests for allowed transitions, invalid transitions, and repeated requests.

### US-10: Secure finance endpoints with JWT
As an operator, I want finance endpoints protected by JWT authentication and role-based authorization so that stipend and payment data is only accessible to authorized users.

Acceptance criteria:
- Add Spring Security and configure JWT validation using the agreed identity provider and issuer.
- Require the finance role for stipend and policy API endpoints.
- Return consistent 401 and 403 responses.
- Configure CORS only for approved dashboard origins and test access-control rules.

### US-11: Standardize API error responses
As an API consumer, I want consistent error bodies so that the dashboard can handle failures predictably.

Acceptance criteria:
- Add a global exception handler and `ErrorResponse` matching the OpenAPI schema.
- Map validation, authentication, authorization, not-found, and invalid-state errors to 400, 401, 403, 404, and 409 respectively.
- Include timestamp, status, error, message, path, and field validation details where applicable.
- Add tests that verify response status and body for each error category.

### US-12: Maintain entity safety and API contract alignment
As a developer, I want persistence models and the OpenAPI contract to stay safe and consistent so that serialization and schema changes do not introduce runtime errors.

Acceptance criteria:
- Avoid generated `toString`, `equals`, or `hashCode` traversing bidirectional or lazy JPA relationships.
- Keep OpenAPI schemas, DTO validation, API behavior, and database fields aligned.
- Add regression tests for policy/tier serialization and entity relationship behavior.

## Local development setup

### Prerequisites

- Java 17 is the project target (`maven.compiler.source` and `maven.compiler.target`). The current Maven build has been run with Maven 3.9.9; use Maven 3.9.x.
- PostgreSQL must be available with a database named `stipenddb` and a user that can create/update tables. The bundled Docker Compose setup publishes PostgreSQL on `localhost:5538`; a separately installed PostgreSQL can use the default `localhost:5432`.
- Kafka must be available at `localhost:9092`. The registration consumer reads from the `registration-events` topic.

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
mvn spring-boot:run
```

For a separately installed PostgreSQL on its default port, use `DB_URL=jdbc:postgresql://localhost:5432/stipenddb`. The application defaults to `JPA_DDL_AUTO=update` for local development. For a separately managed schema, set `JPA_DDL_AUTO=validate`; production deployments should use versioned migrations. Kafka's bootstrap address is configured as `localhost:9092` in `src/main/resources/application.yaml`.
