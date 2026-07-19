[CmdletBinding()]
param(
    [string]$OutputDirectory = "demo-output/generated-data",
    [int]$PointCount = 2000,
    [double]$TimeStepSeconds = 0.1,
    [int]$Seed = 20260719,
    [double]$AbnormalThreshold = 60.0
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$output = if ([IO.Path]::IsPathRooted($OutputDirectory)) { $OutputDirectory } else { Join-Path $root $OutputDirectory }
$culture = [Globalization.CultureInfo]::InvariantCulture
$utf8 = [Text.UTF8Encoding]::new($false)
$header = "time,true_x,true_y,true_z,track_x,track_y,track_z"
$sources = @(
    [pscustomobject]@{ Name = "FUSION"; File = "fusion-track.csv"; Sigma = 7.0; Intervals = @(,@(700, 739, 72.0, 10.0, 0.0)) },
    [pscustomobject]@{ Name = "RADAR"; File = "radar-track.csv"; Sigma = 18.0; Intervals = @(@(450, 519, 86.0, 20.0, 0.0), @(1300, 1379, -92.0, 15.0, 10.0)) },
    [pscustomobject]@{ Name = "INFRARED"; File = "infrared-track.csv"; Sigma = 32.0; Intervals = @(@(250, 399, 118.0, 25.0, 15.0), @(900, 1099, -125.0, 40.0, 20.0), @(1500, 1699, 35.0, 115.0, -15.0)) }
)

if ($PointCount -lt 2) { throw "PointCount must be at least 2." }
if ($TimeStepSeconds -le 0) { throw "TimeStepSeconds must be positive." }
New-Item -ItemType Directory -Force -Path $output | Out-Null

function Next-Gaussian([Random]$Random) {
    $u1 = [Math]::Max($Random.NextDouble(), [double]::Epsilon)
    $u2 = $Random.NextDouble()
    [Math]::Sqrt(-2.0 * [Math]::Log($u1)) * [Math]::Cos(2.0 * [Math]::PI * $u2)
}

function Format-Number([double]$Value) {
    $Value.ToString("0.000000", $culture)
}

$random = [Random]::new($Seed)
$truth = for ($i = 0; $i -lt $PointCount; $i++) {
    $t = $i * $TimeStepSeconds
    [pscustomobject]@{
        Time = $t
        X = 1000.0 + 82.0 * $t + 0.015 * $t * $t
        Y = 500.0 + 0.42 * $t * $t + 85.0 * [Math]::Sin($t / 28.0)
        Z = 1800.0 + 28.0 * [Math]::Sin($t / 18.0) + 0.08 * $t
    }
}

$offline = @()
foreach ($source in $sources) {
    $path = Join-Path $output $source.File
    $builder = [Text.StringBuilder]::new()
    [void]$builder.AppendLine($header)
    $errors = [Collections.Generic.List[double]]::new()
    for ($i = 0; $i -lt $PointCount; $i++) {
        $p = $truth[$i]
        $dx = (Next-Gaussian $random) * $source.Sigma
        $dy = (Next-Gaussian $random) * $source.Sigma
        $dz = (Next-Gaussian $random) * ($source.Sigma * 0.65)
        foreach ($interval in $source.Intervals) {
            if ($i -ge $interval[0] -and $i -le $interval[1]) {
                $dx += $interval[2]; $dy += $interval[3]; $dz += $interval[4]
            }
        }
        $trackX = $p.X + $dx; $trackY = $p.Y + $dy; $trackZ = $p.Z + $dz
        $error = [Math]::Sqrt($dx * $dx + $dy * $dy + $dz * $dz)
        $errors.Add($error)
        $line = @($p.Time, $p.X, $p.Y, $p.Z, $trackX, $trackY, $trackZ) | ForEach-Object { Format-Number $_ }
        [void]$builder.AppendLine(($line -join ','))
    }
    [IO.File]::WriteAllText($path, $builder.ToString(), $utf8)
    $mean = ($errors | Measure-Object -Average).Average
    $rmse = [Math]::Sqrt((($errors | ForEach-Object { $_ * $_ }) | Measure-Object -Average).Average)
    $abnormal = @($errors | Where-Object { $_ -gt $AbnormalThreshold }).Count
    $offline += [pscustomobject]@{ Source = $source.Name; Path = $path; Rows = $PointCount; MeanError = $mean; Rmse = $rmse; AbnormalCount = $abnormal; AbnormalRatio = $abnormal / $PointCount }
}

$loaded = @{}
foreach ($source in $sources) {
    $path = Join-Path $output $source.File
    $bytes = [IO.File]::ReadAllBytes($path)
    $text = $utf8.GetString($bytes)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) { throw "$($source.File) must be UTF-8 without a BOM." }
    $lines = [IO.File]::ReadAllLines($path, $utf8)
    if ($lines[0] -ne $header) { throw "Unexpected CSV header in $($source.File)." }
    if ($lines.Count -ne ($PointCount + 1)) { throw "Unexpected row count in $($source.File)." }
    if (@($lines | Where-Object { ($_ -split ',', -1).Count -ne 7 }).Count -ne 0) { throw "Invalid column count in $($source.File)." }
    $rows = @(Import-Csv -LiteralPath $path -Encoding UTF8)
    $previous = [double]::NegativeInfinity
    foreach ($row in $rows) {
        foreach ($column in @('time','true_x','true_y','true_z','track_x','track_y','track_z')) {
            if ([string]::IsNullOrWhiteSpace($row.$column)) { throw "Empty value in $($source.File)." }
            $number = 0.0
            if (-not [double]::TryParse($row.$column, [Globalization.NumberStyles]::Float, $culture, [ref]$number) -or [double]::IsNaN($number) -or [double]::IsInfinity($number)) { throw "Non-finite value in $($source.File)." }
        }
        $time = [double]::Parse($row.time, $culture)
        if ($time -le $previous) { throw "Time is not strictly increasing in $($source.File)." }
        $previous = $time
    }
    $loaded[$source.Name] = $rows
}

