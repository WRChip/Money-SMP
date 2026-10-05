// E2E: whitelisted bot launches mid-air with riptide I..255; the launch must match
// vanilla's 1.5 + 0.75 per extra level and the level must survive the swap both ways
const mineflayer = require('mineflayer')
const fs = require('fs')

const bot = mineflayer.createBot({ host: 'localhost', port: 25599, username: 'rtbot', version: '1.21.11' })
const cmd = c => bot.chat('/' + c)
const wait = t => bot.waitForTicks(t)
const results = []

let msgs = []
let vels = []
let spun = false
bot.on('messagestr', m => msgs.push(m))
bot._client.on('entity_velocity', p => {
  if (bot.entity && p.entityId === bot.entity.id) vels.push(Math.hypot(p.velocity.x, p.velocity.y, p.velocity.z))
})
bot._client.on('entity_metadata', p => {
  if (bot.entity && p.entityId === bot.entity.id) for (const m of p.metadata) if (m.key === 8 && (m.value & 4)) spun = true
})

async function query(c) {
  msgs = []
  cmd(c)
  await wait(10)
  return msgs.join(' | ')
}

async function trial(level) {
  cmd('kill @e[type=trident]')
  cmd('clear rtbot')
  cmd(`give rtbot trident[enchantments={riptide:${level}}]`)
  cmd('tp rtbot 0.5 200 0.5 0 -30')
  cmd('effect give rtbot slow_falling 5 0 true')
  await wait(10)
  const held = await query('data get entity rtbot Inventory[0].components."minecraft:enchantments"')
  bot.setQuickBarSlot(0)
  await wait(2)
  vels = []
  spun = false
  bot.activateItem()
  await wait(15)
  bot.deactivateItem()
  await wait(10)
  const launchVel = Math.max(0, ...vels)
  const want = 1.5 + 0.75 * (level - 1)
  const thrown = (await query('execute if entity @e[type=trident]')).includes('passed')
  const r = {
    level, want, launchVel: +launchVel.toFixed(2), spun, thrown,
    keptLevel: held.includes(`"riptideanywhere:riptide": ${level}`),
    // one tick of air drag/gravity can land between push and send on a falling player
    ok: spun && !thrown && launchVel > want * 0.9 && launchVel < want * 1.05 && held.includes(`"riptideanywhere:riptide": ${level}`)
  }
  results.push(r)
  console.log(JSON.stringify(r))
}

bot.once('spawn', async () => {
  await wait(40)
  cmd('gamemode survival rtbot')
  cmd('effect give rtbot resistance infinite 255 true')
  cmd('tag rtbot add riptide')
  await wait(10)
  for (const lvl of [1, 2, 3, 4, 5, 10, 255]) await trial(lvl)

  cmd('tag rtbot remove riptide')
  cmd('clear rtbot')
  cmd('give rtbot trident[enchantments={riptide:10}]')
  cmd('tag rtbot add riptide')
  await wait(5)
  cmd('tag rtbot remove riptide')
  await wait(5)
  const back = await query('data get entity rtbot Inventory[0].components."minecraft:enchantments"')
  const r = { level: 10, check: 'swapped back to minecraft:riptide on un-whitelist', ok: back.includes('"minecraft:riptide": 10') }
  results.push(r)
  console.log(JSON.stringify(r))

  const pass = results.every(x => x.ok)
  fs.writeFileSync('riptide-levels-e2e-result.json', JSON.stringify({ pass, results }, null, 2))
  console.log(pass ? 'ALL PASS' : 'SOME FAILED')
  bot.quit()
  process.exit(pass ? 0 : 1)
})
bot.on('kicked', r => { console.log('kicked', JSON.stringify(r)); process.exit(1) })
bot.on('error', e => { console.log('error', e.message); process.exit(1) })
