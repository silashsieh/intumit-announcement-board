# CentOS Tomcat deployment

This runbook deploys `announcement-board.war` to the system Tomcat 10.1 service on the CentOS Stream 10 **development** VM. It does not use Docker, an embedded production Tomcat, or a remote database. It does not make that VM public.

Production deployment on a separate host is documented in [`PRODUCTION_DEPLOYMENT.md`](PRODUCTION_DEPLOYMENT.md). Do not install Nginx or Certbot here, and do not open ports 8080 or 3306.

Application context: `/announcement-board`

## Prerequisites

- CentOS Stream 10 host with OpenJDK 21, Maven Wrapper, system Tomcat 10.1, and MySQL 8.4 Community Server
- Passwordless `sudo` for the deploying user
- A clean Git checkout of this repository
- Protected environment files already in place (see below)
- Tomcat HTTP bound to loopback and reached with an SSH tunnel
- `firewalld` and SELinux left enabled; ports 8080 and 3306 must not be opened

SSH to the VM:

```bash
ssh -i "<ssh-key-path>" -o IdentitiesOnly=yes "<ssh-user>@<centos-host>"
```

Repository on the VM:

```bash
cd "<project-directory>"
git checkout main   # or the deployment branch
git pull --ff-only
```

## Protected environment files

Database credentials are not stored in Git. Required variable names (see [`.env.example`](../.env.example)):

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

| File | Used by | Mode |
| --- | --- | --- |
| `~/.config/announcement-board/env` | Maven builds and tests via `scripts/with-db-env` | `600`, user-owned |
| `/etc/announcement-board.env` | Tomcat systemd drop-in | `600`, root-owned |

Do not print, copy, or commit the contents of those files. `src/localdev.env` is a local override, is Git-ignored, and stays user-owned.

Confirm modes without reading values:

```bash
stat -c '%a %U:%G %n' ~/.config/announcement-board/env
sudo stat -c '%a %U:%G %n' /etc/announcement-board.env
```

## Systemd drop-in

Tomcat must load `/etc/announcement-board.env`. The version-controlled template is [`deploy/systemd/tomcat.service.d/announcement-board.conf`](../deploy/systemd/tomcat.service.d/announcement-board.conf). It contains no secrets:

```ini
[Service]
EnvironmentFile=/etc/announcement-board.env
```

Install or refresh the drop-in. Do **not** replace `/usr/lib/systemd/system/tomcat.service`.

```bash
sudo install -d -m 0700 /etc/systemd/system/tomcat.service.d
sudo install -m 0600 deploy/systemd/tomcat.service.d/announcement-board.conf \
  /etc/systemd/system/tomcat.service.d/announcement-board.conf
sudo systemctl daemon-reload
sudo systemctl restart tomcat
```

If the drop-in is already present at `/etc/systemd/system/tomcat.service.d/announcement-board.conf` with the same `[Service]` content, reinstall it only when the file is missing or no longer loads `EnvironmentFile=/etc/announcement-board.env`.

## Clean build and test

From the repository root on the VM:

```bash
python3 scripts/with-db-env ./mvnw -B clean test
python3 scripts/with-db-env ./mvnw -B clean package
```

The packaged artifact is `target/announcement-board.war`.

## Deployment helper

`scripts/deploy-centos` is the supported path. It fails on a dirty Git tree, builds with tests, records the Git commit and WAR SHA-256, backs up the previous WAR under `/var/backups/announcement-board/` (not under `webapps`, and not with a `.war` suffix), stops Tomcat, replaces `/var/lib/tomcat/webapps/announcement-board.war`, removes only the exact exploded directory `/var/lib/tomcat/webapps/announcement-board`, starts Tomcat, waits for startup, and runs the smoke test. If a deployment fails after the WAR was replaced and a backup exists, the helper restores that backup.

```bash
python3 scripts/deploy-centos
```

Run it only on the CentOS Tomcat host, from this repository.

## Manual deployment fallback

Use this only if the helper cannot be run. Keep every path exact; do not use wildcards.

