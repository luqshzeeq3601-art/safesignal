# SafeSignal — Synthetic accelerometer trace generator
# Generates labelled sensor traces: fall_*, walk_*, sit_*, drop_phone_*, mandown_*
# Format: tMs,x,y,z in m/s^2 at ~50 Hz (20 ms interval)

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir
$targetDir = Join-Path $projectRoot "detection\src\test\resources\traces"

if (-not (Test-Path $targetDir)) {
    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
}

$gravity = 9.80665

function New-Noise([double]$scale) {
    return ([System.Random]::Shared.NextDouble() * 2.0 - 1.0) * $scale
}

function Export-Trace([string]$fileName, [array]$samples) {
    $outPath = Join-Path $targetDir $fileName
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add("tMs,x,y,z")
    foreach ($s in $samples) {
        $xStr = [string]::Format([System.Globalization.CultureInfo]::InvariantCulture, "{0:F4}", $s.x)
        $yStr = [string]::Format([System.Globalization.CultureInfo]::InvariantCulture, "{0:F4}", $s.y)
        $zStr = [string]::Format([System.Globalization.CultureInfo]::InvariantCulture, "{0:F4}", $s.z)
        $lines.Add("$($s.tMs),$xStr,$yStr,$zStr")
    }
    [System.IO.File]::WriteAllLines($outPath, $lines)
    Write-Host "Generated: $fileName ($($samples.Count) samples)"
}

# 1. fall_forward.csv (walking -> trip -> free fall -> hard impact -> motionless on floor)
$samples = [System.Collections.Generic.List[object]]::new()
for ($t = 0; $t -le 6000; $t += 20) {
    if ($t -lt 1500) {
        # Walking (phone in pocket, upright: y ~ gravity)
        $phase = ($t / 1000.0) * [Math]::PI * 3.6
        $x = [Math]::Sin($phase * 0.5) * 1.5 + (New-Noise 0.3)
        $y = $gravity + [Math]::Sin($phase) * 2.2 + (New-Noise 0.4)
        $z = [Math]::Cos($phase) * 1.8 + (New-Noise 0.3)
    } elseif ($t -lt 1680) {
        # Free fall: near weightlessness
        $x = 0.5 + (New-Noise 0.3)
        $y = 1.2 + (New-Noise 0.4)
        $z = 0.8 + (New-Noise 0.3)
    } elseif ($t -lt 1760) {
        # Impact spike: violent ground impact (> 28 m/s^2 magnitude)
        $x = 12.0 + (New-Noise 1.0)
        $y = 26.0 + (New-Noise 1.5)
        $z = 8.0 + (New-Noise 1.0)
    } elseif ($t -lt 1900) {
        # Rebound settling
        $x = 2.0 + (New-Noise 0.5)
        $y = 9.5 + (New-Noise 0.6)
        $z = 1.5 + (New-Noise 0.5)
    } else {
        # Stillness on floor: phone resting face down/tilted, motionless
        $x = 0.2 + (New-Noise 0.05)
        $y = 0.3 + (New-Noise 0.05)
        $z = $gravity + (New-Noise 0.05)
    }
    $samples.Add(@{ tMs = $t; x = $x; y = $y; z = $z })
}
Export-Trace "fall_forward.csv" $samples

# 2. fall_backward.csv (standing still -> sudden slip -> free fall -> impact -> motionless)
$samples = [System.Collections.Generic.List[object]]::new()
for ($t = 0; $t -le 6000; $t += 20) {
    if ($t -lt 1200) {
        # Standing still
        $x = 0.1 + (New-Noise 0.08)
        $y = $gravity + (New-Noise 0.08)
        $z = 0.2 + (New-Noise 0.08)
    } elseif ($t -lt 1440) {
        # Free fall
        $x = 0.8 + (New-Noise 0.2)
        $y = 1.5 + (New-Noise 0.3)
        $z = -0.5 + (New-Noise 0.2)
    } elseif ($t -lt 1520) {
        # Impact spike
        $x = -8.0 + (New-Noise 1.0)
        $y = -24.0 + (New-Noise 1.2)
        $z = 12.0 + (New-Noise 1.0)
    } else {
        # Stillness
        $x = -0.1 + (New-Noise 0.04)
        $y = 0.2 + (New-Noise 0.04)
        $z = -$gravity + (New-Noise 0.04)
    }
    $samples.Add(@{ tMs = $t; x = $x; y = $y; z = $z })
}
Export-Trace "fall_backward.csv" $samples

