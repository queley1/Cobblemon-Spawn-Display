Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$sheetId = '1qpUZhUhjp3NTLUzbdF7R_fcYi0Z8-Ky4gKR2FSGtNKs'
$sheetGid = '985377545'
$sheetUrl = "https://docs.google.com/spreadsheets/d/$sheetId/edit?gid=$sheetGid#gid=$sheetGid"
$csvUrl = "https://docs.google.com/spreadsheets/d/$sheetId/export?format=csv&gid=$sheetGid"
$outputPath = Join-Path (Split-Path -Parent $PSScriptRoot) 'src\main\resources\assets\cobblemon_spawn_display\rarity_buckets.json'

$bucketOrder = @('common', 'uncommon', 'rare', 'ultra-rare')
$canonicalBuckets = @{
    'common' = 'common'
    'uncommon' = 'uncommon'
    'rare' = 'rare'
    'ultra-rare' = 'ultra-rare'
    'ultra_rare' = 'ultra-rare'
    'ultrarare' = 'ultra-rare'
}

$response = Invoke-WebRequest -UseBasicParsing -Uri $csvUrl
$csvBytes = $response.RawContentStream.ToArray()
$csvText = [Text.Encoding]::UTF8.GetString($csvBytes)
$rows = @($csvText | ConvertFrom-Csv)

if ($rows.Count -eq 0) {
    throw 'The published bucket sheet returned no rows.'
}

foreach ($requiredColumn in @('Dex', 'Pokemon', 'Bucket')) {
    if ($rows[0].PSObject.Properties.Name -notcontains $requiredColumn) {
        throw "The published bucket sheet is missing the '$requiredColumn' column."
    }
}

$byDex = @{}
$mappedRows = 0

foreach ($row in $rows) {
    $rawBucket = ([string]$row.Bucket).Trim().ToLowerInvariant()
    if ([string]::IsNullOrWhiteSpace($rawBucket)) {
        continue
    }
    if (-not $canonicalBuckets.ContainsKey($rawBucket)) {
        throw "Unknown bucket '$($row.Bucket)' for Dex $($row.Dex) $($row.Pokemon)."
    }

    $nationalDexNumber = 0
    if (-not [int]::TryParse(([string]$row.Dex).Trim(), [ref]$nationalDexNumber) -or $nationalDexNumber -le 0) {
        throw "Invalid National Dex number '$($row.Dex)' for $($row.Pokemon)."
    }

    if (-not $byDex.ContainsKey($nationalDexNumber)) {
        $byDex[$nationalDexNumber] = New-Object 'System.Collections.Generic.HashSet[string]'
    }
    [void]$byDex[$nationalDexNumber].Add($canonicalBuckets[$rawBucket])
    $mappedRows++
}

$dexBuckets = [ordered]@{}
$multiBucketDexCount = 0
foreach ($nationalDexNumber in @($byDex.Keys | Sort-Object)) {
    $orderedBuckets = [System.Collections.Generic.List[string]]::new()
    foreach ($bucket in $bucketOrder) {
        if ($byDex[$nationalDexNumber].Contains($bucket)) {
            $orderedBuckets.Add($bucket)
        }
    }
    if ($orderedBuckets.Count -gt 1) {
        $multiBucketDexCount++
    }
    $dexBuckets[[string]$nationalDexNumber] = $orderedBuckets.ToArray()
}

$sha256 = [Security.Cryptography.SHA256]::Create()
try {
    $csvSha256 = ($sha256.ComputeHash($csvBytes) | ForEach-Object { $_.ToString('x2') }) -join ''
}
finally {
    $sha256.Dispose()
}

$snapshot = [ordered]@{
    schemaVersion = 1
    source = [ordered]@{
        sheetUrl = $sheetUrl
        csvExportUrl = $csvUrl
        retrievedAtUtc = [DateTime]::UtcNow.ToString('o')
        csvSha256 = $csvSha256
        rowCount = $rows.Count
        mappedRowCount = $mappedRows
        dexCount = $dexBuckets.Count
        multiBucketDexCount = $multiBucketDexCount
    }
    dexBuckets = $dexBuckets
}

$outputDirectory = Split-Path -Parent $outputPath
[void](New-Item -ItemType Directory -Force -Path $outputDirectory)
$utf8WithoutBom = New-Object Text.UTF8Encoding($false)
[IO.File]::WriteAllText($outputPath, ($snapshot | ConvertTo-Json -Depth 6), $utf8WithoutBom)

Write-Host "Wrote $($dexBuckets.Count) National Dex entries from $mappedRows mapped rows to $outputPath"
Write-Host "Multi-bucket entries: $multiBucketDexCount"
