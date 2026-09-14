# Phase 7 acceptance evidence

Date tested: 2026-09-14

Tested Git commit: recorded in the Final deploy section after `scripts/deploy-centos` ran from a clean `codex/phase-7-acceptance` checkout.

Host: CentOS Stream 10 (Coughlan) aarch64, `haha@192.168.64.26`

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
| Git commit | _fill after deploy_ |
| WAR SHA-256 | _fill after deploy_ |
| `python3 scripts/deploy-centos` | _fill after deploy_ |
| Independent `python3 scripts/smoke-test-deployment` | _fill after deploy_ |
| Independent `python3 scripts/acceptance-test` | _fill after deploy_ |
| Maven tests during deploy | _fill after deploy_ |

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
ssh -N -L 8080:127.0.0.1:8080 -i ~/.ssh/id_ed25519_centos_vm haha@192.168.64.26
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
| Create one uniquely identified Phase 7 record | _fill after restart check_ |
| Fetch it successfully | _fill after restart check_ |
| `sudo systemctl restart tomcat` and wait until ready | _fill after restart check_ |
| Fetch the same record | _fill after restart check_ |
| Delete that exact record | _fill after restart check_ |

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
| `/home/haha/.config/announcement-board/env` mode 600 | Pass (`haha:haha`) |
| `/etc/announcement-board.env` mode 600 | Pass (`root:root`) |
| No credentials in application responses | Pass (API error bodies checked by the acceptance script) |

## Database cleanup

| Point | `announcements` count |
| --- | --- |
| Baseline | 0 |
| After API acceptance-test | 0 |
| After browser runs (IDs deleted individually) | 0 |
| After Tomcat-restart persistence record | _fill after restart check_ |

Expected final state matches baseline: **zero application rows**. Flyway history was not deleted.

## Defects found and fixed

### 1. Delete/create modal could stay open

**Symptom.** Clicking Delete, then Cancel or Confirm while Bootstrap was still finishing the show transition, left `#delete-modal` with class `show`. Bootstrap 5.3 `Modal.hide()` returns immediately when `_isTransitioning` is true, and Modal never adds a `showing` class.

**Fix.** `app.js` patches `Modal.hide` so a hide requested during the show transition waits for `shown.bs.modal`, and `hideModal()` retries until the dialog actually closes.

**Regression.** `AnnouncementUiTest.appJsIsServed` asserts `__abHidePatched`, `shown.bs.modal.abHide`, and the retry loop.

### 2. Keyboard focus outline did not win on Bootstrap buttons

**Symptom.** `app.css` set `:focus-visible { outline: ... }`, but Bootstrap `.btn:focus-visible { outline: 0 }` is more specific, so the primary button showed no custom outline.

**Fix.** Add matching `.btn:focus-visible` / `.page-link:focus-visible` / `.form-control:focus-visible` / `.btn-close:focus-visible` rules in `app.css`.

**Regression.** `AnnouncementUiTest.appCssIsServed` asserts `.btn:focus-visible`.

## Known non-blocking observations

- Flyway logs `Flyway upgrade recommended: MySQL 8.4 is newer than this version of Flyway and support has not been tested. The latest supported version of MySQL is 8.1.` Schema remains V1 and the application starts. No Flyway upgrade was performed.
- Hibernate SQL for repository tests is visible in Maven output because those tests use the JPA test slice defaults. Production SQL logging stays off unless `JPA_SHOW_SQL` is set.

## Documentation review

README build, database, deployment, tunnel, API, and test commands were executed on this VM. The Phase 7 acceptance helper and this evidence file are linked from README. `doc/DEPLOYMENT.md` needed no command corrections. `doc/PROJECT_PLAN.md` was not changed; the modal and focus fixes do not alter the design.

Final homework screenshots remain Phase 8.
