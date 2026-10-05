scoreboard players reset @a[scores={riptide.launch=1..}] riptide.launch
execute as @a[scores={riptide.use=1..},tag=!riptide.using] at @s run function riptide:released
tag @a remove riptide.using
execute as @a[tag=riptide] unless score @s riptide.use matches 1.. run function riptide:to_dummy
