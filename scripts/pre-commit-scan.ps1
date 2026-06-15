#!/usr/bin/env pwsh
# pre-commit-scan.ps1
# Runs INSIDE the pre-commit hook. Three jobs, in order:
#   1. Sync .secrets.local from @secret markers in code (so newly rotated /
#      newly added secrets are registered before the filter sees them).
#   2. If sync changed .secrets.local, re-stage every staged file that's routed
#      through the secrets filter. Critical: the clean filter already ran during
#      'git add' using the OLD .secrets.local. Without re-staging, freshly
#      rotated keys would slip into the commit unredacted.
#   3. Final defence: scan the staged diff for known secret patterns. Reject if
#      any are present, whether registered or not (registered values must have
#      been replaced by the filter; if they're still there, something is wrong).

$ErrorActionPreference = 'Stop'

$repoRoot    = Split-Path -Parent $PSScriptRoot
$secretsFile = Join-Path $repoRoot '.secrets.local'
$attrFile    = Join-Path $repoRoot '.gitattributes'

function Get-FileSha {
    param([string]$path)
    if (-not (Test-Path -LiteralPath $path)) { return '' }
    return (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
}

# --- 1. Auto-sync .secrets.local from @secret markers in code ---
$syncScript = Join-Path $PSScriptRoot 'secrets-sync.ps1'
if (Test-Path -LiteralPath $syncScript) {
    & powershell -NoProfile -ExecutionPolicy Bypass -File $syncScript -Yes -Prune | Out-Host
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'pre-commit: secrets-sync failed; aborting commit.' -ForegroundColor Red
        exit 1
    }
}

# --- 2. ALWAYS re-stage protected files so the clean filter runs against the
#        current .secrets.local. This handles two cases:
#        (a) sync just changed .secrets.local -> filter must re-run.
#        (b) user did 'git add' before .secrets.local was up-to-date -> filter
#            ran with stale data; re-stage to fix.
Write-Host '==> Re-staging protected files through clean filter...' -ForegroundColor Cyan

$globs = @()
if (Test-Path -LiteralPath $attrFile) {
    foreach ($line in (Get-Content -LiteralPath $attrFile -Encoding UTF8)) {
        $t = $line.Trim()
        if ($t -eq '' -or $t.StartsWith('#')) { continue }
        if ($t -match '^(\S+)\s+filter=secrets\b') { $globs += $matches[1] }
    }
}

$staged = @(& git diff --cached --name-only)
$reStaged = @()
foreach ($glob in $globs) {
    $matchedStaged = @(& git ls-files --cached -- $glob)
    foreach ($f in $matchedStaged) {
        if ($staged -contains $f -and $reStaged -notcontains $f) {
            Write-Host "    re-stage: $f"
            # --renormalize forces git to re-run the clean filter even when the
            # file's stat hasn't changed. Plain 'git add' would silently skip
            # files git thinks are unchanged.
            & git add --renormalize -- $f
            if ($LASTEXITCODE -ne 0) {
                Write-Host "pre-commit: failed to re-stage $f" -ForegroundColor Red
                exit 1
            }
            $reStaged += $f
        }
    }
}

# --- 3. Final defence: pattern scan staged blobs ---
$registered = @()
if (Test-Path -LiteralPath $secretsFile) {
    foreach ($line in (Get-Content -LiteralPath $secretsFile -Encoding UTF8)) {
        $t = $line.Trim()
        if ($t -eq '' -or $t.StartsWith('#')) { continue }
        $eq = $t.IndexOf('=')
        if ($eq -lt 1) { continue }
        $v = $t.Substring($eq + 1)
        if ($v -ne '' -and -not $v.StartsWith('<')) { $registered += $v }
    }
}

$patterns = @(
    @{ Name = 'Volcano ARK API Key'; Regex = 'ark-[A-Za-z0-9]{4,}-[A-Za-z0-9-]{8,}' },
    @{ Name = 'OpenAI API Key';      Regex = 'sk-[A-Za-z0-9]{32,}' },
    @{ Name = 'AWS Access Key';      Regex = 'AKIA[0-9A-Z]{16}' },
    @{ Name = 'GitHub PAT';          Regex = 'ghp_[A-Za-z0-9]{36}' }
)

$diff = & git diff --cached -U0 --no-color
if (-not $diff) { exit 0 }

$violations = @()
$currentFile = $null
foreach ($line in ($diff -split "`n")) {
    if ($line -match '^\+\+\+ b/(.+)$') { $currentFile = $matches[1]; continue }
    if (-not $line.StartsWith('+')) { continue }
    if ($line.StartsWith('+++')) { continue }
    $added = $line.Substring(1)
    foreach ($p in $patterns) {
        $hits = [regex]::Matches($added, $p.Regex)
        foreach ($hit in $hits) {
            $val = $hit.Value
            $violations += [PSCustomObject]@{
                File         = $currentFile
                Pattern      = $p.Name
                Value        = $val
                IsRegistered = ($registered -contains $val)
            }
        }
    }
}

if ($violations.Count -eq 0) { exit 0 }

Write-Host ''
Write-Host '================================================================' -ForegroundColor Red
Write-Host '  pre-commit: SECRET DETECTED IN STAGED CONTENT' -ForegroundColor Red
Write-Host '================================================================' -ForegroundColor Red
foreach ($v in $violations) {
    $masked = if ($v.Value.Length -gt 12) { $v.Value.Substring(0,8) + '...' + $v.Value.Substring($v.Value.Length-4) } else { '***' }
    $tag = if ($v.IsRegistered) { '[FILTER FAILED]' } else { '[UNREGISTERED]  ' }
    Write-Host ("  {0,-22} {1} {2}  in {3}" -f $v.Pattern, $tag, $masked, $v.File) -ForegroundColor Yellow
}
Write-Host ''
Write-Host '  Diagnosis:' -ForegroundColor Cyan
Write-Host '    [FILTER FAILED]  secret IS registered but slipped through.'
Write-Host '                     Check that the file is covered by .gitattributes:'
Write-Host '                       git check-attr filter <file>'
Write-Host '    [UNREGISTERED]   secret has no @secret marker in code.'
Write-Host '                     Add  // @secret KEY_NAME  next to it, then re-commit.'
Write-Host ''
Write-Host '  Bypass once (NOT recommended): git commit --no-verify' -ForegroundColor DarkGray
Write-Host ''
exit 1
