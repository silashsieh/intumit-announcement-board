# Phase 7 acceptance evidence

Date tested: 2026-09-14

Tested Git commit: `7dbdc6e97731143c094f87d5099d04a7e8059b7c` (`codex/phase-7-acceptance`)

Host: CentOS Stream 10 (Coughlan) aarch64, `<ssh-user>@<centos-host>`

| Component | Version |
| --- | --- |
| OpenJDK | 21.0.12.1 (Red_Hat-21.0.12.1.1-2) |
| Maven compiler target | Java 17 |
| Spring Boot | 3.5.16 |
| External Tomcat | 10.1.49 (`tomcat-10.1.49-3.el10.noarch`) |
| MySQL Community Server | 8.4.11 (`mysql-community-server-8.4.11-1.el10.aarch64`) |
| Application context | `/announcement-board` |

Baseline before Phase 7 work (commit `3b5103d599e4666f79828756ab0d3e2289bad0e2`):

- `python3 scripts/with-db-env ./mvnw -B test` → **Tests run: 30, Failures: 0, Errors: 0, Skipped: 0**
- `tomcat` and `mysqld` active and enabled
- `announcements` row count: 0
- UI `/`, `css/app.css`, `js/app.js`, and `GET /api/announcements?page=0&size=10` returned 200
- Flyway history: version `1`, success `1`

No credential values were printed, copied, or committed. Protected environment files were checked by ownership and mode only.

## How each check was run

| Method | What it covers |
| --- | --- |
| Automated Maven tests | JUnit/MockMvc against the installed MySQL database |
| API acceptance script | `python3 scripts/acceptance-test` against the live Tomcat WAR |
| Deployment helper / smoke test | `python3 scripts/deploy-centos` and `python3 scripts/smoke-test-deployment` |
| Manual/browser verification | Google Chrome against `http://localhost:8080/announcement-board/` through the SSH tunnel |
| Service-level verification | `systemctl`, `ss`, `firewall-cmd`, `getenforce`, `stat`, `journalctl`, `python3 scripts/describe-schema` |

## Final deploy

Recorded after deploying a clean committed Phase 7 tree:

| Item | Result |
| --- | --- |
| Git commit | `7dbdc6e97731143c094f87d5099d04a7e8059b7c` |
| WAR SHA-256 | `6a8a0e730703a48e7e899d537922a9b141db07d6e0928b1737e8cbf984be97df` |
| `python3 scripts/deploy-centos` | success (startup ready after 5.5s; embedded smoke-test success) |
| Independent `python3 scripts/smoke-test-deployment` | success (`P6SMOKE-7fa8823b983c`, created/deleted id 669) |
| Independent `python3 scripts/acceptance-test` | success (`P7ACCEPT-58b4c43997dc`, created ids 670–681, cleaned up, `final_count=0`) |
| Maven tests during deploy | **Tests run: 30, Failures: 0, Errors: 0, Skipped: 0**; WAR packaging succeeded |

## Project plan checklist (section 10)

| Checklist item | Result | Evidence |
| --- | --- | --- |
| List displays title, publish date, and deadline date | Pass | Browser: created row showed title plus both dates. API list items include those fields. |
| Pagination works with more records than the page size | Pass | API script created 12 temporary rows: page 0 had 10 items, page 1 had the remainder. Browser created more than 10 rows; Previous/Next/numbered buttons requested zero-based `page` with `size=10`. |
| A valid announcement can be created and appears in the list | Pass | Browser create through the shared modal. API create returned 201 and the record appeared in list metadata. |
| An existing announcement can be edited | Pass | Browser Edit loaded all five fields and the updated title/dates appeared in the list. API PUT returned the exact written fields. |
| Delete asks for confirmation and removes the record | Pass | Browser opened the Bootstrap delete modal; Cancel left the row; Confirm issued DELETE 204 and removed the row. API DELETE returned 204, then GET returned 404. |
| Blank required fields are rejected | Pass | Browser marked blank controls invalid. API POST of empty/null required fields returned 400 with `fieldErrors`. |
| A deadline before the publish date is rejected | Pass | Browser marked the deadline control invalid. API POST returned 400 with `fieldErrors.deadlineDate`. |
| Missing records return a clear `404` response | Pass | API GET `/api/announcements/999999999` returned 404, message `Announcement not found`, safe `fieldErrors` object. |
| Refreshing the browser preserves data in MySQL | Pass | Browser reload kept the created row. Dedicated Tomcat-only restart check is in Persistence across Tomcat restart. |
| The layout is usable on desktop and a narrow screen | Pass | Browser at 1280×800 and 375×812; action columns remained reachable after horizontal scroll in `.table-responsive`. |
| The application runs from the deployed Tomcat URL on CentOS | Pass | Live URL through the SSH tunnel: `http://localhost:8080/announcement-board/`. On the VM: `http://127.0.0.1:8080/announcement-board/`. |
| Database passwords and other secrets are absent from GitHub | Pass | No secret files committed. Protected env files remain mode `600`. Error bodies and committed files were checked for password/JDBC/stack-trace leakage. |
| README contains build, database, deployment, and test instructions | Pass | README commands were run on this VM: SSH, `with-db-env` Maven test, `describe-schema`, deploy helper, smoke test, acceptance-test, and the documented tunnel URLs. |

