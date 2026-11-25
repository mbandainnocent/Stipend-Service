# Deploying on Railway

Stipend Service is deployed into the **same Railway project** as the attendance
service, because it consumes attendance's Kafka broker over Railway's private network
and validates the JWTs attendance issues. Deploy `kafka`, `AttendanceDB` and
`attendance` first, following `RAILWAY.md` in the attendance repo.

| Railway service | Source |
|---|---|
| `StipendDB` | Railway PostgreSQL (separate from attendance's database) |
| `stipend` | this GitHub repo (`railway.json` → Dockerfile) |

## 1. Database

**New → Database → PostgreSQL**, rename it `StipendDB`.

A fresh database needs no manual steps: Hibernate creates the schema on first start.
The scripts in `src/main/resources/db/manual-migrations/` are only for **upgrading an
existing older database**. Run them in order (001 → 003) in
**StipendDB → Data → Query** before deploying the new version.

## 2. Stipend app

1. **New → GitHub Repo** → this repository. `railway.json` makes Railway build the
   `Dockerfile` and health-check `/actuator/health`.
2. **Variables → Raw Editor**:

   ```
   SPRING_DATASOURCE_URL=jdbc:postgresql://${{StipendDB.PGHOST}}:${{StipendDB.PGPORT}}/${{StipendDB.PGDATABASE}}
   SPRING_DATASOURCE_USERNAME=${{StipendDB.PGUSER}}
   SPRING_DATASOURCE_PASSWORD=${{StipendDB.PGPASSWORD}}
   SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka.railway.internal:9092
   APP_JWT_SECRET=${{shared.APP_JWT_SECRET}}
   APP_CORS_ALLOWED_ORIGINS=https://<finance dashboard domain>
   ```

   - `APP_JWT_SECRET` **must be identical** to the attendance service's value, or every
     token is rejected with 401. Using a project Shared Variable keeps them in sync.
   - `APP_CORS_ALLOWED_ORIGINS` is a comma-separated list of exact browser origins
     (scheme + host, no trailing slash). Leave it unset if no browser calls the API
     directly; cross-origin requests are then refused.
   - **Do not** set `SPRING_PROFILES_ACTIVE=dev` here. The `dev` profile exposes the
     manual calculation endpoint `/api/dev/stipends/calculate`. Without it, the nightly
     scheduler (02:00) calculates stipends.
   - Optional: `APP_SECURITY_FINANCE_ROLES` (default `ADMIN,FINANCE`).
3. **Settings → Networking → Generate Domain**. Railway terminates HTTPS and injects
   `PORT`; the app listens on it.

## Checks

- `https://<stipend domain>/actuator/health` → `{"status":"UP"}`
- `https://<stipend domain>/swagger-ui/index.html`
- A token from `POST https://<attendance domain>/api/v1/auth/login` (ADMIN or FINANCE
  user) works on `GET https://<stipend domain>/api/v1/stipends`.
- Stipend logs show partitions assigned for `stipend-service-*-group` consumers.

Order matters only for the first deploy: if stipend starts before Kafka is up, it
keeps retrying and connects once the broker is ready.
