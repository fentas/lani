// Smoke checks: the English village (the lakeland culture pack, a dale in the Lake District). A second bridge plays a
// profile whose culture is lakeland and whose target is English (from Slovene): it serves the lakeland cast and the
// English word packs (companion/packs/en and the pack's own, not Jan's Slovene ones), builds the villager talks in
// English with Slovene translations and the pack's setting (first names, and "Mr" or "Mrs" for the polite four), records
// a pack's words as English with their Slovene meaning (never the English word as its own meaning), tells the tutor about
// the village, and speaks with the pack's voice cast, in English (the phone's English TTS while the cast has no voices).
import { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js'
import { readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { initLearnerData } from '../../src/learners'
import { validatePack } from '../../src/packs'
import { allLines } from '../../src/villagers'
import { bridgeEleven, bridgeWhisper, check, checkCultureVoices, culturesDir, guideOf, languageBook, port as smokePort, tempDir } from './harness'

const repo = resolve(import.meta.dir, '../../../..')
// its own port, next to the smoke's (8797 is the QA's dev town, 8792 and up are learners' ports)
const port = Number(process.env.LANI_SMOKE_LAKELAND_PORT ?? smokePort + 6)
const base = `http://127.0.0.1:${port}`
const token = 'lakeland-smoke-token'
const auth = { authorization: `Bearer ${token}`, 'content-type': 'application/json' }
const LANGS = ['en', 'sl', 'it', 'de']

export default async function lakeland() {
  // --- a learner of English from Slovene, in the lakeland village ----------------------------------------
  const dataDir = tempDir('lani-smoke-lakeland-')
  initLearnerData(repo, dataDir, { name: 'Ana', target: 'en', base: 'sl', culture: 'lakeland' })
  const ttsBefore = bridgeEleven.st.tts.length
  const client = new Client({ name: 'smoke-lakeland', version: '0' })
  await client.connect(
    new StdioClientTransport({
      command: 'bun',
      args: [resolve(import.meta.dir, '../../src/index.ts')],
      env: {
        ...process.env, LANI_PROFILE: 'ana', LANI_CULTURE: 'lakeland', LANI_DATA_DIR: dataDir, LANI_BRIDGE_PORT: String(port),
        LANI_BRIDGE_TOKEN: token, LANI_PACKS_DIR: join(repo, 'companion/packs'), LANI_CULTURES_DIR: culturesDir, LANI_RHYTHM: 'off',
        LANI_SCENES_DIR: join(repo, 'companion/scenes'), LANI_MODULES_DIR: join(repo, 'companion/modules'), LANI_SCENARIOS_DIR: join(repo, 'companion/scenarios'),
        LANI_VOICE_DESIGN: 'off', ELEVENLABS_API_KEY: 'test-key', ELEVENLABS_BASE_URL: bridgeEleven.url, LANI_GEPARD_URL: 'http://127.0.0.1:9',
        LANI_VOICE_QUEUE_DELAY_MS: '0', LANI_STT_URL: bridgeWhisper.url, LANI_LEXICON_URL: tempDir('lani-smoke-lakeland-lexicon-'),
        LANI_LEXICON_CACHE: tempDir('lani-smoke-lakeland-lexicon-cache-'),
      } as Record<string, string>,
      stderr: 'inherit',
    }),
  )
  await Bun.sleep(300)
  try {
    const culture = await (await fetch(`${base}/culture`, { headers: auth })).json()
    check('lakeland: GET /culture plays the lakeland pack, in English', culture.id === 'lakeland' && culture.language === 'en' && culture.status === 'complete' && !culture.wanted && culture.places.some((p: any) => p.name.en === 'Keswick'), culture)

    // --- the cast --------------------------------------------------------------------------------------
    const cast = await (await fetch(`${base}/villagers`, { headers: auth })).json()
    check('lakeland: GET /villagers serves the lakeland cast of 13, Granny Maggie first', cast.length === 13 && cast[0].id === 'maggie' && cast[0].name === 'Granny Maggie' && cast.every((v: any) => v.source === 'curated'), cast.map((v: any) => v.id))
    check(
      'lakeland: their lines, gift lines, roles and likes are English with Slovene, Italian and German',
      cast.every((v: any) => allLines(v.lines).every((l: any) => LANGS.every(k => l[k])) && LANGS.every(k => v.role[k]) && v.likes.every((l: any) => LANGS.every(k => l[k]))),
      cast[0].lines.greet[0],
    )
    check('lakeland: everyone thanks for a gift in English: 3 lines for a good they like, 2 for an ordinary one, 1 for a rare one', cast.every((v: any) => v.lines.gift?.liked.length === 3 && v.lines.gift.ordinary.length === 2 && v.lines.gift.rare.length === 1), cast.filter((v: any) => !v.lines.gift).map((v: any) => v.id))
    check('lakeland: four people are "Mr" or "Mrs" to the learner (register vi), the others first names', JSON.stringify(cast.filter((v: any) => v.register === 'vi').map((v: any) => v.name).sort()) === JSON.stringify(['Mr Dixon', 'Mr Tyson', 'Mrs Birkett', 'Mrs Hartley']), cast.map((v: any) => [v.id, v.register]))
    const maggie = cast[0]
    const talk = await (await fetch(`${base}/villagers/maggie/scenario`, { headers: auth })).json()
    const hello = maggie.lines.greet.find((l: any) => (l.level ?? 0) === 0)
    check(
      "lakeland: a talk opens with Maggie's English greeting, translated into Slovene, in a dale of the Lake District",
      talk.opener_sl === hello.en && talk.opener_en === hello.sl && talk.setting.startsWith("My Village, Ana's village in a dale of the Lake District") && talk.role.includes('of My Village') && talk.role.includes('Everyone says first names here'),
      talk,
    )
    check('lakeland: the hints are English, meant in Slovene', talk.vocabulary_hints[0].sl === 'How are you?' && talk.vocabulary_hints[0].en === 'Kako si?' && talk.goals[0].includes('(first names)') && talk.vocabulary_hints.every((h: any) => h.sl !== h.en), talk.vocabulary_hints)
    const dixon = await (await fetch(`${base}/villagers/dixon/scenario`, { headers: auth })).json()
    check('lakeland: Mr Dixon: greet him politely, as "Mr Dixon"; the hints meant in the Slovene polite form', dixon.goals[0].includes('politely ("Mr" or "Mrs" and the surname)') && dixon.vocabulary_hints[0].sl === 'How are you?' && dixon.vocabulary_hints[0].en === 'Kako ste?' && dixon.role.includes('Ana says "Mr" or "Mrs" and the surname to them'), dixon)

    // --- the word packs: English, the village's own, not Jan's -------------------------------------------
    const packs = await (await fetch(`${base}/packs`, { headers: auth })).json()
    const ids = packs.map((p: any) => p.id)
    check(
      "lakeland: GET /packs serves the English starter packs and the pack's own, not the Slovene or the Italian ones",
      ['greetings', 'family', 'home-and-kitchen', 'food', 'numbers-and-time', 'in-the-village', 'feast-christmas', 'feast-show', 'by-the-fire', 'on-the-fell', 'tales'].every(i => ids.includes(i)) &&
        !ids.includes('druzina') && !ids.includes('praznik-bozic') && !ids.includes('famiglia') && packs.every((p: any) => p.language === 'en'),
      ids,
    )
    const fest = packs.filter((p: any) => p.festival)
    check('lakeland: 14 festival packs, each named for a festival of the pack', fest.length === 14 && fest.every((p: any) => p.id.startsWith('feast-')), fest.map((p: any) => [p.id, p.festival]))
    const family = await (await fetch(`${base}/packs/family`, { headers: auth })).json()
    check('lakeland: a pack comes with its words in English, Slovene, Italian and German, its title per language', family.language === 'en' && family.title.en === 'Family' && family.words.every((w: any) => LANGS.every(k => w[k] && w[`example_${k}`]) && !w.gender), family.title)
    const w = family.words[0]
    const learn = await fetch(`${base}/packs/family/learn`, { method: 'POST', headers: auth, body: JSON.stringify({ results: [{ word_id: w.id, quality: 4 }], duration_minutes: 3 }) })
    const learned = await learn.json()
    const sr = JSON.parse(readFileSync(join(dataDir, 'spaced-repetition.json'), 'utf8'))
    const item = sr.items?.[`vocab_family_${w.id}`]
    check('lakeland: a learned word is an English card with its Slovene meaning', learn.ok && learned.added?.length === 1 && item?.content === w.en && item?.answer === w.sl, { learned, item })

    // word lookup without an English dictionary: the pack words (meant in Slovene, never in English) and the villagers
    const lookup = async (q: string) => (await (await fetch(`${base}/lookup?${q}`, { headers: auth })).json()) as any
    const plural = family.words.find((x: any) => x.plural && x.plural !== x.en)
    const found = await lookup(`w=${encodeURIComponent(plural.plural)}`)
    const f0 = found.entries[0]
    check('lakeland: a lookup finds an English pack word by its plural, meant in Slovene (gloss_lang sl), its example meant in Slovene', f0?.lemma === plural.en && f0.gloss[0] === plural.sl && f0.gloss_lang === 'sl' && f0.grammar === 'plural' && f0.source === 'pack' && (!f0.example || f0.example.en === plural.example_sl), found)
    const roll = await lookup(`w=roll&line=${encodeURIComponent('We roll the eggs down the hill.')}`)
    check('lakeland: a verb is found without its "to" (roll: to roll), meant in Slovene', roll.entries.some((e: any) => e.lemma === 'to roll' && e.source === 'pack' && e.gloss_lang === 'sl'), roll)
    const byName = await lookup('w=Maggie')
    check('lakeland: a villager is found by name', byName.entries.some((e: any) => e.lemma === 'Granny Maggie' && e.source === 'village'), byName)

    // Jan's curated Slovene modules, role-plays and scenes aren't this village's
    const modules = await (await fetch(`${base}/modules`, { headers: auth })).json()
    const scenarios = await (await fetch(`${base}/scenarios`, { headers: auth })).json()
    const scenes = await (await fetch(`${base}/scenes`, { headers: auth })).json()
    check('lakeland: no Slovene modules, role-plays or scenes for the English village', Array.isArray(modules) && !modules.some((m: any) => m.id === 'dvojina-osnove') && Array.isArray(scenarios) && !scenarios.some((s: any) => s.id === 'v-pekarni') && !scenes.some((s: any) => ['ob-ognju', 'na-gricih', 'al-fuoco', 'il-mare'].includes(s.id)), { scenes: scenes.map((s: any) => s.id) })
    // the pack's own place: the fell above the lake, in English with its translations, its words from on-the-fell
    const fell = scenes.find((s: any) => s.id === 'on-the-fell')
    check(
      "lakeland: the pack's own scene is served (On the fell, from the horizon), in English with Slovene, Italian and German",
      fell?.culture === 'lakeland' && fell.language === 'en' && fell.art === 'alps' && fell.from[0] === 'spot:horizon' && LANGS.every(k => fell.title[k]) && fell.dialogs.every((d: any) => d.lines.every((l: any) => !l.who || LANGS.every(k => l[k]))),
      fell && { title: fell.title, culture: fell.culture },
    )
    const wall = fell?.objects.find((o: any) => o.slot === 'wall')
    check('lakeland: its objects are English words with their translations (resolved from its pack)', wall?.en && wall.sl && wall.it && wall.de && wall.example_en && wall.example_sl && wall.pack === 'on-the-fell' && wall.en !== wall.sl, wall)
    check("lakeland: the guest's first-time dialog isn't served to the village's own app", !fell.happenings.some((h: any) => h.guests) && fell.dialogs.every((d: any) => !d.guests), fell?.happenings.map((h: any) => h.id))
    // the campfire: Old Wilf tells his stories there in the evening, in English
    const fire = scenes.find((s: any) => s.id === 'by-the-fire')
    check(
      'lakeland: Old Wilf tells his ten stories by the fire, in English, with their words',
      fire?.culture === 'lakeland' && fire.art === 'campfire' && fire.stories?.length === 10 && fire.stories.every((s: any) => s.teller === 'wilf' && s.language === 'en' && s.culture === 'lakeland') &&
        fire.stories[0].words.every((w: any) => LANGS.every(k => w[k])) && scenes.filter((s: any) => s.id !== 'by-the-fire').every((s: any) => !s.stories),
      fire && { stories: fire.stories?.map((s: any) => [s.id, s.teller]) },
    )

    // --- the tutor is told about the village ----------------------------------------------------------
    const instructions = await guideOf(client)
    check(
      "lakeland: the instructions name Ana, the village's cast and language, its registers and its style",
      instructions.includes('Learner profile "ana"') && instructions.includes('Granny Maggie') && instructions.includes('I still think of') && instructions.includes('ti = first names, vi = "Mr" or "Mrs" and the surname') &&
        instructions.includes('Lake District style') && instructions.includes('companion/cultures/lakeland') && !instructions.includes('Primorska style') && !instructions.includes('the learner is a child'),
      instructions.slice(0, 400),
    )
    check(
      'lakeland: packs are written in English for this village, meant in the base (English isn\'t its own translation)',
      instructions.includes('"language": "en"') && instructions.includes('(a noun without "the", its plural in "plural") with "sl" translation') && !instructions.includes('"sl" and "en" translations') && instructions.includes('{"en", "sl"}'),
      instructions.match(/This village speaks[^\n]*/)?.[0],
    )
    await languageBook('lakeland', base, auth, 'en', 'English', 'Grammar', 'sl', instructions)

    // --- the voices: the pack's cast speaks English (without voices yet, the phone does) -----------------------
    await checkCultureVoices({ culture: 'lakeland', base, headers: auth, line: 'Now then! Are you hungry?', word: 'kettle', role: /Granny Maggie/, since: ttsBefore })

    // --- speech recognition: in the village's language when the app doesn't say --------------------------
    // (the fake worker has an English model for this check only)
    const take = new Uint8Array([0, 0, 0, 0x18, ...new TextEncoder().encode('ftypM4A '), 0, 0, 0, 0, 7, 7, 7, 7])
    const had = [...bridgeWhisper.st.languages]
    bridgeWhisper.st.languages.push('en')
    const heard = await fetch(`${base}/stt?prompt=Hello`, { method: 'POST', headers: { authorization: auth.authorization, 'content-type': 'audio/mp4' }, body: take })
    bridgeWhisper.st.languages = had
    check("lakeland: POST /stt without a language recognizes English, the village's", heard.ok && bridgeWhisper.st.calls.at(-1)?.query.language === 'en', bridgeWhisper.st.calls.at(-1)?.query)
  } finally {
    await client.close()
  }

  // --- the repository's English packs validate ----------------------------------------------------------
  for (const dir of [join(repo, 'companion/packs/en'), join(culturesDir, 'lakeland/packs')]) {
    for (const f of new Bun.Glob('*.json').scanSync(dir)) {
      const v = validatePack(JSON.parse(readFileSync(join(dir, f), 'utf8')))
      check(`lakeland: pack validates: ${f}`, v.ok && v.pack.language === 'en' && v.pack.words.every(x => LANGS.every(k => !!(x as any)[k])), !v.ok && v.errors)
    }
  }
}
