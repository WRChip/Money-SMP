<#
.SYNOPSIS
    Builds (and optionally sends) the /moneysmp commands to set up the given
    tier/team roster, with each team's F-tier player as leader holding the
    listed "Remaining" balance and everyone else on the team zeroed.

.NOTES
    NOTE: the roster used "Yellow", but the mod's six team names are Red,
    Blue, Purple, Green, White, Gold -- Yellow was renamed to Gold. That block
    is mapped to Gold below; fix $Roster if that's wrong.

    Commands are written without a leading "/", matching server console and
    RCON syntax. Add a leading "/" yourself if pasting into player chat.

.PARAMETER Apply
    Actually send the commands over RCON. Without this switch the script
    only prints the commands and writes them to -OutFile; nothing touches
    a live server.

.PARAMETER RconHost
.PARAMETER RconPort
.PARAMETER RconPassword
    Connection info for -Apply. Must match server.properties
    (enable-rcon=true, rcon.port, rcon.password).

.PARAMETER OutFile
    Where the generated command list is written. Defaults to
    moneysmp-setup-commands.txt next to this script.

.EXAMPLE
    .\Setup-MoneySmpRoster.ps1
    Dry run: prints the commands and writes them to the default out file.

.EXAMPLE
    .\Setup-MoneySmpRoster.ps1 -Apply -RconHost localhost -RconPort 25575 -RconPassword "secret"
    Sends every command live over RCON.
#>
param(
    [switch]$Apply,
    [string]$RconHost = 'localhost',
    [int]$RconPort = 25575,
    [string]$RconPassword,
    [string]$OutFile = (Join-Path $PSScriptRoot 'moneysmp-setup-commands.txt')
)

# tier order matters only for readability; team/tier assignment order does not.
# Remaining is set only on the F-tier player, who becomes the team's leader.
$Roster = @(
    @{ Team = 'Purple'; Players = @(
        @{ Tier = 'F'; Name = 'motoneco';  Remaining = 660 }
        @{ Tier = 'C'; Name = 'commkel' }
        @{ Tier = 'B'; Name = 'enzoxz_' }
        @{ Tier = 'A'; Name = 'Mouqtain' }
        @{ Tier = 'S'; Name = '2put' }
    )}
    @{ Team = 'White'; Players = @(
        @{ Tier = 'F'; Name = 'kagezuki'; Remaining = 300 }
        @{ Tier = 'C'; Name = 'kschlam' }
        @{ Tier = 'B'; Name = 'blizzy09' }
        @{ Tier = 'A'; Name = 'djrx_' }
        @{ Tier = 'S'; Name = 'Paikia' }
    )}
    @{ Team = 'Red'; Players = @(
        @{ Tier = 'F'; Name = 'Sentimc'; Remaining = 610 }
        @{ Tier = 'C'; Name = 'endable' }
        @{ Tier = 'B'; Name = 'rxnzo' }
        @{ Tier = 'A'; Name = 'aethnithalt' }
        @{ Tier = 'S'; Name = 'c1rclee' }
    )}
    @{ Team = 'Green'; Players = @(
        @{ Tier = 'F'; Name = 'theinfernix'; Remaining = 90 }
        @{ Tier = 'C'; Name = 'lyumz' }
        @{ Tier = 'B'; Name = 'cyrillll' }
        @{ Tier = 'A'; Name = 'celicocat' }
        @{ Tier = 'S'; Name = 'kur0ba7' }
    )}
    @{ Team = 'Blue'; Players = @(
        @{ Tier = 'F'; Name = 'crischutu07'; Remaining = 410 }
        @{ Tier = 'C'; Name = 'icedmellow' }
        @{ Tier = 'B'; Name = 'centrifugal28' }
        @{ Tier = 'A'; Name = 'Shadow1506' }
        @{ Tier = 'S'; Name = 'Tubsyyy' }
    )}
    @{ Team = 'Gold'; Players = @(   # was "Yellow" in the source roster
        @{ Tier = 'F'; Name = 's1mzx'; Remaining = 25 }
        @{ Tier = 'C'; Name = '5tr__' }
        @{ Tier = 'B'; Name = 'Dashingflash' }
        @{ Tier = 'A'; Name = 'nobecc' }
        @{ Tier = 'S'; Name = 'hirowho' }
    )}
)

