@echo off
setlocal
title Crowns ^& Cannons - Helfer
rem Doppelklick: einrichten und einmal ausfuehren. "Crowns-Helfer.bat entfernen": Aufgabe und Dateien wieder loeschen.
powershell -NoProfile -ExecutionPolicy Bypass -Command "$c=[IO.File]::ReadAllText('%~f0'); $c=$c.Substring($c.LastIndexOf('#'+'#PS##')+6); & ([scriptblock]::Create($c)) -Selbst '%~f0' -Modus '%~1'"
echo.
if /i not "%~1"=="auto" pause
exit /b
##PS##
param([string]$Selbst, [string]$Modus, [string]$Quelle = 'https://editor.niklasmetzger.de/pack/crowns.json', [string]$Wurzel = '', [switch]$Test)
$ErrorActionPreference = 'Stop'
$Ordner = Join-Path $env:LOCALAPPDATA 'CrownsHelfer'
$Aufgabe = 'Crowns & Cannons Helfer'
$Auto = ($Modus -eq 'auto')
$Log = Join-Path $Ordner 'helfer.log'
New-Item -ItemType Directory -Force -Path $Ordner | Out-Null

function Schreib([string]$t) {
    $z = (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + '  ' + $t
    if (-not $Auto) { Write-Host $t }
    Add-Content -LiteralPath $Log -Value $z -Encoding UTF8
}

# ---- Entfernen ----
if ($Modus -eq 'entfernen') {
    try { Unregister-ScheduledTask -TaskName $Aufgabe -Confirm:$false -ErrorAction Stop; Write-Host 'Aufgabe entfernt.' } catch { Write-Host 'Keine Aufgabe gefunden.' }
    Remove-Item -LiteralPath $Ordner -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host 'Crowns-Helfer ist entfernt. Mods in deinen Instanzen bleiben, wie sie sind.'
    return
}

# ---- Einrichten (beim Doppelklick) ----
if (-not $Auto) {
    Write-Host ''
    Write-Host 'Crowns & Cannons Helfer'
    Write-Host 'Haelt Zusatz-Mods und das Java-Argument fuer das Modpack automatisch aktuell.'
    Write-Host 'Eigene Mods in deinem mods-Ordner werden nie angefasst.'
    Write-Host ''
    $ziel = Join-Path $Ordner 'Crowns-Helfer.bat'
    if ($Selbst -and ((Resolve-Path -LiteralPath $Selbst).Path -ne $ziel)) { Copy-Item -LiteralPath $Selbst -Destination $ziel -Force }
    $vbs = Join-Path $Ordner 'helfer.vbs'
    # Ohne sichtbares Fenster starten
    Set-Content -LiteralPath $vbs -Encoding ASCII -Value ('CreateObject("WScript.Shell").Run """' + $ziel + '"" auto", 0, False')
    try {
        $aktion = New-ScheduledTaskAction -Execute 'wscript.exe' -Argument ('"' + $vbs + '"')
        $t1 = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
        $t2 = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) -RepetitionInterval (New-TimeSpan -Minutes 15)
        $einst = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Minutes 10)
        Register-ScheduledTask -TaskName $Aufgabe -Action $aktion -Trigger @($t1, $t2) -Settings $einst -Description 'Haelt das Crowns & Cannons Modpack aktuell (Zusatz-Mods, Java-Argument).' -Force | Out-Null
        Write-Host 'Eingerichtet: laeuft ab jetzt unsichtbar bei jeder Anmeldung und alle 15 Minuten.'
    } catch {
        Write-Host ('Aufgabe konnte nicht eingerichtet werden: ' + $_.Exception.Message)
        Write-Host 'Der Helfer laeuft jetzt trotzdem einmal.'
    }
    Write-Host ''
}

# ---- Liste holen (sonst die zuletzt geladene) ----
$cache = Join-Path $Ordner 'crowns.json'
$pack = $null
try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    if ($Quelle -match '^https?://') {
        $roh = (Invoke-WebRequest -UseBasicParsing -Uri $Quelle -TimeoutSec 20 -Headers @{ 'User-Agent' = 'crowns-helfer/1.0' }).Content
    } else {
        $roh = [IO.File]::ReadAllText($Quelle)
    }
    $pack = $roh | ConvertFrom-Json
    [IO.File]::WriteAllText($cache, $roh)
} catch {
    if (Test-Path -LiteralPath $cache) {
        $pack = [IO.File]::ReadAllText($cache) | ConvertFrom-Json
        Schreib ('Liste nicht erreichbar, nehme die zuletzt geladene (' + $_.Exception.Message + ')')
    } else {
        Schreib ('Liste nicht erreichbar und noch keine gespeichert: ' + $_.Exception.Message)
        return
    }
}

# ---- Instanzen finden ----
$roots = New-Object System.Collections.Generic.List[string]
if ($Wurzel) { $roots.Add($Wurzel) } else {
$storage = Join-Path $env:APPDATA 'CurseForge\storage.json'
if (Test-Path -LiteralPath $storage) {
    try {
        $s = Get-Content -LiteralPath $storage -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($s.'minecraft-settings') {
            $mc = $s.'minecraft-settings' | ConvertFrom-Json
            if ($mc.minecraftRoot) { $roots.Add((Join-Path $mc.minecraftRoot 'Instances')); $roots.Add((Join-Path $mc.minecraftRoot 'minecraft\Instances')) }
        }
    } catch { }
}
$roots.Add('C:\curseforge\Instances')
$roots.Add((Join-Path $env:USERPROFILE 'curseforge\minecraft\Instances'))
$roots.Add((Join-Path $env:USERPROFILE 'Documents\curseforge\minecraft\Instances'))
}

