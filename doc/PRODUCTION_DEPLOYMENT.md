# Production deployment guide

This document describes how to deploy the announcement board on a **separate** production CentOS/RHEL-compatible host. It does **not** authorize converting the current private development VM (`haha@192.168.64.26`) into a public or production server.

Do not install Nginx or Certbot on the development VM. Do not open ports 8080 or 3306. Do not disable `firewalld` or SELinux. Do not acquire a real certificate or create DNS records for the development host.

Package names, module streams, and ACME client packaging change over time. Confirm each step against the current official documentation linked in each section before running commands.

Placeholder hostname used throughout: `board.example.com`. Replace it with the production hostname. Do not treat it as a real domain.

Application context on production remains:

```text
https://board.example.com/announcement-board/
```

Commands that contain `board.example.com`, `/opt/announcement-board`, or example JVM sizes are **examples**. Adapt them to the target hostname, filesystem, and memory.

## 1. Architecture and limitations

Recommended single-host topology:

```text
Internet or private organization network
               |
           HTTPS :443
               |
      Nginx reverse proxy
               |
     127.0.0.1:8080 Tomcat
               |
     127.0.0.1:3306 MySQL
```

| Topic | Current development VM | Production host |
| --- | --- | --- |
| Audience | Private homework development | A dedicated server you control |
| Public TLS endpoint | None. Reach Tomcat with an SSH tunnel | Nginx on ports 80 and 443 |
| Tomcat HTTP | Loopback `127.0.0.1:8080` | Remains loopback `127.0.0.1:8080` |
| MySQL | Loopback `127.0.0.1:3306` | Remains loopback `127.0.0.1:3306` |
| Nginx / Certbot | Not installed; do not add them there | Required on the production host |
| Authentication | None | Still none in this application; see the warning below |

Assumptions in this guide:

- One host runs Nginx, Tomcat, and MySQL.
- The WAR is still `announcement-board.war` at context `/announcement-board`.
- Flyway still owns schema migrations. Hibernate stays at `ddl-auto=validate`.
- `scripts/deploy-centos` stops Tomcat, replaces the WAR, and starts Tomcat. That is a short service interruption. Zero-downtime deployment needs more than one application node or a blue/green layout, which is outside this project.

## 2. Critical authentication warning

**This application has no authentication and no authorization.** Any client that can reach the UI or `/announcement-board/api/announcements` can list, create, edit, and delete announcements.

Do not expose it as an unrestricted public CRUD application.

Before the production URL is reachable beyond a trusted group, choose at least one of:

1. Deploy only on a trusted private network.
2. Require VPN access to the host.
3. Put the site behind an organization’s identity-aware proxy or SSO.
4. Use a temporary reverse-proxy access control for an evaluator.
5. Implement proper application authentication in a later, reviewed change before general public use.

HTTP Basic Authentication at Nginx is **not** a complete long-term authorization design. If it is used as a temporary evaluator gate, require HTTPS and store credentials outside Git (for example `auth_basic_user_file` with mode `600`). It still does not give per-user permissions inside the application.

## 3. Host preparation

Use a dedicated CentOS Stream / RHEL-compatible server, not the development VM.

Confirm current package names from:

