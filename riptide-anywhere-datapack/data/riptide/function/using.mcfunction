advancement revoke @s only riptide:using
# real riptide has to be back on before release, otherwise letting go in the air throws the trident
function riptide:to_vanilla
execute unless entity @s[tag=riptide] run return 0
scoreboard players add @s riptide.stat 0
scoreboard players operation @s riptide.last = @s riptide.stat
scoreboard players add @s riptide.use 1
tag @s add riptide.using