```bash
python3 scripts/with-db-env ./mvnw -B clean package
test -f target/announcement-board.war
git rev-parse HEAD
sha256sum target/announcement-board.war

sudo install -d -m 0750 -o root -g "$(id -gn)" /var/backups/announcement-board
sudo systemctl stop tomcat

if sudo test -f /var/lib/tomcat/webapps/announcement-board.war; then
  sudo cp -a -- /var/lib/tomcat/webapps/announcement-board.war \
    /var/backups/announcement-board/announcement-board.manual.war.bak
fi

# Exact exploded directory only. Never delete /var/lib/tomcat/webapps itself.
if sudo test -d /var/lib/tomcat/webapps/announcement-board \
   && ! sudo test -L /var/lib/tomcat/webapps/announcement-board; then
  sudo rm -rf -- /var/lib/tomcat/webapps/announcement-board
fi

sudo install -m 0644 -o tomcat -g tomcat \
  target/announcement-board.war \
  /var/lib/tomcat/webapps/announcement-board.war.deploying
sudo mv -f -- \
  /var/lib/tomcat/webapps/announcement-board.war.deploying \
  /var/lib/tomcat/webapps/announcement-board.war

sudo systemctl start tomcat
python3 scripts/smoke-test-deployment
```

## Smoke test

```bash
python3 scripts/smoke-test-deployment
python3 scripts/smoke-test-deployment http://127.0.0.1:8080/announcement-board
```

The helper checks the UI root, `css/app.css`, `js/app.js`, list/create/get/update/delete, pagination metadata, a `400` validation body, and a `404` for a missing record. It creates one uniquely titled `P6SMOKE-...` row and deletes that id in a `finally` path. It never deletes unrelated announcements.

## Service status and journals

```bash
systemctl status tomcat --no-pager
systemctl status mysqld --no-pager
systemctl is-enabled tomcat mysqld
sudo journalctl -u tomcat -n 80 --no-pager
python3 scripts/describe-schema
```

`describe-schema` reports Flyway version/success and the `announcements` row count without printing credentials. A healthy start applies or validates Flyway `V1`, then Hibernate `ddl-auto=validate` initializes the entity manager. Do not paste environment-file contents or JDBC passwords from logs into tickets.

## SSH tunnel

Tomcat remains on loopback. From a workstation:

```bash
ssh -i "<ssh-key-path>" -o IdentitiesOnly=yes \
  -N -L 8080:127.0.0.1:8080 \
  "<ssh-user>@<centos-host>"
```

## URLs

On the VM, or on a workstation through the tunnel:

| What | URL |
| --- | --- |
| UI | `http://127.0.0.1:8080/announcement-board/` |
| UI (localhost alias) | `http://localhost:8080/announcement-board/` |
| CSS | `http://127.0.0.1:8080/announcement-board/css/app.css` |
| JavaScript | `http://127.0.0.1:8080/announcement-board/js/app.js` |
| List API | `http://127.0.0.1:8080/announcement-board/api/announcements?page=0&size=10` |

## Loopback and firewall checks

```bash
ss -lnt | awk '$4 ~ /:8080$|:3306$/'
sudo firewall-cmd --state
sudo firewall-cmd --list-ports
sudo firewall-cmd --list-services
getenforce
```

Expected:

- Tomcat `8080` on `127.0.0.1` (IPv4-mapped `[::ffff:127.0.0.1]:8080` is still loopback)
- MySQL `3306` on `127.0.0.1`
- `firewalld` running; services such as `ssh` only; **no** `8080` or `3306` ports
- SELinux enforcing

Do not open 8080 or 3306, disable `firewalld`, disable SELinux, or bind MySQL remotely.

## Rollback

Backups are written to `/var/backups/announcement-board/` as `*.war.bak`. They must not be copied into `webapps` under a `.war` name except as the real `announcement-board.war`.

```bash
BACKUP=/var/backups/announcement-board/announcement-board.TIMESTAMP.COMMIT.war.bak

sudo systemctl stop tomcat
if sudo test -d /var/lib/tomcat/webapps/announcement-board \
   && ! sudo test -L /var/lib/tomcat/webapps/announcement-board; then
  sudo rm -rf -- /var/lib/tomcat/webapps/announcement-board
fi
sudo install -m 0644 -o tomcat -g tomcat "$BACKUP" \
  /var/lib/tomcat/webapps/announcement-board.war.deploying
sudo mv -f -- \
  /var/lib/tomcat/webapps/announcement-board.war.deploying \
  /var/lib/tomcat/webapps/announcement-board.war
sudo systemctl start tomcat
python3 scripts/smoke-test-deployment
```

`scripts/deploy-centos` performs this restore automatically when a deployment fails after a backup was taken.
