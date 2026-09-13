# Intumit Announcement Board

A homework-sized announcement board: Spring MVC, Spring Data JPA/Hibernate, MySQL, and a Bootstrap/jQuery frontend, packaged as a WAR for external Tomcat.

**Phase 2 status:** the MySQL persistence layer is in place (Flyway schema, JPA entity, Spring Data repository, and repository tests). Announcement CRUD API and UI are not implemented yet.

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
git checkout codex/phase-2-persistence   # or the current working branch
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

That helper reports table names, Flyway version and success state, `SHOW CREATE TABLE announcements`, character set/collation, and the `announcements` row count. Repository tests roll back inserted rows, so the table should remain empty except for Flyway metadata.

## Remote build and test

On the CentOS VM:

```bash
python3 scripts/with-db-env ./mvnw -B clean test
python3 scripts/with-db-env ./mvnw -B clean package
```

The packaged artifact is `target/announcement-board.war`. Deploy it to the system Tomcat `webapps` directory. On startup Flyway applies pending migrations, then Hibernate validates the schema. This WAR still has no announcement CRUD endpoints.

## SSH tunnel for local browser access

Tomcat's HTTP port stays on the VM loopback/private interface during development. Do not open port 8080 in the firewall. Forward it from macOS instead:

```bash
ssh -i ~/.ssh/id_ed25519_centos_vm -o IdentitiesOnly=yes \
  -L 8080:127.0.0.1:8080 \
  haha@192.168.64.26
```

Then open:

```text
http://127.0.0.1:8080/announcement-board/
```

A 404 at that URL is expected until the UI phase.

## What is not in this phase

- Announcement list/create/edit/delete API and UI
- Request/response DTOs, Bean Validation on the API, or Bootstrap/jQuery pages
- Sample production data
- Docker, Docker Compose, or containers of any kind
