[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'ClassroomLan.Common.psm1') -Force

try {
    Assert-ClassroomLanWindows
    $result = Stop-ClassroomLanPortForward
    if ($result.Stopped) {
        Write-Host 'Classroom LAN bridge stopped. Kubernetes workloads and PVC were not changed.'
    }
} catch {
    Write-Error $_.Exception.Message
    exit 1
}
