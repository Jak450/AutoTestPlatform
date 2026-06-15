#!/usr/bin/env pwsh
# secrets-sync.ps1
# Code is the source of truth. Scans every file routed through the 'secrets'
# filter for @secret markers, extracts the real value next to each marker,
# and updates .secrets.local accordingly.
#
# Marker syntax (works in any language with line comments):
#   <line containing the value> // @secret KEY_NAME       (Java, JS)
#   <line containing the value> # @secret KEY_NAME        (yaml, properties)
#   // @secret KEY_NAME
#   <line containing the value>
#
# KEY_NAME must match [A-Z][A-Z0-9_]*.
#
# Usage:
#   powershell -File scripts/secrets-sync.ps1            # show diff, ask Y/n
#   powershell -File scripts/secrets-sync.ps1 -Yes       # apply without asking
#   powershell -File scripts/secrets-sync.ps1 -Prune     # delete .secrets.local entries
#                                                        # whose @secret marker no longer
#                                                        # exists in code
#   powershell -File scripts/secrets-sync.ps1 -DryRun    # show only, do not write

param(
    [switch]$Yes,
    [switch]$Prune,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $repoRoot

$secretsFile = Join-Path $repoRoot '.secrets.local'
$attrFile    = Join-Path $repoRoot '.gitattributes'

function Extract-Value {
    param([string]$line)
    if ($null -eq $line) { return '' }
    # Strip the marker comment so it doesn't pollute regex.
    $clean = [regex]::Replace($line, '\s*(//|#)\s*@secret\s+[A-Z][A-Z0-9_]*.*$', '')

    # 1. Double-quoted string
    $m = [regex]::Match($clean, '"([^"\\]*(?:\\.[^"\\]*)*)"')
    if ($m.Success) { return $m.Groups[1].Value }
    # 2. Single-quoted string
    $m = [regex]::Match($clean, "'([^'\\]*(?:\\.[^'\\]*)*)'")
    if ($m.Success) { return $m.Groups[1].Value }
    # 3. yaml/properties: key: value  or  key=value
    $m = [regex]::Match($clean, '[:=]\s*(\S.*?)\s*$')
    if ($m.Success) {
        $v = $m.Groups[1].Value
        $v = [regex]::Replace($v, '\s+(#|//).*$', '')
        return $v.Trim()
    }
    return ''
}

function Mask {
    param([string]$v)
    if ($v.Length -le 8) { return '***' }
    return $v.Substring(0, 4) + '...' + $v.Substring($v.Length - 4)
}

# --- 1. Discover protected file globs from .gitattributes ---
if (-not (Test-Path -LiteralPath $attrFile)) {
    throw '.gitattributes not found.'
}

$globs = @()
foreach ($line in (Get-Content -LiteralPath $attrFile -Encoding UTF8)) {
    $t = $line.Trim()
    if ($t -eq '' -or $t.StartsWith('#')) { continue }
    if ($t -match '^(\S+)\s+filter=secrets\b') {
        $globs += $matches[1]
    }
}
if ($globs.Count -eq 0) {
    Write-Host 'No filter=secrets entries in .gitattributes. Nothing to scan.' -ForegroundColor Yellow
    exit 0
}

# --- 2. Resolve globs to concrete files via git ---
$files = @()
foreach ($glob in $globs) {
    $found = & git ls-files --cached --others --exclude-standard -- $glob 2>$null
    if ($LASTEXITCODE -eq 0 -and $found) { $files += $found }
}
$files = $files | Sort-Object -Unique | Where-Object { Test-Path -LiteralPath $_ }

if ($files.Count -eq 0) {
    Write-Host 'No files matched the protected globs.' -ForegroundColor Yellow
    exit 0
}
Write-Host "==> Scanning $($files.Count) protected file(s)..." -ForegroundColor Cyan

# --- 3. Extract @secret markers + real values from worktree ---
$discovered = [ordered]@{}
$violations = @()

foreach ($file in $files) {
    $lines = Get-Content -LiteralPath $file -Encoding UTF8
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        $m = [regex]::Match($line, '@secret\s+([A-Z][A-Z0-9_]*)')
        if (-not $m.Success) { continue }
        $key = $m.Groups[1].Value

        $value = Extract-Value $line
        if ([string]::IsNullOrEmpty($value)) {
            for ($j = $i + 1; $j -lt [Math]::Min($i + 4, $lines.Count); $j++) {
                if ($lines[$j].Trim() -eq '') { continue }
                $value = Extract-Value $lines[$j]
                break
            }
        }

        if ([string]::IsNullOrEmpty($value)) {
            $violations += "  ${file}:$($i+1)  @secret $key  (could not extract value)"
            continue
        }
        if ($value -like '<your *>') { continue }

        if ($discovered.Contains($key) -and $discovered[$key] -ne $value) {
            $violations += "  conflict: $key has two different values across files"
            continue
        }
        $discovered[$key] = $value
    }
}

