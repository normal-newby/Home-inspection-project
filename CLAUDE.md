# Home Inspection App

Report-writing tool for home inspectors. Two services, run together via Docker (WSL):
- `home-inspection/` — Spring Boot 4 / Java 25 backend + frontend, SQLite via JPA
- `pdf-service/` — Flask + WeasyPrint; turns rendered HTML into the report PDF

Deployment, config/env vars, data persistence, backups and external services: see [INFRASTRUCTURE.md](INFRASTRUCTURE.md).

## Commands

```bash
# Full app (what start.bat does) — http://localhost:8080
wsl docker compose up --build

# Backend tests (run inside home-inspection/; use .\mvnw.cmd from PowerShell)
./mvnw test              # unit tests only
./mvnw test -DincludeIT  # unit + integration (@Tag("integration"))
./mvnw test -DonlyIT     # integration only

# Fresh database: load field definitions into an EMPTY db (plain CREATE TABLE)
sqlite3 data/database.db < seed/field_definitions.sql
```

## Architecture

- Frontend is plain HTML/JS/CSS under `home-inspection/src/main/resources/static/` — no build step.
  Pages are `static/html/*.html` (`/` redirects to `/html/index.html`); JS is grouped by page in `static/js/`.
- Backend: `controller/` → `service/` → `repository/` → `entity/`, with DTOs in `DTO/`. Schema comes from the
  entities (`ddl-auto=update`); there are no migrations.
- PDF flow: `ReportViewController` builds a Thymeleaf context → `templates/report.html` →
  `ReportViewService.generatePdf` inlines `static/styles.css` and POSTs `{html, appendixBase64}` to
  `pdf-service /generate-pdf` → WeasyPrint. Appendix PDFs are converted to SVG pages (PyMuPDF) and added before `</body>`.
- Images are base64-inlined into the report HTML; annotations are drawn onto the images at that point.
- Data lives outside the repo image: `data/database.db` and `uploads/` (bind-mounted, gitignored).
- Optional integrations are configured in `home-inspection/.env` (loaded by Spring and by compose):
  `GOOGLE_CLIENT_ID/SECRET` (Calendar + sending email via the Gmail API), `GEMINI_API_KEY`. If blank, the feature turns off.
  `RESEND_API_KEY` is bound to `resend.api-key` but nothing reads it.

## Gotchas

- **Two different `styles.css` files**: `static/styles.css` = PDF report styling; `static/css/styles.css` = web UI styling.
- Keep SQLite in `journal_mode=DELETE` — WAL doesn't work on the WSL/Docker bind mount.
- Default paths (`../data/database.db`, `../uploads`, pdf service at `localhost:3001`) assume the backend
  runs from inside `home-inspection/`.
- `HomeInspectionApplicationTests` and `ReportTemplateRenderTest` have no `test` profile, so they boot
  against the real `../data/database.db`. New Spring tests should use `@ActiveProfiles("test")`
  (throwaway db in `target/`; the pdf service points at a dead port, so mock `RestTemplate`).
- pdf-service outside Docker on Windows: WeasyPrint needs native Pango DLLs from MSYS2
  (`pacman -S mingw-w64-ucrt-x86_64-pango`) and the user env var `WEASYPRINT_DLL_DIRECTORIES=C:\msys64\ucrt64\bin`;
  `libgobject-2.0-0` import errors mean one of these is missing.
- `scripts/backup.ps1` backs up the database with sqlite3 `.backup` and copies `uploads/` to D:. Never copy the live `database.db` file directly.
