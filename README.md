# Intumit Announcement Board

A homework-sized announcement board: Spring MVC, Spring Data JPA/Hibernate, MySQL, and a Bootstrap/jQuery frontend, packaged as a WAR for external Tomcat.

**Phase 4 status:** the announcement REST API and a static Bootstrap UI are in place. The WAR serves a single-page list with a shared create/edit modal and a delete confirmation dialog. jQuery AJAX against `/api/announcements` is deferred to Phase 5, so the table stays empty until then.

Project design notes are in [`doc/PROJECT_PLAN.md`](doc/PROJECT_PLAN.md).

## Runtime compatibility

Development runs on a **CentOS Stream 10 (aarch64)** VM. Docker and Docker Compose are **not** used. MySQL is installed directly on the VM.

| Component | Version | Notes |
| --- | --- | --- |
| OpenJDK | 21 (AppStream `java-21-openjdk-devel`) | Runtime on CentOS |
| Maven compiler target | Java 17 | Keeps the WAR portable |
| Spring Boot | 3.5.16 | Current supported 3.5.x release, compatible with Tomcat 10.1 |
| Tomcat | 10.1 (CentOS AppStream package) | External container; Tomcat is `provided` in the WAR |
| MySQL | 8.4 LTS Community Server | Official MySQL EL10 repository, not Innovation/9.x |
| Flyway | Managed by Spring Boot 3.5.16 | `flyway-core` plus `flyway-mysql` |

Spring Boot 4 and Tomcat 11 were not used because CentOS Stream 10 ships Tomcat 10.1.

## CentOS development SSH workflow

From macOS:

```bash
ssh -i ~/.ssh/id_ed25519_centos_vm -o IdentitiesOnly=yes haha@192.168.64.26
```

On the VM, the GitHub deploy key is `~/.ssh/id_ed25519_github`, selected for `github.com` through `~/.ssh/config`. The application checkout is:

```text
/home/haha/projects/intumit-announcement-board
```

All builds, tests, database work, and application execution happen on this VM.

```bash
cd /home/haha/projects/intumit-announcement-board
git checkout main   # or the current working branch
git pull --ff-only
```

## Protected database environment

Database credentials are **not** stored in Git. On the VM they live in mode `600` files:

- `/etc/announcement-board.env` (read by the Tomcat systemd unit)
- `/home/haha/.config/announcement-board/env` (used for Maven builds/tests)

See [`.env.example`](.env.example) for the required variable names and non-secret placeholders:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

Load the variables before Maven with the helper (it does not print secrets):

```bash
python3 scripts/with-db-env ./mvnw -B clean test
python3 scripts/with-db-env ./mvnw -B clean package
```

MySQL listens only on `127.0.0.1`. Port 3306 is not opened in the firewall.

## Schema and Flyway

Flyway owns schema creation. The initial migration is [`src/main/resources/db/migration/V1__create_announcements.sql`](src/main/resources/db/migration/V1__create_announcements.sql). It creates the `announcements` table, a `CHECK` constraint requiring `deadline_date >= publish_date`, and an index on `(publish_date DESC, id DESC)`.

Hibernate is configured with `spring.jpa.hibernate.ddl-auto=validate`. It maps the `Announcement` entity and refuses to start if the database does not match; it does not create or update tables. Do not add `schema.sql` or `data.sql` alongside Flyway.

SQL logging is **disabled by default**. Enable it only while debugging:

```bash
JPA_SHOW_SQL=true JPA_FORMAT_SQL=true python3 scripts/with-db-env ./mvnw -B test
```

After tests or a Tomcat deploy, inspect non-secret schema metadata without printing credentials:

```bash
python3 scripts/describe-schema
```

That helper reports table names, Flyway version and success state, `SHOW CREATE TABLE announcements`, character set/collation, and the `announcements` row count. Repository and API tests roll back inserted rows, so the table should remain empty except for Flyway metadata.

## REST API

Base path (external Tomcat context included): `/announcement-board/api/announcements`.

Controllers map `/api/announcements`. Tomcat adds the `/announcement-board` context from the WAR name.

