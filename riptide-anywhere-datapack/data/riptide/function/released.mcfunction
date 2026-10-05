# riptide.stat only goes up when vanilla launched us (water/rain), so skip those
execute if score @s riptide.use matches 10.. if score @s riptide.stat = @s riptide.last if items entity @s weapon.* minecraft:trident[minecraft:enchantments~[{enchantments:"minecraft:riptide"}]] run function riptide:launch
scoreboard players set @s riptide.use 0
