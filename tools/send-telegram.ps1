param(
    [Parameter(Mandatory = $true)]
    [string]$Text,

    [Parameter(Mandatory = $false)]
    [string]$File
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFile = Join-Path (Split-Path -Parent $scriptDir) "telegram.local.env"
if (-not (Test-Path $envFile)) {
    $envFile = "C:\mr-vpn-app\telegram.local.env"
}

if (-not (Test-Path $envFile)) {
    Write-Error "Config file telegram.local.env not found."
    exit 1
}

$botToken = ""
$chatId = ""

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -match "^BOT_TOKEN=(.+)$") {
        $botToken = $matches[1].Trim()
    } elseif ($line -match "^CHAT_ID=(.+)$") {
        $chatId = $matches[1].Trim()
    }
}

if ([string]::IsNullOrWhiteSpace($botToken) -or [string]::IsNullOrWhiteSpace($chatId)) {
    Write-Error "BOT_TOKEN or CHAT_ID is missing in telegram.local.env."
    exit 1
}

# Ensure message length <= 4096
$maxLen = 4000
$sendText = $Text
if ($sendText.Length -gt $maxLen) {
    $sendText = $sendText.Substring(0, $maxLen) + "`n…(full report in the project folder)"
}

$fileToSend = $null
$fileSkippedNotice = ""

if (-not [string]::IsNullOrWhiteSpace($File)) {
    if (Test-Path $File) {
        $item = Get-Item $File
        $sizeMb = [math]::Round($item.Length / 1MB, 2)
        if ($sizeMb -le 49.0) {
            $fileToSend = $item
        } else {
            $fileSkippedNotice = "`n⚠️ File skipped (size: ${sizeMb} MB > 49 MB Telegram bot limit): $($item.Name)"
        }
    } else {
        $fileSkippedNotice = "`n⚠️ Attached file not found: $File"
    }
}

if ($fileSkippedNotice -ne "") {
    $sendText += $fileSkippedNotice
}

# 1. Send text message
$textUrl = "https://api.telegram.org/bot$botToken/sendMessage"
try {
    $body = @{
        chat_id = $chatId
        text = $sendText
        disable_web_page_preview = $true
    } | ConvertTo-Json

    $msgRes = Invoke-RestMethod -Uri $textUrl -Method Post -Body $body -ContentType "application/json; charset=utf-8"
    if ($msgRes.ok) {
        $msgId = $msgRes.result.message_id
        Write-Host "Telegram report sent, message_id: $msgId"
    } else {
        Write-Host "Telegram API Error: $($msgRes.description)"
    }
} catch {
    $errDesc = $_.Exception.Message
    if ($_.Exception.Response -ne $null) {
        try {
            $stream = $_.Exception.Response.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($stream)
            $raw = $reader.ReadToEnd() | ConvertFrom-Json
            if ($raw.description) { $errDesc = $raw.description }
        } catch {}
    }
    Write-Host "Telegram API Error: $errDesc"
}

# 2. Send document if requested and <= 49MB
if ($fileToSend -ne $null) {
    $docUrl = "https://api.telegram.org/bot$botToken/sendDocument"
    $caption = "$($fileToSend.Name) ($sizeMb MB)"
    
    # Use curl.exe for reliable multipart/form-data upload
    $curlOut = & curl.exe -s -F "chat_id=$chatId" -F "caption=$caption" -F "document=@$($fileToSend.FullName)" $docUrl
    try {
        $docRes = $curlOut | ConvertFrom-Json
        if ($docRes.ok) {
            $docId = $docRes.result.message_id
            Write-Host "Telegram file sent, message_id: $docId"
        } else {
            Write-Host "Telegram API Error (file upload): $($docRes.description)"
        }
    } catch {
        Write-Host "Telegram API Error (file upload): Failed to parse response"
    }
}