if ($violations.Count -gt 0) {
    Write-Host ''
    Write-Host '==> Issues found:' -ForegroundColor Red
    foreach ($v in $violations) { Write-Host $v -ForegroundColor Yellow }
    Write-Host ''
}

# --- 4. Load existing .secrets.local ---
$existing = [ordered]@{}
if (Test-Path -LiteralPath $secretsFile) {
    foreach ($line in (Get-Content -LiteralPath $secretsFile -Encoding UTF8)) {
        $t = $line.Trim()
        if ($t -eq '' -or $t.StartsWith('#')) { continue }
        $eq = $t.IndexOf('=')
        if ($eq -lt 1) { continue }
        $existing[$t.Substring(0, $eq).Trim()] = $t.Substring($eq + 1)
    }
}

# --- 5. Compute diff ---
$toAdd = @(); $toUpdate = @(); $toRemove = @()
foreach ($k in $discovered.Keys) {
    if (-not $existing.Contains($k))         { $toAdd += $k }
    elseif ($existing[$k] -ne $discovered[$k]) { $toUpdate += $k }
}
foreach ($k in $existing.Keys) {
    if (-not $discovered.Contains($k)) { $toRemove += $k }
}

if ($toAdd.Count -eq 0 -and $toUpdate.Count -eq 0 -and ($toRemove.Count -eq 0 -or -not $Prune)) {
    Write-Host '==> .secrets.local is already in sync with code.' -ForegroundColor Green
    exit 0
}

# --- 6. Display diff ---
Write-Host ''
Write-Host '==> Planned changes to .secrets.local:' -ForegroundColor Cyan
foreach ($k in $toAdd)    { Write-Host ("  + {0,-30} = {1}" -f $k, (Mask $discovered[$k])) -ForegroundColor Green }
foreach ($k in $toUpdate) { Write-Host ("  ~ {0,-30} {1} -> {2}" -f $k, (Mask $existing[$k]), (Mask $discovered[$k])) -ForegroundColor Yellow }
if ($Prune) {
    foreach ($k in $toRemove) { Write-Host ("  - {0,-30} (no @secret marker in code)" -f $k) -ForegroundColor Red }
} elseif ($toRemove.Count -gt 0) {
    Write-Host ''
    Write-Host "  ($($toRemove.Count) entries in .secrets.local have no @secret marker in code." -ForegroundColor DarkGray
    Write-Host '   Re-run with -Prune to remove them.)' -ForegroundColor DarkGray
}

if ($DryRun) {
    Write-Host ''
    Write-Host '==> DryRun: no file modified.' -ForegroundColor Magenta
    exit 0
}

# --- 7. Confirm ---
if (-not $Yes) {
    Write-Host ''
    $resp = Read-Host 'Apply these changes to .secrets.local? [y/N]'
    if ($resp -notmatch '^[yY]') { Write-Host 'Aborted.' -ForegroundColor Yellow; exit 1 }
}

# --- 8. Build new .secrets.local content ---
$final = [ordered]@{}
foreach ($k in $existing.Keys) {
    if ($Prune -and $toRemove -contains $k) { continue }
    $final[$k] = $existing[$k]
}
foreach ($k in $discovered.Keys) { $final[$k] = $discovered[$k] }

$header = @(
    '# .secrets.local - real values used by secrets-clean/smudge filters.'
    '# Auto-managed by scripts/secrets-sync.ps1 - manual edits are allowed but will'
    '# be overwritten on the next sync if they conflict with @secret markers in code.'
    '# NEVER commit this file. (.gitignore protects it.)'
    ''
)
$body = foreach ($k in $final.Keys) { "$k=$($final[$k])" }
$content = ($header + $body) -join "`n"
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($secretsFile, $content + "`n", $utf8NoBom)

Write-Host ''
Write-Host "==> .secrets.local updated. ($($final.Count) entries)" -ForegroundColor Green
