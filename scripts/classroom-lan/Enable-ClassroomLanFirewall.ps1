[CmdletBinding()]
param(
    [ValidateRange(1, 65535)]
    [int]$Port = 30517
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'ClassroomLan.Common.psm1') -Force

try {
    Assert-ClassroomLanWindows
    if (-not (Test-ClassroomLanAdministrator)) {
        throw 'Administrator privileges are required. Open PowerShell with Run as administrator and retry.'
    }

    $status = Get-ClassroomLanFirewallStatus -Port $Port
    $action = Get-ClassroomLanFirewallAction -Status $status
    $name = Get-ClassroomLanFirewallRuleName -Port $Port
    $displayName = Get-ClassroomLanFirewallDisplayName -Port $Port
    $group = 'Realtime Tile Game Classroom LAN'

    switch ($action) {
        'None' {
            Write-Host "Firewall rule already matches the required LocalSubnet policy: $displayName"
            exit 0
        }
        'Recreate' {
            $existing = Get-NetFirewallRule -Name $name -ErrorAction SilentlyContinue
            if ($existing -and $existing.Group -ne $group) {
                throw "A firewall rule with the reserved name '$name' exists outside the project group. Refusing to modify it."
            }
            Remove-NetFirewallRule -Name $name -ErrorAction Stop
        }
    }

    New-NetFirewallRule `
        -Name $name `
        -DisplayName $displayName `
        -Group $group `
        -Description 'Allow the Realtime Tile Game classroom LAN frontend bridge from LocalSubnet only.' `
        -Direction Inbound `
        -Action Allow `
        -Enabled True `
        -Profile Any `
        -Protocol TCP `
        -LocalPort $Port `
        -RemoteAddress LocalSubnet | Out-Null

    $verified = Get-ClassroomLanFirewallStatus -Port $Port
    if (-not $verified.IsValid) {
        throw "Firewall rule was created but verification failed: $($verified.Reason)"
    }
    Write-Host "Firewall rule enabled: $displayName"
    Write-Host 'Scope: inbound TCP, LocalSubnet only. Windows Firewall was not disabled.'
} catch {
    Write-Error $_.Exception.Message
    exit 1
}