# 3. fall_lateral.csv (walking -> sideways slip -> free fall -> impact -> motionless)
$samples = [System.Collections.Generic.List[object]]::new()
for ($t = 0; $t -le 6000; $t += 20) {
    if ($t -lt 2000) {
        $phase = ($t / 1000.0) * [Math]::PI * 3.6
        $x = [Math]::Sin($phase * 0.5) * 1.6 + (New-Noise 0.2)
        $y = $gravity + [Math]::Sin($phase) * 2.0 + (New-Noise 0.3)
        $z = [Math]::Cos($phase) * 1.5 + (New-Noise 0.2)
    } elseif ($t -lt 2240) {
        # Free fall
        $x = 1.0 + (New-Noise 0.3)
        $y = 1.0 + (New-Noise 0.3)
        $z = 1.0 + (New-Noise 0.3)
    } elseif ($t -lt 2320) {
        # Lateral impact spike
        $x = 24.0 + (New-Noise 1.5)
        $y = 10.0 + (New-Noise 1.0)
        $z = 14.0 + (New-Noise 1.0)
    } else {
        # Stillness on side
        $x = $gravity + (New-Noise 0.04)
        $y = 0.1 + (New-Noise 0.04)
        $z = 0.1 + (New-Noise 0.04)
    }
    $samples.Add(@{ tMs = $t; x = $x; y = $y; z = $z })
}
Export-Trace "fall_lateral.csv" $samples

# 4. fall_faint.csv (standing -> collapse free fall -> impact -> motionless)
$samples = [System.Collections.Generic.List[object]]::new()
for ($t = 0; $t -le 7000; $t += 20) {
    if ($t -lt 2000) {
        # Standing
        $x = 0.1 + (New-Noise 0.05)
        $y = $gravity + (New-Noise 0.05)
        $z = 0.1 + (New-Noise 0.05)
    } elseif ($t -lt 2260) {
        # Free fall
        $x = 0.6 + (New-Noise 0.2)
        $y = 1.8 + (New-Noise 0.3)
        $z = 0.4 + (New-Noise 0.2)
    } elseif ($t -lt 2340) {
        # Impact
        $x = 6.0 + (New-Noise 1.0)
        $y = 25.0 + (New-Noise 1.2)
        $z = 8.0 + (New-Noise 1.0)
    } else {
        # Stillness
        $x = 0.1 + (New-Noise 0.04)
        $y = 0.1 + (New-Noise 0.04)
        $z = $gravity + (New-Noise 0.04)
    }
    $samples.Add(@{ tMs = $t; x = $x; y = $y; z = $z })
}
Export-Trace "fall_faint.csv" $samples

# 5. walk_normal.csv (rhythmic walking, 1.8 Hz, magnitude 7.5 - 13 m/s^2)
$samples = [System.Collections.Generic.List[object]]::new()
for ($t = 0; $t -le 8000; $t += 20) {
    $phase = ($t / 1000.0) * [Math]::PI * 3.6
    $x = [Math]::Sin($phase * 0.5) * 1.4 + (New-Noise 0.2)
    $y = $gravity + [Math]::Sin($phase) * 2.2 + (New-Noise 0.3)
    $z = [Math]::Cos($phase) * 1.6 + (New-Noise 0.2)
    $samples.Add(@{ tMs = $t; x = $x; y = $y; z = $z })
}
Export-Trace "walk_normal.csv" $samples

# 6. walk_fast.csv (fast walking / jogging, 2.4 Hz, magnitude 6.5 - 15.5 m/s^2)
$samples = [System.Collections.Generic.List[object]]::new()
for ($t = 0; $t -le 8000; $t += 20) {
    $phase = ($t / 1000.0) * [Math]::PI * 4.8
    $x = [Math]::Sin($phase * 0.5) * 2.0 + (New-Noise 0.3)
    $y = $gravity + [Math]::Sin($phase) * 3.6 + (New-Noise 0.4)
    $z = [Math]::Cos($phase) * 2.5 + (New-Noise 0.3)
    $samples.Add(@{ tMs = $t; x = $x; y = $y; z = $z })
}
Export-Trace "walk_fast.csv" $samples

# 7. walk_stairs.csv (stairs navigation, periodic peaks 14-17 m/s^2, no free fall)
$samples = [System.Collections.Generic.List[object]]::new()
for ($t = 0; $t -le 8000; $t += 20) {
    $phase = ($t / 1000.0) * [Math]::PI * 3.0
    $stepShock = if (([Math]::Sin($phase) -gt 0.85)) { 4.0 } else { 0.0 }
    $x = [Math]::Sin($phase * 0.5) * 1.8 + (New-Noise 0.3)
    $y = $gravity + [Math]::Sin($phase) * 2.8 + $stepShock + (New-Noise 0.4)
    $z = [Math]::Cos($phase) * 2.0 + (New-Noise 0.3)
    $samples.Add(@{ tMs = $t; x = $x; y = $y; z = $z })
}
Export-Trace "walk_stairs.csv" $samples