| Method | Path | Success | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/announcements?page=0&size=10` | `200` | One server-side page, sorted by `publishDate DESC`, then `id DESC` |
| `GET` | `/api/announcements/{id}` | `200` | One announcement for editing |
| `POST` | `/api/announcements` | `201` | Create an announcement |
| `PUT` | `/api/announcements/{id}` | `200` | Replace all editable fields |
| `DELETE` | `/api/announcements/{id}` | `204` | Delete an announcement |

Query parameters: `page` is zero-based and defaults to `0`. `size` defaults to `10` and must be between `1` and `100`. Client-controlled sorting is not accepted.

Request body fields: `title`, `publisher`, `publishDate`, `deadlineDate`, `content`. All are required. `title` max 200 characters, `publisher` max 100 characters, and `deadlineDate` must be on or after `publishDate`.

Response body fields: `id`, `title`, `publisher`, `publishDate`, `deadlineDate`, `content`. Persistence timestamps are not exposed.

Example create request:

```json
{
  "title": "System maintenance",
  "publisher": "Administrator",
  "publishDate": "2026-09-11",
  "deadlineDate": "2026-09-18",
  "content": "The service will be unavailable."
}
```

Example list response:

```json
{
  "items": [
    {
      "id": 12,
      "title": "System maintenance",
      "publisher": "Administrator",
      "publishDate": "2026-09-11",
      "deadlineDate": "2026-09-18",
      "content": "The service will be unavailable."
    }
  ],
  "page": 0,
  "size": 10,
  "totalItems": 21,
  "totalPages": 3
}
```

The `id` value above is illustrative. The database assigns IDs; do not treat example IDs as permanent data.

Example error response:

```json
{
  "message": "Validation failed",
  "fieldErrors": {
    "title": "Title is required"
  }
}
```

Validation failures and invalid `page`/`size` values return `400`. Missing announcements return `404`. Malformed JSON and invalid dates return `400` with a safe message. Database constraint violations return a safe `400` without SQL or table details. Unexpected errors return a generic `500` and are logged on the server. Error bodies never include stack traces or exception class names.

## Remote curl examples

These assume an SSH tunnel (see below) or a shell on the VM. They do not use database credentials.

```bash
BASE=http://127.0.0.1:8080/announcement-board/api/announcements

curl -sS "$BASE"

CREATED=$(curl -sS -X POST "$BASE" \
  -H 'Content-Type: application/json' \
  -d '{
    "title": "System maintenance",
    "publisher": "Administrator",
    "publishDate": "2026-09-11",
    "deadlineDate": "2026-09-18",
    "content": "The service will be unavailable."
  }')
ID=$(printf '%s' "$CREATED" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')

curl -sS "$BASE/$ID"

curl -sS -X PUT "$BASE/$ID" \
  -H 'Content-Type: application/json' \
  -d '{
    "title": "Updated maintenance",
    "publisher": "Administrator",
    "publishDate": "2026-09-11",
    "deadlineDate": "2026-09-19",
    "content": "The window was extended."
  }'

curl -sS "$BASE?page=0&size=1"

curl -sS -D - -X POST "$BASE" \
  -H 'Content-Type: application/json' \
  -d '{"title":""}'

curl -sS -D - "$BASE/999999999"

curl -sS -D - -X DELETE "$BASE/$ID"
```

`ID` comes from the create response. Delete smoke-test records when you are done.

## Remote build and test

On the CentOS VM:

```bash
python3 scripts/with-db-env ./mvnw -B clean test
python3 scripts/with-db-env ./mvnw -B clean package
```

The packaged artifact is `target/announcement-board.war`. Deploy it to the system Tomcat `webapps` directory. On startup Flyway applies pending migrations, then Hibernate validates the schema. The deployed WAR serves the Bootstrap UI at `/announcement-board/` and the REST API at `/announcement-board/api/announcements`.

## Frontend UI

The WAR serves a single Bootstrap 5.3.8 page at the application root (`index.html`) with custom assets at `css/app.css` and `js/app.js`. Bootstrap and jQuery 3.7.1 are loaded from pinned CDN URLs with `integrity` and `crossorigin` attributes. Asset paths are relative so the page works under the `/announcement-board` context path.

The page includes the list table, empty/loading/error/pagination DOM hooks, one create/edit modal, and a delete confirmation modal. `app.js` only resets the shared form and prevents a full-page submit. It does not call `/api/announcements`.

## SSH tunnel for local browser access

Tomcat's HTTP port stays on the VM loopback/private interface during development. Do not open port 8080 in the firewall. Forward it from macOS instead:

```bash
ssh -i ~/.ssh/id_ed25519_centos_vm -o IdentitiesOnly=yes \
  -N -L 8080:127.0.0.1:8080 \
  haha@192.168.64.26
```

Then open:

```text
http://127.0.0.1:8080/announcement-board/
http://localhost:8080/announcement-board/
```

That URL is the static Bootstrap UI. List loading, pagination, create, edit, and delete still require the Phase 5 AJAX work. API calls use `/announcement-board/api/announcements`.

## What is not in this phase

- jQuery AJAX, list loading, pagination requests, create/edit/delete requests, or API error handling in the browser
- Authentication, roles, attachments, rich text, search, filtering, or configurable sorting
- Sample production data
- Docker, Docker Compose, or containers of any kind
