@echo off
setlocal
title Crowns ^& Cannons - Helfer
rem Doppelklick: einrichten und einmal ausfuehren. "Crowns-Helfer.bat entfernen": Aufgabe und Dateien wieder loeschen.
powershell -NoProfile -ExecutionPolicy Bypass -Command "$c=[IO.File]::ReadAllText('%~f0'); $c=$c.Substring($c.LastIndexOf('#'+'#PS##')+6); & ([scriptblock]::Create($c)) -Selbst '%~f0' -Modus '%~1'"
echo.
if /i not "%~1"=="auto" pause
exit /b
##PS##
param([string]$Selbst, [string]$Modus, [string]$Quelle = 'https://editor.niklasmetzger.de/pack/crowns.json', [string]$Wurzel = '', [string]$Downloads = '', [switch]$Test)
$ErrorActionPreference = 'Stop'
$Ordner = Join-Path $env:LOCALAPPDATA 'CrownsHelfer'
$Aufgabe = 'Crowns & Cannons Helfer'
$Auto = ($Modus -eq 'auto')
$Log = Join-Path $Ordner 'helfer.log'
$Ua = @{ 'User-Agent' = 'crowns-helfer/2.0' }
$Erlaubt = '^(mods|resourcepacks|datapacks|shaderpacks|kubejs)/'
New-Item -ItemType Directory -Force -Path $Ordner | Out-Null
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

function Schreib([string]$t) {
    if (-not $Auto) { Write-Host $t }
    Add-Content -LiteralPath $Log -Value ((Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + '  ' + $t) -Encoding UTF8
}

function PfadOk([string]$p) {
    return ($p -match $Erlaubt) -and ($p -notmatch '(^|/)\.\.?(/|$)') -and ($p -notmatch '[:\\]') -and ($p.Length -lt 300)
}

function Hash512([string]$p) { (Get-FileHash -LiteralPath $p -Algorithm SHA512).Hash.ToLower() }

# ---- Entfernen ----
if ($Modus -eq 'entfernen') {
    try { Unregister-ScheduledTask -TaskName $Aufgabe -Confirm:$false -ErrorAction Stop; Write-Host 'Aufgabe entfernt.' } catch { Write-Host 'Keine Aufgabe gefunden.' }
    Remove-Item -LiteralPath $Ordner -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host 'Crowns-Helfer ist entfernt. Die Dateien in deinen Instanzen bleiben, wie sie sind.'
    return
}

# ---- Einrichten (beim Doppelklick) ----
if (-not $Auto -and -not $Test) {
    Write-Host ''
    Write-Host 'Crowns & Cannons Helfer'
    Write-Host 'Haelt das Modpack automatisch aktuell: Mods, Ressourcenpakete, Datenpakete, Shader, KubeJS und das Java-Argument.'
    Write-Host 'Eigene Dateien, die du selbst hinzugefuegt hast, werden nie angefasst.'
    Write-Host ''
    $ziel = Join-Path $Ordner 'Crowns-Helfer.bat'
    if ($Selbst -and ((Resolve-Path -LiteralPath $Selbst).Path -ne $ziel)) { Copy-Item -LiteralPath $Selbst -Destination $ziel -Force }
    $vbs = Join-Path $Ordner 'helfer.vbs'
    Set-Content -LiteralPath $vbs -Encoding ASCII -Value ('CreateObject("WScript.Shell").Run """' + $ziel + '"" auto", 0, False')
    try {
        $aktion = New-ScheduledTaskAction -Execute 'wscript.exe' -Argument ('"' + $vbs + '"')
        $t1 = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
        $t2 = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) -RepetitionInterval (New-TimeSpan -Minutes 15)
        $einst = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Minutes 30) -MultipleInstances IgnoreNew
        Register-ScheduledTask -TaskName $Aufgabe -Action $aktion -Trigger @($t1, $t2) -Settings $einst -Description 'Haelt das Crowns & Cannons Modpack aktuell.' -Force | Out-Null
        Write-Host 'Eingerichtet: laeuft ab jetzt unsichtbar bei jeder Anmeldung und alle 15 Minuten.'
    } catch {
        Write-Host ('Aufgabe konnte nicht eingerichtet werden: ' + $_.Exception.Message)
    }
    Write-Host ''
}

