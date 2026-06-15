#!/usr/bin/env pwsh
# secrets-install.ps1 - one-shot setup for the secrets filter and pre-commit hook.
# Run once after clone, or after editing .secrets.local schema.

$ErrorActionPreference = 'Stop'

# scripts/ lives at <repo>/scripts; resolve repo root relative to this file
# to avoid relying on git's UTF-8 stdout (broken in PS 5.1 with non-ASCII paths).
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path -LiteralPath (Join-Path $repoRoot '.git'))) {
    throw "Repo root not detected at $repoRoot (no .git directory)."
}

Set-Location -LiteralPath $repoRoot

Write-Host '==> Detecting PowerShell executable...' -ForegroundColor Cyan
$psExe = (Get-Command pwsh -ErrorAction SilentlyContinue)
if (-not $psExe) { $psExe = (Get-Command powershell -ErrorAction Stop) }
$psPath = $psExe.Source
Write-Host "    using $psPath"

Write-Host '==> Registering git filter "secrets"...' -ForegroundColor Cyan
# Use forward slashes - git config on Windows handles them and avoids \ escaping headaches.
$cleanScript  = ($repoRoot -replace '\\', '/') + '/scripts/secrets-clean.ps1'
$smudgeScript = ($repoRoot -replace '\\', '/') + '/scripts/secrets-smudge.ps1'
$psPathFwd    = ($psPath -replace '\\', '/')

& git config filter.secrets.clean  "`"$psPathFwd`" -NoProfile -ExecutionPolicy Bypass -File `"$cleanScript`""
& git config filter.secrets.smudge "`"$psPathFwd`" -NoProfile -ExecutionPolicy Bypass -File `"$smudgeScript`""
& git config filter.secrets.required true

Write-Host '==> Installing pre-commit hook...' -ForegroundColor Cyan
$hookSrc = Join-Path $repoRoot 'scripts/pre-commit'
$hookDst = Join-Path $repoRoot '.git/hooks/pre-commit'
Copy-Item -LiteralPath $hookSrc -Destination $hookDst -Force
Write-Host "    installed -> $hookDst"

if (-not (Test-Path -LiteralPath (Join-Path $repoRoot '.secrets.local'))) {
    Write-Warning '.secrets.local not found. Copy .secrets.template -> .secrets.local and fill in real values.'
} else {
    Write-Host '==> .secrets.local detected.' -ForegroundColor Green
}

Write-Host ''
Write-Host '==> Re-normalizing tracked files through the filter...' -ForegroundColor Cyan
Write-Host '    (this rewrites the index so already-tracked files get clean placeholders)'
& git add --renormalize .

Write-Host ''
Write-Host 'Done. Filter is active. Try: git status' -ForegroundColor Green