- [RHEL 10, deploying web servers and reverse proxies](https://docs.redhat.com/en/documentation/red_hat_enterprise_linux/10/html/deploying_web_servers_and_reverse_proxies/setting-up-and-configuring-nginx) (Nginx install, reverse proxy, TLS)
- [RHEL 10, configuring firewalls](https://docs.redhat.com/en/documentation/red_hat_enterprise_linux/10/html/configuring_firewalls_and_packet_filters/using-and-configuring-firewalld)
- [RHEL 10, using SELinux](https://docs.redhat.com/en/documentation/red_hat_enterprise_linux/10/html/using_selinux/configuring-selinux-for-applications-and-services-with-non-standard-configurations)
- [RHEL 10, configuring time synchronization](https://docs.redhat.com/en/documentation/red_hat_enterprise_linux/10/html/configuring_time_synchronization/using-chrony)
- [Apache Tomcat 10.1 HTTP Connector](https://tomcat.apache.org/tomcat-10.1-doc/config/http.html)
- [MySQL 8.4 server system variables (`bind_address`)](https://dev.mysql.com/doc/refman/8.4/en/server-system-variables.html#sysvar_bind_address)
- [Certbot instructions](https://certbot.eff.org/instructions)

Prepare the host with:

- A dedicated non-root account for Git checkout and `scripts/deploy-centos` (passwordless `sudo` only for the commands that helper already uses).
- Package users for services: `tomcat`, `mysql`, and `nginx`. Do not run those daemons as `root`.
- OpenJDK 21 for the Tomcat runtime. The Maven compiler target remains Java 17.
- External Tomcat 10.1 as the container (`spring-boot-starter-tomcat` is `provided` in the WAR).
- MySQL 8.4 LTS Community Server installed **directly** on the host. Do not introduce Docker for this application.
- Nginx as the public TLS reverse proxy.
- An ACME client such as Certbot after DNS points at this host.
- `firewalld` enabled.
- SELinux enforcing.
- Time synchronization with `chronyd`:

```bash
sudo systemctl enable --now chronyd
chronyc tracking
```

Keep a security-update policy. On RHEL-compatible systems that usually means regular `dnf upgrade` for the OS, Tomcat, Nginx, OpenJDK, and MySQL, plus rebuilding the WAR when application dependencies change. Do not disable `firewalld` or SELinux to “make it work.”

Example package installs (confirm names on the target release before running):

```bash
# Examples. Check the current AppStream / MySQL repository docs first.
sudo dnf install java-21-openjdk-devel tomcat nginx chrony
```

MySQL Community Server is installed from Oracle’s EL repository, not from Docker. Follow the current [MySQL Yum repository notes](https://dev.mysql.com/doc/mysql-yum-repo-quick-guide/en/) for the host’s major version.

## 4. DNS and networking

1. Create public (or private-DNS) `A` and/or `AAAA` records for `board.example.com` that point at the production host. Obtain a certificate only after those records resolve to this host.
2. Expose SSH (so you cannot lock yourself out), HTTP (80), and HTTPS (443). Port 80 is for HTTP-to-HTTPS redirect and/or ACME HTTP-01 validation. Port 443 serves the application.
3. Do **not** open 8080 or 3306 in `firewalld`.
4. Keep MySQL `bind-address = 127.0.0.1` (and `mysqlx-bind-address = 127.0.0.1` if MySQL X Protocol is enabled).
5. Keep the Tomcat HTTP Connector `address="127.0.0.1"` on port 8080. See [Tomcat HTTP Connector `address`](https://tomcat.apache.org/tomcat-10.1-doc/config/http.html).
6. Leave `firewalld` and SELinux enabled.
7. When changing firewall rules, keep SSH allowed in the same transaction. Apply `--permanent` then `--reload` only after you have confirmed SSH is still in the zone.

Example (adapt the zone; do not copy blindly):

```bash
sudo firewall-cmd --state
sudo firewall-cmd --get-active-zones
sudo firewall-cmd --permanent --add-service=ssh
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --permanent --add-service=https
sudo firewall-cmd --reload
sudo firewall-cmd --list-services
sudo firewall-cmd --list-ports
```

[RHEL 10 `firewalld` documentation](https://docs.redhat.com/en/documentation/red_hat_enterprise_linux/10/html/configuring_firewalls_and_packet_filters/using-and-configuring-firewalld) describes adding predefined services. Prefer `http` and `https` over opening 8080.

Verify listeners after Tomcat and MySQL start:

```bash
ss -lnt | awk '$4 ~ /:80$|:443$|:8080$|:3306$/'
```

Expected: 80/443 on public addresses; 8080 and 3306 on `127.0.0.1` only.

## 5. Database provisioning

Create a dedicated schema and a least-privileged application user that can connect only from localhost. Grant access only to that schema. Use `utf8mb4`.

Official references:

- [Adding accounts and assigning privileges](https://dev.mysql.com/doc/refman/8.4/en/creating-accounts.html)
- [`GRANT`](https://dev.mysql.com/doc/refman/8.4/en/grant.html)
- [Application character set and collation](https://dev.mysql.com/doc/refman/8.4/en/charset-applications.html)
- [Using option files](https://dev.mysql.com/doc/refman/8.4/en/option-files.html) (do not put passwords on the shell command line)

Do not paste database passwords into the shell. Load them from a mode `600` option file.

Example (placeholders only; run from a root MySQL session that already uses an option file):

```sql
CREATE DATABASE announcement_board
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER 'announcement_app'@'localhost' IDENTIFIED BY 'replace-with-a-secret';

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, TRIGGER
  ON announcement_board.* TO 'announcement_app'@'localhost';

FLUSH PRIVILEGES;
```

Adjust the privilege list to what Flyway and the application actually need on that host. Do not grant `*.*` or `WITH GRANT OPTION` to the application user.

Store runtime credentials in `/etc/announcement-board.env`:

```bash
sudo install -d -m 0750 /etc
sudo install -m 0600 /dev/null /etc/announcement-board.env
sudo chown root:root /etc/announcement-board.env
```

Edit that file as root so it contains only:

```bash
DB_URL=jdbc:mysql://127.0.0.1:3306/announcement_board?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci
DB_USERNAME=announcement_app
DB_PASSWORD=replace-with-a-secret
```

Keep Flyway responsible for schema migrations. Keep `spring.jpa.hibernate.ddl-auto=validate`. Do not add `schema.sql` or `data.sql` alongside Flyway.

Before a release that contains a new `src/main/resources/db/migration/V*.sql` file:

1. Back up the database.
2. Deploy the WAR that contains the new migration.
3. Confirm Flyway applied it (`python3 scripts/describe-schema` on the host, or an equivalent metadata query).

Never edit an already-applied Flyway migration. Never assume that restoring a previous WAR also reverses a database migration. If you must undo a schema change, restore a tested backup or add a new forward migration. See [Flyway migrations](https://documentation.red-gate.com/flyway/flyway-concepts/migrations).

## 6. Application configuration

Required environment variables (see [`.env.example`](../.env.example)):

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

| File | Owner | Mode | Used by |
| --- | --- | --- | --- |
| `/etc/announcement-board.env` | `root:root` | `600` | Tomcat systemd drop-in |
| `~/.config/announcement-board/env` | deploying user | `600` | `scripts/with-db-env` for Maven |

Do not commit secrets. `src/localdev.env` is Git-ignored and is not a production file.

The version-controlled drop-in is [`deploy/systemd/tomcat.service.d/announcement-board.conf`](../deploy/systemd/tomcat.service.d/announcement-board.conf). On the production host, install it without replacing `/usr/lib/systemd/system/tomcat.service`:

```bash
sudo install -d -m 0700 /etc/systemd/system/tomcat.service.d
sudo install -m 0600 deploy/systemd/tomcat.service.d/announcement-board.conf \
  /etc/systemd/system/tomcat.service.d/announcement-board.conf
```

JVM memory belongs in a drop-in as an **example**, not a universal value. Size it from the host’s RAM and other services on the box. A homework-sized board on a small VM might use:

```ini
[Service]
EnvironmentFile=/etc/announcement-board.env
Environment=JAVA_OPTS=-Xms256m -Xmx512m
```

Confirm that the Tomcat unit actually consumes `JAVA_OPTS` (RHEL’s `/etc/tomcat/tomcat.conf` does). Then:

```bash
sudo systemctl daemon-reload
sudo systemctl enable tomcat mysqld nginx
sudo systemctl restart tomcat
```

## 7. Artifact build and delivery

Build from a clean, reviewed Git commit on a controlled host (the production server or a dedicated build host).

```bash
git status --porcelain   # must be empty
git rev-parse HEAD
python3 scripts/with-db-env ./mvnw -B clean test
python3 scripts/with-db-env ./mvnw -B clean package
test -f target/announcement-board.war
sha256sum target/announcement-board.war
```

Record the Git commit and the WAR SHA-256 in the change ticket. Transfer `target/announcement-board.war` over SSH if you built elsewhere.

When the production host matches the helper’s assumptions (system Tomcat, `/var/lib/tomcat/webapps`, drop-in already loading `/etc/announcement-board.env`, loopback Tomcat and MySQL, passwordless `sudo`), deploy with:

```bash
python3 scripts/deploy-centos
```

That helper refuses a dirty Git tree, runs tests, records commit and SHA-256, backs up the previous WAR under `/var/backups/announcement-board/` as `*.war.bak` (not under `webapps`, and not with a `.war` suffix), stops Tomcat, installs `/var/lib/tomcat/webapps/announcement-board.war`, starts Tomcat, and runs `scripts/smoke-test-deployment`.

After deployment:

```bash
python3 scripts/smoke-test-deployment
python3 scripts/acceptance-test
python3 scripts/describe-schema
```

### Rollback

Rolling back the WAR does not undo Flyway. Only restore a previous WAR when the database schema is still compatible with that WAR, or restore a matching database backup first.

```bash
BACKUP=/var/backups/announcement-board/announcement-board.TIMESTAMP.COMMIT.war.bak

sudo systemctl stop tomcat
# Remove only the exploded context directory, never /var/lib/tomcat/webapps itself.
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

## 8. Nginx reverse proxy

Nginx is the only public HTTP/HTTPS listener. Tomcat stays on loopback. Do not proxy `/` to Tomcat: a default Tomcat install may still contain manager or sample applications.

Official references:

- [`proxy_pass` and `proxy_set_header`](https://nginx.org/en/docs/http/ngx_http_proxy_module.html)
- [Configuring HTTPS servers](https://nginx.org/en/docs/http/configuring_https_servers.html)
- [`return` in the rewrite module](https://nginx.org/en/docs/http/ngx_http_rewrite_module.html)
- [Controlling nginx (`nginx -s reload`)](https://nginx.org/en/docs/control.html)
- [RHEL 10 Nginx as a reverse proxy](https://docs.redhat.com/en/documentation/red_hat_enterprise_linux/10/html/deploying_web_servers_and_reverse_proxies/setting-up-and-configuring-nginx)

If `proxy_pass` includes a URI path, Nginx replaces the matched location prefix. If `proxy_pass` has **no** URI path, Nginx passes the original request URI. Use the second form so `/announcement-board/` stays `/announcement-board/` on Tomcat.

The following is an **example**. Test it with `nginx -t` on the production host. It is not copied from a live production server.

```nginx
# /etc/nginx/conf.d/announcement-board.conf
# Example only. Replace board.example.com and certificate paths.

# HTTP: ACME challenge (if used) and redirect everything else to HTTPS.
server {
    listen 80;
    listen [::]:80;
    server_name board.example.com;

    location ^~ /.well-known/acme-challenge/ {
        root /var/www/acme;
        default_type text/plain;
    }

    location / {
        return 301 https://$host$request_uri;
    }
}

server {
    listen 443 ssl;
    listen [::]:443 ssl;
    server_name board.example.com;

    ssl_certificate     /etc/letsencrypt/live/board.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/board.example.com/privkey.pem;

    # Request bodies are JSON announcements, not file uploads.
    client_max_body_size 1m;

    # Do not proxy arbitrary user-controlled upstreams.
    location = /announcement-board {
        return 301 /announcement-board/;
    }

    location /announcement-board/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        # Overwrite at this public edge. Do not append a client-supplied
        # X-Forwarded-For chain ($proxy_add_x_forwarded_for) unless every
        # previous hop is already trusted.
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Connection "";
        proxy_connect_timeout 10s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;

        # Repeat add_header in this location. Nginx does not inherit
        # parent add_header directives once a location defines its own.
        add_header X-Content-Type-Options "nosniff" always;
        add_header Referrer-Policy "strict-origin-when-cross-origin" always;
        add_header X-Frame-Options "DENY" always;
    }
}
```

Do not enable HSTS until HTTPS has been confirmed reliable, including renewal. Then you may add, in the HTTPS `location`:

```nginx
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
```

Test and reload; do not stop Nginx unnecessarily:

```bash
sudo nginx -t
sudo systemctl reload nginx
```

[Nginx reload](https://nginx.org/en/docs/control.html) keeps the old configuration if the new one fails.

### Forwarded headers and generated URLs

This WAR runs on **external** Tomcat. Spring Boot properties such as `server.forward-headers-strategy` configure **embedded** Tomcat and do not apply to `/etc/tomcat/server.xml`.

The UI calls the relative URL `api/announcements`, so list/create/edit/delete AJAX works without forwarded headers. Tomcat still uses the request scheme and host for some redirects (for example a missing trailing slash). Configure Tomcat’s `RemoteIpValve` so `X-Forwarded-Proto: https` from loopback Nginx is honored. See [Remote IP Valve](https://tomcat.apache.org/tomcat-10.1-doc/config/valve.html#Remote_IP_Valve) and [Proxy Support How-To](https://tomcat.apache.org/tomcat-10.1-doc/proxy-howto.html).

Example Engine valve (internal proxies already include `127.0.0.1` by default):

```xml
<Valve className="org.apache.catalina.valves.RemoteIpValve"
       protocolHeader="X-Forwarded-Proto"
       protocolHeaderHttpsValue="https"
       remoteIpHeader="X-Forwarded-For" />
```

Nginx must overwrite `X-Forwarded-*` as shown above so clients cannot set those headers themselves. Do not copy this valve into the development VM as part of closeout.

## 9. SELinux

Keep SELinux enforcing. Nginx running as `httpd_t` needs permission to connect to Tomcat on loopback port 8080.

[RHEL 10 documents](https://docs.redhat.com/en/documentation/red_hat_enterprise_linux/10/html/deploying_web_servers_and_reverse_proxies/setting-up-and-configuring-nginx) the boolean for Nginx reverse proxying:

```bash
getsebool httpd_can_network_connect
sudo setsebool -P httpd_can_network_connect 1
getsebool httpd_can_network_connect
getenforce
```

That is the minimum documented boolean for this topology. Do not set SELinux to permissive or disabled. If a denial remains, use `ausearch` / `sealert` and add the smallest additional allow rule; do not broadly disable the policy.

Private keys and `/etc/announcement-board.env` must keep confined labels and mode `600`. After writing new Nginx config under `/etc/nginx`, run `sudo restorecon -Rv /etc/nginx` if restorecon reports mismatches.

## 10. TLS

1. Point DNS at the production host first.
2. Open port 80 for HTTP-01, or use DNS-01 if HTTP-01 is not available.
3. Obtain a certificate with an ACME client such as Certbot. Official instructions: [certbot.eff.org](https://certbot.eff.org/instructions) and [Certbot user guide](https://eff-certbot.readthedocs.io/en/stable/using.html).
4. Redirect HTTP to HTTPS after the certificate is installed.
5. Confirm automatic renewal (`systemctl list-timers` or crontab for `certbot renew`).
6. Test renewal without writing a new live certificate:

```bash
sudo certbot renew --dry-run
```

[`certbot renew --dry-run`](https://eff-certbot.readthedocs.io/en/stable/using.html#renewing-certificates) uses the staging server and does not save certificates.

7. Enable HSTS only after HTTPS and renewal work.
8. Never commit private keys or certificate bodies to Git. RHEL’s Nginx TLS section also requires mode `600` on the key: [Adding TLS encryption to an NGINX web server](https://docs.redhat.com/en/documentation/red_hat_enterprise_linux/10/html/deploying_web_servers_and_reverse_proxies/setting-up-and-configuring-nginx).

## 11. Security headers and CDN dependencies

`index.html` loads pinned CDN assets with Subresource Integrity:

- `https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/css/bootstrap.min.css`
- `https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/js/bootstrap.bundle.min.js`
- `https://code.jquery.com/jquery-3.7.1.min.js`

Custom assets are same-origin: `css/app.css` and `js/app.js`. AJAX uses `connect-src` of the application origin (`api/announcements` under `/announcement-board/`).

Two production choices:

1. **Keep the exact pinned CDN URLs and SRI hashes.** Add a restrictive Content-Security-Policy that allows only those origins, then **test** the UI and AJAX before calling it production-ready.
2. **Vendor those files into the WAR** in a future reviewed change, then tighten CSP to `'self'` only.

Do not claim an untested CSP is ready for production. Bootstrap’s JavaScript sets inline styles on modals and `document.body` (display, padding, overflow). A `style-src` that omits those capabilities will likely break create/edit/delete dialogs. Do **not** add `'unsafe-eval'`.

Example starting point to **test**, not to enable blindly:

```nginx
add_header Content-Security-Policy "default-src 'self'; script-src 'self' https://code.jquery.com https://cdn.jsdelivr.net; style-src 'self' https://cdn.jsdelivr.net; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'" always;
```

After enabling it, confirm: list load, create modal, edit modal, delete modal, pagination, and a failing validation still work. Adjust `style-src` only as far as that test requires.

Other headers worth sending from Nginx (see the example `location` above):

- `X-Content-Type-Options: nosniff` — stop MIME sniffing of CSS/JS.
- `Referrer-Policy: strict-origin-when-cross-origin` — avoid leaking path query strings to third-party CDNs.
- `X-Frame-Options: DENY` — the board is not meant to be framed.

## 12. Backups and recovery

Use MySQL logical backups (`mysqldump`) for this single-schema homework application. Official references: [mysqldump](https://dev.mysql.com/doc/refman/8.4/en/mysqldump.html) and [option files](https://dev.mysql.com/doc/refman/8.4/en/option-files.html).

Put backup client credentials in a root-owned mode `600` option file, for example `/root/.config/announcement-board/backup.cnf`. Do not put passwords on the command line or in `HISTFILE`.

Example option file (placeholders):

```ini
[client]
host=127.0.0.1
user=announcement_backup
password=replace-with-a-secret
```

Example dump (adapt paths; `--defaults-extra-file` must be the first option):

```bash
sudo mysqldump --defaults-extra-file=/root/.config/announcement-board/backup.cnf \
  --single-transaction --routines --triggers \
  --default-character-set=utf8mb4 \
  announcement_board \
  | sudo gzip -c > /var/backups/announcement-board/db/announcement_board.TIMESTAMP.sql.gz
```

Then:

- Encrypt backups at rest, or store them only on media that is already encrypted and access-controlled.
- Restrict the backup directory to `root` (mode `750` or tighter).
- Keep a documented retention policy (for example daily copies for 14 days, weekly copies for 8 weeks).
- Copy backups **off the host**.
- Restore onto a scratch instance on a schedule and run `python3 scripts/smoke-test-deployment` against that restore.
- Also back up `/var/lib/tomcat/webapps/announcement-board.war`, `/etc/announcement-board.env` (secret; treat like a key), Tomcat drop-ins, and Nginx config. The helper already writes WAR backups under `/var/backups/announcement-board/`.

Recovery order:

1. Restore Nginx and Tomcat configuration (no secrets in Git; env file from the secret backup).
2. Restore MySQL, then the logical dump, with Flyway history intact.
3. Restore the WAR that matches that schema.
4. Start `mysqld`, then `tomcat`, then `nginx`.
5. Verify with the checklist in section 14.

Rolling back only the WAR after Flyway has applied a newer migration will fail Hibernate `ddl-auto=validate` or run against the wrong schema.

## 13. Logging, monitoring, and maintenance

| Source | Where |
| --- | --- |
| Tomcat | `journalctl -u tomcat` |
| Nginx access/error | `/var/log/nginx/` |
| MySQL | error log path from the installed MySQL package (`mysqld --verbose --help` / `log_error`) |

Watch:

- Disk space on `/`, `/var`, and the backup volume.
- `systemctl is-active nginx tomcat mysqld`.
- Certificate expiry (`certbot certificates`, or an external probe on port 443).
- Whether database backups actually appear and pass restore tests.
- Application availability: `GET https://board.example.com/announcement-board/` and `GET https://board.example.com/announcement-board/api/announcements?page=0&size=1`. There is no separate Actuator health endpoint in this project.

Patch cadence: OS and Tomcat/Nginx/Java/MySQL from the distribution or vendor; application dependencies via a reviewed Maven upgrade and a full test run before deploy.

Log retention: use the distribution’s `journald` and `logrotate` defaults, then tighten if disk is small. Do not log `DB_PASSWORD`, `/etc/announcement-board.env`, or request bodies (they may contain announcement content and, in a future authenticated design, credentials).

## 14. Production verification checklist

Label: every `board.example.com` command is an example. Substitute the real hostname.

```bash
# Nginx syntax and reload
sudo nginx -t
systemctl is-active nginx
systemctl is-enabled nginx

# Application and database units
systemctl is-active tomcat mysqld
systemctl is-enabled tomcat mysqld

# Listening ports: 8080 and 3306 must remain loopback
ss -lnt | awk '$4 ~ /:80$|:443$|:8080$|:3306$/'

# Firewall: ssh, http, https; no 8080 or 3306
sudo firewall-cmd --state
sudo firewall-cmd --list-services
sudo firewall-cmd --list-ports

# SELinux
getenforce
getsebool httpd_can_network_connect

# HTTPS and HTTP-to-HTTPS (adapt hostname)
curl -sS -o /dev/null -w '%{http_code} %{url_effective}\n' \
  https://board.example.com/announcement-board/
curl -sS -o /dev/null -w '%{http_code} %{redirect_url}\n' \
  http://board.example.com/announcement-board/

# UI, CSS, JavaScript
curl -sS -o /dev/null -w '%{http_code}\n' https://board.example.com/announcement-board/
curl -sS -o /dev/null -w '%{http_code}\n' https://board.example.com/announcement-board/css/app.css
curl -sS -o /dev/null -w '%{http_code}\n' https://board.example.com/announcement-board/js/app.js

# API list
curl -sS https://board.example.com/announcement-board/api/announcements?page=0&size=10

# Smoke test against the public URL (or loopback if run on the host)
python3 scripts/smoke-test-deployment https://board.example.com/announcement-board

# Flyway and row count (no credential printing)
python3 scripts/describe-schema

# Backup presence (adapt path)
sudo ls -l /var/backups/announcement-board/db

# Certificate renewal dry run
sudo certbot renew --dry-run

# Rollback artifact exists
sudo ls -l /var/backups/announcement-board/*.war.bak
```

Also open the UI in a browser: list, create, edit, delete, pagination, and a 375px-wide window.

Protected files:

```bash
sudo stat -c '%a %U:%G %n' /etc/announcement-board.env
stat -c '%a %U:%G %n' "$HOME/.config/announcement-board/env"
```

Both must remain mode `600`.
