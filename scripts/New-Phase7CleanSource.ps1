[CmdletBinding()]
param(
    [Parameter()]
    [string]$ProjectRoot,

    [Parameter()]
    [string]$OutputPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Test-RummikubProjectRoot {
    param([Parameter(Mandatory)][string]$Path)

    return (
        (Test-Path -LiteralPath (Join-Path $Path 'backend') -PathType Container) -and
        (Test-Path -LiteralPath (Join-Path $Path 'frontend') -PathType Container) -and
        (Test-Path -LiteralPath (Join-Path $Path 'k8s') -PathType Container) -and
        (Test-Path -LiteralPath (Join-Path $Path 'scripts') -PathType Container) -and
        (Test-Path -LiteralPath (Join-Path $Path 'compose.yaml') -PathType Leaf)
    )
}

# 스크립트를 프로젝트 루트에서 실행해도 되고, scripts 폴더 안에 둬도 된다.
if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $candidates = @(
        $PSScriptRoot,
        (Join-Path $PSScriptRoot '..'),
        (Get-Location).Path
    )

    $detectedRoot = $null
    foreach ($candidate in $candidates) {
        $fullCandidate = [System.IO.Path]::GetFullPath($candidate)
        if (Test-RummikubProjectRoot -Path $fullCandidate) {
            $detectedRoot = $fullCandidate
            break
        }
    }

    if ($null -eq $detectedRoot) {
        throw @"
프로젝트 루트를 자동으로 찾지 못했습니다.
- 이 파일을 프로젝트 루트 또는 scripts 폴더에 두고 실행하거나
- -ProjectRoot 인수로 루트 경로를 지정하세요.
"@
    }

    $ProjectRoot = $detectedRoot
}

$resolvedRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path

if (-not (Test-RummikubProjectRoot -Path $resolvedRoot)) {
    throw "유효한 프로젝트 루트가 아닙니다: $resolvedRoot"
}

# Production 전체 소스에 필요한 항목만 허용한다.
# 루트의 .git, .runtime, .agents, spikes, Phase*_Documents, RuntimeEvidence 등은
# 애초에 복사 대상에 넣지 않는다.
$sourceDirectories = @(
    'backend',
    'frontend',
    'k8s',
    'scripts',
    'docs'
)

$requiredRootFiles = @(
    '.env.example',
    '.gitignore',
    'compose.yaml',
    'README.md'
)

$optionalRootFiles = @(
    'LICENSE',
    'LICENSE.md',
    'CONTRIBUTING.md',
    'AGENTS.md'
)

foreach ($relativePath in ($sourceDirectories + $requiredRootFiles)) {
    $fullPath = Join-Path $resolvedRoot $relativePath
    if (-not (Test-Path -LiteralPath $fullPath)) {
        throw "클린 소스 필수 항목이 없습니다: $relativePath"
    }
}

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $fileName = 'phase{0}-phase7-precloseout-clean-source.zip' -f (Get-Date -Format 'MMdd-HH-mm')
    $OutputPath = Join-Path (Split-Path -Parent $resolvedRoot) $fileName
}
else {
    $OutputPath = [System.IO.Path]::GetFullPath($OutputPath)
}

$stagingRoot = Join-Path (
    [System.IO.Path]::GetTempPath()
) ('rummikub-clean-' + [guid]::NewGuid().ToString('N'))

# 허용 디렉터리 내부에서도 빌드/런타임/IDE 산출물은 제외한다.
$excludeDirectories = @(
    'node_modules',
    'target',
    'dist',
    'coverage',
    '.vite',
    '.runtime',
    '.git',
    '.idea',
    '.vscode',
    '.cache',
    '.turbo',
    'build',
    'out',
    'generated',
    'test-results',
    'playwright-report',
    'allure-results',
    'allure-report'
)

$robocopyExcludeFiles = @(
    '*.zip',
    '*.log',
    '*.pid',
    '*.tmp',
    '*.bak',
    '*.dmp',
    '*.trace',
    '*.har',
    'Thumbs.db',
    '.DS_Store'
)

function Remove-ForbiddenFilesFromStaging {
    param([Parameter(Mandatory)][string]$Root)

    $files = Get-ChildItem -LiteralPath $Root -Recurse -Force -File

    foreach ($file in $files) {
        $name = $file.Name

        $isPrivateEnv = (
            $name -ieq '.env' -or
            ($name -ilike '.env.*' -and $name -ine '.env.example')
        )

        $isPrivateKey = $file.Extension -in @(
            '.pem', '.key', '.pfx', '.p12', '.jks', '.keystore'
        )

        $isKnownSecretConfig = $name -match '^(?i)(application-(local|secret|secrets)\.(yml|yaml|properties)|secret\.(yml|yaml|json)|secrets\.(yml|yaml|json))$'

        if ($isPrivateEnv -or $isPrivateKey -or $isKnownSecretConfig) {
            Remove-Item -LiteralPath $file.FullName -Force
        }
    }
}

