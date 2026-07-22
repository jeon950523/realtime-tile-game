[CmdletBinding()]
param(
    [ValidateRange(1, 65535)]
    [int]$Port = 30517,

    [ValidateNotNullOrEmpty()]
    [ValidatePattern('^[a-z0-9]([-a-z0-9]*[a-z0-9])?$')]
    [string]$Namespace = 'realtime-tile-game'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'ClassroomLan.Common.psm1') -Force

try {
    Assert-ClassroomLanWindows
    Assert-ClassroomLanCommand -Name 'kubectl' | Out-Null

    $contextResult = Invoke-ClassroomLanKubectl -Arguments @('config', 'current-context') -AllowFailure
    $context = if ($contextResult.ExitCode -eq 0) { $contextResult.Text } else { 'unavailable' }
    $namespaceExists = Test-ClassroomLanNamespaceExists -Namespace $Namespace
    $workloads = if ($namespaceExists) {
        Get-ClassroomLanWorkloadStatus -Namespace $Namespace
    } else {
        [pscustomobject]@{
            MySql = [pscustomobject]@{ IsReady = $false }
            Backend = [pscustomobject]@{ IsReady = $false }
            Frontend = [pscustomobject]@{ IsReady = $false }
        }
    }

    $state = Read-ClassroomLanState
    $identity = $null
    $health = $null
    if ($state) {
        $schema = Test-ClassroomLanStateSchema -State $state
        if ($schema.IsValid) {
            $processInfo = Get-ClassroomLanProcessInfo -ProcessId ([int]$state.Pid)
            $identity = Test-ClassroomLanPortForwardIdentity -State $state -ProcessInfo $processInfo
            if ($identity.IsValid) {
                $health = Test-ClassroomLanHealthEndpoint -Url "http://127.0.0.1:$($state.Port)/api/health"
            }
        } else {
            $identity = $schema
        }
    }

    $firewall = Get-ClassroomLanFirewallStatus -Port $Port
    $stateForView = if ($state -and $identity -and $identity.Reason -notlike 'StateMissing:*' -and $identity.Reason -notin @('InvalidProcessStartTime', 'InvalidPid', 'InvalidLanIp', 'InvalidPort', 'InvalidNamespace')) { $state } else { $null }
    $view = New-ClassroomLanStatusView -Context $context -NamespaceExists $namespaceExists `
        -Workloads $workloads -State $stateForView -Identity $identity -Firewall $firewall -Health $health

    $view | Format-List
    if ($firewall.Reason -eq 'AccessDenied') {
        Write-Warning 'Firewall rule existence could not be verified because access was denied. Run Get-ClassroomLanStatus.ps1 as administrator for a definitive firewall status.'
    }
    if ($state -and $identity -and -not $identity.IsValid) {
        Write-Warning "Runtime state is stale or PID identity validation failed: $($identity.Reason)"
    }
} catch {
    Write-Error $_.Exception.Message
    exit 1
}
