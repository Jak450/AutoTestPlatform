#!/usr/bin/env pwsh
# secrets-smudge.ps1 - placeholder -> real value
# Mirror of secrets-clean.ps1 in the opposite direction. Byte-perfect when no-op.

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$secretsFile = Join-Path $repoRoot '.secrets.local'

$stdin = [Console]::OpenStandardInput()
$ms = New-Object System.IO.MemoryStream
$buf = New-Object byte[] 8192
while (($n = $stdin.Read($buf, 0, $buf.Length)) -gt 0) { $ms.Write($buf, 0, $n) }
$bytes = $ms.ToArray()
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$content = $utf8NoBom.GetString($bytes)
$original = $content

if (Test-Path -LiteralPath $secretsFile) {
    foreach ($line in (Get-Content -LiteralPath $secretsFile -Encoding UTF8)) {
        $t = $line.Trim()
        if ($t -eq '' -or $t.StartsWith('#')) { continue }
        $eq = $t.IndexOf('=')
        if ($eq -lt 1) { continue }
        $key = $t.Substring(0, $eq).Trim()
        $val = $t.Substring($eq + 1)
        if ($val -eq '' -or $val.StartsWith('<')) { continue }
        $content = $content.Replace("<your $key>", $val)
    }
}

$stdout = [Console]::OpenStandardOutput()
if ($content -eq $original) {
    $stdout.Write($bytes, 0, $bytes.Length)
} else {
    $outBytes = $utf8NoBom.GetBytes($content)
    $stdout.Write($outBytes, 0, $outBytes.Length)
}
$stdout.Flush()