# ---- Liste holen ----
$cache = Join-Path $Ordner 'crowns.json'
$pack = $null
try {
    if ($Quelle -match '^https?://') { $roh = (Invoke-WebRequest -UseBasicParsing -Uri $Quelle -TimeoutSec 20 -Headers $Ua).Content }
    else { $roh = [IO.File]::ReadAllText($Quelle) }
    $pack = $roh | ConvertFrom-Json
    [IO.File]::WriteAllText($cache, $roh)
} catch {
    if (Test-Path -LiteralPath $cache) { $pack = [IO.File]::ReadAllText($cache) | ConvertFrom-Json; Schreib ('Liste nicht erreichbar, nehme die zuletzt geladene (' + $_.Exception.Message + ')') }
    else { Schreib ('Liste nicht erreichbar und noch keine gespeichert: ' + $_.Exception.Message); return }
}
$basis = if ($Quelle -match '^(https?://[^/]+)') { $Matches[1] } else { '' }

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
        foreach ($p in $pack.instanzen) { if ($name.StartsWith($p) -or $d.Name.StartsWith($p)) { $instanzen += [pscustomobject]@{ Ordner = $d.FullName; Datei = $j; Name = $name }; break } }
    }
}
if ($instanzen.Count -eq 0) { Schreib 'Keine Crowns-&-Cannons-Instanz gefunden.'; return }

# ---- Downloads-Ordner (fuer Dateien, die man selbst herunterladen muss) ----
if (-not $Downloads) {
    try { $Downloads = (New-Object -ComObject Shell.Application).NameSpace('shell:Downloads').Self.Path } catch { }
    if (-not $Downloads) { $Downloads = Join-Path $env:USERPROFILE 'Downloads' }
}
function AusDownloads($d) {
    if (-not (Test-Path -LiteralPath $Downloads)) { return $null }
    foreach ($f in (Get-ChildItem -LiteralPath $Downloads -File -ErrorAction SilentlyContinue)) {
        if ($d.groesse -and $f.Length -ne [int64]$d.groesse) { continue }
        if ((Hash512 $f.FullName) -eq $d.sha512.ToLower()) { return $f.FullName }
    }
    return $null
}

$cfLaeuft = (-not $Test) -and [bool](Get-Process -Name CurseForge -ErrorAction SilentlyContinue)
$manuell = @{}

