<#
  Sets XGram's GitHub Actions secrets and variables from the files on this PC, so nothing secret is typed or printed:
    ~/.xgram/keystore.properties  keystore.file, keystore.password, key.alias, key.password
    the keystore file it names    -> XGRAM_KEYSTORE_BASE64
    app/google-services.json      -> GOOGLE_SERVICES_JSON (the real Firebase file, kept out of git)
    ~/.xgram/telegram.env         -> XGRAM_BOT_TOKEN, TELEGRAM_API_ID, TELEGRAM_API_HASH (secrets),
                                     XGRAM_CHANNEL_ID, XGRAM_GROUP_ID, XGRAM_TOPIC_UPDATES, XGRAM_TOPIC_SUPPORT,
                                     XGRAM_CI_CHANNEL_LINK (variables)
  Secrets reach gh through a temporary file on stdin (never on the command line) that is deleted right away.

  Windows PowerShell, from the repository folder:
    powershell -ExecutionPolicy Bypass -File scripts\ci\setup-github-secrets.ps1 -DryRun   # check, set nothing
    powershell -ExecutionPolicy Bypass -File scripts\ci\setup-github-secrets.ps1           # set them
#>
param([switch]$DryRun)
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$userHome = $env:USERPROFILE
$xgram = Join-Path $userHome '.xgram'

function Read-Properties ($path) {
  $map = @{}
  if (Test-Path $path) {
    foreach ($line in Get-Content -LiteralPath $path -Encoding UTF8) {
      if ($line -match '^\s*([A-Za-z0-9_.]+)\s*=\s*(.*?)\s*$' -and -not $line.TrimStart().StartsWith('#')) {
        $value = $Matches[2]
        if ($value -and -not $value.StartsWith('<')) { $map[$Matches[1]] = $value }   # "<placeholder>" = unset
      }
    }
  }
  return $map
}

Push-Location $root
try {
  # The XGram repository is the "origin" remote. gh's own default can be Telegram X ("upstream"), so never ask gh.
  $origin = (& git remote get-url origin).Trim()
  $repo = $null
  if ($origin -match 'github\.com[:/]([^/]+/[^/]+?)(\.git)?$') { $repo = $Matches[1] }
  if (-not $repo) { throw "The origin remote is not a GitHub repository: $origin" }
  if ($repo -like 'TGX-Android/*') { throw "origin points at Telegram X ($repo), not at XGram's repository." }
  Write-Host "Repository: $repo"

  $secrets = [ordered]@{}
  $variables = [ordered]@{}
  $problems = @()

  # Signing key
  $ks = Read-Properties (Join-Path $xgram 'keystore.properties')
  foreach ($k in 'keystore.file', 'keystore.password', 'key.alias', 'key.password') {
    if (-not $ks.ContainsKey($k)) { $problems += "keystore.properties has no $k" }
  }
  if ($ks.ContainsKey('keystore.file')) {
    $jks = $ks['keystore.file'] -replace '/', '\'
    if (Test-Path -LiteralPath $jks) {
      $secrets['XGRAM_KEYSTORE_BASE64'] = [Convert]::ToBase64String([IO.File]::ReadAllBytes($jks))
    } else { $problems += "keystore file not found: $jks" }
  }
  if ($ks.ContainsKey('keystore.password')) { $secrets['XGRAM_KEYSTORE_PASSWORD'] = $ks['keystore.password'] }
  if ($ks.ContainsKey('key.alias')) { $secrets['XGRAM_KEY_ALIAS'] = $ks['key.alias'] }
  if ($ks.ContainsKey('key.password')) { $secrets['XGRAM_KEY_PASSWORD'] = $ks['key.password'] }

  # Firebase
  $gs = Join-Path $root 'app\google-services.json'
  if (Test-Path $gs) {
    $json = [IO.File]::ReadAllText($gs)
    if ($json -match 'xgram-placeholder') { $problems += 'app/google-services.json is still the placeholder (push notifications)' }
    elseif ($json -notmatch '"com\.fahadali\.xgram"') { $problems += 'app/google-services.json has no com.fahadali.xgram client' }
    else { $secrets['GOOGLE_SERVICES_JSON'] = $json }
  }

  # Telegram
  $tg = Read-Properties (Join-Path $xgram 'telegram.env')
  foreach ($k in 'XGRAM_BOT_TOKEN', 'TELEGRAM_API_ID', 'TELEGRAM_API_HASH') {
    if ($tg.ContainsKey($k)) { $secrets[$k] = $tg[$k] }
    else { $problems += "telegram.env has no $k" + $(if ($k -like 'TELEGRAM_*') { ' (needed to post APKs over 49 MB)' } else { '' }) }
  }
  foreach ($k in 'XGRAM_CHANNEL_ID', 'XGRAM_GROUP_ID', 'XGRAM_TOPIC_UPDATES', 'XGRAM_TOPIC_SUPPORT', 'XGRAM_CI_CHANNEL_LINK') {
    if ($tg.ContainsKey($k)) { $variables[$k] = $tg[$k] }
  }
  if ($tg.ContainsKey('XGRAM_CI_CHANNEL_LINK')) { $variables['XGRAM_DOWNLOAD_URL'] = 'https://' + ($tg['XGRAM_CI_CHANNEL_LINK'] -replace '^https?://', '') }

  foreach ($p in $problems) { Write-Warning $p }
  Write-Host ("Secrets:   " + (($secrets.Keys) -join ', '))
  Write-Host ("Variables: " + (($variables.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join ', '))
  if ($DryRun) { Write-Host 'Dry run: nothing was set.'; return }

  foreach ($entry in $secrets.GetEnumerator()) {
    $tmp = [IO.Path]::GetTempFileName()
    try {
      [IO.File]::WriteAllText($tmp, $entry.Value, (New-Object Text.UTF8Encoding $false))   # no newline, no BOM
      $p = Start-Process -FilePath 'gh' -ArgumentList @('secret', 'set', $entry.Key, '--repo', $repo) -RedirectStandardInput $tmp -NoNewWindow -Wait -PassThru
      if ($p.ExitCode -ne 0) { throw "gh secret set $($entry.Key) failed" }
      Write-Host "set secret $($entry.Key)"
    } finally {
      Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue
    }
  }
  foreach ($entry in $variables.GetEnumerator()) {
    & gh variable set $entry.Key --repo $repo --body $entry.Value
    if ($LASTEXITCODE -ne 0) { throw "gh variable set $($entry.Key) failed" }
    Write-Host "set variable $($entry.Key)"
  }
  Write-Host 'Done. Check: gh secret list; gh variable list'
} finally {
  Pop-Location
}
