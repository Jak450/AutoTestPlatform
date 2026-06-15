#!/usr/bin/env pwsh
# secrets-clean.ps1 - real value -> placeholder
# Reads stdin as bytes, decodes as UTF-8, replaces, writes back as UTF-8 bytes.
# Critical: must NOT alter byte content when no replacements happen,
# otherwise git --renormalize will mark unrelated files as modified.

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$secretsFile = Join-Path $repoRoot '.secrets.local'

# Read stdin as raw bytes -> UTF-8 string (no BOM, no line-ending mangling)
$stdin = [Console]::OpenStandardInput()
$ms = New-Object System.IO.MemoryStream
$buf = New-Object byte[] 8192
while (($n = $stdin.Read($buf, 0, $buf.Length)) -gt 0) { $ms.Write($buf, 0, $n) }
$bytes = $ms.ToArray()
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$content = $utf8NoBom.GetString($bytes)
$original = $content

if (Test-Path -LiteralPath $secretsFile) {
    $entries = @()
    foreach ($line in (Get-Content -LiteralPath $secretsFile -Encoding UTF8)) {
        $t = $line.Trim()
        if ($t -eq '' -or $t.StartsWith('#')) { continue }
        $eq = $t.IndexOf('=')
        if ($eq -lt 1) { continue }
        $key = $t.Substring(0, $eq).Trim()
        $val = $t.Substring($eq + 1)
        if ($val -eq '' -or $val.StartsWith('<')) { continue }
        $entries += [PSCustomObject]@{ Key = $key; Value = $val }
    }
    $entries = $entries | Sort-Object { $_.Value.Length } -Descending
    foreach ($e in $entries) {
        $content = $content.Replace($e.Value, "<your $($e.Key)>")
    }
}

$stdout = [Console]::OpenStandardOutput()
if ($content -eq $original) {
    # No replacement happened: emit original bytes verbatim, byte-perfect.
    $stdout.Write($bytes, 0, $bytes.Length)
} else {
    $outBytes = $utf8NoBom.GetBytes($content)
    $stdout.Write($outBytes, 0, $outBytes.Length)
}
$stdout.Flush()
