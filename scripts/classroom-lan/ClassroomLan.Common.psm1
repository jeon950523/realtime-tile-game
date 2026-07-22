Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:FirewallGroup = 'Realtime Tile Game Classroom LAN'

function Get-ClassroomLanProjectRoot {
    return (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
}

function Get-ClassroomLanRuntimeDirectory {
    return Join-Path (Get-ClassroomLanProjectRoot) '.runtime\classroom-lan'
}

function Get-ClassroomLanStatePath {
    return Join-Path (Get-ClassroomLanRuntimeDirectory) 'port-forward.json'
}

function Get-ClassroomLanFirewallRuleName {
    param(
        [Parameter(Mandatory)]
        [ValidateRange(1, 65535)]
        [int]$Port
    )

    return "RealtimeTileGame-ClassroomLan-TCP-$Port"
}

function Get-ClassroomLanFirewallDisplayName {
    param(
        [Parameter(Mandatory)]
        [ValidateRange(1, 65535)]
        [int]$Port
    )

    return "Realtime Tile Game Classroom LAN TCP $Port"
}

function Test-ClassroomLanPrivateIPv4 {
    param(
        [Parameter(Mandatory)]
        [AllowEmptyString()]
        [string]$IpAddress
    )

    $parsed = $null
    if (-not [System.Net.IPAddress]::TryParse($IpAddress, [ref]$parsed)) {
        return $false
    }

    if ($parsed.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork) {
        return $false
    }

    $octets = $parsed.GetAddressBytes()
    if ($octets[0] -eq 10) {
        return $true
    }
    if ($octets[0] -eq 172 -and $octets[1] -ge 16 -and $octets[1] -le 31) {
        return $true
    }
    if ($octets[0] -eq 192 -and $octets[1] -eq 168) {
        return $true
    }

    return $false
}

function Test-ClassroomLanVirtualAdapterName {
    param(
        [Parameter(Mandatory)]
        [AllowEmptyString()]
        [string]$Name
    )

    return $Name -match '(?i)(docker|wsl|hyper-v|vethernet|loopback|vpn|tailscale|zerotier|virtualbox|vmware|bluetooth|container)'
}

function Get-ClassroomLanIPv4Candidates {
    if (-not (Get-Command Get-NetIPAddress -ErrorAction SilentlyContinue)) {
        throw 'Get-NetIPAddress is unavailable. Run this script in Windows PowerShell 5.1 or PowerShell 7 on Windows.'
    }

    $addresses = Get-NetIPAddress -AddressFamily IPv4 -AddressState Preferred -ErrorAction Stop
    $hasNetAdapter = [bool](Get-Command Get-NetAdapter -ErrorAction SilentlyContinue)
    $candidates = foreach ($address in $addresses) {
        $alias = [string]$address.InterfaceAlias
        $description = ''
        $adapter = $null
        if ($hasNetAdapter) {
            $adapter = Get-NetAdapter -InterfaceIndex $address.InterfaceIndex -ErrorAction SilentlyContinue
            if ($adapter -and [string]$adapter.Status -ne 'Up') {
                continue
            }
            if ($adapter -and $adapter.PSObject.Properties.Name -contains 'HardwareInterface' -and
                -not [bool]$adapter.HardwareInterface) {
                continue
            }
            if ($adapter) {
                $description = [string]$adapter.InterfaceDescription
            }
        }
        if (-not $description -and $address.PSObject.Properties.Name -contains 'InterfaceDescription') {
            $description = [string]$address.InterfaceDescription
        }

        if ($address.SkipAsSource) {
            continue
        }
        if (-not (Test-ClassroomLanPrivateIPv4 -IpAddress ([string]$address.IPAddress))) {
            continue
        }
        if (Test-ClassroomLanVirtualAdapterName -Name "$alias $description") {
            continue
        }

        [pscustomobject]@{
            IpAddress = [string]$address.IPAddress
            InterfaceAlias = $alias
            InterfaceDescription = $description
        }
    }

    return @($candidates | Sort-Object IpAddress -Unique)
}

function Select-ClassroomLanIPv4 {
    param(
        [AllowNull()]
        [AllowEmptyString()]
        [string]$RequestedIp,

        [Parameter(Mandatory)]
        [AllowEmptyCollection()]
        [object[]]$Candidates
    )

    $safeCandidates = @($Candidates | Where-Object {
        $candidateIp = if ($_ -is [string]) { $_ } else { [string]$_.IpAddress }
        Test-ClassroomLanPrivateIPv4 -IpAddress $candidateIp
    })

    if (-not [string]::IsNullOrWhiteSpace($RequestedIp)) {
        if (-not (Test-ClassroomLanPrivateIPv4 -IpAddress $RequestedIp)) {
            throw "LanIp must be an RFC1918 private IPv4 address: $RequestedIp"
        }

        $match = $safeCandidates | Where-Object {
            $candidateIp = if ($_ -is [string]) { $_ } else { [string]$_.IpAddress }
            $candidateIp -eq $RequestedIp
        }
        if (@($match).Count -ne 1) {
            throw "LanIp is not assigned to an eligible physical LAN adapter: $RequestedIp"
        }
        return $RequestedIp
    }

    if ($safeCandidates.Count -eq 0) {
        throw 'No eligible private LAN IPv4 address was found. Connect to the classroom LAN or specify -LanIp after verifying the host adapter.'
    }
    if ($safeCandidates.Count -gt 1) {
        $lines = $safeCandidates | ForEach-Object {
            if ($_ -is [string]) {
                "  - $_"
            } else {
                "  - $($_.IpAddress) [$($_.InterfaceAlias)]"
            }
        }
        throw "Multiple eligible LAN IPv4 addresses were found. Re-run with -LanIp and choose one explicitly:`n$($lines -join "`n")"
    }

    if ($safeCandidates[0] -is [string]) {
        return [string]$safeCandidates[0]
    }
    return [string]$safeCandidates[0].IpAddress
}

function Get-ClassroomLanCorsOrigins {
    param(
        [Parameter(Mandatory)]
        [string]$LanIp,

        [Parameter(Mandatory)]
        [ValidateRange(1, 65535)]
        [int]$Port
    )

    if (-not (Test-ClassroomLanPrivateIPv4 -IpAddress $LanIp)) {
        throw "Invalid private LAN IPv4 address: $LanIp"
    }

    return @(
        "http://localhost:$Port"
        "http://127.0.0.1:$Port"
        "http://${LanIp}:$Port"
    ) | Select-Object -Unique
}

function Get-ClassroomLanCorsOriginsValue {
    param(
        [Parameter(Mandatory)]
        [string]$LanIp,

        [Parameter(Mandatory)]
        [ValidateRange(1, 65535)]
        [int]$Port
    )

    return (Get-ClassroomLanCorsOrigins -LanIp $LanIp -Port $Port) -join ','
}

function Assert-ClassroomLanWindows {
    $isWindowsPlatform = $PSVersionTable.PSEdition -eq 'Desktop' -or [System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT
    if (-not $isWindowsPlatform) {
        throw 'Classroom LAN scripts support Windows only.'
    }
}

function Assert-ClassroomLanCommand {
    param(
        [Parameter(Mandatory)]
        [string]$Name
    )

    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if (-not $command) {
        throw "Required command is unavailable: $Name"
    }
    return $command
}

function Invoke-ClassroomLanKubectl {
    param(
        [Parameter(Mandatory)]
        [string[]]$Arguments,

        [switch]$AllowFailure
    )

    Assert-ClassroomLanCommand -Name 'kubectl' | Out-Null
    $output = & kubectl @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0 -and -not $AllowFailure) {
        throw "kubectl failed (exit $exitCode): kubectl $($Arguments -join ' ')`n$($output -join "`n")"
    }

    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = @($output | ForEach-Object { [string]$_ })
        Text = (@($output) -join "`n").Trim()
    }
}