function Build-Commands {
    $cmds = New-Object System.Collections.Generic.List[string]
    foreach ($team in $Roster) {
        $cmds.Add("moneysmp team enable $($team.Team)")
        foreach ($p in $team.Players) {
            $cmds.Add("moneysmp tier set $($p.Name) $($p.Tier)")
        }
        foreach ($p in $team.Players) {
            $cmds.Add("moneysmp team set $($p.Name) $($team.Team)")
        }
        $leader = $team.Players | Where-Object { $_.Tier -eq 'F' } | Select-Object -First 1
        $cmds.Add("moneysmp team leader $($leader.Name)")
        foreach ($p in $team.Players) {
            $amount = if ($p.ContainsKey('Remaining')) { $p.Remaining } else { 0 }
            $cmds.Add("moneysmp set $($p.Name) $amount")
        }
    }
    return $cmds
}

# --- minimal Source RCON client (Minecraft's built-in RCON uses this protocol) ---
function Send-RconCommand {
    param(
        [System.Net.Sockets.NetworkStream]$Stream,
        [int]$RequestId,
        [int]$Type,
        [string]$Body
    )
    $payload = [System.Text.Encoding]::UTF8.GetBytes($Body)
    $length = 4 + 4 + $payload.Length + 2
    $buf = New-Object byte[] (4 + $length)
    [BitConverter]::GetBytes([int]$length).CopyTo($buf, 0)
    [BitConverter]::GetBytes([int]$RequestId).CopyTo($buf, 4)
    [BitConverter]::GetBytes([int]$Type).CopyTo($buf, 8)
    $payload.CopyTo($buf, 12)
    $Stream.Write($buf, 0, $buf.Length)
    $Stream.Flush()

    $header = New-Object byte[] 4
    $null = $Stream.Read($header, 0, 4)
    $respLen = [BitConverter]::ToInt32($header, 0)
    $body = New-Object byte[] $respLen
    $read = 0
    while ($read -lt $respLen) {
        $n = $Stream.Read($body, $read, $respLen - $read)
        if ($n -le 0) { break }
        $read += $n
    }
    $respId = [BitConverter]::ToInt32($body, 0)
    $respText = [System.Text.Encoding]::UTF8.GetString($body, 8, [Math]::Max(0, $respLen - 10))
    return [PSCustomObject]@{ RequestId = $respId; Text = $respText }
}

$commands = Build-Commands

if (-not $Apply) {
    Write-Host "Dry run - nothing sent. $($commands.Count) commands would run:" -ForegroundColor Yellow
    $commands | ForEach-Object { Write-Host "  $_" }
    $commands | Set-Content -Path $OutFile -Encoding utf8
    Write-Host "`nWritten to $OutFile. Re-run with -Apply -RconHost -RconPort -RconPassword to send them live." -ForegroundColor Yellow
    return
}

if (-not $RconPassword) {
    Write-Error "-Apply requires -RconPassword (and enable-rcon=true in server.properties)."
    return
}

$client = New-Object System.Net.Sockets.TcpClient
try {
    $client.Connect($RconHost, $RconPort)
    $stream = $client.GetStream()

    $auth = Send-RconCommand -Stream $stream -RequestId 1 -Type 3 -Body $RconPassword
    if ($auth.RequestId -eq -1) {
        Write-Error "RCON authentication failed."
        return
    }

    $id = 2
    foreach ($cmd in $commands) {
        $resp = Send-RconCommand -Stream $stream -RequestId $id -Type 2 -Body $cmd
        Write-Host "> $cmd" -ForegroundColor Cyan
        if ($resp.Text) { Write-Host "  $($resp.Text)" }
        $id++
    }
}
finally {
    $client.Close()
}

$commands | Set-Content -Path $OutFile -Encoding utf8
Write-Host "`nDone. Command list also written to $OutFile." -ForegroundColor Green