foreach ($i in $instanzen) {
    Schreib ('Instanz: ' + $i.Name + ' (Pack-Version ' + $pack.version + ')')
    $neu = 0; $weg = 0
    foreach ($d in $pack.dateien) {
        if (-not (PfadOk $d.pfad)) { continue }
        $ziel = Join-Path $i.Ordner ($d.pfad -replace '/', '\')
        $ok = (Test-Path -LiteralPath $ziel) -and ((Hash512 $ziel) -eq $d.sha512.ToLower())
        if (-not $ok) {
            New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ziel) | Out-Null
            $tmp = Join-Path $Ordner 'download.tmp'
            foreach ($q in $d.quellen) {
                $url = if ($q.StartsWith('/')) { $basis + $q } else { $q }
                try {
                    Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $tmp -TimeoutSec 300 -Headers $Ua
                    if ((Hash512 $tmp) -ne $d.sha512.ToLower()) { throw 'Pruefsumme stimmt nicht' }
                    Move-Item -LiteralPath $tmp -Destination $ziel -Force
                    $ok = $true; $neu++
                    Schreib ('  neu: ' + $d.pfad)
                    break
                } catch {
                    Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue
                    Schreib ('  ' + $d.pfad + ': Quelle fehlgeschlagen (' + $_.Exception.Message + ')')
                }
            }
            if (-not $ok) {
                $dl = AusDownloads $d
                if ($dl) { Move-Item -LiteralPath $dl -Destination $ziel -Force; $ok = $true; $neu++; Schreib ('  aus Downloads uebernommen: ' + $d.pfad) }
                else { $manuell[$d.sha512] = $d }
            }
        }
        if ($ok) {
            foreach ($a in $d.alt) {
                if (-not (PfadOk $a) -or $a -eq $d.pfad) { continue }
                $altPfad = Join-Path $i.Ordner ($a -replace '/', '\')
                if (Test-Path -LiteralPath $altPfad) {
                    try { Remove-Item -LiteralPath $altPfad -Force; $weg++; Schreib ('  alte Version entfernt: ' + $a) }
                    catch { Schreib ('  ' + $a + ' ist gerade gesperrt (Spiel offen?), naechster Versuch spaeter') }
                }
            }
        }
    }
    Schreib ('  ' + $neu + ' neu, ' + $weg + ' entfernt')

    # ---- Java-Argumente (nur wenn die CurseForge-App zu ist, sonst ueberschreibt sie es beim Beenden) ----
    if (-not $pack.jvm -or $pack.jvm.Count -eq 0) { continue }
    $inst = Get-Content -LiteralPath $i.Datei -Raw -Encoding UTF8 | ConvertFrom-Json
    $altArgs = [string]$inst.javaArgsOverride
    $fehlt = @($pack.jvm | Where-Object { -not (($altArgs -split '\s+') -contains $_) })
    if ($fehlt.Count -eq 0) { continue }
    if ($cfLaeuft) { Schreib '  Java-Argument fehlt, CurseForge ist offen: wird gesetzt, sobald die App geschlossen ist'; continue }
    $neuArgs = (($altArgs.Trim() + ' ' + ($fehlt -join ' ')).Trim())
    $bytes = [IO.File]::ReadAllBytes($i.Datei)
    $bom = ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
    $text = [IO.File]::ReadAllText($i.Datei)
    $wert = '"javaArgsOverride": ' + ($neuArgs | ConvertTo-Json)
    $rx = [regex]'"javaArgsOverride"\s*:\s*(null|"(?:[^"\\]|\\.)*")'
    if ($rx.IsMatch($text)) { $text = $rx.Replace($text, $wert.Replace('$', '$$'), 1) }
    else { $text = $text -replace '^\s*\{', ('{' + "`r`n  " + $wert.Replace('$', '$$') + ',') }
    Copy-Item -LiteralPath $i.Datei -Destination ($i.Datei + '.bak') -Force
    [IO.File]::WriteAllText($i.Datei, $text, (New-Object Text.UTF8Encoding($bom)))
    try { $null = [IO.File]::ReadAllText($i.Datei) | ConvertFrom-Json; Schreib ('  Java-Argument gesetzt: ' + $neuArgs) }
    catch { Copy-Item -LiteralPath ($i.Datei + '.bak') -Destination $i.Datei -Force; Schreib '  Java-Argument: Datei waere kaputt gegangen, alte Fassung wiederhergestellt' }
}

# ---- Dateien, die man selbst herunterladen muss: Fenster mit Links, Helfer uebernimmt aus Downloads ----
if ($manuell.Count -gt 0 -and -not $Test) {
    $hinweis = Join-Path $Ordner 'hinweis.json'
    $zuletzt = 0
    try { $zuletzt = [int64](Get-Content -LiteralPath $hinweis -Raw | ConvertFrom-Json).zeit } catch { }
    $jetzt = [int64]([DateTimeOffset]::Now.ToUnixTimeSeconds())
    if (-not $Auto -or ($jetzt - $zuletzt) -gt 6 * 3600) {
        @{ zeit = $jetzt } | ConvertTo-Json | Set-Content -LiteralPath $hinweis -Encoding UTF8
        Add-Type -AssemblyName System.Windows.Forms, System.Drawing
        $form = New-Object Windows.Forms.Form
        $form.Text = 'Crowns & Cannons: bitte herunterladen'
        $form.Width = 640; $form.Height = 180 + 34 * [Math]::Min($manuell.Count, 12); $form.StartPosition = 'CenterScreen'; $form.TopMost = $true
        $lbl = New-Object Windows.Forms.Label
        $lbl.Text = "Diese Dateien darf der Helfer nicht selbst laden. Klick auf den Link, lade die Datei herunter (in deinen Downloads-Ordner) und lass dieses Fenster offen: der Helfer legt sie automatisch an die richtige Stelle."
        $lbl.SetBounds(12, 10, 600, 50)
        $form.Controls.Add($lbl)
        $y = 64
        $zeilen = @{}
        foreach ($d in $manuell.Values) {
            $l = New-Object Windows.Forms.LinkLabel
            $l.Text = ($d.name + '  (' + $d.pfad.Split('/')[0] + ')')
            $l.Tag = $d.seite
            $l.SetBounds(16, $y, 590, 24)
            $l.Add_LinkClicked({ param($s, $e) if ($s.Tag) { Start-Process $s.Tag } })
            $form.Controls.Add($l)
            $zeilen[$d.sha512] = $l
            $y += 34
        }
        $timer = New-Object Windows.Forms.Timer
        $timer.Interval = 3000
        $timer.Add_Tick({
            foreach ($k in @($manuell.Keys)) {
                $d = $manuell[$k]
                $dl = AusDownloads $d
                if (-not $dl) { continue }
                foreach ($i in $instanzen) {
                    $ziel = Join-Path $i.Ordner ($d.pfad -replace '/', '\')
                    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ziel) | Out-Null
                    Copy-Item -LiteralPath $dl -Destination $ziel -Force
                }
                Remove-Item -LiteralPath $dl -Force -ErrorAction SilentlyContinue
                Schreib ('  aus Downloads uebernommen: ' + $d.pfad)
                $zeilen[$k].Text = ('erledigt: ' + $d.name); $zeilen[$k].LinkArea = New-Object Windows.Forms.LinkArea(0, 0)
                $manuell.Remove($k)
            }
            if ($manuell.Count -eq 0) { $timer.Stop(); $form.Close() }
        })
        $timer.Start()
        [void]$form.ShowDialog()
    }
}

try { $z = Get-Content -LiteralPath $Log -Encoding UTF8; if ($z.Count -gt 800) { $z | Select-Object -Last 400 | Set-Content -LiteralPath $Log -Encoding UTF8 } } catch { }
if (-not $Auto) { Write-Host ''; Write-Host 'Fertig. Protokoll:' $Log }