$instanzen = @()
foreach ($r in ($roots | Select-Object -Unique)) {
    if (-not (Test-Path -LiteralPath $r)) { continue }
    foreach ($d in (Get-ChildItem -LiteralPath $r -Directory)) {
        $j = Join-Path $d.FullName 'minecraftinstance.json'
        if (-not (Test-Path -LiteralPath $j)) { continue }
        $name = $d.Name
        try { $n = (Get-Content -LiteralPath $j -Raw -Encoding UTF8 | ConvertFrom-Json).name; if ($n) { $name = $n } } catch { }
        foreach ($p in $pack.instanzen) {
            if ($name.StartsWith($p) -or $d.Name.StartsWith($p)) { $instanzen += [pscustomobject]@{ Ordner = $d.FullName; Datei = $j; Name = $name }; break }
        }
    }
}
if ($instanzen.Count -eq 0) { Schreib 'Keine Crowns-&-Cannons-Instanz gefunden.'; return }

$cfLaeuft = (-not $Test) -and [bool](Get-Process -Name CurseForge -ErrorAction SilentlyContinue)

foreach ($i in $instanzen) {
    Schreib ('Instanz: ' + $i.Name)
    $mods = Join-Path $i.Ordner 'mods'
    New-Item -ItemType Directory -Force -Path $mods | Out-Null

    # ---- Zusatz-Mods ----
    foreach ($m in $pack.mods) {
        $ziel = Join-Path $mods $m.datei
        $ok = (Test-Path -LiteralPath $ziel) -and ((Get-FileHash -LiteralPath $ziel -Algorithm SHA512).Hash -ieq $m.sha512)
        if (-not $ok) {
            $tmp = Join-Path $Ordner ($m.datei + '.download')
            try {
                Invoke-WebRequest -UseBasicParsing -Uri $m.url -OutFile $tmp -TimeoutSec 120 -Headers @{ 'User-Agent' = 'crowns-helfer/1.0' }
                if ((Get-FileHash -LiteralPath $tmp -Algorithm SHA512).Hash -ine $m.sha512) { throw 'Pruefsumme stimmt nicht' }
                Move-Item -LiteralPath $tmp -Destination $ziel -Force
                $ok = $true
                Schreib ('  ' + $m.name + ': ' + $m.datei + ' installiert')
            } catch {
                Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue
                Schreib ('  ' + $m.name + ': Download fehlgeschlagen (' + $_ + '), naechster Versuch spaeter')
            }
        } else {
            Schreib ('  ' + $m.name + ': aktuell')
        }
        # Andere Versionen derselben Mod entfernen, aber nur wenn die richtige da ist
        if ($ok -and $m.ersetzt) {
            foreach ($alt in (Get-ChildItem -LiteralPath $mods -Filter $m.ersetzt -File)) {
                if ($alt.Name -eq $m.datei) { continue }
                try { Remove-Item -LiteralPath $alt.FullName -Force; Schreib ('  alte Version entfernt: ' + $alt.Name) }
                catch { Schreib ('  ' + $alt.Name + ' ist gerade gesperrt (Spiel offen?), naechster Versuch spaeter') }
            }
        }
    }

    # ---- Java-Argument (nur wenn die CurseForge-App zu ist, sonst ueberschreibt sie es beim Beenden) ----
    $inst = Get-Content -LiteralPath $i.Datei -Raw -Encoding UTF8 | ConvertFrom-Json
    $alt = [string]$inst.javaArgsOverride
    $fehlt = @($pack.jvm | Where-Object { -not (($alt -split '\s+') -contains $_) })
    if ($fehlt.Count -eq 0) { Schreib '  Java-Argument: gesetzt'; continue }
    if ($cfLaeuft) { Schreib '  Java-Argument fehlt, CurseForge ist offen: wird gesetzt, sobald die App geschlossen ist'; continue }
    $neu = (($alt.Trim() + ' ' + ($fehlt -join ' ')).Trim())
    $bytes = [IO.File]::ReadAllBytes($i.Datei)
    $bom = ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
    $text = [IO.File]::ReadAllText($i.Datei)
    $wert = '"javaArgsOverride": ' + ($neu | ConvertTo-Json)
    $rx = [regex]'"javaArgsOverride"\s*:\s*(null|"(?:[^"\\]|\\.)*")'
    if ($rx.IsMatch($text)) { $text = $rx.Replace($text, $wert.Replace('$', '$$'), 1) }
    else { $text = $text -replace '^\s*\{', ('{' + "`r`n  " + $wert.Replace('$', '$$') + ',') }
    Copy-Item -LiteralPath $i.Datei -Destination ($i.Datei + '.bak') -Force
    [IO.File]::WriteAllText($i.Datei, $text, (New-Object Text.UTF8Encoding($bom)))
    try { $null = [IO.File]::ReadAllText($i.Datei) | ConvertFrom-Json; Schreib ('  Java-Argument gesetzt: ' + $neu) }
    catch { Copy-Item -LiteralPath ($i.Datei + '.bak') -Destination $i.Datei -Force; Schreib '  Java-Argument: Datei waere kaputt gegangen, alte Fassung wiederhergestellt' }
}

# Log kurz halten
try { $z = Get-Content -LiteralPath $Log -Encoding UTF8; if ($z.Count -gt 500) { $z | Select-Object -Last 300 | Set-Content -LiteralPath $Log -Encoding UTF8 } } catch { }
if (-not $Auto) { Write-Host ''; Write-Host 'Fertig. Protokoll:' $Log }
