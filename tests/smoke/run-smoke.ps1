# Usage: pwsh tests/smoke/run-smoke.ps1 -Loader fabric|neoforge [-TickSeconds 120]
# Same behaviour as run-smoke.sh for Windows hosts.
param(
  [Parameter(Mandatory)][ValidateSet('fabric', 'neoforge')][string]$Loader,
  [int]$TickSeconds = 120
)
$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$Out = Join-Path $Root 'tests\smoke\out'; New-Item -ItemType Directory -Force $Out | Out-Null
$RunDir = Join-Path $Root "$Loader\runs\server"
$Report = Join-Path $Out "$Loader-report.txt"
$Stdout = Join-Path $Out "$Loader-stdout.log"

foreach ($p in @((Join-Path $RunDir 'world'), (Join-Path $RunDir 'logs'), $Stdout)) {
  if (Test-Path $p) { Remove-Item -Recurse -Force $p }
}
New-Item -ItemType Directory -Force $RunDir | Out-Null
Set-Content (Join-Path $RunDir 'eula.txt') 'eula=true'
Set-Content (Join-Path $RunDir 'server.properties') @"
online-mode=false
view-distance=4
simulation-distance=4
spawn-protection=0
max-tick-time=60000
level-seed=mcmt-smoke
level-type=minecraft\:flat
generator-settings={"layers"\:[{"block"\:"minecraft\:bedrock","height"\:1},{"block"\:"minecraft\:stone","height"\:126},{"block"\:"minecraft\:grass_block","height"\:1}],"biome"\:"minecraft\:plains"}
generate-structures=false
sync-chunk-writes=false
"@
Set-Content $Report ''

$script:p = $null
function Fail($msg) {
  Add-Content $Report "FAIL: $msg"; Write-Host "FAIL: $msg"
  if ($script:p -and -not $script:p.HasExited) { $script:p.Kill($true) }
  exit 1
}

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = Join-Path $Root 'gradlew.bat'
$psi.Arguments = ":$Loader" + ':runServer --no-daemon --console=plain --quiet'
$psi.WorkingDirectory = $Root
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.UseShellExecute = $false
$script:p = [System.Diagnostics.Process]::Start($psi)
$sw = [System.IO.StreamWriter]::new($Stdout); $sw.AutoFlush = $true
$handler = { if ($EventArgs.Data) { $Event.MessageData.WriteLine($EventArgs.Data) } }
Register-ObjectEvent $script:p OutputDataReceived -Action $handler -MessageData $sw | Out-Null
Register-ObjectEvent $script:p ErrorDataReceived -Action $handler -MessageData $sw | Out-Null
$script:p.BeginOutputReadLine(); $script:p.BeginErrorReadLine()

$deadline = (Get-Date).AddSeconds(600)
while ((Get-Date) -lt $deadline) {
  if ($script:p.HasExited) { Fail 'server process exited before Done' }
  if ((Test-Path $Stdout) -and (Select-String -Path $Stdout -Pattern 'Done \(' -Quiet)) { break }
  Start-Sleep 1
}
if (-not (Select-String -Path $Stdout -Pattern 'Done \(' -Quiet)) { Fail 'server did not reach Done within 600s' }
Add-Content $Report 'server started'

function Send($cmd) { $script:p.StandardInput.WriteLine($cmd); $script:p.StandardInput.Flush() }
foreach ($line in Get-Content (Join-Path $Root 'tests\smoke\commands.txt')) {
  if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) { continue }
  if ($line -match '^sleep (\d+)$') {
    $s = [int]$Matches[1]; if ($s -eq 120) { $s = $TickSeconds }; Start-Sleep $s
  } elseif ($line -match '^repeat (\d+) (.+)$') {
    $n = [int]$Matches[1]; $c = $Matches[2]
    for ($i = 0; $i -lt $n; $i++) { Send $c }
  } else { Send $line }
}

if (-not $script:p.WaitForExit(90000)) { Fail 'server did not stop within 90s after /stop' }
Start-Sleep 1
Add-Content $Report 'server stopped cleanly'
$sw.Close()

$allow = Get-Content (Join-Path $Root 'tests\smoke\allowlist.txt') | Where-Object { $_ -and -not $_.StartsWith('#') }
$filtered = Get-Content $Stdout | Where-Object { $l = $_; -not ($allow | Where-Object { $l -match $_ }) }
Set-Content (Join-Path $Out "$Loader-filtered.log") $filtered
$pattern = 'Exception|Error:|ERROR\]|FATAL|Watchdog|A single server tick took|Mixin apply failed|Could not load mod'
$bad = $filtered | Select-String -Pattern $pattern
if ($bad) {
  Add-Content $Report '--- offending lines ---'
  $bad | Select-Object -First 50 | ForEach-Object { Add-Content $Report $_.Line }
  Fail 'errors found in server log'
}
if (-not (Select-String -Path $Stdout -Pattern 'MCMT is currently' -Quiet)) { Fail '/mcmt config state produced no output' }
if (-not (Select-String -Path $Stdout -Pattern 'Current max threads' -Quiet)) { Fail '/mcmt stats produced no output' }
if (-not (Select-String -Path $Stdout -Pattern 'MCMT thread pool started' -Quiet)) { Fail 'thread pool was never started' }
$tasks = (Select-String -Path $Stdout -Pattern 'Parallel tasks since start: World:(\d+) Entity:(\d+) TE:(\d+) Env:(\d+)' | Select-Object -Last 1)
if (-not $tasks) { Fail 'no task counters in output' }
Add-Content $Report $tasks.Matches[0].Value
if ([int]$tasks.Matches[0].Groups[1].Value -le 0) { Fail 'no level ticks ran on the worker pool' }
if ([int]$tasks.Matches[0].Groups[2].Value -le 0) { Fail 'no entity ticks ran on the worker pool' }
Add-Content $Report 'PASS'; Write-Host 'PASS'
