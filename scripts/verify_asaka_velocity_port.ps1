param(
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"

function Normalize-Text([string]$Text) {
    return ($Text -replace "`r`n", "`n").TrimEnd()
}

function Assert-PortEqual {
    param(
        [string]$Name,
        [string]$SourcePath,
        [string]$TargetPath,
        [scriptblock]$Adapt
    )

    $source = Get-Content -LiteralPath $SourcePath -Raw -Encoding UTF8
    $target = Get-Content -LiteralPath $TargetPath -Raw -Encoding UTF8
    $expected = & $Adapt $source

    if ((Normalize-Text $expected) -cne (Normalize-Text $target)) {
        Write-Error "$Name 与 Asaka 权威源码不一致：$TargetPath"
    }

    $sourceHash = (Get-FileHash -LiteralPath $SourcePath -Algorithm SHA256).Hash
    $targetHash = (Get-FileHash -LiteralPath $TargetPath -Algorithm SHA256).Hash
    Write-Output "PASS $Name source=$sourceHash target=$targetHash"
}

$sourceRoot = Join-Path $RepositoryRoot "SkidProjects/Asaka-26.2x/common/src/main/java/asaka/lol/client"
$targetRoot = Join-Path $RepositoryRoot "common/src/main/java/tech/hakuri/graven"

Assert-PortEqual "Velocity" `
    (Join-Path $sourceRoot "modules/impl/combat/Velocity.java") `
    (Join-Path $targetRoot "modules/impl/combat/Velocity.java") `
    {
        param($text)
        $text = $text.Replace("package asaka.lol.client.modules.impl.combat;", "package tech.hakuri.graven.modules.impl.combat;")
        $text = $text.Replace("asaka.lol.client.events.", "tech.hakuri.graven.events.")
        $text = $text.Replace("asaka.lol.client.modules.", "tech.hakuri.graven.modules.")
        $text = $text.Replace("asaka.lol.client.settings.", "tech.hakuri.graven.settings.")
        $text = $text.Replace("asaka.lol.client.utils.combat.FightManager", "tech.hakuri.graven.utils.asaka.grimvelocity.FightManager")
        $text = $text.Replace("asaka.lol.client.utils.player.ChatUtils", "tech.hakuri.graven.utils.asaka.grimvelocity.ChatUtils")
        $text = $text.Replace("asaka.lol.client.utils.player.EnchantmentUtils", "tech.hakuri.graven.utils.asaka.grimvelocity.EnchantmentUtils")
        $text = $text.Replace("asaka.lol.client.utils.player.PlayerUtils", "tech.hakuri.graven.utils.asaka.grimvelocity.PlayerUtils")
        $text.Replace("asaka.lol.client.utils.timer.TimerUtils", "tech.hakuri.graven.utils.asaka.grimvelocity.TimerUtils")
    }

$dependencies = @(
    @{ Name = "FightManager"; Source = "utils/combat/FightManager.java"; Target = "utils/asaka/grimvelocity/FightManager.java" },
    @{ Name = "PlayerUtils"; Source = "utils/player/PlayerUtils.java"; Target = "utils/asaka/grimvelocity/PlayerUtils.java" },
    @{ Name = "EnchantmentUtils"; Source = "utils/player/EnchantmentUtils.java"; Target = "utils/asaka/grimvelocity/EnchantmentUtils.java" },
    @{ Name = "TimerUtils"; Source = "utils/timer/TimerUtils.java"; Target = "utils/asaka/grimvelocity/TimerUtils.java" }
)

foreach ($dependency in $dependencies) {
    Assert-PortEqual $dependency.Name `
        (Join-Path $sourceRoot $dependency.Source) `
        (Join-Path $targetRoot $dependency.Target) `
        {
            param($text)
            $text = $text.Replace("package asaka.lol.client.utils.combat;", "package tech.hakuri.graven.utils.asaka.grimvelocity;")
            $text = $text.Replace("package asaka.lol.client.utils.player;", "package tech.hakuri.graven.utils.asaka.grimvelocity;")
            $text = $text.Replace("package asaka.lol.client.utils.timer;", "package tech.hakuri.graven.utils.asaka.grimvelocity;")
            $text.Replace("asaka.lol.client.", "tech.hakuri.graven.")
        }
}

Assert-PortEqual "ChatUtils" `
    (Join-Path $sourceRoot "utils/player/ChatUtils.java") `
    (Join-Path $targetRoot "utils/asaka/grimvelocity/ChatUtils.java") `
    {
        param($text)
        $text = $text.Replace("package asaka.lol.client.utils.player;", "package tech.hakuri.graven.utils.asaka.grimvelocity;")
        $text = $text.Replace("asaka.lol.client.", "tech.hakuri.graven.")
        $text = $text.Replace("mc.gui.hud.getChat()", "mc.gui.getChat()")
        $text.Replace("asaka`$addClientSystemMessage", "graven`$addClientSystemMessage")
    }

Write-Output "PASS Asaka Velocity 严格移植校验完成"
