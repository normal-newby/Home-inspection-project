# Infrastructure

How the Home Inspection app is built, run, configured, and backed up. For using the app, see
[README.md](README.md); for working on the code, see [CLAUDE.md](CLAUDE.md).

## Overview

A single-user app on one Windows machine. Two containers run under Docker Engine inside WSL and are started by `start.bat`.

```
 Browser ──► :8080  backend (Spring Boot 4 / Java 25)
                     │  ├─ SQLite   ./data/database.db   (bind mount)
                     │  ├─ Photos   ./uploads/           (bind mount)
                     │  └─ HTTPS ──► Google OAuth / Calendar / Gmail, Gemini
                     │
                     └─ POST /generate-pdf ──► :3001  pdf-service (Flask + WeasyPrint)
```

| Service       | Source             | Image base                                                        | Port | Role                                              |
|---------------|--------------------|-------------------------------------------------------------------|------|---------------------------------------------------|
| `backend`     | `home-inspection/` | `maven:3.9-eclipse-temurin-25` (build) → `eclipse-temurin:25-jre-jammy` | 8080 | Web UI (static files), REST API, database, report rendering |
| `pdf-service` | `pdf-service/`     | `python:3.12-slim` + WeasyPrint (apt)                              | 3001 | Turns rendered report HTML into a PDF; merges appendix PDFs |

Both are defined in `docker-compose.yaml`. The backend reaches the PDF service over the compose network at
`http://pdf-service:3001`. `depends_on` only sets start order. There are no healthchecks.

## Starting and stopping

`start.bat` (double-click, or use the shortcut):

1. Runs `wsl docker info`. If the daemon is down, runs `wsl sudo service docker start` and waits up to 60 s.
   This targets Docker Engine installed **inside the WSL distro**, not Docker Desktop. `sudo` has to work
   without a password prompt, or the script will stall in the background.
2. Opens a second window running `wsl docker compose up --build`, so every launch rebuilds both images.
3. Polls `http://localhost:8080` for up to 120 s, then opens the browser.

To stop the app, close the "Docker" window, or run `wsl docker compose down`.

Manual equivalent from the repo root:

```bash
wsl docker compose up --build      # foreground
wsl docker compose up --build -d   # background
wsl docker compose logs -f backend
```

### Build details

- **backend**: two-stage build. `mvn package -DskipTests` runs inside the container, so a JDK and Maven are
  not needed on the host. Only `pom.xml` and `src/` are copied in, so `.env`, `data/` and `uploads/` never
  end up in the image.
- **pdf-service**: installs `weasyprint` from apt, which brings in the native Pango/GTK libraries, then
  runs `pip install -r requirements.txt` (`flask`, `weasyprint`, `pymupdf`). It runs Flask's built-in dev server
  (`python server.py`) on `0.0.0.0:3001`.

## Configuration

Spring reads `home-inspection/.env` through `spring.config.import=optional:file:.env[.properties]` when you
run from inside `home-inspection/`. Compose also loads the same file with `env_file`. `.env` is gitignored.
If the file doesn't exist, compose fails, so create an empty one on a fresh clone.

### Environment variables

| Variable                 | Default (`application.properties`)                    | Set by compose to              | Purpose |
|--------------------------|-------------------------------------------------------|--------------------------------|---------|
| `DATASOURCE_URL`         | `jdbc:sqlite:../data/database.db`                     | `jdbc:sqlite:/app/data/database.db` | SQLite file |
| `UPLOAD_DIR`             | `../uploads`                                          | `/app/uploads`                 | Photo / appendix storage |
| `PDF_SERVICE_URL`        | `http://localhost:3001`                               | `http://pdf-service:3001`      | PDF service base URL |
| `GOOGLE_CLIENT_ID`       | *(blank → Calendar/Gmail off)*                        | from `.env`                    | Google OAuth client |
| `GOOGLE_CLIENT_SECRET`   | *(blank)*                                             | from `.env`                    | Google OAuth client |
| `GOOGLE_REDIRECT_URI`    | `http://localhost:8080/api/google/calendar/callback`  | —                              | Must match the OAuth client exactly |
| `GEMINI_API_KEY`         | *(blank → AI features off)*                           | from `.env`                    | Gemini API |
| `GEMINI_MODEL`           | `gemini-3.6-flash`                                    | —                              | Gemini model id |
| `RESEND_API_KEY`         | *(blank)*                                             | from `.env`                    | Bound to `resend.api-key`, but **nothing in the code reads it**. Email is sent through the Gmail API using the Google OAuth link |
| `REPORT_IMAGE_MAX_WIDTH` | `1600`                                                | —                              | Width photos are scaled to before being base64-inlined into the report |
| `JPA_SHOW_SQL`           | `true`                                                | —                              | SQL echo in logs |
| `LOG_LEVEL_APP`          | `INFO`                                                | —                              | Log level for `ca.inspection.home` |
| `LOG_FILE`               | `logs/home-inspection.log`                            | —                              | Rolling log file (10 MB × 14) |

Compose also sets `SPRING_APPLICATION_JSON` to force SQLite `journal_mode=DELETE`. This repeats the value
already in `application.properties`. It sets `SPRING_BASE_URL` on `pdf-service` too, but `server.py` never reads it.

### Other fixed settings

