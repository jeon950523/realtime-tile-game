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
    $context = Assert-ClassroomLanKubernetesContext
    if (-not (Test-ClassroomLanNamespaceExists -Namespace $Namespace)) {
        throw "Kubernetes namespace does not exist: $Namespace"
    }
    $workloads = Assert-ClassroomLanWorkloadsReady -Namespace $Namespace

    $state = Read-ClassroomLanState
    if (-not $state) {
        throw 'Classroom LAN port-forward state is missing. Run Start-ClassroomLan.ps1 first.'
    }
    $schema = Test-ClassroomLanStateSchema -State $state
    if (-not $schema.IsValid) {
        throw "Classroom LAN runtime state is invalid: $($schema.Reason)"
    }
    if ([int]$state.Port -ne $Port -or [string]$state.Namespace -ne $Namespace) {
        throw "Runtime state does not match requested Port/Namespace. State: $($state.Port)/$($state.Namespace)"
    }
    if (-not (Test-ClassroomLanPrivateIPv4 -IpAddress ([string]$state.LanIp))) {
        throw "Runtime state contains an invalid LAN IPv4 address: $($state.LanIp)"
    }

    $processInfo = Get-ClassroomLanProcessInfo -ProcessId ([int]$state.Pid)
    $identity = Test-ClassroomLanPortForwardIdentity -State $state -ProcessInfo $processInfo
    if (-not $identity.IsValid) {
        throw "Managed port-forward process is not valid: $($identity.Reason)"
    }
    if (-not (Test-ClassroomLanPortListening -Port $Port)) {
        throw "TCP port $Port is not in LISTEN state."
    }

    $firewall = Get-ClassroomLanFirewallStatus -Port $Port
    if (-not $firewall.IsValid) {
        if ($firewall.Reason -eq 'AccessDenied') {
            throw 'Firewall verification requires an elevated PowerShell. Run Test-ClassroomLan.ps1 as administrator.'
        }
        throw "Firewall verification failed: $($firewall.Reason)"
    }

    $localHealth = Test-ClassroomLanHealthEndpoint -Url "http://127.0.0.1:$Port/api/health"
    $lanHealth = Test-ClassroomLanHealthEndpoint -Url "http://$($state.LanIp):$Port/api/health"
    if (-not $localHealth.Success) {
        throw "Local health failed: $($localHealth.Error)"
    }
    if (-not $lanHealth.Success) {
        throw "LAN IPv4 health failed: $($lanHealth.Error)"
    }

    [pscustomobject][ordered]@{
        KubectlContext = $context
        MySqlReady = $workloads.MySql.IsReady
        BackendReady = $workloads.Backend.IsReady
        FrontendReady = $workloads.Frontend.IsReady
        PortForwardPid = $state.Pid
        PortListening = $true
        FirewallLocalSubnet = $true
        LocalHealth = 'UP'
        LanHealth = 'UP'
        Database = 'UP'
        ClientUrl = "http://$($state.LanIp):$Port"
    } | Format-List

    Write-Host 'Host verification passed. A second classroom PC must still perform the documented client verification.'
} catch {
    Write-Error $_.Exception.Message
    exit 1
}
