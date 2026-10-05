<#
.SYNOPSIS
    Types /whitelist add <name> into Minecraft's chat for every roster
    username, the same way Type-MoneySmpRoster.ps1 types the moneysmp
    commands: open chat with "t", type, enter, repeat.

.PARAMETER WhatIf
    Print what would be typed instead of actually sending any keys.

.NOTES
    /whitelist add requires operator permission level 3+ (or console).
    This sends keystrokes to whatever window has focus -- don't touch
    the keyboard/mouse while it's running.
#>
param(
    [string]$WindowTitle = 'Minecraft',
    [int]$StartDelaySeconds = 5,
    [int]$KeyDelayMs = 100,
    [int]$CommandDelayMs = 250,
    [switch]$WhatIf
)

$Usernames = @(
    "motoneco", "commkel", "enzoxz_", "Mouqtain", "2put",
    "kagezuki", "kschlam", "blizzy09", "djrx_", "Paikia",
    "Sentimc", "endable", "rxnzo", "aethnithalt", "c1rclee",
    "theinfernix", "lyumz", "cyrillll", "celicocat", "kur0ba7",
    "crischutu07", "icedmellow", "centrifugal28", "Shadow1506", "Tubsyyy",
    "s1mzx", "5tr__", "Dashingflash", "nobecc", "hirowho"
)

$commands = $Usernames | ForEach-Object { "whitelist add $_" }

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

Write-Host "Done - whitelisted $($commands.Count) usernames." -ForegroundColor Green
