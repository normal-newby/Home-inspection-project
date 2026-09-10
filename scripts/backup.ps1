# for local backups of the SQLite database and uploaded photos, to a second physical disk
# script made by claude code

$ErrorActionPreference = 'Stop'

$Project    = Split-Path -Parent $PSScriptRoot
$Db         = Join-Path $Project 'data\database.db'
$Uploads    = Join-Path $Project 'uploads'
$RetainDays = 30

# H: travels - if the drive isn't attached, exit quietly instead of erroring
if (-not (Test-Path $Db)) { Write-Output 'database not found (drive not attached?) - skipping'; exit 0 }

# --- destinations (missing ones are skipped, so this works when H: travels) ---
$DbDests    = @(
    (Join-Path $Project 'backups\db'),
    'D:\Home-inspection-backups\db'
)
$UploadDest = 'D:\Home-inspection-backups\uploads'

# --- locate sqlite3 ---
$sqlite = (Get-Command sqlite3.exe -ErrorAction SilentlyContinue).Source
if (-not $sqlite) {
    foreach ($c in @('C:\msys64\ucrt64\bin\sqlite3.exe','C:\msys64\usr\bin\sqlite3.exe')) {
        if (Test-Path $c) { $sqlite = $c; break }
    }
}
if (-not $sqlite) { throw 'sqlite3.exe not found' }

$Log = Join-Path $Project 'backups\backup.log'
New-Item -ItemType Directory -Force -Path (Split-Path $Log) | Out-Null
function Say($m) {
    $line = "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')  $m"
    Write-Output $line
    Add-Content -Path $Log -Value $line -Encoding utf8
}

Say "=== backup start ==="

# --- 1. consistent DB snapshot (SQLite backup API, not a file copy) ---
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$tmp   = Join-Path $env:TEMP "hi-backup-$stamp.db"
& $sqlite $Db ".backup '$tmp'"
if ($LASTEXITCODE -ne 0) { throw "sqlite3 .backup failed ($LASTEXITCODE)" }

# verify the snapshot before trusting it
$check = & $sqlite $tmp 'PRAGMA integrity_check;'
if ($check -ne 'ok') { Remove-Item $tmp -Force; throw "snapshot failed integrity_check: $check" }
$c = (& $sqlite $tmp 'SELECT (SELECT count(*) FROM inspection_bookings), (SELECT count(*) FROM inspection_field), (SELECT count(*) FROM inspection_image);').Split('|')
$rows = "$($c[0]) bookings, $($c[1]) fields, $($c[2]) images"
Say "snapshot ok - $rows"

foreach ($d in $DbDests) {
    try {
        New-Item -ItemType Directory -Force -Path $d | Out-Null
        Copy-Item $tmp (Join-Path $d "database-$stamp.db") -Force
        # prune old snapshots
        Get-ChildItem $d -Filter 'database-*.db' -ErrorAction SilentlyContinue |
            Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$RetainDays) } |
            Remove-Item -Force
        $n = (Get-ChildItem $d -Filter 'database-*.db').Count
        Say "db -> $d  ($n snapshots retained)"
    } catch { Say "db -> $d  SKIPPED: $($_.Exception.Message)" }
}
Remove-Item $tmp -Force

# --- 2. mirror photos to a second physical disk ---
if (Test-Path $Uploads) {
    try {
        New-Item -ItemType Directory -Force -Path $UploadDest | Out-Null
        # /MIR mirrors, /XO skips older, /R:1 /W:1 keeps retries short, /NFL /NDL quiet
        robocopy $Uploads $UploadDest /MIR /R:1 /W:1 /NFL /NDL /NJH /NJS | Out-Null
        # robocopy exit codes 0-7 are success; 8+ are real failures
        if ($LASTEXITCODE -ge 8) { Say "uploads -> $UploadDest  FAILED (robocopy $LASTEXITCODE)" }
        else {
            $sz = '{0:N0} MB' -f ((Get-ChildItem $UploadDest -Recurse -File | Measure-Object Length -Sum).Sum / 1MB)
            Say "uploads -> $UploadDest  ($sz)"
        }
    } catch { Say "uploads -> $UploadDest  SKIPPED: $($_.Exception.Message)" }
}

Say "=== backup done ==="

exit 0