function Assert-ClassroomLanKubernetesContext {
    $result = Invoke-ClassroomLanKubectl -Arguments @('config', 'current-context')
    if ($result.Text -ne 'docker-desktop') {
        throw "Unsupported kubectl context '$($result.Text)'. Expected 'docker-desktop'."
    }
    return $result.Text
}

function Test-ClassroomLanNamespaceExists {
    param(
        [Parameter(Mandatory)]
        [string]$Namespace
    )

    $result = Invoke-ClassroomLanKubectl -Arguments @('get', 'namespace', $Namespace, '-o', 'name') -AllowFailure
    return $result.ExitCode -eq 0
}

function Get-ClassroomLanWorkloadStatus {
    param(
        [Parameter(Mandatory)]
        [string]$Namespace
    )

    $mysqlJson = Invoke-ClassroomLanKubectl -Arguments @('get', 'statefulset/mysql', '-n', $Namespace, '-o', 'json') -AllowFailure
    $backendJson = Invoke-ClassroomLanKubectl -Arguments @('get', 'deployment/backend', '-n', $Namespace, '-o', 'json') -AllowFailure
    $frontendJson = Invoke-ClassroomLanKubectl -Arguments @('get', 'deployment/frontend', '-n', $Namespace, '-o', 'json') -AllowFailure

    function Convert-ReadyState {
        param($Result, [string]$Kind)
        if ($Result.ExitCode -ne 0) {
            return [pscustomobject]@{ Kind = $Kind; Exists = $false; Desired = 0; Ready = 0; IsReady = $false }
        }
        $resource = $Result.Text | ConvertFrom-Json
        $desired = 0
        $ready = 0
        if ($resource.PSObject.Properties.Name -contains 'spec' -and $resource.spec -and
            $resource.spec.PSObject.Properties.Name -contains 'replicas') {
            $desired = [int]$resource.spec.replicas
        }
        if ($resource.PSObject.Properties.Name -contains 'status' -and $resource.status -and
            $resource.status.PSObject.Properties.Name -contains 'readyReplicas') {
            $ready = [int]$resource.status.readyReplicas
        }
        return [pscustomobject]@{
            Kind = $Kind
            Exists = $true
            Desired = $desired
            Ready = $ready
            IsReady = ($desired -eq 1 -and $ready -eq 1)
        }
    }

    return [pscustomobject]@{
        MySql = Convert-ReadyState -Result $mysqlJson -Kind 'statefulset/mysql'
        Backend = Convert-ReadyState -Result $backendJson -Kind 'deployment/backend'
        Frontend = Convert-ReadyState -Result $frontendJson -Kind 'deployment/frontend'
    }
}

