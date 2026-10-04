// Smoke checks: word packs, learning words.
import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { PackStore, validatePack } from '../../src/packs'
import { auth, base, channelEvents, check, client, curatedDir, dataDir } from './harness'

export default async function packs() {
  // word packs
  const words = ['kruh', 'mleko', 'sir', 'jajce', 'juha', 'vilice', 'nož', 'žlica', 'krožnik'].map(sl => ({
    id: sl.normalize('NFD').replace(/[^a-z]/g, ''),
    sl,
    en: `${sl} (en)`,
  }))
  words[0] = { ...words[0], emoji: '🍞', gender: 'm', plural: 'kruhi', example_sl: 'Kruh je še topel.', example_en: 'The bread is still warm.' } as any
  const pack = { schema: 'lani.pack/v0', id: 'smoke-kitchen', title: 'V kuhinji · In the kitchen', emoji: '🍳', level: 'A1', giver: { name: 'Babica Micka', emoji: '👵' }, words }
  const curated = { ...pack, id: 'curated-greetings', title: 'Pozdravi · Greetings', giver: undefined, words: words.map(w => ({ ...w, id: `g-${w.id}` })) }
  writeFileSync(join(curatedDir, 'curated-greetings.json'), JSON.stringify(curated))
  writeFileSync(join(curatedDir, 'broken.json'), '{"schema":"lani.pack/v0","id":"broken"}')
  mkdirSync(join(dataDir, 'app/packs'), { recursive: true })
  writeFileSync(join(dataDir, 'app/packs/by-hand.json'), JSON.stringify({ ...pack, id: 'by-hand', title: 'Written by hand' }))

  const tooSmall = await client.callTool({ name: 'publish_pack', arguments: { pack: { ...pack, words: words.slice(0, 7) } } })
  check('pack with 7 words rejected', tooSmall.isError, tooSmall)
  const dupe = await client.callTool({ name: 'publish_pack', arguments: { pack: { ...pack, words: [...words.slice(0, 8), words[0]] } } })
  check('pack with duplicate word ids rejected', dupe.isError && JSON.stringify(dupe).includes('duplicate'), dupe)
  const clash = await client.callTool({ name: 'publish_pack', arguments: { pack: { ...pack, id: 'curated-greetings' } } })
  check('publish_pack refuses a curated id', clash.isError, clash)
  const pubPack = await client.callTool({ name: 'publish_pack', arguments: { pack, note: 'Kitchen words from Micka' } })
  check('publish_pack publishes', !pubPack.isError, pubPack)
  check('pack file written to <data>/app/packs', existsSync(join(dataDir, 'app/packs/smoke-kitchen.json')))
  await Bun.sleep(100)
  const packEvents = await (await fetch(`${base}/events/backlog?since=0`, { headers: auth })).json()
  check('pack_published event', packEvents.some((b: any) => b.event.type === 'pack_published' && b.event.id === 'smoke-kitchen'))

  const packList = await (await fetch(`${base}/packs`, { headers: auth })).json()
  const listed1 = packList.find((p: any) => p.id === 'smoke-kitchen')
  check('GET /packs lists curated, published and hand-written packs, skips broken', ['curated-greetings', 'smoke-kitchen', 'by-hand'].every(id => packList.some((p: any) => p.id === id)) && !packList.some((p: any) => p.id === 'broken'), packList.map((p: any) => p.id))
  check('GET /packs has source and progress', listed1?.source === 'tutor' && listed1.total === 9 && listed1.learned === 0 && !listed1.words, listed1)
  check('curated pack marked curated', packList.find((p: any) => p.id === 'curated-greetings')?.source === 'curated')
  const onePack = await (await fetch(`${base}/packs/smoke-kitchen`, { headers: auth })).json()
  check('GET /packs/:id serves the words', onePack.words?.length === 9 && onePack.words[0].gender === 'm' && Array.isArray(onePack.learned), onePack)
  check('GET /packs/:id 404s', (await fetch(`${base}/packs/nope`, { headers: auth })).status === 404)

  const learn = (id: string, results: object[]) =>
    fetch(`${base}/packs/${id}/learn`, { method: 'POST', headers: auth, body: JSON.stringify({ results, duration_minutes: 4 }) })
  const l1 = await learn('smoke-kitchen', [{ word_id: 'kruh', quality: 4 }, { word_id: 'mleko', quality: 3 }, { word_id: 'sir', quality: 2 }])
  const l1Body = await l1.json()
  check('POST /packs/:id/learn adds new words', l1.ok && l1Body.added?.length === 3 && l1Body.added[0] === 'vocab_smoke-kitchen_kruh', l1Body)
  const sr = JSON.parse(readFileSync(join(dataDir, 'spaced-repetition.json'), 'utf8')).items
  const kruh = sr['vocab_smoke-kitchen_kruh']
  check('learned word is a vocabulary SR item', kruh?.type === 'vocabulary' && kruh.content === 'kruh' && kruh.answer === 'kruh (en)' && kruh.category === 'smoke-kitchen' && kruh.difficulty === 'A1' && kruh.last_quality === 4, kruh)
  await Bun.sleep(100)
  const wl = channelEvents.find(n => n.params?.meta?.kind === 'words_learned')
  check('words_learned reaches Claude', wl && wl.params.content.includes('kruh') && wl.params.content.includes('Struggled with: sir'), wl)
  const l2 = await (await learn('smoke-kitchen', [{ word_id: 'kruh', quality: 5 }, { word_id: 'juha', quality: 4 }])).json()
  check('already learned words are skipped', l2.added?.length === 1 && l2.skipped?.[0] === 'kruh', l2)
  const learnOnce = () =>
    fetch(`${base}/packs/smoke-kitchen/learn`, {
      method: 'POST',
      headers: auth,
      body: JSON.stringify({ client_id: 'outbox-learn-0001', results: [{ word_id: 'vilice', quality: 4 }], duration_minutes: 2 }),
    })
  const lo1 = await (await learnOnce()).json()
  const lo2 = await (await learnOnce()).json()
  check('repeated client_id on /packs/:id/learn returns the original result', lo1.added?.[0] === 'vocab_smoke-kitchen_vilice' && JSON.stringify(lo1) === JSON.stringify(lo2), [lo1, lo2])
  check('unknown word id is a 400', (await learn('smoke-kitchen', [{ word_id: 'pizza', quality: 4 }])).status === 400)
  check('bad quality is a 400', (await learn('smoke-kitchen', [{ word_id: 'jajce', quality: 9 }])).status === 400)
  check('learning an unknown pack is a 404', (await learn('nope', [{ word_id: 'kruh', quality: 4 }])).status === 404)
  const after = (await (await fetch(`${base}/packs`, { headers: auth })).json()) as any[]
  check('progress counts learned words', after.find(p => p.id === 'smoke-kitchen')?.learned === 5)
  check('a word known from another pack counts as learned', after.find(p => p.id === 'by-hand')?.learned === 5, after.find(p => p.id === 'by-hand'))
  const lp = await client.callTool({ name: 'list_packs', arguments: {} })
  check('list_packs shows progress and words', JSON.stringify(lp).includes('smoke-kitchen') && JSON.stringify(lp).includes('krožnik'))

  await festivals(learn)
}

