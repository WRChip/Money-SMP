<#
.SYNOPSIS
    Types the MoneySMP roster-setup commands straight into Minecraft's chat,
    the same way you'd type them by hand: open chat with "t", type the
    command, press enter, repeat.

.PARAMETER CommandFile
    Plain text file with one command per line, no leading "/" (that's what
    Setup-MoneySmpRoster.ps1 writes). Blank lines and lines starting with #
    are skipped.

.PARAMETER WhatIf
    Print what would be typed instead of actually sending any keys. Use this
    first to check the command list before running it for real.

.NOTES
    This sends keystrokes to whatever window has focus when each SendKeys
    call fires, same as any SendKeys script -- it does not check that
    Minecraft is actually the active window beyond the AppActivate attempt
    below. Don't touch the keyboard/mouse while it's running.
#>
param(
    [string]$CommandFile = (Join-Path $PSScriptRoot 'moneysmp-setup-commands.txt'),
    [string]$WindowTitle = 'Minecraft',
    [int]$StartDelaySeconds = 5,
    [int]$KeyDelayMs = 100,
    [int]$CommandDelayMs = 250,
    [switch]$WhatIf
)

if (-not (Test-Path $CommandFile)) {
    Write-Error "Command file not found: $CommandFile"
    return
}

$commands = Get-Content $CommandFile | Where-Object { $_.Trim() -ne '' -and $_.Trim() -notlike '#*' }
if ($commands.Count -eq 0) {
    Write-Error "No commands found in $CommandFile"
    return
}

# SendKeys treats + ^ % ~ ( ) { } [ ] specially; wrap them so they're sent literally
function ConvertTo-SendKeysSafe {
    param([string]$Text)
    -join ($Text.ToCharArray() | ForEach-Object {
        if ('+^%~(){}[]'.Contains($_)) { "{$_}" } else { $_ }
    })
}

if ($WhatIf) {
    Write-Host "WhatIf - would type $($commands.Count) commands into '$WindowTitle':" -ForegroundColor Yellow
    $commands | ForEach-Object { Write-Host "  /$_" }
    return
}

$wshell = New-Object -ComObject WScript.Shell

Write-Host "Switch to Minecraft! Starting in $StartDelaySeconds seconds..." -ForegroundColor Yellow
Start-Sleep -Seconds 1
if (-not $wshell.AppActivate($WindowTitle)) {
    Write-Host "Couldn't find a window titled '$WindowTitle' -- make sure it's already open and focus it yourself." -ForegroundColor Yellow
}
Start-Sleep -Seconds ($StartDelaySeconds - 1)

$i = 0
foreach ($cmd in $commands) {
    $i++
    Write-Host "[$i/$($commands.Count)] /$cmd"

    $wshell.SendKeys('t')
    Start-Sleep -Milliseconds $KeyDelayMs

    $wshell.SendKeys((ConvertTo-SendKeysSafe "/$cmd"))
    Start-Sleep -Milliseconds $KeyDelayMs

    $wshell.SendKeys('{ENTER}')
    Start-Sleep -Milliseconds $CommandDelayMs
}

Write-Host "Done - sent $($commands.Count) commands." -ForegroundColor Green
