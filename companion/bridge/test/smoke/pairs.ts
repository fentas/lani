// Smoke checks: language pairs beyond Jan's (sl ← en) and the second learner's (it ← sl) (plan 2, §1). Bridges one after
// another, on the friuli section's port once that bridge is gone: a German speaker in the Slovene village (sl ← de), an
// English speaker in Friuli (it ← en), and an Italian speaker in an English village (en ← it; a fixture pack, primorska
// copied with "language": "en", until an English pack is there). Each tells the tutor the pair, speaks the target, looks
// words up in the target with meanings in the base (or English), and hears speech in the target. Then
// lani-profile add picks the village by the target language, and says so plainly when no pack speaks it.
import { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js'
import { cpSync, readFileSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { channelRules, initLearnerData, languageCode, learnerInstructions, portFree } from '../../src/learners'
import { bridgeEleven, bridgeWhisper, check, culturesDir, guideOf, tempDir } from './harness'

const repo = resolve(import.meta.dir, '../../../..')
const port = Number(process.env.LANI_SMOKE_FRIULI_PORT ?? 8798)
const base = `http://127.0.0.1:${port}`
const token = 'pairs-smoke-token'
const auth = { authorization: `Bearer ${token}`, 'content-type': 'application/json' }

/** A cultures directory with primorska (the default) and friuli as they are, and an English village made of primorska. */
function fixtureCultures(): string {
  const dir = tempDir('lani-smoke-cultures-')
  for (const c of ['primorska', 'friuli']) cpSync(join(culturesDir, c), join(dir, c), { recursive: true })
  cpSync(join(culturesDir, 'primorska'), join(dir, 'smoke-en'), { recursive: true })
  const m = JSON.parse(readFileSync(join(dir, 'smoke-en/culture.json'), 'utf8'))
  writeFileSync(join(dir, 'smoke-en/culture.json'), JSON.stringify({
    ...m, id: 'smoke-en', language: 'en', region: { en: 'Smoke Vale' }, name: { en: 'Smoke Vale' },
    tutor: { village: 'Smoke Vale', setting: "Smoke Vale, {learner}'s village", style: 'Dales style', voice: 'A native English speaker.' },
  }))
  return dir
}

type Pair = { id: string; name: string; target: string; base: string; culture: string; targetName: string; baseName: string }

async function withBridge(p: Pair, cultures: string, run: (client: Client) => Promise<void>) {
  for (let i = 0; i < 50 && !portFree(port); i++) await Bun.sleep(100) // the previous bridge lets go of the port
  const dataDir = tempDir(`lani-smoke-pair-${p.id}-`)
  initLearnerData(repo, dataDir, { name: p.name, target: p.target, base: p.base, culture: p.culture })
  const client = new Client({ name: `smoke-pair-${p.id}`, version: '0' })
  await client.connect(
    new StdioClientTransport({
      command: 'bun',
      args: [resolve(import.meta.dir, '../../src/index.ts')],
      env: {
        ...process.env, LANI_PROFILE: p.id, LANI_CULTURE: p.culture, LANI_DATA_DIR: dataDir, LANI_BRIDGE_PORT: String(port),
        LANI_BRIDGE_TOKEN: token, LANI_PACKS_DIR: join(repo, 'companion/packs'), LANI_CULTURES_DIR: cultures, LANI_RHYTHM: 'off',
        LANI_SCENES_DIR: join(repo, 'companion/scenes'), LANI_MODULES_DIR: join(repo, 'companion/modules'), LANI_SCENARIOS_DIR: join(repo, 'companion/scenarios'),
        LANI_VOICE_DESIGN: 'off', ELEVENLABS_API_KEY: 'test-key', ELEVENLABS_BASE_URL: bridgeEleven.url, LANI_GEPARD_URL: 'http://127.0.0.1:9',
        LANI_VOICE_QUEUE_DELAY_MS: '0', LANI_STT_URL: bridgeWhisper.url, LANI_LEXICON_URL: '',
      } as Record<string, string>,
      stderr: 'inherit',
    }),
  )
  await Bun.sleep(300)
  try {
    await run(client)
  } finally {
    await client.close()
  }
}

const get = async (path: string) => (await fetch(`${base}${path}`, { headers: auth })).json() as Promise<any>

/** What every pair's bridge must say and do: the pair in the rules and the guide, the village's language, speech in it. */
async function common(p: Pair, client: Client) {
  const tag = `${p.target} ← ${p.base}`
  const rules = client.getInstructions() ?? ''
  const guide = await guideOf(client)
  check(`pairs ${tag}: the rules fit and name the pair`, rules.length <= 2048 && rules.includes(`${p.name} learns ${p.targetName} (A1) from ${p.baseName}.`), rules.slice(0, 400))
  check(`pairs ${tag}: the guide applies the paragraphs to ${p.targetName}, explained in ${p.baseName}`, guide.includes(`apply them to ${p.name} and ${p.targetName},\nand explain in ${p.baseName}.`), guide.slice(0, 600))
  const culture = await get('/culture')
  check(`pairs ${tag}: the village is ${p.culture}, in ${p.targetName}`, culture.id === p.culture && culture.language === p.target, culture)
  const lex = await get('/lexicon/status')
  check(`pairs ${tag}: word lookups are in ${p.targetName}, meant in ${p.baseName}`, lex.language === p.target && lex.base === p.base, lex)
  const take = new Uint8Array([0, 0, 0, 0x18, ...new TextEncoder().encode('ftypM4A '), 0, 0, 0, 0, 7, 7, 7, 7])
  const heard = await fetch(`${base}/stt?prompt=x`, { method: 'POST', headers: { authorization: auth.authorization, 'content-type': 'audio/mp4' }, body: take })
  check(`pairs ${tag}: speech without a language is heard in ${p.targetName}`, heard.ok && bridgeWhisper.st.calls.at(-1)?.query.language === p.target, bridgeWhisper.st.calls.at(-1)?.query)
  return { rules, guide }
}

export default async function pairs() {
  const cultures = fixtureCultures()

  // --- sl ← de: a German speaker in the Slovene village -------------------------------------------------------
  const slDe: Pair = { id: 'lena', name: 'Lena', target: 'sl', base: 'de', culture: 'primorska', targetName: 'Slovene', baseName: 'German' }
  await withBridge(slDe, cultures, async client => {
    const { guide } = await common(slDe, client)
    check('pairs sl ← de: the guide says the labels read "Slovene · German", the curated meanings English', guide.includes('"Slovene · German"') && guide.includes('with English meanings, which you explain in German'), guide.slice(0, 900))
    check('pairs sl ← de: new packs carry German meanings too', guide.includes('This village speaks Slovene, explained in German: write packs with "language": "sl", each word in "sl" with "de" and "en" translations, examples in all three (example_sl, example_de, example_en), notes in "note" and "note_de".'), guide.match(/This village speaks[^\n]*/)?.[0])
    const w = await get(`/lookup?w=Micka`)
    check('pairs sl ← de: a villager is found by name', w.entries?.[0]?.source === 'village', w)
    const talk = await get('/villagers/micka/scenario')
    check('pairs sl ← de: a talk opens in Slovene', typeof talk.opener_sl === 'string' && talk.opener_sl.length > 0 && talk.setting?.includes('Lena'), talk)
  })

  // --- it ← en: an English speaker in Friuli -----------------------------------------------------------------
  const itEn: Pair = { id: 'sam', name: 'Sam', target: 'it', base: 'en', culture: 'friuli', targetName: 'Italian', baseName: 'English' }
  await withBridge(itEn, cultures, async client => {
    const { guide } = await common(itEn, client)
    check('pairs it ← en: the guide names the friuli pack, and new packs are Italian with English meanings', guide.includes('companion/cultures/friuli') && guide.includes('This village speaks Italian, explained in English: write packs with "language": "it", each word in "it" (a noun with its article) with "en" translations, examples in both (example_it, example_en)'), guide.match(/This village speaks[^\n]*/)?.[0])
    const casa = await get(`/lookup?w=case&line=${encodeURIComponent('Le case sono vecchie.')}`)
    check('pairs it ← en: an Italian pack word, meant in English', casa.entries?.[0]?.lemma === 'la casa' && casa.entries[0].gloss_lang === 'en' && casa.entries[0].source === 'pack', casa)
    const rosa = (await get('/villagers')).find((v: any) => v.id === 'rosa')
    const talk = await get('/villagers/rosa/scenario')
    const hello = rosa?.lines?.greet?.find((l: any) => (l.level ?? 0) === 0)
    check("pairs it ← en: Rosa's greeting in Italian, translated into English", !!hello && talk.opener_sl === hello.it && talk.opener_en === hello.en, { talk: [talk.opener_sl, talk.opener_en], hello })
  })

  // --- en ← it: an Italian speaker in an English village -----------------------------------------------------
  const enIt: Pair = { id: 'giulia', name: 'Giulia', target: 'en', base: 'it', culture: 'smoke-en', targetName: 'English', baseName: 'Italian' }
  await withBridge(enIt, cultures, async client => {
    const { guide } = await common(enIt, client)
    check('pairs en ← it: the guide names the English village and its language', guide.includes('companion/cultures/smoke-en') && guide.includes('are in English, with translations') && guide.includes('from its en string table'), guide.slice(0, 700))
    check('pairs en ← it: role-plays: English has one "you"', guide.includes('English has one "you"') && !guide.includes('expects vi back'), guide.match(/Villager role-play[^\n]*/)?.[0]?.slice(0, 400))
    const talk = await get('/villagers/micka/scenario')
    const micka = (await get('/villagers')).find((v: any) => v.id === 'micka')
    // the opener is one of her first greetings that fits the hour (a "Good day" by day, not in the evening)
    const hellos = (micka?.lines?.greet ?? []).filter((l: any) => (l.level ?? 0) === 0)
    check("pairs en ← it: Micka greets in English (the village's language)", hellos.some((l: any) => talk.opener_sl === l.en), { talk: talk.opener_sl, hellos })
  })

  check('a profile may name the app\'s languages in any of the four', languageCode('Slowenisch') === 'sl' && languageCode('tedesco') === 'de' && languageCode('Englisch') === 'en' && languageCode('italijanščina') === 'it' && languageCode('Klingon') === undefined)

  // --- Jan with another base: the default profile's text stays as it was for English, and names another base -------------
  const jan = { id: 'default', isDefault: true, child: false, culture: 'primorska' }
  const janEn = { name: 'Jan', target: 'Slovene', base: 'English', targetCode: 'sl', baseCode: 'en' }
  const janDe = { ...janEn, base: 'German', baseCode: 'de' }
  const paragraphs = ['Jan learns Slovene. Explain in English.']
  check("Jan from English: the default profile's rules and guide as they were", channelRules(jan, janEn, '/data') === channelRules(jan, { name: '', target: '', base: '' }, '/data') && learnerInstructions(paragraphs, jan, janEn, '/data') === paragraphs[0])
  const rulesDe = channelRules(jan, janDe, '/data')
  const guideDe = learnerInstructions(paragraphs, jan, janDe, '/data')
  check('Jan from German: the rules say so and fit, the guide says to explain in German', rulesDe.includes('Jan learns Slovene from German: explain, translate and give feedback in German.') && rulesDe.length <= 2048 && guideDe.startsWith("Jan's base language is German now") && guideDe.includes('"Slovene · German"') && guideDe.endsWith(paragraphs[0]), rulesDe)

  // --- lani-profile add: the village by the target language ------------------------------------------------
  const cli = resolve(import.meta.dir, '../../src/cli/profile.ts')
  const profiles = tempDir('lani-smoke-profiles-')
  // explicit ports (numbers in the registry only: the CLI then binds nothing to find a free one), and a tailnet name
  let n = 0
  const add = (id: string, ...args: string[]) => addIn(cultures, id, ...args)
  function addIn(packs: string, id: string, ...args: string[]) {
    n++
    return Bun.spawnSync(['bun', cli, 'add', id, '--name', id, '--port', String(18870 + n), '--https-port', String(18443 + n * 1000), ...args], {
      env: { ...process.env, LANI_PROFILES_DIR: profiles, LANI_CULTURES_DIR: packs, LANI_TAILNET_HOST: 'smoke.example.ts.net' } as Record<string, string>,
    })
  }
  const text = (r: ReturnType<typeof add>) => `${r.stdout}${r.stderr}`
  const de = add('greta', '--target', 'de', '--base', 'en')
  check('lani-profile add --target de without a German pack: stops, and says why and what to do', de.exitCode !== 0 && text(de).includes('No culture pack speaks German yet') && text(de).includes('--culture <pack>') && text(de).includes('friuli (Italian)'), text(de))
  const same = add('tom', '--target', 'it', '--base', 'Italian')
  check('lani-profile add: target and base must differ', same.exitCode !== 0 && text(same).includes('two different languages'), text(same))
  const fr = add('zoe', '--target', 'fr', '--base', 'en')
  check("lani-profile add: a language the app has no labels for is refused, the app's four named", fr.exitCode !== 0 && text(fr).includes("one of the app's languages: sl (Slovene), en (English), it (Italian), de (German)"), text(fr))
  const it = add('marco', '--target', 'Italian', '--base', 'de')
  const reg = () => JSON.parse(readFileSync(join(profiles, 'profiles.json'), 'utf8')).profiles
  check('lani-profile add --target it: the village is friuli, the Italian pack', it.exitCode === 0 && reg().find((e: any) => e.id === 'marco')?.culture === 'friuli' && reg().find((e: any) => e.id === 'marco')?.base === 'de', text(it))
  const en = add('ella', '--target', 'en', '--base', 'sl')
  check('lani-profile add --target en: the English pack', en.exitCode === 0 && reg().find((e: any) => e.id === 'ella')?.culture === 'smoke-en', text(en))
  // the repository's packs: German has its village (kaernten)
  const hanna = addIn(culturesDir, 'hanna', '--target', 'de', '--base', 'sl')
  check('lani-profile add --target de with the repository\'s packs: the German village, kaernten', hanna.exitCode === 0 && reg().find((e: any) => e.id === 'hanna')?.culture === 'kaernten', text(hanna))
  const lp = JSON.parse(readFileSync(join(profiles, 'ella/data/learner-profile.json'), 'utf8'))
  check("… the learner profile names the pair in words and codes", lp.learner.target_language === 'English' && lp.learner.target_language_code === 'en' && lp.learner.base_language === 'Slovene' && lp.learner.base_language_code === 'sl' && lp.home_language === 'en', lp.learner)
}