# 8. sit_down.csv (walking -> sits in chair -> resting still; no free fall < 5 m/s^2, peak ~14 m/s^2)
$samples = [System.Collections.Generic.List[object]]::new()
for ($t = 0; $t -le 6000; $t += 20) {
    if ($t -lt 2000) {
        # Walking
        $phase = ($t / 1000.0) * [Math]::PI * 3.6
        $x = [Math]::Sin($phase * 0.5) * 1.2 + (New-Noise 0.2)
        $y = $gravity + [Math]::Sin($phase) * 1.8 + (New-Noise 0.2)
        $z = [Math]::Cos($phase) * 1.2 + (New-Noise 0.2)
    } elseif ($t -lt 2500) {
        # Deceleration / lowering body into chair (dip to ~7 m/s^2)
        $p = ($t - 2000) / 500.0
        $x = 0.5 + (New-Noise 0.2)
        $y = $gravity - [Math]::Sin($p * [Math]::PI) * 2.8 + (New-Noise 0.3)
        $z = 0.4 + (New-Noise 0.2)
    } elseif ($t -lt 2700) {
        # Soft chair contact shock (~14 m/s^2, well below fall impact threshold)
        $x = 1.0 + (New-Noise 0.3)
        $y = $gravity + 4.5 + (New-Noise 0.5)
        $z = 1.2 + (New-Noise 0.3)
    } else {
        # Sitting still
        $x = 0.1 + (New-Noise 0.05)
        $y = $gravity + (New-Noise 0.05)
        $z = 0.1 + (New-Noise 0.05)
    }
    $samples.Add(@{ tMs = $t; x = $x; y = $y; z = $z })
}
Export-Trace "sit_down.csv" $samples

# 9. sit_quick.csv (quick sit on firm chair: contact peak ~16 m/s^2, no free-fall)
$samples = [System.Collections.Generic.List[object]]::new()
for ($t = 0; $t -le 6000; $t += 20) {
    if ($t -lt 1500) {
        $x = 0.1 + (New-Noise 0.06)
        $y = $gravity + (New-Noise 0.06)
        $z = 0.1 + (New-Noise 0.06)
    } elseif ($t -lt 1800) {
        # Quick drop onto chair: dip to 6.5 m/s^2 (above free-fall threshold)
        $x = 0.4 + (New-Noise 0.2)
        $y = 6.8 + (New-Noise 0.3)
        $z = 0.3 + (New-Noise 0.2)
    } elseif ($t -lt 1940) {
        # Impact on firm seat (~16.5 m/s^2, below fall impact threshold 20 m/s^2)
        $x = 2.0 + (New-Noise 0.4)
        $y = 16.5 + (New-Noise 0.5)
        $z = 1.8 + (New-Noise 0.4)
    } else {
        # Stillness
        $x = 0.1 + (New-Noise 0.04)
        $y = $gravity + (New-Noise 0.04)
        $z = 0.1 + (New-Noise 0.04)
    }
    $samples.Add(@{ tMs = $t; x = $x; y = $y; z = $z })
}
Export-Trace "sit_quick.csv" $samples

# 10. drop_phone_pickup.csv (slips from hand -> free fall 240 ms -> impact 36 m/s^2 -> picked up 700 ms later: active motion cancels stillness)
$samples = [System.Collections.Generic.List[object]]::new()
for ($t = 0; $t -le 6000; $t += 20) {
    if ($t -lt 1000) {
        # In hand
        $x = 0.2 + (New-Noise 0.1)
        $y = $gravity + (New-Noise 0.1)
        $z = 0.2 + (New-Noise 0.1)
    } elseif ($t -lt 1240) {
        # Free fall in air
        $x = 0.2 + (New-Noise 0.2)
        $y = 0.4 + (New-Noise 0.2)
        $z = 0.3 + (New-Noise 0.2)
    } elseif ($t -lt 1320) {
        # Hard floor impact
        $x = 15.0 + (New-Noise 1.5)
        $y = 30.0 + (New-Noise 2.0)
        $z = 12.0 + (New-Noise 1.5)
    } elseif ($t -lt 1900) {
        # Rests on floor briefly (only ~580 ms, not enough for 2000 ms stillness)
        $x = 0.2 + (New-Noise 0.05)
        $y = 0.2 + (New-Noise 0.05)
        $z = $gravity + (New-Noise 0.05)
    } else {
        # Worker bends down, picks up phone, rotates it (active movement: magnitude swings 5 to 16 m/s^2)
        $phase = (($t - 1900) / 1000.0) * [Math]::PI * 4.0
        $x = [Math]::Sin($phase) * 4.0 + (New-Noise 0.5)
        $y = $gravity + [Math]::Cos($phase) * 5.0 + (New-Noise 0.5)
        $z = [Math]::Sin($phase * 1.5) * 3.5 + (New-Noise 0.5)
    }
    $samples.Add(@{ tMs = $t; x = $x; y = $y; z = $z })
}
Export-Trace "drop_phone_pickup.csv" $samples