## Automated Maven tests

Command: `python3 scripts/with-db-env ./mvnw -B test`

Result at baseline (commit `3b5103d`): **30 tests, 0 failures**.

Phase 7 adds assertions inside existing UI tests (modal hide retry and `:focus-visible` CSS). It does not add a new test class. The deploy helper runs `clean package`, which re-runs the suite.

## API acceptance script

Helper: `scripts/acceptance-test`

Default base URL: `http://127.0.0.1:8080/announcement-board`

Prefix: `P7ACCEPT-<token>`. Created IDs are printed and deleted only by those IDs in a `finally` path.

First run against the live WAR (prefix `P7ACCEPT-ffe5aed2391c`):

```text
ok: initial list response shape
baseline_ids=[]
baseline_count=0
ok: created ... fetched ... updated
ok: created 12 temporary records to exceed page size 10
ok: server-side page 0 metadata and max 10 items
ok: remaining items are on the next zero-based page
ok: ordering is publishDate DESC, then id DESC
ok: UI pagination labels are one-based while API pages stay zero-based
ok: blank required fields return 400
ok: whitespace-only fields return 400
ok: deadline before publish date returns 400
ok: title over 200 characters returns 400
ok: publisher over 100 characters returns 400
ok: malformed JSON returns a safe 400
ok: missing record returns a safe 404
ok: delete returned 204
ok: deleted record subsequently returns 404
ok: all temporary records removed; final_count=0 baseline_count=0
acceptance-test: success
```

The independent post-deploy run is recorded in Final deploy.

## Browser acceptance

Tunnel:

```bash
ssh -N -L 8080:127.0.0.1:8080 -i "<ssh-key-path>" "<ssh-user>@<centos-host>"
```

Browser URL: `http://localhost:8080/announcement-board/`

Driver: Google Chrome (not curl-only). Temporary records used the `P7ACCEPT-` prefix and were deleted by exact IDs afterward.

| Check | Result |
| --- | --- |
| Empty state when there are no records | Pass |
| New announcement opens the shared modal | Pass |
| Valid announcement created through the UI | Pass |
| Refresh preserves the row in MySQL | Pass |
| Title, publish date, and deadline date in the list | Pass |
| Edit loads all five fields | Pass |
| Editing persists and updates the list | Pass |
| Delete opens the Bootstrap confirmation modal | Pass |
| Cancel leaves the record unchanged | Pass after modal-hide fix |
| Confirm removes it | Pass after modal-hide fix |
| Blank and whitespace-only required fields rejected | Pass |
| Deadline before publish date rejected | Pass |
| More than 10 temporary records paginate | Pass |
| Previous, Next, and numbered pages request the correct server page | Pass |
| Deleting the only record on a later page returns to the preceding page | Pass |
| Loading, empty, success, and error states | Pass |
| HTML-like text rendered as text and does not execute | Pass |
| Non-ASCII text persists and renders | Pass |
| Desktop layout usable | Pass |
| 375px viewport usable; action columns reachable | Pass |
| Keyboard focus remains visible | Pass after CSS specificity fix (button `:focus-visible` outline) |
| Modal focus behavior usable | Pass (title focused on open; Tab moved to publisher) |
| Bootstrap and jQuery load | Pass |
| No uncaught application JavaScript errors | Pass |

## Persistence across Tomcat restart

