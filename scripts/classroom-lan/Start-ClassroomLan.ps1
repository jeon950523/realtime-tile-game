[CmdletBinding()]
param(
    [string]$LanIp,

    [ValidateRange(1, 65535)]
    [int]$Port = 30517,

    [ValidateNotNullOrEmpty()]
    [ValidatePattern('^[a-z0-9]([-a-z0-9]*[a-z0-9])?$')]
    [string]$Namespace = 'realtime-tile-game',

    [switch]$SkipFirewallCheck
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'ClassroomLan.Common.psm1') -Force

try {
    Assert-ClassroomLanWindows
    Assert-ClassroomLanCommand -Name 'kubectl' | Out-Null
    $context = Assert-ClassroomLanKubernetesContext

    if (-not (Test-ClassroomLanNamespaceExists -Namespace $Namespace)) {
        throw "Kubernetes namespace does not exist: $Namespace"
    }
    Assert-ClassroomLanWorkloadsReady -Namespace $Namespace | Out-Null

    $candidates = Get-ClassroomLanIPv4Candidates
    $selectedIp = Select-ClassroomLanIPv4 -RequestedIp $LanIp -Candidates $candidates

    if (-not $SkipFirewallCheck) {
        $firewall = Get-ClassroomLanFirewallStatus -Port $Port
        if (-not $firewall.IsValid) {
            switch ($firewall.Reason) {
                'AccessDenied' {
                    throw 'Firewall verification requires an elevated PowerShell. Run Start-ClassroomLan.ps1 as administrator.'
                }
                'NotFound' {
                    throw "The project firewall rule is missing for TCP $Port. Run Enable-ClassroomLanFirewall.ps1 in an elevated PowerShell."
                }
                'ConfigurationMismatch' {
                    throw "The project firewall rule configuration is invalid for TCP $Port. Run Enable-ClassroomLanFirewall.ps1 in an elevated PowerShell to repair the project rule."
                }
                default {
                    throw "Firewall verification failed for TCP ${Port}: $($firewall.Reason). Use -SkipFirewallCheck only for an intentional local diagnostic."
                }
            }
        }
    }

    $state = Read-ClassroomLanState
    $identity = $null
    if ($state) {
        $schema = Test-ClassroomLanStateSchema -State $state
        if ($schema.IsValid) {
            $processInfo = Get-ClassroomLanProcessInfo -ProcessId ([int]$state.Pid)
            $identity = Test-ClassroomLanPortForwardIdentity -State $state -ProcessInfo $processInfo
        } else {
            $identity = $schema
        }
    }

    $disposition = Get-ClassroomLanStartDisposition -State $state -Identity $identity `
        -LanIp $selectedIp -Port $Port -Namespace $Namespace

    switch ($disposition) {
        'Reuse' {
            $cors = Set-ClassroomLanCorsOrigins -Namespace $Namespace -LanIp $selectedIp -Port $Port
            $healthUrls = @(
                "http://127.0.0.1:$Port/api/health",
                "http://${selectedIp}:$Port/api/health"
            )
            try {
                Wait-ClassroomLanHealth -Urls $healthUrls -TimeoutSeconds 45 | Out-Null
                Write-Host 'Classroom LAN bridge is already running.'
                Write-Host "Context : $context"
                Write-Host "PID     : $($state.Pid)"
                Write-Host "Local   : http://127.0.0.1:$Port"
                Write-Host "Client  : http://${selectedIp}:$Port"
                Write-Host "CORS    : $(if ($cors.Changed) { 'updated' } else { 'unchanged' })"
                return
            } catch {
                Write-Warning 'The registered port-forward process is alive but unhealthy. Replacing only the verified managed process.'
                Stop-ClassroomLanPortForward -Quiet | Out-Null
            }
        }
        'Replace' {
            Write-Host 'Existing managed port-forward uses different settings. Replacing it safely.'
            Stop-ClassroomLanPortForward -Quiet | Out-Null
        }
        'CleanupStale' {
            Write-Warning 'Removing stale Classroom LAN runtime state. No unverified process will be terminated.'
            Remove-ClassroomLanState
        }
    }

    $cors = Set-ClassroomLanCorsOrigins -Namespace $Namespace -LanIp $selectedIp -Port $Port
    Assert-ClassroomLanWorkloadsReady -Namespace $Namespace | Out-Null

    $newState = Start-ClassroomLanPortForward -LanIp $selectedIp -Port $Port -Namespace $Namespace
    try {
        Wait-ClassroomLanHealth -Urls @(
            "http://127.0.0.1:$Port/api/health",
            "http://${selectedIp}:$Port/api/health"
        ) -TimeoutSeconds 45 | Out-Null
    } catch {
        Stop-ClassroomLanPortForward -Quiet | Out-Null
        throw
    }

    Write-Host ''
    Write-Host 'Classroom LAN bridge started successfully.'
    Write-Host "Context : $context"
    Write-Host "PID     : $($newState.Pid)"
    Write-Host "Local   : http://127.0.0.1:$Port"
    Write-Host "Client  : http://${selectedIp}:$Port"
    Write-Host "Health  : http://${selectedIp}:$Port/health"
    Write-Host "CORS    : $(if ($cors.Changed) { 'updated and backend rolled out' } else { 'already exact' })"
    Write-Host ''
    Write-Host 'This bridge is bound only to 127.0.0.1 and the selected LAN IPv4.'
    Write-Host 'It is for a trusted classroom LocalSubnet, not internet exposure.'
} catch {
    Write-Error $_.Exception.Message
    exit 1
}