- Upload limits: 500 MB per file, 1000 MB per request (`spring.servlet.multipart.*`).
- Outbound HTTP (`config/AppConfig.java`): one shared `RestTemplate` with a **10 s connect / 60 s read**
  timeout. This covers PDF generation, so a report that takes WeasyPrint longer than 60 s to render will fail.
- Hikari pool: max 3 connections, `busy_timeout=5000`, `synchronous=NORMAL`.

## Data and persistence

| What              | Host path             | Container path  | Notes |
|-------------------|-----------------------|-----------------|-------|
| Database          | `data/database.db`    | `/app/data`     | SQLite, gitignored |
| Uploaded files    | `uploads/`            | `/app/uploads`  | Photos and appendix PDFs, gitignored |
| Application logs  | —                     | `/app/logs`     | **Not mounted.** Lost when the container is recreated (every `up --build`). Use `docker compose logs` or add a volume if you need history |

- **Schema**: generated by Hibernate from the entities (`ddl-auto=update`). There are no migrations. Renaming or dropping a column
  leaves the old one in place, and changing a type can fail silently.
- **Fresh install**: create an empty database and load the field definitions:
  `sqlite3 data/database.db < seed/field_definitions.sql`. The seed uses plain `CREATE TABLE`, so it only
  works on an empty database. Start the app afterwards, and Hibernate adds the remaining tables.
- **Journal mode**: must stay `DELETE`. WAL needs shared-memory files, which don't work on a Windows
  filesystem bind-mounted into a WSL container. Tests use WAL safely because their database sits in `target/`.

## Backups

`scripts/backup.ps1` isn't scheduled by anything in the repo. Run it by hand or from Windows Task Scheduler.

1. Takes a consistent snapshot with `sqlite3 .backup` into `%TEMP%` (never a raw file copy), runs
   `PRAGMA integrity_check`, and logs row counts.
2. Copies the snapshot to `backups/db/` (inside the project, gitignored) and `D:\Home-inspection-backups\db\`,
   and deletes snapshots older than 30 days. A destination that can't be reached is skipped, not treated as an error.
3. Mirrors `uploads/` to `D:\Home-inspection-backups\uploads\` with `robocopy /MIR`. Photos deleted in the app
   are deleted from the mirror too, so the mirror is not a history of past versions.
4. Writes a log to `backups/backup.log`. If the database isn't found (drive not attached), it exits 0 without doing anything.

It needs `sqlite3.exe` on `PATH` or under `C:\msys64\{ucrt64,usr}\bin\`.

**Restore**: stop the app, replace `data/database.db` with a `database-<stamp>.db` snapshot, copy
`uploads/` back from the mirror, then start again.

## External services

All integrations are optional. With the key left blank, the feature switches itself off.

| Service                    | Endpoints                                                              | Used for |
|----------------------------|------------------------------------------------------------------------|----------|
| Google OAuth               | `accounts.google.com`, `oauth2.googleapis.com`, `www.googleapis.com/oauth2` | Linking the inspector's Google account |
| Google Calendar            | `www.googleapis.com/calendar/v3`                                       | Syncing bookings to a calendar |
| Gmail                      | `gmail.googleapis.com/gmail/v1/users/me/messages/send`                 | Sending email as the linked account |
| Gemini                     | `generativelanguage.googleapis.com/v1beta`                             | AI text features |

OAuth scopes requested: `calendar.events`, `calendar.readonly`, `gmail.send`. The Google Cloud setup steps are
in [README.md](README.md#linking-google-calendar-optional). If Google can't be reached, bookings are
still saved and the error is logged.

## Running outside Docker (development)

Default paths assume the backend runs from inside `home-inspection/`:

```powershell
cd home-inspection
.\mvnw.cmd spring-boot:run        # uses ../data/database.db, ../uploads, localhost:3001
```

For the PDF service, the easiest option is to run only that container: `wsl docker compose up --build pdf-service`.
Port 3001 is published to the host so a locally run backend can reach it. A native Windows run needs the
GTK/Pango DLLs as well as `pip install -r requirements.txt`.

### Tests

- `./mvnw test` runs unit tests only. `-DincludeIT` runs everything, and `-DonlyIT` runs only `@Tag("integration")` tests.
- The `test` profile (`src/test/resources/application-test.properties`) uses `target/integration-test.db`
  (`create-drop`), writes uploads to `target/`, points the PDF service at dead port 0, and blanks all API keys.
- `HomeInspectionApplicationTests` and `ReportTemplateRenderTest` don't use that profile and boot against
  the **real** `../data/database.db`.
- There is no CI. Tests only run locally.

## Security notes

- No authentication. Anyone who can reach port 8080 has full access.
- Compose publishes `8080` and `3001` on all interfaces, so both are reachable from other machines on the LAN
  if the Windows firewall and WSL networking mode allow it. To keep the app local only, bind them to loopback
  (`"127.0.0.1:8080:8080"`).
- `pdf-service` runs Flask's development server and renders whatever HTML it receives.
- Secrets live only in `home-inspection/.env`. Google OAuth tokens are stored in the SQLite database, so
  backups contain them.

## Repo housekeeping

Stray files that are tracked in git and can probably be removed:

- `home-inspection/database.db;` (filename ends in a semicolon)
- `__pycache__/definition_value_maker.cpython-314.pyc` at the repo root
- `pdf-service/__pycache__/` (untracked, not ignored)
