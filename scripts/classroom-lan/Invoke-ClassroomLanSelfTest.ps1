[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'ClassroomLan.Common.psm1') -Force

$script:Passed = 0
$script:Failed = 0

function Assert-True {
    param([bool]$Condition, [string]$Name)
    if (-not $Condition) {
        throw "Assertion failed: $Name"
    }
}

function Assert-Equal {
    param($Expected, $Actual, [string]$Name)
    if ($Expected -ne $Actual) {
        throw "Assertion failed: $Name. Expected '$Expected', actual '$Actual'."
    }
}

function Assert-Throws {
    param([scriptblock]$Action, [string]$Name)
    $thrown = $false
    try {
        & $Action
    } catch {
        $thrown = $true
    }
    if (-not $thrown) {
        throw "Assertion failed: $Name. Expected an exception."
    }
}

function Invoke-TestCase {
    param([string]$Name, [scriptblock]$Action)
    try {
        & $Action
        $script:Passed++
        Write-Host "[PASS] $Name"
    } catch {
        $script:Failed++
        Write-Host "[FAIL] $Name -- $($_.Exception.Message)"
    }
}

Invoke-TestCase 'Invalid IPv4 and public IPv4 are rejected' {
    Assert-True (-not (Test-ClassroomLanPrivateIPv4 -IpAddress 'not-an-ip')) 'invalid text'
    Assert-True (-not (Test-ClassroomLanPrivateIPv4 -IpAddress '169.254.10.20')) 'link-local'
    Assert-True (-not (Test-ClassroomLanPrivateIPv4 -IpAddress '8.8.8.8')) 'public IPv4'
    Assert-True (Test-ClassroomLanPrivateIPv4 -IpAddress '192.168.10.20') 'RFC1918 IPv4'
}

Invoke-TestCase 'Virtual adapters are excluded' {
    Assert-True (Test-ClassroomLanVirtualAdapterName -Name 'vEthernet (WSL)') 'WSL adapter'
    Assert-True (Test-ClassroomLanVirtualAdapterName -Name 'Docker Desktop Network') 'Docker adapter'
    Assert-True (-not (Test-ClassroomLanVirtualAdapterName -Name 'Intel(R) Wi-Fi 6 AX201')) 'physical adapter'
}

Invoke-TestCase 'Multiple adapter candidates require explicit LanIp' {
    $candidates = @(
        [pscustomobject]@{ IpAddress = '192.168.0.10'; InterfaceAlias = 'Wi-Fi' },
        [pscustomobject]@{ IpAddress = '10.0.0.20'; InterfaceAlias = 'Ethernet' }
    )
    Assert-Throws { Select-ClassroomLanIPv4 -Candidates $candidates } 'multiple candidate rejection'
    Assert-Equal '10.0.0.20' (Select-ClassroomLanIPv4 -RequestedIp '10.0.0.20' -Candidates $candidates) 'explicit candidate'
}

Invoke-TestCase 'Explicit LanIp must belong to an eligible adapter' {
    $candidates = @([pscustomobject]@{ IpAddress = '192.168.0.10'; InterfaceAlias = 'Wi-Fi' })
    Assert-Throws { Select-ClassroomLanIPv4 -RequestedIp '192.168.0.99' -Candidates $candidates } 'unassigned address rejection'
}

Invoke-TestCase 'Origin list is exact, unique, and replaces an old LAN Origin' {
    $first = Get-ClassroomLanCorsOrigins -LanIp '192.168.0.10' -Port 30517
    $second = Get-ClassroomLanCorsOrigins -LanIp '192.168.0.11' -Port 30517
    Assert-Equal 3 @($first).Count 'origin count'
    Assert-Equal 3 @($first | Select-Object -Unique).Count 'origin uniqueness'
    Assert-True ($second -contains 'http://localhost:30517') 'localhost retained'
    Assert-True ($second -contains 'http://127.0.0.1:30517') 'loopback retained'
    Assert-True ($second -contains 'http://192.168.0.11:30517') 'new LAN origin present'
    Assert-True (-not ($second -contains 'http://192.168.0.10:30517')) 'old LAN origin removed'
}

Invoke-TestCase 'Malformed runtime state is rejected without trusting its PID' {
    $missingStartTime = [pscustomobject]@{ Pid = 42; LanIp = '192.168.0.10'; Port = 30517; Namespace = 'realtime-tile-game' }
    $invalidPid = [pscustomobject]@{ Pid = 'not-a-pid'; ProcessStartTimeUtc = '2026-07-16T00:00:00Z'; LanIp = '192.168.0.10'; Port = 30517; Namespace = 'realtime-tile-game' }
    Assert-True (-not (Test-ClassroomLanStateSchema -State $missingStartTime).IsValid) 'missing start time'
    Assert-True (-not (Test-ClassroomLanStateSchema -State $invalidPid).IsValid) 'invalid PID'
}

