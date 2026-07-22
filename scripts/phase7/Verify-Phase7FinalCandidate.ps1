[CmdletBinding()]
param(
    [Parameter()]
    [switch]$SkipInstall
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$backendRoot = Join-Path $projectRoot 'backend'
$frontendRoot = Join-Path $projectRoot 'frontend'

function Invoke-Step {
    param(
        [Parameter(Mandatory)]
        [string]$Title,

        [Parameter(Mandatory)]
        [scriptblock]$Action
    )

    Write-Host ''
    Write-Host ('=' * 72)
    Write-Host $Title
    Write-Host ('=' * 72)

    & $Action

    if ($LASTEXITCODE -ne 0) {
        throw "$Title failed (exit code: $LASTEXITCODE)"
    }
}

$backendWrapper = Join-Path $backendRoot 'mvnw.cmd'
$frontendPackage = Join-Path $frontendRoot 'package.json'

if (-not (Test-Path -LiteralPath $backendWrapper -PathType Leaf)) {
    throw "Missing backend Maven wrapper: $backendWrapper"
}

if (-not (Test-Path -LiteralPath $frontendPackage -PathType Leaf)) {
    throw "Missing frontend package.json: $frontendPackage"
}

$testGroups = @(
    @(
        'src/__tests__/App.spec.ts',
        'src/__tests__/AuthStore.spec.ts',
        'src/__tests__/AuthenticatedStompClient.spec.ts',
        'src/__tests__/AuthenticationViews.spec.ts',
        'src/__tests__/ConnectionStore.spec.ts',
        'src/__tests__/GameApi.spec.ts',
        'src/__tests__/GameStore.spec.ts',
        'src/__tests__/GameView.spec.ts',
        'src/__tests__/HttpClientAuthentication.spec.ts',
        'src/__tests__/Phase7CommitFrontend.spec.ts',
        'src/__tests__/Phase7FinalClosureStageA.spec.ts'
    ),
    @(
        'src/__tests__/Phase7FinalClosureStageB.spec.ts',
        'src/__tests__/Phase7FinalClosureStageC.spec.ts',
        'src/__tests__/Phase7FinalClosureStageD.spec.ts',
        'src/__tests__/Phase7ProductionCloseout.spec.ts',
        'src/__tests__/Phase7RackSortAndTableDragPerformance.spec.ts',
        'src/__tests__/Phase7SecondReviewFrontend.spec.ts',
        'src/__tests__/Phase7ThirdReviewFrontend.spec.ts'
    ),
    @(
        'src/__tests__/Phase7TilePlacementGridFix.spec.ts',
        'src/__tests__/RackGroupHold.spec.ts',
        'src/__tests__/RackMotionPolish.spec.ts',
        'src/__tests__/RackPresentation.spec.ts',
        'src/__tests__/RackSlotCapacity.spec.ts',
        'src/__tests__/RackSorting.spec.ts',
        'src/__tests__/RackVisualGroups.spec.ts'
    ),
    @(
        'src/__tests__/RoomStore.spec.ts',
        'src/__tests__/RoomViews.spec.ts',
        'src/__tests__/RouterAuthenticationGuard.spec.ts',
        'src/__tests__/RuntimeEndpoints.spec.ts',
        'src/__tests__/SystemHealthClient.spec.ts',
        'src/__tests__/TurnDraft.spec.ts'
    )
)

Push-Location $backendRoot
try {
    Invoke-Step '1/8 Backend full test suite' {
        & .\mvnw.cmd test
    }
}
finally {
    Pop-Location
}

Push-Location $frontendRoot
try {
    if (-not $SkipInstall) {
        Invoke-Step '2/8 Frontend npm ci' {
            & npm.cmd ci
        }
    }
    else {
        Write-Host '2/8 Frontend npm ci skipped (-SkipInstall)'
    }

    Invoke-Step '3/8 Frontend TypeScript' {
        & npm.cmd run type-check
    }

    for ($index = 0; $index -lt $testGroups.Count; $index++) {
        $stepNumber = 4 + $index
        $group = $testGroups[$index]

        Invoke-Step "$stepNumber/8 Frontend Vitest serial group $($index + 1)/4" {
            & npx.cmd vitest run @group --maxWorkers=1 --minWorkers=1 --reporter=dot
        }
    }

    Invoke-Step '8/8 Frontend production build' {
        & npm.cmd run build-only
    }
}
finally {
    Pop-Location
}

Write-Host ''
Write-Host ('=' * 72)
Write-Host 'Phase 7 FINAL Candidate automated verification PASS'
Write-Host ('=' * 72)
Write-Host 'Next: run the browser runtime checks in:'
Write-Host 'docs\Phase7_FINAL_Production_Closeout_Runtime_Verification_Guide.md'