for ($i = 0; $i -lt $PointCount; $i++) {
    $expected = @($loaded.FUSION[$i].time, $loaded.FUSION[$i].true_x, $loaded.FUSION[$i].true_y, $loaded.FUSION[$i].true_z) -join ','
    foreach ($name in @('RADAR','INFRARED')) {
        $actual = @($loaded[$name][$i].time, $loaded[$name][$i].true_x, $loaded[$name][$i].true_y, $loaded[$name][$i].true_z) -join ','
        if ($actual -ne $expected) { throw "Truth trajectory differs for $name at row $($i + 1)." }
    }
}

$ranked = @($offline | Sort-Object Rmse)
if (($ranked.Source -join ',') -ne 'FUSION,RADAR,INFRARED') { throw "Offline RMSE quality order is invalid." }
$abnormalRanked = @($offline | Sort-Object AbnormalRatio)
if (($abnormalRanked.Source -join ',') -ne 'FUSION,RADAR,INFRARED') { throw "Offline abnormal-ratio order is invalid." }
if (@($offline | Where-Object { $_.AbnormalCount -le 0 -or $_.AbnormalCount -ge $PointCount }).Count -ne 0) { throw "Each source must contain both normal and abnormal points." }

Write-Output "Generated deterministic synthetic tracks (seed=$Seed, points=$PointCount, interval=$TimeStepSeconds s, threshold=$AbnormalThreshold m)."
$offline | ForEach-Object { Write-Output ("{0}: path={1}; rows={2}; offlineRMSE={3:N3}; offlineAbnormalRatio={4:P2}" -f $_.Source, $_.Path, $_.Rows, $_.Rmse, $_.AbnormalRatio) }
