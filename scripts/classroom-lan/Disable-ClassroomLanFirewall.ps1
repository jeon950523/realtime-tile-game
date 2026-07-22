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

    $name = Get-ClassroomLanFirewallRuleName -Port $Port
    $group = 'Realtime Tile Game Classroom LAN'
    $rule = Get-NetFirewallRule -Name $name -ErrorAction SilentlyContinue
    if (-not $rule) {
        Write-Host 'Project firewall rule is already absent.'
        exit 0
    }
    if ($rule.Group -ne $group) {
        throw "A firewall rule with the reserved name '$name' exists outside the project group. Refusing to remove it."
    }

    Remove-NetFirewallRule -Name $name -ErrorAction Stop
    Write-Host "Removed project firewall rule: $($rule.DisplayName)"
    Write-Host 'No other firewall rules were changed.'
} catch {
    Write-Error $_.Exception.Message
    exit 1
}