Recorded after the clean Phase 7 deploy. Only Tomcat is restarted; MySQL is not reset.

| Step | Result |
| --- | --- |
| Create one uniquely identified Phase 7 record | Pass: id **682**, title `P7ACCEPT-d682779a62c0-restart` |
| Fetch it successfully | Pass: GET 200, fields match |
| `sudo systemctl restart tomcat` and wait until ready | Pass: Tomcat returned to `active`; UI root 200 |
| Fetch the same record | Pass: GET `/api/announcements/682` still 200 with the same title, publisher, dates, and content |
| Delete that exact record | Pass: DELETE 204, subsequent GET 404 |

## Service, network, and security

| Check | Result |
| --- | --- |
| `tomcat` active and enabled | Pass |
| `mysqld` active and enabled | Pass |
| Flyway remains V1, success=1 | Pass (`python3 scripts/describe-schema`) |
| Hibernate schema validation | Pass: `ddl-auto=validate`; Tomcat journal shows Flyway connect, Hibernate ORM 6.6.53.Final, `Started ServletInitializer`, and no `Schema-validation` errors |
| Tomcat 8080 loopback-only | Pass: `[::ffff:127.0.0.1]:8080` |
| MySQL 3306 loopback-only | Pass: `127.0.0.1:3306` |
| firewalld running; ports 8080 and 3306 not opened | Pass: listed ports empty; services `cockpit dhcpv6-client ssh` |
| SELinux enforcing | Pass |
| `~/.config/announcement-board/env` mode 600 | Pass (user-owned) |
| `/etc/announcement-board.env` mode 600 | Pass (`root:root`) |
| No credentials in application responses | Pass (API error bodies checked by the acceptance script) |

## Database cleanup

| Point | `announcements` count |
| --- | --- |
| Baseline | 0 |
| After API acceptance-test | 0 |
| After browser runs (IDs deleted individually) | 0 |
| After Tomcat-restart persistence record | 0 (id 682 deleted) |

Expected final state matches baseline: **zero application rows**. Flyway history was not deleted.

## Defects found and fixed

### 1. Delete/create modal could stay open

**Symptom.** Clicking Delete, then Cancel or Confirm while Bootstrap was still finishing the show transition, left `#delete-modal` with class `show`. Bootstrap 5.3 `Modal.hide()` returns immediately when `_isTransitioning` is true, and Modal never adds a `showing` class.

**Fix.** `app.js` patches `Modal.hide` so a hide requested during the show transition waits for `shown.bs.modal`. `hideModal()` always calls that patched `hide()` unless the instance is already fully closed: it does not treat `display === "none"` as hidden while `_isShown` or `_isTransitioning` is true, and it does not look for a Bootstrap `showing` class.

**Regression.** `AnnouncementUiTest.appJsIsServed` asserts `__abHidePatched`, `shown.bs.modal.abHide`, `_isShown` / `_isTransitioning` guards, and the retry loop.

### 2. Keyboard focus outline did not win on Bootstrap buttons

**Symptom.** `app.css` set `:focus-visible { outline: ... }`, but Bootstrap `.btn:focus-visible { outline: 0 }` is more specific, so the primary button showed no custom outline.

**Fix.** Add matching `.btn:focus-visible` / `.page-link:focus-visible` / `.form-control:focus-visible` / `.btn-close:focus-visible` rules in `app.css`.

**Regression.** `AnnouncementUiTest.appCssIsServed` asserts `.btn:focus-visible`.

## Known non-blocking observations

- Flyway logs `Flyway upgrade recommended: MySQL 8.4 is newer than this version of Flyway and support has not been tested. The latest supported version of MySQL is 8.1.` Schema remains V1 and the application starts. No Flyway upgrade was performed.
- Hibernate SQL for repository tests is visible in Maven output because those tests use the JPA test slice defaults. Production SQL logging stays off unless `JPA_SHOW_SQL` is set.

## Documentation review

README build, database, deployment, tunnel, API, and test commands were executed on this VM. The Phase 7 acceptance helper and this evidence file are linked from README. `doc/DEPLOYMENT.md` needed no command corrections. `doc/PROJECT_PLAN.md` was not changed; the modal and focus fixes do not alter the design.

Final homework screenshots and the documentation closeout are recorded in the Phase 8 section below.

## Phase 8 closeout

Date tested: 2026-09-14