# 11. drop_phone_desk.csv (phone tossed onto desk from 5 cm; no free-fall dip below 5.5 m/s^2, sharp tap ~17 m/s^2)
$samples = [System.Collections.Generic.List[object]]::new()
for ($t = 0; $t -le 5000; $t += 20) {
    if ($t -lt 1000) {
        $x = 0.1 + (New-Noise 0.05)
        $y = $gravity + (New-Noise 0.05)
        $z = 0.1 + (New-Noise 0.05)
    } elseif ($t -lt 1060) {
        # Slight dip only
        $x = 0.3 + (New-Noise 0.1)
        $y = 7.2 + (New-Noise 0.2)
        $z = 0.3 + (New-Noise 0.1)
    } elseif ($t -lt 1120) {
        # Tap on desk (~17 m/s^2, below fall impact threshold)
        $x = 3.0 + (New-Noise 0.5)
        $y = 16.8 + (New-Noise 0.6)
        $z = 4.0 + (New-Noise 0.5)
    } else {
        # Resting on desk
        $x = 0.05 + (New-Noise 0.03)
        $y = 0.05 + (New-Noise 0.03)
        $z = $gravity + (New-Noise 0.03)
    }
    $samples.Add(@{ tMs = $t; x = $x; y = $y; z = $z })
}
Export-Trace "drop_phone_desk.csv" $samples

# 12. drop_phone_bounce.csv (dropped on resilient surface, bounces and picked up immediately)
$samples = [System.Collections.Generic.List[object]]::new()
for ($t = 0; $t -le 6000; $t += 20) {
    if ($t -lt 1000) {
        $x = 0.1 + (New-Noise 0.05)
        $y = $gravity + (New-Noise 0.05)
        $z = 0.1 + (New-Noise 0.05)
    } elseif ($t -lt 1200) {
        # Free fall
        $x = 0.3 + (New-Noise 0.2)
        $y = 0.8 + (New-Noise 0.2)
        $z = 0.3 + (New-Noise 0.2)
    } elseif ($t -lt 1280) {
        # Impact 1
        $x = 10.0 + (New-Noise 1.0)
        $y = 26.0 + (New-Noise 1.5)
        $z = 8.0 + (New-Noise 1.0)
    } elseif ($t -lt 1500) {
        # Bounce in air
        $x = 3.0 + (New-Noise 0.5)
        $y = 4.0 + (New-Noise 0.6)
        $z = 2.0 + (New-Noise 0.5)
    } elseif ($t -lt 1580) {
        # Impact 2
        $x = 8.0 + (New-Noise 0.8)
        $y = 18.0 + (New-Noise 1.2)
        $z = 6.0 + (New-Noise 0.8)
    } else {
        # Picked up and moved
        $phase = (($t - 1580) / 1000.0) * [Math]::PI * 3.0
        $x = [Math]::Sin($phase) * 3.5 + (New-Noise 0.4)
        $y = $gravity + [Math]::Cos($phase) * 4.0 + (New-Noise 0.4)
        $z = [Math]::Sin($phase * 2) * 2.5 + (New-Noise 0.4)
    }
    $samples.Add(@{ tMs = $t; x = $x; y = $y; z = $z })
}
Export-Trace "drop_phone_bounce.csv" $samples

# 13. mandown_collapse.csv (walking -> gradual collapse -> immobility for 10 s)
$samples = [System.Collections.Generic.List[object]]::new()
for ($t = 0; $t -le 14000; $t += 20) {
    if ($t -lt 2000) {
        # Walking
        $phase = ($t / 1000.0) * [Math]::PI * 3.6
        $x = [Math]::Sin($phase * 0.5) * 1.5 + (New-Noise 0.2)
        $y = $gravity + [Math]::Sin($phase) * 2.2 + (New-Noise 0.3)
        $z = [Math]::Cos($phase) * 1.8 + (New-Noise 0.2)
    } elseif ($t -lt 3000) {
        # Gradual slump/collapse (no hard impact, magnitude remains 7 - 12 m/s^2)
        $p = ($t - 2000) / 1000.0
        $x = (1.0 - $p) * 1.5 + (New-Noise 0.2)
        $y = $gravity - [Math]::Sin($p * [Math]::PI) * 1.5 + (New-Noise 0.2)
        $z = (1.0 - $p) * 1.0 + (New-Noise 0.2)
    } else {
        # Complete motionless immobility (> 11 s)
        $x = 0.05 + (New-Noise 0.02)
        $y = 0.05 + (New-Noise 0.02)
        $z = $gravity + (New-Noise 0.02)
    }
    $samples.Add(@{ tMs = $t; x = $x; y = $y; z = $z })
}
Export-Trace "mandown_collapse.csv" $samples

Write-Host "All traces generated successfully in $targetDir"
