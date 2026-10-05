// E2E: real server + mineflayer client. mineflayer doesn't simulate vanilla's client-side riptide push,
// so "ours" = a self velocity packet from the datapack, "vanilla" = trident took durability without one.
const mineflayer = require('mineflayer')
const fs = require('fs')

const bot = mineflayer.createBot({ host: 'localhost', port: 25599, username: 'rtbot', version: '1.21.11' })
const cmd = c => bot.chat('/' + c)
const wait = t => bot.waitForTicks(t)
const results = []

let msgs = []
let vels = []
let spun = false
let hop = 0
bot._client.on('entity_metadata', p => { if (bot.entity && p.entityId === bot.entity.id) for (const m of p.metadata) if (m.key === 8 && (m.value & 4)) spun = true })
bot._client.on('position', p => { if (p.flags && p.flags.y && p.y > 0.5) hop = p.y })
bot.on('messagestr', m => msgs.push(m))
bot._client.on('entity_velocity', p => {
  if (!bot.entity || p.entityId !== bot.entity.id) return
  const v = p.velocity
  vels.push(Math.hypot(v.x, v.y, v.z))
  bot.entity.velocity.set(v.x, v.y, v.z)
})

async function query(c) {
  msgs = []
  cmd(c)
  await wait(10)
  return msgs.join(' | ')
}

async function trial(label, { fluid, midair }, expect) {
  cmd('fill -3 99 -3 3 104 3 air')
  if (!midair) cmd('fill -1 99 -1 1 99 1 glass')
  cmd('kill @e[type=trident]')
  cmd('clear rtbot')
  cmd('give rtbot trident[enchantments={riptide:3}]')
  if (fluid) cmd(`fill -1 100 -1 1 101 1 ${fluid}`)
  await wait(10)
  cmd(midair ? 'tp rtbot 0.5 140 0.5 0 -30' : 'tp rtbot 0.5 100 0.5 0 -30')
  cmd(midair ? 'effect give rtbot slow_falling 5 0 true' : 'effect clear rtbot slow_falling')
  await wait(midair ? 5 : 30)
  bot.setQuickBarSlot(0)
  await wait(2)
  vels = []
  spun = false
  hop = 0
  bot.activateItem()
  await wait(15)
  const p0 = bot.entity.position.clone()
  bot.deactivateItem()
  let dist = 0
  for (let i = 0; i < 15; i++) {
    await wait(1)
    dist = Math.max(dist, bot.entity.position.distanceTo(p0))
  }
  const launchVel = Math.max(0, ...vels.filter(v => v > 0.5))
  const thrown = (await query('execute if entity @e[type=trident]')).includes('passed')
  const item = await query('data get entity rtbot Inventory[0].components')
  const damaged = item.includes('"minecraft:damage": 1')
  const ench = (item.match(/enchantments.: {([^}]*)}/) || [])[1]
  const got = launchVel > 0 ? 'ours' : damaged ? 'vanilla' : 'none'
  const r = { label, expect, got, spun, hop: +hop.toFixed(2), launchVel: +launchVel.toFixed(2), dist: +dist.toFixed(2), thrown, ench, ok: got === expect && !thrown && spun === (got !== 'none') }
  results.push(r)
  console.log(JSON.stringify(r))
}

bot.once('spawn', async () => {
  await wait(40)
  cmd('weather clear 100000')
  cmd('gamemode survival rtbot')
  cmd('effect give rtbot resistance infinite 255 true')
  cmd('effect give rtbot fire_resistance infinite 0 true')
  cmd('tag rtbot remove riptide')
  await wait(10)

  await trial('untagged, standing in air', {}, 'none')
  await trial('untagged, water (vanilla riptide)', { fluid: 'water' }, 'vanilla')
  cmd('tag rtbot add riptide')
  await trial('whitelisted, standing in air', {}, 'ours')
  await trial('whitelisted, falling mid-air', { midair: true }, 'ours')
  await trial('whitelisted, lava', { fluid: 'lava' }, 'ours')
  await trial('whitelisted, water', { fluid: 'water' }, 'ours')
  cmd('tag rtbot remove riptide')
  await trial('removed from whitelist, air', {}, 'none')

  cmd('fill -3 99 -3 3 104 3 air')
  const pass = results.every(r => r.ok)
  fs.writeFileSync('riptide-standalone-e2e-result.json', JSON.stringify({ pass, results }, null, 2))
  console.log(pass ? 'ALL PASS' : 'SOME FAILED')
  bot.quit()
  process.exit(pass ? 0 : 1)
})
bot.on('kicked', r => { console.log('kicked', r); process.exit(1) })
bot.on('error', e => { console.log('error', e.message); process.exit(1) })