Invoke-TestCase 'Managed kubectl identity rejects dead or unrelated PIDs' {
    $state = [pscustomobject]@{
        Pid = 4242
        ProcessStartTimeUtc = '2026-07-16T00:00:00Z'
        LanIp = '192.168.0.10'
        Port = 30517
        Namespace = 'realtime-tile-game'
    }
    $validProcess = [pscustomobject]@{
        Id = 4242
        Name = 'kubectl'
        StartTimeUtc = '2026-07-16T00:00:01Z'
        CommandLine = 'kubectl port-forward service/frontend 30517:80 -n realtime-tile-game --address=127.0.0.1,192.168.0.10'
    }
    $wrongProcess = [pscustomobject]@{
        Id = 4242
        Name = 'java'
        StartTimeUtc = '2026-07-16T00:00:01Z'
        CommandLine = 'java -jar application.jar'
    }
    $validIdentity = Test-ClassroomLanPortForwardIdentity -State $state -ProcessInfo $validProcess
    $deadIdentity = Test-ClassroomLanPortForwardIdentity -State $state -ProcessInfo $null
    $wrongIdentity = Test-ClassroomLanPortForwardIdentity -State $state -ProcessInfo $wrongProcess
    Assert-True $validIdentity.IsValid 'valid port-forward'
    Assert-True (-not $deadIdentity.IsValid) 'dead PID'
    Assert-True (-not $wrongIdentity.IsValid) 'unrelated PID'
}

Invoke-TestCase 'Start disposition prevents duplicate processes and cleans stale state' {
    $state = [pscustomobject]@{ LanIp = '192.168.0.10'; Port = 30517; Namespace = 'realtime-tile-game' }
    $valid = [pscustomobject]@{ IsValid = $true }
    $invalid = [pscustomobject]@{ IsValid = $false }
    Assert-Equal 'Reuse' (Get-ClassroomLanStartDisposition -State $state -Identity $valid -LanIp '192.168.0.10' -Port 30517 -Namespace 'realtime-tile-game') 'duplicate reuse'
    Assert-Equal 'Replace' (Get-ClassroomLanStartDisposition -State $state -Identity $valid -LanIp '192.168.0.11' -Port 30517 -Namespace 'realtime-tile-game') 'IP change replacement'
    Assert-Equal 'CleanupStale' (Get-ClassroomLanStartDisposition -State $state -Identity $invalid -LanIp '192.168.0.10' -Port 30517 -Namespace 'realtime-tile-game') 'stale cleanup'
    Assert-Equal 'Start' (Get-ClassroomLanStartDisposition -State $null -Identity $null -LanIp '192.168.0.10' -Port 30517 -Namespace 'realtime-tile-game') 'fresh start'
}

Invoke-TestCase 'Firewall action is idempotent' {
    Assert-Equal 'Create' (Get-ClassroomLanFirewallAction -Status $null) 'missing rule'
    Assert-Equal 'None' (Get-ClassroomLanFirewallAction -Status ([pscustomobject]@{ Exists = $true; IsValid = $true })) 'matching rule'
    Assert-Equal 'Recreate' (Get-ClassroomLanFirewallAction -Status ([pscustomobject]@{ Exists = $true; IsValid = $false })) 'mismatched project rule'
}

Invoke-TestCase 'Firewall permission denial is reported as AccessDenied, not NotFound' {
    $deniedQuery = {
        param([string]$RuleName)
        throw [System.UnauthorizedAccessException]::new("PermissionDenied: Windows System Error 5 while querying $RuleName")
    }
    $status = Get-ClassroomLanFirewallStatus -Port 30517 -RuleQuery $deniedQuery
    Assert-True (-not $status.QuerySucceeded) 'query failure is explicit'
    Assert-True ($null -eq $status.Exists) 'existence remains unknown'
    Assert-True (-not $status.IsValid) 'rule cannot be validated'
    Assert-Equal 'AccessDenied' $status.Reason 'permission failure classification'
    Assert-True ($status.Reason -ne 'NotFound') 'permission failure is not reported as missing'

    $workloads = [pscustomobject]@{
        MySql = [pscustomobject]@{ IsReady = $true }
        Backend = [pscustomobject]@{ IsReady = $true }
        Frontend = [pscustomobject]@{ IsReady = $true }
    }
    $view = New-ClassroomLanStatusView -Context 'docker-desktop' -NamespaceExists $true -Workloads $workloads `
        -State $null -Identity $null -Firewall $status -Health $null
    Assert-True ($null -eq $view.FirewallRuleExists) 'status does not claim the rule is absent'
    Assert-True (-not $view.FirewallRuleValid) 'status keeps validation false'
    Assert-Equal 'AccessDenied' $view.FirewallReason 'status exposes the permission reason'
}

Invoke-TestCase 'Status view exposes only operational fields' {
    $workloads = [pscustomobject]@{
        MySql = [pscustomobject]@{ IsReady = $true }
        Backend = [pscustomobject]@{ IsReady = $true }
        Frontend = [pscustomobject]@{ IsReady = $true }
    }
    $state = [pscustomobject]@{ Pid = 100; LanIp = '192.168.0.10'; Port = 30517 }
    $view = New-ClassroomLanStatusView -Context 'docker-desktop' -NamespaceExists $true -Workloads $workloads `
        -State $state -Identity ([pscustomobject]@{ IsValid = $true }) `
        -Firewall ([pscustomobject]@{ Exists = $true; IsValid = $true; Reason = 'Match' }) -Health ([pscustomobject]@{ Success = $true })
    $json = $view | ConvertTo-Json -Compress
    Assert-True ($json -notmatch '(?i)(password|secret|jwt|token)') 'sensitive field absence'
    Assert-True ($json -match '192\.168\.0\.10') 'operational LAN address present'
}

Write-Host ''
Write-Host "Classroom LAN self-test result: $script:Passed passed, $script:Failed failed"
if ($script:Failed -gt 0) {
    exit 1
}