try {
    New-Item -ItemType Directory -Path $stagingRoot -Force | Out-Null

    foreach ($directory in $sourceDirectories) {
        $source = Join-Path $resolvedRoot $directory
        $destination = Join-Path $stagingRoot $directory

        New-Item -ItemType Directory -Path $destination -Force | Out-Null

        $arguments = @(
            $source,
            $destination,
            '/E',
            '/COPY:DAT',
            '/DCOPY:DAT',
            '/R:2',
            '/W:1',
            '/NFL',
            '/NDL',
            '/NJH',
            '/NJS',
            '/NP',
            '/XD'
        ) + $excludeDirectories + @('/XF') + $robocopyExcludeFiles

        & robocopy @arguments | Out-Null

        # robocopy 0~7은 성공/차이 있음, 8 이상은 실패다.
        if ($LASTEXITCODE -ge 8) {
            throw "robocopy 실패: '$directory' (exit code: $LASTEXITCODE)"
        }
    }

    foreach ($file in $requiredRootFiles) {
        Copy-Item `
            -LiteralPath (Join-Path $resolvedRoot $file) `
            -Destination (Join-Path $stagingRoot $file) `
            -Force
    }

    foreach ($file in $optionalRootFiles) {
        $sourceFile = Join-Path $resolvedRoot $file
        if (Test-Path -LiteralPath $sourceFile -PathType Leaf) {
            Copy-Item `
                -LiteralPath $sourceFile `
                -Destination (Join-Path $stagingRoot $file) `
                -Force
        }
    }

    # robocopy 패턴만으로 놓칠 수 있는 환경 파일·개인 키를 한 번 더 제거한다.
    Remove-ForbiddenFilesFromStaging -Root $stagingRoot

    $outputDirectory = Split-Path -Parent $OutputPath
    if (-not (Test-Path -LiteralPath $outputDirectory)) {
        New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    }

    if (Test-Path -LiteralPath $OutputPath) {
        Remove-Item -LiteralPath $OutputPath -Force
    }

    Add-Type -AssemblyName System.IO.Compression.FileSystem

    [System.IO.Compression.ZipFile]::CreateFromDirectory(
        $stagingRoot,
        $OutputPath,
        [System.IO.Compression.CompressionLevel]::Optimal,
        $false
    )

    # 생성된 ZIP 자체를 다시 검사한다.
    $archive = [System.IO.Compression.ZipFile]::OpenRead($OutputPath)
    try {
        $entryNames = @(
            $archive.Entries |
                ForEach-Object { $_.FullName.Replace('\', '/') }
        )

        $requiredEntries = @(
            'backend/pom.xml',
            'frontend/package.json',
            '.env.example',
            '.gitignore',
            'compose.yaml',
            'README.md'
        )

        foreach ($entry in $requiredEntries) {
            if ($entryNames -notcontains $entry) {
                throw "ZIP 검증 실패 — 필수 파일 누락: $entry"
            }
        }

        foreach ($prefix in @('k8s/', 'scripts/', 'docs/')) {
            if (-not ($entryNames | Where-Object { $_.StartsWith($prefix) } | Select-Object -First 1)) {
                throw "ZIP 검증 실패 — 디렉터리 내용 누락: $prefix"
            }
        }

        $forbiddenDirectoryPattern = '(^|/)(node_modules|target|dist|coverage|\.vite|\.runtime|\.git|\.idea|\.vscode|\.cache|\.turbo|build|out|generated|test-results|playwright-report|allure-results|allure-report)(/|$)'

        $forbiddenEntries = @(
            $entryNames | Where-Object {
                $normalized = $_
                $leaf = [System.IO.Path]::GetFileName($normalized)

                $privateEnv = (
                    $leaf -ieq '.env' -or
                    ($leaf -ilike '.env.*' -and $leaf -ine '.env.example')
                )

                $privateKey = $leaf -match '(?i)\.(pem|key|pfx|p12|jks|keystore)$'
                $generatedFile = $leaf -match '(?i)\.(zip|log|pid|tmp|bak|dmp|trace|har)$'

                $normalized -match $forbiddenDirectoryPattern -or
                $privateEnv -or
                $privateKey -or
                $generatedFile
            }
        )

        if ($forbiddenEntries.Count -gt 0) {
            throw "ZIP 검증 실패 — 금지 항목 포함:`n$($forbiddenEntries -join "`n")"
        }

        # 루트에 허용하지 않은 대형 작업 폴더가 들어가지 않았는지 확인한다.
        $forbiddenTopLevel = @(
            '.agents/',
            '.runtime/',
            'spikes/',
            'Phase6_',
            'Phase7_'
        )

        $unexpectedEntries = @(
            $entryNames | Where-Object {
                $name = $_
                $forbiddenTopLevel | Where-Object { $name.StartsWith($_) }
            }
        )

        if ($unexpectedEntries.Count -gt 0) {
            throw "ZIP 검증 실패 — 클린 소스 외 루트 산출물 포함:`n$($unexpectedEntries -join "`n")"
        }

        $fileCount = @($archive.Entries | Where-Object { -not [string]::IsNullOrEmpty($_.Name) }).Count
    }
    finally {
        $archive.Dispose()
    }

    $zipInfo = Get-Item -LiteralPath $OutputPath
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $OutputPath).Hash
    $sizeMb = [Math]::Round($zipInfo.Length / 1MB, 2)

    Write-Host ''
    Write-Host '========================================'
    Write-Host 'Phase 7 clean source created'
    Write-Host '========================================'
    Write-Host "Project root : $resolvedRoot"
    Write-Host "Output       : $OutputPath"
    Write-Host "Files        : $fileCount"
    Write-Host "Size         : $sizeMb MB"
    Write-Host "SHA-256      : $hash"
    Write-Host ''
    Write-Host 'Excluded from root:'
    Write-Host '  .git, .runtime, .agents, spikes, Phase*_Documents, RuntimeEvidence'
    Write-Host 'Excluded inside source:'
    Write-Host '  node_modules, target, dist, coverage, IDE/build/runtime artifacts, private env/key files'
}
catch {
    if (
        -not [string]::IsNullOrWhiteSpace($OutputPath) -and
        (Test-Path -LiteralPath $OutputPath)
    ) {
        Remove-Item -LiteralPath $OutputPath -Force
    }

    throw
}
finally {
    if (Test-Path -LiteralPath $stagingRoot) {
        Remove-Item -LiteralPath $stagingRoot -Recurse -Force
    }
}