/**
 * The festival packs (companion/packs/praznik-*.json): one per festival of the Primorska culture pack's calendar
 * (companion/cultures/primorska/festivals.json names them; game/Calendar.kt reads its words from them), valid, served
 * with their festival, and learned through the same route a festival's run uses.
 */
async function festivals(learn: (id: string, results: object[]) => Promise<Response>) {
  const repo = resolve(import.meta.dir, '../../..')
  const calendar = JSON.parse(readFileSync(join(repo, 'cultures/primorska/festivals.json'), 'utf8')).festivals as { id: string; words: string }[]
  const ids = calendar.map(f => f.id)
  check('the culture pack has 11 festivals, each naming its word pack', ids.length === 11 && calendar.every(f => f.words === `praznik-${f.id.replaceAll('_', '-')}`), calendar)
  const files = readdirSync(join(repo, 'packs')).filter(f => f.endsWith('.json'))
  const invalid = files.filter(f => !validatePack(JSON.parse(readFileSync(join(repo, 'packs', f), 'utf8'))).ok)
  check('every curated pack in companion/packs validates', invalid.length === 0, invalid)
  const curated = new PackStore(join(repo, 'packs'), join(dataDir, 'unused-packs')).all()
  const feasts = curated.filter(p => p.festival)
  check('one festival pack per calendar festival, named praznik-<festival>', JSON.stringify(feasts.map(p => p.festival).sort()) === JSON.stringify([...ids].sort()) && feasts.every(p => p.id === `praznik-${p.festival!.replaceAll('_', '-')}`), feasts.map(p => [p.id, p.festival]))
  check('festival packs: 8 words or more (a run takes 6), each with an example', feasts.every(p => p.words.length >= 8 && p.words.every(w => w.example_sl && w.example_en)), feasts.map(p => [p.id, p.words.length]))
  check('festival packs: nouns have a gender, a level A1-A2, a giver', feasts.every(p => ['A1', 'A2'].includes(p.level) && p.giver && p.words.filter(w => w.plural).every(w => w.gender)), feasts.map(p => p.id))

  // served like any curated pack, with its festival; a festival's run learns its words through /packs/:id/learn
  const martinovo = curated.find(p => p.id === 'praznik-martinovo')!
  const { source: _, published_at: __, ...file } = martinovo
  writeFileSync(join(curatedDir, 'praznik-martinovo.json'), JSON.stringify(file))
  const listed = ((await (await fetch(`${base}/packs`, { headers: auth })).json()) as any[]).find(p => p.id === 'praznik-martinovo')
  check('GET /packs serves a festival pack with its festival', listed?.festival === 'martinovo' && listed.source === 'curated' && listed.total === martinovo.words.length && listed.learned === 0, listed)
  const one = await (await fetch(`${base}/packs/praznik-martinovo`, { headers: auth })).json()
  check('GET /packs/:id serves the festival and its words', one.festival === 'martinovo' && one.words.some((w: any) => w.sl === 'gos'), one)
  // the smoke pack above taught "kruh" … nothing of St Martin's: the run's answers add its words; a right one 4, a wrong one 2
  const run = await (await learn('praznik-martinovo', [{ word_id: 'gos', quality: 4 }, { word_id: 'klet', quality: 2 }, { word_id: 'sod', quality: 4 }])).json()
  check('a festival run adds its words to spaced repetition', JSON.stringify(run.added) === JSON.stringify(['vocab_praznik-martinovo_gos', 'vocab_praznik-martinovo_klet', 'vocab_praznik-martinovo_sod']), run)
  const again = await (await learn('praznik-martinovo', [{ word_id: 'gos', quality: 5 }, { word_id: 'krst', quality: 4 }])).json()
  check('a second run (a grace day, another try) skips the words already known', again.skipped?.[0] === 'gos' && again.added?.[0] === 'vocab_praznik-martinovo_krst', again)
}