function Assert-ClassroomLanWorkloadsReady {
    param(
        [Parameter(Mandatory)]
        [string]$Namespace
    )

    $status = Get-ClassroomLanWorkloadStatus -Namespace $Namespace
    $notReady = @(@($status.MySql, $status.Backend, $status.Frontend) | Where-Object { -not $_.IsReady })
    if ($notReady.Count -gt 0) {
        $details = $notReady | ForEach-Object { "$($_.Kind): ready $($_.Ready)/$($_.Desired), exists=$($_.Exists)" }
        throw "Kubernetes workloads are not ready:`n$($details -join "`n")"
    }
    return $status
}

function Set-ClassroomLanCorsOrigins {
    param(
        [Parameter(Mandatory)]
        [string]$Namespace,

        [Parameter(Mandatory)]
        [string]$LanIp,

        [Parameter(Mandatory)]
        [ValidateRange(1, 65535)]
        [int]$Port
    )

    $desired = Get-ClassroomLanCorsOriginsValue -LanIp $LanIp -Port $Port
    $current = Invoke-ClassroomLanKubectl -Arguments @(
        'get', 'configmap/realtime-tile-game-config', '-n', $Namespace,
        '-o', 'jsonpath={.data.CORS_ALLOWED_ORIGINS}'
    )

    if ($current.Text -eq $desired) {
        return [pscustomobject]@{ Changed = $false; Value = $desired }
    }

    $runtimeDirectory = Get-ClassroomLanRuntimeDirectory
    New-Item -ItemType Directory -Path $runtimeDirectory -Force | Out-Null
    $patchPath = Join-Path $runtimeDirectory 'cors-configmap-patch.json'
    try {
        $patchJson = @{ data = @{ CORS_ALLOWED_ORIGINS = $desired } } | ConvertTo-Json -Compress
        [System.IO.File]::WriteAllText($patchPath, $patchJson, [System.Text.UTF8Encoding]::new($false))
        Invoke-ClassroomLanKubectl -Arguments @(
            'patch', 'configmap/realtime-tile-game-config', '-n', $Namespace,
            '--type', 'merge', '--patch-file', $patchPath
        ) | Out-Null
    } finally {
        Remove-Item -LiteralPath $patchPath -Force -ErrorAction SilentlyContinue
    }

    Invoke-ClassroomLanKubectl -Arguments @('rollout', 'restart', 'deployment/backend', '-n', $Namespace) | Out-Null
    Invoke-ClassroomLanKubectl -Arguments @(
        'rollout', 'status', 'deployment/backend', '-n', $Namespace, '--timeout=180s'
    ) | Out-Null

    return [pscustomobject]@{ Changed = $true; Value = $desired }
}

function Read-ClassroomLanState {
    $path = Get-ClassroomLanStatePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        return $null
    }

    try {
        return Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json
    } catch {
        Remove-Item -LiteralPath $path -Force -ErrorAction SilentlyContinue
        return $null
    }
}

function Write-ClassroomLanState {
    param(
        [Parameter(Mandatory)]
        [psobject]$State
    )

    $directory = Get-ClassroomLanRuntimeDirectory
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $path = Get-ClassroomLanStatePath
    $temporaryPath = "$path.tmp"
    $stateJson = $State | ConvertTo-Json -Depth 5
    [System.IO.File]::WriteAllText($temporaryPath, $stateJson, [System.Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $temporaryPath -Destination $path -Force
}

function Remove-ClassroomLanState {
    Remove-Item -LiteralPath (Get-ClassroomLanStatePath) -Force -ErrorAction SilentlyContinue
}


function Test-ClassroomLanStateSchema {
    param(
        [Parameter(Mandatory)]
        [psobject]$State
    )

    $requiredStateProperties = @('Pid', 'ProcessStartTimeUtc', 'LanIp', 'Port', 'Namespace')
    foreach ($property in $requiredStateProperties) {
        if ($State.PSObject.Properties.Name -notcontains $property) {
            return [pscustomobject]@{ IsValid = $false; Reason = "StateMissing:$property" }
        }
    }

    $parsedStartTime = [datetime]::MinValue
    if (-not [datetime]::TryParse([string]$State.ProcessStartTimeUtc, [ref]$parsedStartTime)) {
        return [pscustomobject]@{ IsValid = $false; Reason = 'InvalidProcessStartTime' }
    }
    $parsedPid = 0
    if (-not [int]::TryParse([string]$State.Pid, [ref]$parsedPid) -or $parsedPid -le 0) {
        return [pscustomobject]@{ IsValid = $false; Reason = 'InvalidPid' }
    }
    if (-not (Test-ClassroomLanPrivateIPv4 -IpAddress ([string]$State.LanIp))) {
        return [pscustomobject]@{ IsValid = $false; Reason = 'InvalidLanIp' }
    }
    $parsedPort = 0
    if (-not [int]::TryParse([string]$State.Port, [ref]$parsedPort) -or $parsedPort -lt 1 -or $parsedPort -gt 65535) {
        return [pscustomobject]@{ IsValid = $false; Reason = 'InvalidPort' }
    }
    if ([string]$State.Namespace -notmatch '^[a-z0-9]([-a-z0-9]*[a-z0-9])?$') {
        return [pscustomobject]@{ IsValid = $false; Reason = 'InvalidNamespace' }
    }

    return [pscustomobject]@{ IsValid = $true; Reason = 'Match' }
}

function Get-ClassroomLanProcessInfo {
    param(
        [Parameter(Mandatory)]
        [int]$ProcessId
    )

    try {
        $process = Get-Process -Id $ProcessId -ErrorAction Stop
        $startTimeUtc = $process.StartTime.ToUniversalTime().ToString('o')
    } catch {
        return $null
    }

    $cim = Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction SilentlyContinue
    return [pscustomobject]@{
        Id = $process.Id
        Name = $process.ProcessName
        StartTimeUtc = $startTimeUtc
        CommandLine = if ($cim) { [string]$cim.CommandLine } else { '' }
    }
}

function Test-ClassroomLanPortForwardIdentity {
    param(
        [Parameter(Mandatory)]
        [psobject]$State,

        [AllowNull()]
        [psobject]$ProcessInfo
    )

    $schema = Test-ClassroomLanStateSchema -State $State
    if (-not $schema.IsValid) {
        return $schema
    }
    if (-not $ProcessInfo) {
        return [pscustomobject]@{ IsValid = $false; Reason = 'ProcessNotFound' }
    }

    if ([int]$ProcessInfo.Id -ne [int]$State.Pid) {
        return [pscustomobject]@{ IsValid = $false; Reason = 'PidMismatch' }
    }
    if ([string]$ProcessInfo.Name -notmatch '(?i)^kubectl(?:\.exe)?$') {
        return [pscustomobject]@{ IsValid = $false; Reason = 'ExecutableMismatch' }
    }

    $commandLine = [string]$ProcessInfo.CommandLine
    $requiredFragments = @(
        'port-forward',
        'service/frontend',
        "$($State.Port):80",
        [string]$State.Namespace,
        [string]$State.LanIp,
        '127.0.0.1'
    )
    foreach ($fragment in $requiredFragments) {
        if ($commandLine.IndexOf($fragment, [System.StringComparison]::OrdinalIgnoreCase) -lt 0) {
            return [pscustomobject]@{ IsValid = $false; Reason = "CommandLineMismatch:$fragment" }
        }
    }

    try {
        $expected = [datetime]::Parse([string]$State.ProcessStartTimeUtc).ToUniversalTime()
        $actual = [datetime]::Parse([string]$ProcessInfo.StartTimeUtc).ToUniversalTime()
    } catch {
        return [pscustomobject]@{ IsValid = $false; Reason = 'InvalidProcessStartTime' }
    }
    if ([math]::Abs(($actual - $expected).TotalSeconds) -gt 2) {
        return [pscustomobject]@{ IsValid = $false; Reason = 'ProcessStartTimeMismatch' }
    }

    return [pscustomobject]@{ IsValid = $true; Reason = 'Match' }
}

function Get-ClassroomLanStartDisposition {
    param(
        [AllowNull()]
        [psobject]$State,

        [AllowNull()]
        [psobject]$Identity,

        [Parameter(Mandatory)]
        [string]$LanIp,

        [Parameter(Mandatory)]
        [int]$Port,

        [Parameter(Mandatory)]
        [string]$Namespace
    )

    if (-not $State) {
        return 'Start'
    }
    if (-not $Identity -or -not $Identity.IsValid) {
        return 'CleanupStale'
    }
    if ([string]$State.LanIp -eq $LanIp -and [int]$State.Port -eq $Port -and [string]$State.Namespace -eq $Namespace) {
        return 'Reuse'
    }
    return 'Replace'
}

function Test-ClassroomLanPortListening {
    param(
        [Parameter(Mandatory)]
        [ValidateRange(1, 65535)]
        [int]$Port
    )

    if (-not (Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue)) {
        throw 'Get-NetTCPConnection is unavailable. Run this script on Windows.'
    }
    return @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue).Count -gt 0
}

function Start-ClassroomLanPortForward {
    param(
        [Parameter(Mandatory)]
        [string]$LanIp,

        [Parameter(Mandatory)]
        [ValidateRange(1, 65535)]
        [int]$Port,

        [Parameter(Mandatory)]
        [string]$Namespace
    )

    if (Test-ClassroomLanPortListening -Port $Port) {
        throw "TCP port $Port is already listening. Refusing to replace an unverified process."
    }

    $runtimeDirectory = Get-ClassroomLanRuntimeDirectory
    New-Item -ItemType Directory -Path $runtimeDirectory -Force | Out-Null
    $stdoutPath = Join-Path $runtimeDirectory 'port-forward.stdout.log'
    $stderrPath = Join-Path $runtimeDirectory 'port-forward.stderr.log'
    Remove-Item $stdoutPath, $stderrPath -Force -ErrorAction SilentlyContinue

    $kubectl = Assert-ClassroomLanCommand -Name 'kubectl'
    $address = "127.0.0.1,$LanIp"
    $arguments = @(
        'port-forward', 'service/frontend', "${Port}:80",
        '-n', $Namespace,
        "--address=$address"
    )

    $process = Start-Process -FilePath $kubectl.Source -ArgumentList $arguments -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath
    Start-Sleep -Seconds 2
    $process.Refresh()
    if ($process.HasExited) {
        $errorText = if (Test-Path $stderrPath) { Get-Content $stderrPath -Raw -ErrorAction SilentlyContinue } else { '' }
        throw "kubectl port-forward exited immediately with code $($process.ExitCode). $errorText"
    }

    $state = [pscustomobject]@{
        Pid = $process.Id
        ProcessStartTimeUtc = $process.StartTime.ToUniversalTime().ToString('o')
        LanIp = $LanIp
        Port = $Port
        Namespace = $Namespace
        StartedAtUtc = [datetime]::UtcNow.ToString('o')
        HostUrl = "http://${LanIp}:$Port"
        LocalUrl = "http://127.0.0.1:$Port"
        StdoutLog = $stdoutPath
        StderrLog = $stderrPath
    }
    Write-ClassroomLanState -State $state
    return $state
}

function Stop-ClassroomLanPortForward {
    param(
        [switch]$Quiet
    )

    $state = Read-ClassroomLanState
    if (-not $state) {
        if (-not $Quiet) { Write-Host 'Classroom LAN port-forward is not registered.' }
        return [pscustomobject]@{ Stopped = $false; Reason = 'NoState' }
    }

    $schema = Test-ClassroomLanStateSchema -State $state
    if (-not $schema.IsValid) {
        Remove-ClassroomLanState
        if (-not $Quiet) { Write-Warning "Invalid runtime state was removed: $($schema.Reason)" }
        return [pscustomobject]@{ Stopped = $false; Reason = $schema.Reason }
    }

    $processInfo = Get-ClassroomLanProcessInfo -ProcessId ([int]$state.Pid)
    $identity = Test-ClassroomLanPortForwardIdentity -State $state -ProcessInfo $processInfo
    if (-not $identity.IsValid) {
        Remove-ClassroomLanState
        if (-not $Quiet) {
            Write-Warning "State was stale or PID was reused ($($identity.Reason)). No process was terminated."
        }
        return [pscustomobject]@{ Stopped = $false; Reason = $identity.Reason }
    }

    Stop-Process -Id ([int]$state.Pid) -ErrorAction Stop
    $deadline = [datetime]::UtcNow.AddSeconds(10)
    while ($true) {
        $remainingProcess = Get-ClassroomLanProcessInfo -ProcessId ([int]$state.Pid)
        if (-not $remainingProcess) {
            break
        }
        $remainingIdentity = Test-ClassroomLanPortForwardIdentity -State $state -ProcessInfo $remainingProcess
        if (-not $remainingIdentity.IsValid) {
            Write-Warning 'The original PID was reused while waiting for shutdown. The new process will not be terminated.'
            break
        }
        if ([datetime]::UtcNow -ge $deadline) {
            Stop-Process -Id ([int]$state.Pid) -Force -ErrorAction SilentlyContinue
            break
        }
        Start-Sleep -Milliseconds 200
    }
    Remove-ClassroomLanState
    if (-not $Quiet) { Write-Host "Stopped Classroom LAN port-forward PID $($state.Pid)." }
    return [pscustomobject]@{ Stopped = $true; Reason = 'Stopped' }
}

function Test-ClassroomLanHealthEndpoint {
    param(
        [Parameter(Mandatory)]
        [string]$Url
    )

    try {
        $response = Invoke-RestMethod -Uri $Url -Method Get -TimeoutSec 5 -ErrorAction Stop
        $isUp = $response.success -eq $true -and $response.data.status -eq 'UP' -and $response.data.database -eq 'UP'
        return [pscustomobject]@{
            Url = $Url
            Reachable = $true
            ApplicationUp = $response.data.status -eq 'UP'
            DatabaseUp = $response.data.database -eq 'UP'
            Success = $isUp
            Error = $null
        }
    } catch {
        return [pscustomobject]@{
            Url = $Url
            Reachable = $false
            ApplicationUp = $false
            DatabaseUp = $false
            Success = $false
            Error = $_.Exception.Message
        }
    }
}

function Wait-ClassroomLanHealth {
    param(
        [Parameter(Mandatory)]
        [string[]]$Urls,

        [ValidateRange(1, 300)]
        [int]$TimeoutSeconds = 45
    )

    $deadline = [datetime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $results = @($Urls | ForEach-Object { Test-ClassroomLanHealthEndpoint -Url $_ })
        if (@($results | Where-Object { -not $_.Success }).Count -eq 0) {
            return $results
        }
        Start-Sleep -Seconds 1
    } while ([datetime]::UtcNow -lt $deadline)

    $details = $results | ForEach-Object { "$($_.Url): success=$($_.Success), error=$($_.Error)" }
    throw "Classroom LAN health did not become UP within $TimeoutSeconds seconds:`n$($details -join "`n")"
}

function Test-ClassroomLanAdministrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = [Security.Principal.WindowsPrincipal]::new($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Get-ClassroomLanFirewallQueryFailureReason {
    param(
        [Parameter(Mandatory)]
        [System.Management.Automation.ErrorRecord]$ErrorRecord
    )

    $category = [string]$ErrorRecord.CategoryInfo.Category
    $categoryReason = [string]$ErrorRecord.CategoryInfo.Reason
    $fullyQualifiedErrorId = [string]$ErrorRecord.FullyQualifiedErrorId
    $details = @(
        [string]$ErrorRecord.Exception.Message,
        $category,
        $categoryReason,
        $fullyQualifiedErrorId,
        [string]$ErrorRecord
    ) -join ' '

    $exception = $ErrorRecord.Exception
    while ($exception) {
        if ($exception -is [System.UnauthorizedAccessException]) {
            return 'AccessDenied'
        }
        $details = "$details $($exception.GetType().FullName) $($exception.Message)"
        $exception = $exception.InnerException
    }

    if ($category -eq 'PermissionDenied' -or
        $details -match '(?i)(access\s+is\s+denied|accessdenied|permissiondenied|permission\s+denied|windows\s+system\s+error\s*5|system\s+error\s*5|error\s*5\b|unauthorized)') {
        return 'AccessDenied'
    }

    if ($category -eq 'ObjectNotFound' -or
        $details -match '(?i)(nomatchingmsft_netfirewallrule|no\s+msft_netfirewallrule\s+objects\s+found|cannot\s+find.*netfirewallrule|not\s+found)') {
        return 'NotFound'
    }

    return 'QueryFailed'
}

function Get-ClassroomLanFirewallStatus {
    param(
        [Parameter(Mandatory)]
        [ValidateRange(1, 65535)]
        [int]$Port,

        [AllowNull()]
        [scriptblock]$RuleQuery = $null
    )

    $name = Get-ClassroomLanFirewallRuleName -Port $Port
    if (-not $RuleQuery -and -not (Get-Command Get-NetFirewallRule -ErrorAction SilentlyContinue)) {
        return [pscustomobject]@{
            QuerySucceeded = $false
            Exists = $null
            IsValid = $false
            Reason = 'FirewallCmdletsUnavailable'
            Name = $name
            DisplayName = $null
        }
    }

    if (-not $RuleQuery) {
        $RuleQuery = {
            param([string]$RuleName)
            Get-NetFirewallRule -Name $RuleName -ErrorAction Stop
        }
    }

    $ruleFound = $false
    try {
        $rule = & $RuleQuery $name
        if (-not $rule) {
            return [pscustomobject]@{
                QuerySucceeded = $true
                Exists = $false
                IsValid = $false
                Reason = 'NotFound'
                Name = $name
                DisplayName = $null
            }
        }

        $ruleFound = $true
        $portFilter = $rule | Get-NetFirewallPortFilter -ErrorAction Stop
        $addressFilter = $rule | Get-NetFirewallAddressFilter -ErrorAction Stop
        $remoteAddresses = @($addressFilter.RemoteAddress)
        $valid = $rule.Direction -eq 'Inbound' -and
            $rule.Action -eq 'Allow' -and
            $rule.Enabled -eq 'True' -and
            $rule.Group -eq $script:FirewallGroup -and
            $portFilter.Protocol -eq 'TCP' -and
            [string]$portFilter.LocalPort -eq [string]$Port -and
            ($remoteAddresses -contains 'LocalSubnet')

        return [pscustomobject]@{
            QuerySucceeded = $true
            Exists = $true
            IsValid = $valid
            Reason = if ($valid) { 'Match' } else { 'ConfigurationMismatch' }
            Name = $name
            DisplayName = $rule.DisplayName
        }
    } catch {
        $reason = Get-ClassroomLanFirewallQueryFailureReason -ErrorRecord $_
        if ($reason -eq 'NotFound' -and -not $ruleFound) {
            return [pscustomobject]@{
                QuerySucceeded = $true
                Exists = $false
                IsValid = $false
                Reason = 'NotFound'
                Name = $name
                DisplayName = $null
            }
        }

        if ($reason -eq 'NotFound') {
            $reason = 'QueryFailed'
        }

        return [pscustomobject]@{
            QuerySucceeded = $false
            Exists = $null
            IsValid = $false
            Reason = $reason
            Name = $name
            DisplayName = $null
        }
    }
}

function Get-ClassroomLanFirewallAction {
    param(
        [AllowNull()]
        [psobject]$Status
    )

    if ($Status -and
        $Status.PSObject.Properties.Name -contains 'QuerySucceeded' -and
        -not [bool]$Status.QuerySucceeded) {
        throw "Firewall status query did not succeed: $($Status.Reason)"
    }
    if (-not $Status -or -not $Status.Exists) {
        return 'Create'
    }
    if ($Status.IsValid) {
        return 'None'
    }
    return 'Recreate'
}

function New-ClassroomLanStatusView {
    param(
        [Parameter(Mandatory)]
        [string]$Context,
        [Parameter(Mandatory)]
        [bool]$NamespaceExists,
        [Parameter(Mandatory)]
        [psobject]$Workloads,
        [AllowNull()]
        [psobject]$State,
        [AllowNull()]
        [psobject]$Identity,
        [Parameter(Mandatory)]
        [psobject]$Firewall,
        [AllowNull()]
        [psobject]$Health
    )

    return [pscustomobject][ordered]@{
        KubectlContext = $Context
        NamespaceExists = $NamespaceExists
        MySqlReady = $Workloads.MySql.IsReady
        BackendReady = $Workloads.Backend.IsReady
        FrontendReady = $Workloads.Frontend.IsReady
        PortForwardPid = if ($State) { [int]$State.Pid } else { $null }
        PortForwardAlive = if ($Identity) { [bool]$Identity.IsValid } else { $false }
        LanIp = if ($State) { [string]$State.LanIp } else { $null }
        HostUrl = if ($State) { "http://$($State.LanIp):$($State.Port)" } else { $null }
        FirewallRuleExists = if ($null -eq $Firewall.Exists) { $null } else { [bool]$Firewall.Exists }
        FirewallRuleValid = [bool]$Firewall.IsValid
        FirewallReason = [string]$Firewall.Reason
        HostHealthUp = if ($Health) { [bool]$Health.Success } else { $false }
    }
}

Export-ModuleMember -Function @(
    'Get-ClassroomLanProjectRoot',
    'Get-ClassroomLanRuntimeDirectory',
    'Get-ClassroomLanStatePath',
    'Get-ClassroomLanFirewallRuleName',
    'Get-ClassroomLanFirewallDisplayName',
    'Test-ClassroomLanPrivateIPv4',
    'Test-ClassroomLanVirtualAdapterName',
    'Get-ClassroomLanIPv4Candidates',
    'Select-ClassroomLanIPv4',
    'Get-ClassroomLanCorsOrigins',
    'Get-ClassroomLanCorsOriginsValue',
    'Assert-ClassroomLanWindows',
    'Assert-ClassroomLanCommand',
    'Invoke-ClassroomLanKubectl',
    'Assert-ClassroomLanKubernetesContext',
    'Test-ClassroomLanNamespaceExists',
    'Get-ClassroomLanWorkloadStatus',
    'Assert-ClassroomLanWorkloadsReady',
    'Set-ClassroomLanCorsOrigins',
    'Read-ClassroomLanState',
    'Write-ClassroomLanState',
    'Remove-ClassroomLanState',
    'Test-ClassroomLanStateSchema',
    'Get-ClassroomLanProcessInfo',
    'Test-ClassroomLanPortForwardIdentity',
    'Get-ClassroomLanStartDisposition',
    'Test-ClassroomLanPortListening',
    'Start-ClassroomLanPortForward',
    'Stop-ClassroomLanPortForward',
    'Test-ClassroomLanHealthEndpoint',
    'Wait-ClassroomLanHealth',
    'Test-ClassroomLanAdministrator',
    'Get-ClassroomLanFirewallStatus',
    'Get-ClassroomLanFirewallAction',
    'New-ClassroomLanStatusView'
)
