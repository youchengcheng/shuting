<#
  小哈书 —— Canal 位点自检脚本（Canal 启动前自动执行）

  背景：
    Canal 会把「消费到哪条 binlog」记录在 conf\<destination>\meta.dat 里。
    MySQL 的 binlog 默认只保留 30 天，一旦 meta.dat 中的位点指向的文件被清理，
    Canal 会持续报 1236（Could not find first log file name in binary log index file）
    并静默停止同步 —— 表现为「新注册的用户、新发布的笔记不再进入 Elasticsearch」。

  本脚本的处理逻辑：
    * 位点文件仍然存在 -> 不做任何修改，正常续传（不丢数据）
    * 位点文件已被清理 -> 自动回退到现存最早的 binlog 重新消费
                         （事件处理是幂等的：会按 MySQL 当前数据覆盖 ES 文档，
                           因此可以补齐漏掉的数据，不会写坏 ES）

  用法：
    powershell -NoProfile -ExecutionPolicy Bypass -File canal-position-guard.ps1
    powershell -NoProfile -ExecutionPolicy Bypass -File canal-position-guard.ps1 -Force   # 即使 Canal 在运行也强制检查
#>
[CmdletBinding()]
param(
    [string]$CanalHome = '',
    [string]$Destination = 'example',
    [string]$BinlogIndexPath = 'E:\docker\mysql8\data\binlog.index',
    [switch]$Force
)

function Write-Log([string]$Message) {
    Write-Host ("[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $Message)
}

try {
    if (-not $CanalHome) {
        $scriptDir = $PSScriptRoot
        if (-not $scriptDir) {
            $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
        }
        $CanalHome = Split-Path -Parent $scriptDir
    }

    $confDir = Join-Path (Join-Path $CanalHome 'conf') $Destination
    $instanceFile = Join-Path $confDir 'instance.properties'
    $metaFile = Join-Path $confDir 'meta.dat'

    if (-not (Test-Path -LiteralPath $instanceFile)) {
        Write-Log "找不到配置文件 $instanceFile，跳过位点自检。"
        exit 0
    }

    if (-not $Force) {
        $listening = Get-NetTCPConnection -State Listen -LocalPort 11111 -ErrorAction SilentlyContinue
        if ($listening) {
            Write-Log "检测到 Canal 已在运行（11111 端口被占用），跳过位点自检。"
            exit 0
        }
    }

    # 1. 读取 MySQL 现存最早的 binlog 文件
    if (-not (Test-Path -LiteralPath $BinlogIndexPath)) {
        Write-Log "读取不到 MySQL binlog 索引（$BinlogIndexPath），跳过位点自检。"
        exit 0
    }
    $availableBinlogs = @(Get-Content -LiteralPath $BinlogIndexPath |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ } |
        ForEach-Object { $_ -replace '^\./', '' } |
        Where-Object { $_ -match '^binlog\.\d+$' })

    if ($availableBinlogs.Count -eq 0) {
        Write-Log "binlog 索引内容为空，跳过位点自检。"
        exit 0
    }
    $oldestBinlog = $availableBinlogs[0]

    # 2. 找出 Canal 实际会使用的起始位点：优先 meta.dat 中的记录，其次 instance.properties
    $positionJournal = $null
    if (Test-Path -LiteralPath $metaFile) {
        $metaText = Get-Content -LiteralPath $metaFile -Raw
        $match = [regex]::Match($metaText, '"journalName"\s*:\s*"([^"]*)"')
        if ($match.Success -and $match.Groups[1].Value) {
            $positionJournal = $match.Groups[1].Value
        }
    }

    $instanceText = [System.IO.File]::ReadAllText($instanceFile)
    if (-not $positionJournal) {
        $match = [regex]::Match($instanceText, '(?m)^\s*canal\.instance\.master\.journal\.name=([^\r\n]*)')
        if ($match.Success -and $match.Groups[1].Value.Trim()) {
            $positionJournal = $match.Groups[1].Value.Trim()
        }
    }

    # 3. 位点仍然有效，直接返回
    if ($positionJournal -and ($availableBinlogs -contains $positionJournal)) {
        Write-Log "位点 $positionJournal 仍然有效，Canal 将从该位置继续同步。"
        exit 0
    }

    # 4. 位点已失效，回退到现存最早的 binlog
    if (-not $positionJournal) {
        Write-Log "未找到任何位点记录，将起始位点指向现存最早的 binlog。"
    } else {
        Write-Log "警告：位点指向的 $positionJournal 已被 MySQL 清理！"
        Write-Log "自动回退到现存最早的 binlog（$oldestBinlog），重新消费以补齐缺失数据。"
    }

    if ($instanceText -match '(?m)^\s*canal\.instance\.master\.journal\.name=') {
        $newText = [regex]::Replace($instanceText, '(?m)^\s*canal\.instance\.master\.journal\.name=[^\r\n]*', "canal.instance.master.journal.name=$oldestBinlog")
    } else {
        $newText = $instanceText + "`ncanal.instance.master.journal.name=$oldestBinlog"
    }
    if ($newText -match '(?m)^\s*canal\.instance\.master\.position=') {
        $newText = [regex]::Replace($newText, '(?m)^\s*canal\.instance\.master\.position=[^\r\n]*', 'canal.instance.master.position=4')
    } else {
        $newText = $newText + "`ncanal.instance.master.position=4"
    }
    [System.IO.File]::WriteAllText($instanceFile, $newText, (New-Object System.Text.UTF8Encoding($false)))
    Write-Log "已更新起始位点配置：$instanceFile"

    if (Test-Path -LiteralPath $metaFile) {
        $backupFile = "$metaFile.bak-$(Get-Date -Format 'yyyyMMddHHmmss')"
        Move-Item -LiteralPath $metaFile -Destination $backupFile -Force
        Write-Log "旧位点文件已备份并移除：$backupFile"
    }
    exit 0
}
catch {
    Write-Log "位点自检出现异常：$($_.Exception.Message)"
    exit 0
}
