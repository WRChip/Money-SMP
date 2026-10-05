# vanilla hops you up 1.2 blocks when launching off the ground
execute if predicate {condition:"entity_properties",entity:"this",predicate:{flags:{is_on_ground:true}}} if block ~ ~2 ~ #minecraft:replaceable if block ~ ~3 ~ #minecraft:replaceable run tp @s ~ ~1.2 ~
function riptide:to_dummy
scoreboard players set @s riptide.launch 1
