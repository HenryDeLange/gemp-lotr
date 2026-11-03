$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$manifest = Join-Path $repositoryRoot 'gemp-lotr\bot-benchmarks\deck-builder-training-references-v1.json'
if (Test-Path -LiteralPath $manifest) {
    Write-Host "Using existing frozen deck-builder training manifest: $manifest"
    exit 0
}

$referenceManifest = Join-Path $repositoryRoot 'gemp-lotr\bot-benchmarks\reference-decks-v1.json'
if (-not (Test-Path -LiteralPath $referenceManifest)) {
    throw "Required holdout reference manifest not found: $referenceManifest"
}

$buildArguments = @(
    '-f', (Join-Path $repositoryRoot 'gemp-lotr\pom.xml'),
    '-pl', 'gemp-lotr-server',
    '-am',
    '-Dmaven.test.skip=true',
    'install'
)
& mvn @buildArguments
if ($LASTEXITCODE -ne 0) {
    throw "Maven reactor install failed with exit code $LASTEXITCODE."
}

$resources = Join-Path $repositoryRoot 'gemp-lotr\gemp-lotr-cards\src\main\resources'
$serverPom = Join-Path $repositoryRoot 'gemp-lotr\gemp-lotr-server\pom.xml'
$generatorArguments = @(
    "--resources=`"$resources`"",
    "--create-manifest=`"$manifest`"",
    "--reference-manifest=`"$referenceManifest`"",
    '--independent-reference-pool=true',
    '--generated=8',
    '--seed=13011'
) -join ' '
$mavenArguments = @(
    '-f', $serverPom,
    'org.codehaus.mojo:exec-maven-plugin:3.5.0:java',
    '-Dexec.mainClass=com.gempukku.lotro.bots.benchmark.BotBenchmarkMain',
    "-Dexec.args=$generatorArguments"
)
& mvn @mavenArguments
if ($LASTEXITCODE -ne 0) {
    throw "Deck-builder reference manifest generation failed with exit code $LASTEXITCODE."
}
if (-not (Test-Path -LiteralPath $manifest)) {
    throw "Manifest generator completed without creating: $manifest"
}

Write-Host "Created frozen deck-builder training manifest: $manifest"
