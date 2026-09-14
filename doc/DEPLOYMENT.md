# CentOS Stream 10 setup and Tomcat deployment

This runbook starts with a clean CentOS Stream 10 host, installs Java 21, system Tomcat 10.1, and MySQL 8.4 LTS directly on the host, then builds and deploys `announcement-board.war`. Docker and Docker Compose are not used.

The application has no authentication. Keep this installation on a trusted private network. Tomcat and MySQL must remain loopback-only; do not open ports 8080 or 3306.

Application context: `/announcement-board`

## Placeholders

Replace these stubs locally. Do not commit their real values:

| Stub | Meaning |
| --- | --- |
| `<ssh-key-path>` | Workstation path to the CentOS SSH private key |
| `<ssh-user>` | Non-root CentOS account used to build and deploy |
| `<centos-host>` | Private hostname or IP address of the CentOS host |
| `<repository-url>` | HTTPS or SSH clone URL for this repository |
| `<project-directory>` | Absolute checkout path on the CentOS host |

## Clean-host prerequisites

- A clean CentOS Stream 10 host with working package repositories and outbound HTTPS
- A non-root SSH account with administrative `sudo` access
- Enough memory and disk for Java, Tomcat, MySQL, Maven dependencies, builds, and WAR backups
- `firewalld` enabled and SELinux enforcing
- Non-interactive `sudo` authorized for the deployment helper; it deliberately uses `sudo -n`

Configure non-interactive `sudo` through a reviewed, least-privilege sudoers policy. Do not add a blanket passwordless rule on a shared host. Confirm the policy without changing system state:

```bash
sudo -n true
```

SSH to the VM:

```bash
ssh -i "<ssh-key-path>" -o IdentitiesOnly=yes "<ssh-user>@<centos-host>"
```

## Install Java, Tomcat, and host tools

CentOS Stream 10 provides OpenJDK 21 and Tomcat 10.1 through its normal repositories. The Maven Wrapper in this repository downloads Maven itself; a system Maven package is not required.

- [Red Hat OpenJDK 21 installation](https://docs.redhat.com/en/documentation/red_hat_build_of_openjdk/21/html-single/installing_and_using_red_hat_build_of_openjdk_21_on_rhel/installing_and_using_red_hat_build_of_openjdk_21_on_rhel)
- [Apache Tomcat 10.1 documentation](https://tomcat.apache.org/tomcat-10.1-doc/)

```bash
sudo dnf upgrade --refresh -y
sudo dnf install -y git curl python3 java-21-openjdk-devel tomcat firewalld

java -version
javac -version
rpm -q tomcat

sudo systemctl enable --now firewalld
sudo firewall-cmd --permanent --add-service=ssh
sudo firewall-cmd --reload
getenforce
```

`getenforce` must report `Enforcing`. Do not add firewall ports 8080 or 3306.

Do not start Tomcat yet. Its database environment and loopback connector are configured below.

## Install MySQL 8.4 LTS

Use Oracle's MySQL 8.4 Community repository for EL10. Confirm the current EL10 repository package on the [official download page](https://dev.mysql.com/downloads/repo/yum/) before installing it. The filename below was current on 2026-09-15; replace it if Oracle publishes a newer `mysql84-community-release-el10` package.

Oracle's [Yum repository guide](https://dev.mysql.com/doc/refman/8.4/en/linux-installation-yum-repo.html) documents repository selection, package installation, initial startup, and the temporary root password.

```bash
curl -fLO https://dev.mysql.com/get/mysql84-community-release-el10-3.noarch.rpm
sudo dnf install -y ./mysql84-community-release-el10-3.noarch.rpm
dnf repolist --enabled | grep '^mysql-8.4-lts-community'
sudo dnf --setopt=install_weak_deps=False install mysql-community-server
```

Disabling weak dependencies prevents DNF from also selecting MariaDB packages that conflict with the Oracle MySQL Community packages.

Before the first start, edit the existing `[mysqld]` section in `/etc/my.cnf` and add these settings. Do not create a second `[mysqld]` section.

```ini
bind-address = 127.0.0.1
mysqlx-bind-address = 127.0.0.1
```

Then initialize and secure MySQL:

```bash
sudo systemctl enable --now mysqld
systemctl is-active mysqld
sudo grep 'temporary password' /var/log/mysqld.log
sudo mysql_secure_installation
```

The temporary password is a secret. Use it only at the local prompt, change it immediately, and do not paste it into Git, tickets, or chat.

Create the application schema and a dedicated account from an interactive root session. Replace `<application-password>` before running the SQL; use the same value later in both protected environment files.

```bash
mysql -u root -p
```

```sql
CREATE DATABASE announcement_board
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER 'announcement'@'localhost'
  IDENTIFIED BY '<application-password>';

GRANT ALL PRIVILEGES ON announcement_board.*
  TO 'announcement'@'localhost';

EXIT;
```

Confirm that MySQL is reachable only on loopback:

```bash
ss -lnt | awk '$4 ~ /:3306$/'
mysql -h 127.0.0.1 -u announcement -p announcement_board -e 'SELECT 1'
```

## Check out the application

Configure Git hosting credentials outside this repository, then clone and enter the checkout:

```bash
git clone "<repository-url>" "<project-directory>"
cd "<project-directory>"
git checkout main
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

Create both files from the tracked placeholder template, then edit them locally. Set `DB_USERNAME=announcement` and set `DB_PASSWORD` to the application password created above.

```bash
install -d -m 0700 ~/.config/announcement-board
install -m 0600 .env.example ~/.config/announcement-board/env
"${EDITOR:-vi}" ~/.config/announcement-board/env

sudo install -m 0600 -o root -g root .env.example /etc/announcement-board.env
sudoedit /etc/announcement-board.env
```

Confirm modes without reading values:

```bash
stat -c '%a %U:%G %n' ~/.config/announcement-board/env
sudo stat -c '%a %U:%G %n' /etc/announcement-board.env
```

## Configure and start Tomcat

Edit `/etc/tomcat/server.xml` and add `address="127.0.0.1"` to the active HTTP connector on port 8080. Preserve the connector's other packaged settings. The result should have this shape:

```xml
<Connector address="127.0.0.1" port="8080" protocol="HTTP/1.1"
           connectionTimeout="20000"
           redirectPort="8443"
           maxParameterCount="1000" />
```

Tomcat must also load `/etc/announcement-board.env`. The version-controlled template is [`deploy/systemd/tomcat.service.d/announcement-board.conf`](../deploy/systemd/tomcat.service.d/announcement-board.conf). It contains no secrets:

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
sudo systemctl enable tomcat mysqld
sudo systemctl start tomcat
systemctl is-active tomcat mysqld
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