Host: CentOS Stream 10 (Coughlan) aarch64, `<ssh-user>@<centos-host>`

Phase 8 is documentation and packaging: final README, implemented-application screenshots, deployment documentation, and repository audit. Application behavior was not redesigned. No defects were found that required a code change.

The development VM remains private. Nginx and Certbot were **not** installed on it. This closeout does not convert that VM into a public server.

### Screenshots

Captured through the SSH tunnel from `http://localhost:8080/announcement-board/` using temporary records:

- Prefix: `P8SCREENSHOT-437e2655a007`
- IDs **803–814** (deleted by those IDs after capture)

Images (do not overwrite the assignment reference files):

- `docs/images/app-list.png`
- `docs/images/app-create.png`
- `docs/images/app-edit.png`
- `docs/images/app-mobile.png`

### Final deploy

Recorded after the Phase 8 squash merge and final deployment of merged `main`:

| Item | Result |
| --- | --- |
| Git commit | `f6237477cb079e14b5df9687ebc688adb7889133` |
| WAR SHA-256 | `a327a26b11b936a331756690425be806de6fad09248a3efb70d505e26931d7f7` |
| `python3 scripts/deploy-centos` | success (startup ready after 6.0s; embedded smoke-test success; previous WAR backed up under `/var/backups/announcement-board/`) |
| Independent `python3 scripts/smoke-test-deployment` | success (`P6SMOKE-d399cda59536`, created/deleted id 893) |
| Independent `python3 scripts/acceptance-test` | success (`P7ACCEPT-f7bbed678166`, created ids 894–905, cleaned up, `final_count=0`) |
| Maven tests during deploy | **Tests run: 30, Failures: 0, Errors: 0, Skipped: 0**; WAR packaging succeeded |

### Browser verification

Tunnel: `ssh -N -L 8080:127.0.0.1:8080 -i "<ssh-key-path>" "<ssh-user>@<centos-host>"`

URL: `http://localhost:8080/announcement-board/`

| Check | Result |
| --- | --- |
| Empty state after deploy | Pass |
| Create through the shared modal | Pass (`P8VERIFY-board-create`, id 846) |
| Edit persists in the list | Pass (title became `P8VERIFY-board-edited`) |
| Pagination with more than 10 rows | Pass (12 rows, page 1 of 2; page 2 showed the remaining two rows; Next disabled on the last page) |
| Delete confirmation modal | Pass (deleted `P8VERIFY-page-00` / id 847) |
| Desktop layout usable | Pass (1280×800) |
| 375px layout usable; action columns reachable by horizontal scroll | Pass |
| Screenshots match the deployed UI | Pass |

Temporary `P8VERIFY-*` IDs 846–857 were deleted after the check. Id 847 was already absent (browser delete) and returned 404 on cleanup.

### Service, network, and security

| Check | Result |
| --- | --- |
| `tomcat` active and enabled | Pass |
| `mysqld` active and enabled | Pass |
| Flyway remains V1, success=1 | Pass |
| Tomcat 8080 loopback-only | Pass: `[::ffff:127.0.0.1]:8080` |
| MySQL 3306 loopback-only | Pass: `127.0.0.1:3306` |
| firewalld running; ports 8080 and 3306 not opened | Pass: listed ports empty; services `cockpit dhcpv6-client ssh` |
| SELinux enforcing | Pass |
| `~/.config/announcement-board/env` mode 600 | Pass (user-owned) |
| `/etc/announcement-board.env` mode 600 | Pass (`root:root`) |
| Nginx / Certbot on the development VM | Not installed |
| Final `announcements` row count | **0** |

### Repository audit

- Maven packaging is WAR; `spring-boot-starter-tomcat` is `provided`
- No Docker / Docker Compose, H2, Testcontainers, nested `src/src/main`, tracked `target/`, WAR/backup files, IDE metadata, credential files, or database dumps
- `git diff --check` passed
- Scripts remain executable (`100755`)
- No secrets in Git; `.env.example` uses placeholders only

### Defects found

None. No application code was changed in Phase 8.

### External VM files

- Replaced `/var/lib/tomcat/webapps/announcement-board.war` via `scripts/deploy-centos`
- Wrote `/var/backups/announcement-board/announcement-board.20260914T154559Z.f6237477cb07.war.bak`
- Did not modify protected env files, Tomcat connector bind, MySQL bind address, firewalld, or SELinux
