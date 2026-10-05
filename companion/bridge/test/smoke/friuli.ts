// Smoke checks: the second learner's village (plan 2, step 3b). A second bridge plays a child's profile whose culture is friuli and
// whose target is Italian (from Slovene): it serves the friuli cast and the Italian word packs (companion/packs/it and
// the pack's festival words, not Jan's Slovene ones), builds the villager talks in Italian with Slovene translations and
// the pack's setting, records a pack's words as Italian with their Slovene meaning, tells the tutor about the village and
// the child, and speaks with the pack's voice cast, in Italian (the phone's Italian TTS while the cast has no voices).
import { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js'
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { initLearnerData } from '../../src/learners'
import { labelShown, meant, said } from '../../src/langs'
import { meaningOf, titleFor, validatePack } from '../../src/packs'
import { JAN_PLACE, allLines, bondOf, validateVillager, villagerScenario } from '../../src/villagers'
import { corpus } from '../../src/voice'
import { bridgeEleven, bridgeWhisper, check, checkCultureVoices, culturesDir, tempDir, guideOf, languageBook } from './harness'
import { lexiconFixture } from './lexicon-fixture'
import { JAN } from '../learner-content'

const repo = resolve(import.meta.dir, '../../../..')
const port = Number(process.env.LANI_SMOKE_FRIULI_PORT ?? 8798)
const base = `http://127.0.0.1:${port}`
const token = 'friuli-smoke-token'
const auth = { authorization: `Bearer ${token}`, 'content-type': 'application/json' }

export default async function friuli() {
  // --- a learner of Italian from German: the cast and the word packs explain themselves in German ---------------------
  const friuliCast = readdirSync(join(culturesDir, 'friuli/villagers')).filter(f => f.endsWith('.json')).sort().flatMap(f => {
    const v = validateVillager(JSON.parse(readFileSync(join(culturesDir, 'friuli/villagers', f), 'utf8')))
    return v.ok ? [v.villager] : []
  })
  const rosaV = friuliCast.find(v => v.id === 'rosa')!
  const rosaDe = villagerScenario(rosaV, bondOf(undefined, 'rosa'), 'A1', { ...JAN_PLACE, target: 'it', base: 'de', learner: 'Tim' })
  check('friuli, for a learner from German: the opener is Italian, meant in German; the hints too', friuliCast.length === 13 && rosaDe.opener_sl === rosaV.lines.greet[0].it && rosaDe.opener_en === rosaV.lines.greet[0].de && rosaDe.vocabulary_hints[0].sl === 'Come stai?' && rosaDe.vocabulary_hints[0].en === "Wie geht's dir?", rosaDe)
  check('friuli: every line is meant in German, said in Italian as before; the roles and likes read in German', friuliCast.every(v => allLines(v.lines).every(l => !!l.de && said(l, 'it') === l.it && meant(l, 'it', 'de') === l.de && meant(l, 'it', 'sl') === l.sl) && labelShown(v.role, 'it', 'de') === `${(v.role as Record<string, string>).it} · ${(v.role as Record<string, string>).de}` && v.likes.every(l => typeof l !== 'string' && !!l.de)))
  const noGerman = JSON.parse(JSON.stringify(friuliCast, (k, x) => (k === 'de' && typeof x === 'string' ? undefined : x)))
  check('friuli: the German changes no voice clip', JSON.stringify(corpus({ learner: JAN, packs: [], modules: [], scenarios: [], villagers: friuliCast, language: 'it' })) === JSON.stringify(corpus({ learner: JAN, packs: [], modules: [], scenarios: [], villagers: noGerman, language: 'it' })))
  const famiglia = validatePack(JSON.parse(readFileSync(join(repo, 'companion/packs/it/famiglia.json'), 'utf8')))
  if (!famiglia.ok) throw new Error(famiglia.errors)
  const fp = famiglia.pack
  check('friuli: an Italian word pack means its words in German, and says a machine wrote them', fp.words.every(w => !!w.de && meaningOf(fp, w, 'de') === w.de && meaningOf(fp, w, 'sl') === w.sl) && titleFor(fp, 'it', 'de') === `La famiglia · ${(fp.title as Record<string, string>).de}` && /^de: machine-written/.test(fp.review ?? ''), fp)

  // --- a child learning Italian from Slovene, in the friuli village ------------------------------------
  const dataDir = tempDir('lani-smoke-friuli-')
  initLearnerData(repo, dataDir, { name: 'Tim', target: 'it', base: 'sl', child: true, culture: 'friuli' })
  // An Italian dictionary as a download (LANI_LEXICON_URL; a local directory here), gzipped, with its meanings in
  // Slovene and German: companion/bin/lexicon-build's, made of tiny extracts (lexicon-fixture.ts).
  const lexSource = lexiconFixture(tempDir).dir
  const lexCache = tempDir('lani-smoke-lexicon-cache-')
  const ttsBefore = bridgeEleven.st.tts.length
  const client = new Client({ name: 'smoke-friuli', version: '0' })
  const notes: any[] = []
  client.fallbackNotificationHandler = async n => void notes.push(n)
  await client.connect(
    new StdioClientTransport({
      command: 'bun',
      args: [resolve(import.meta.dir, '../../src/index.ts')],
      env: {
        ...process.env, LANI_PROFILE: 'tim', LANI_CHILD: '1', LANI_CULTURE: 'friuli', LANI_DATA_DIR: dataDir, LANI_BRIDGE_PORT: String(port),
        LANI_BRIDGE_TOKEN: token, LANI_PACKS_DIR: join(repo, 'companion/packs'), LANI_CULTURES_DIR: culturesDir, LANI_RHYTHM: 'off',
        LANI_SCENES_DIR: join(repo, 'companion/scenes'), LANI_MODULES_DIR: join(repo, 'companion/modules'), LANI_SCENARIOS_DIR: join(repo, 'companion/scenarios'),
        LANI_VOICE_DESIGN: 'off', ELEVENLABS_API_KEY: 'test-key', ELEVENLABS_BASE_URL: bridgeEleven.url, LANI_GEPARD_URL: 'http://127.0.0.1:9',
        LANI_VOICE_QUEUE_DELAY_MS: '0', LANI_STT_URL: bridgeWhisper.url, LANI_LEXICON_URL: lexSource, LANI_LEXICON_CACHE: lexCache, LANI_GLOSS_GAP_MS: '200',
      } as Record<string, string>,
      stderr: 'inherit',
    }),
  )
  await Bun.sleep(300)
  try {
    const culture = await (await fetch(`${base}/culture`, { headers: auth })).json()
    check('friuli: GET /culture plays the friuli pack, in Italian', culture.id === 'friuli' && culture.language === 'it' && culture.status === 'complete' && !culture.wanted && culture.places.some((p: any) => p.name.it === 'Gorizia'), culture)

    // --- the cast --------------------------------------------------------------------------------------
    const cast = await (await fetch(`${base}/villagers`, { headers: auth })).json()
    check('friuli: GET /villagers serves the friuli cast of 13, Nonna Rosa first', cast.length === 13 && cast[0].id === 'rosa' && cast[0].name === 'Nonna Rosa' && cast.every((v: any) => v.source === 'curated'), cast.map((v: any) => v.id))
    check('friuli: their lines are Italian with Slovene and English, their gift lines too, their roles too', cast.every((v: any) => allLines(v.lines).every((l: any) => l.it && l.sl && l.en) && v.role.it && v.role.sl), cast[0].lines.greet[0])
    check('friuli: everyone thanks for a gift in Italian: 3 lines for a good they like, 2 for an ordinary one, 1 for a rare one', cast.every((v: any) => v.lines.gift?.liked.length === 3 && v.lines.gift.ordinary.length === 2 && v.lines.gift.rare.length === 1), cast.filter((v: any) => !v.lines.gift).map((v: any) => v.id))
    const rosa = cast[0]
    const talk = await (await fetch(`${base}/villagers/rosa/scenario`, { headers: auth })).json()
    const hello = rosa.lines.greet.find((l: any) => (l.level ?? 0) === 0)
    check(
      "friuli: a talk opens with Rosa's Italian greeting, translated into the child's Slovene, in the Collio",
      talk.opener_sl === hello.it && talk.opener_en === hello.sl && talk.setting.startsWith("Il mio villaggio, Tim's village in the Collio hills near Gorizia") && talk.role.includes('of Il mio villaggio') && talk.role.includes('Everyone says tu here'),
      talk,
    )
    check('friuli: the hints are Italian, meant in Slovene', talk.vocabulary_hints[0].sl === 'Come stai?' && talk.vocabulary_hints[0].en === 'Kako si?' && talk.vocabulary_hints.at(-1).sl === 'Ciao!' && talk.goals[0].includes('(tu)'), talk.vocabulary_hints)
    const elena = await (await fetch(`${base}/villagers/elena/scenario`, { headers: auth })).json()
    check('friuli: a Lei villager: the polite goal and hints', elena.goals[0].includes('politely (Lei)') && elena.vocabulary_hints[0].sl === 'Come sta?' && elena.vocabulary_hints[0].en === 'Kako ste?' && elena.role.includes('Tim says Lei to them'), elena)

    // --- the word packs: Italian, the village's festivals', not Jan's -------------------------------------
    const packs = await (await fetch(`${base}/packs`, { headers: auth })).json()
    const ids = packs.map((p: any) => p.id)
    check(
      'friuli: GET /packs serves the Italian starter packs and the festivals\' packs, not the Slovene ones',
      ['saluti', 'famiglia', 'casa-e-cucina', 'cibo', 'numeri-e-ore', 'in-paese', 'festa-natale', 'festa-vendemmia'].every(i => ids.includes(i)) && !ids.includes('druzina') && !ids.includes('praznik-bozic') && packs.every((p: any) => p.language === 'it'),
      ids,
    )
    const fest = packs.filter((p: any) => p.festival)
    check('friuli: 14 festival packs, each named for a festival of the pack', fest.length === 14 && fest.every((p: any) => p.id.startsWith('festa-')), fest.map((p: any) => [p.id, p.festival]))
    const famiglia = await (await fetch(`${base}/packs/famiglia`, { headers: auth })).json()
    check('friuli: a pack comes with its words in Italian, Slovene and English, its title per language', famiglia.language === 'it' && famiglia.title.it === 'La famiglia' && famiglia.words.every((w: any) => w.it && w.sl && w.en && w.example_it), famiglia.title)
    const w = famiglia.words[0]
    const learn = await fetch(`${base}/packs/famiglia/learn`, { method: 'POST', headers: auth, body: JSON.stringify({ results: [{ word_id: w.id, quality: 4 }], duration_minutes: 3 }) })
    const learned = await learn.json()
    const sr = JSON.parse(readFileSync(join(dataDir, 'spaced-repetition.json'), 'utf8'))
    const item = sr.items?.[`vocab_famiglia_${w.id}`]
    check('friuli: a learned word is an Italian card with its Slovene meaning', learn.ok && learned.added?.length === 1 && item?.content === w.it && item?.answer === w.sl, { learned, item })

    // --- the Italian dictionary, downloaded from LANI_LEXICON_URL, checked and cached ---------------------------
    const lookup = async (q: string) => (await (await fetch(`${base}/lookup?${q}`, { headers: auth })).json()) as any
    let lex: any
    for (let i = 0; i < 50; i++) {
      lex = await (await fetch(`${base}/lexicon/status`, { headers: auth })).json()
      if (lex.download !== 'loading' && lex.download !== 'off') break
      await Bun.sleep(100)
    }
    check("friuli: GET /lexicon/status: the Italian dictionary is downloaded, with its meanings in the child's Slovene (and German)", lex.language === 'it' && lex.download === 'ready' && lex.from === 'download' && lex.lemmas === 8 && lex.base === 'sl' && lex.base_meanings === 6 && JSON.stringify(lex.bases) === '["en","sl","de"]' && /^\d{4}-\d\d-\d\d-[0-9a-f]{8}$/.test(lex.version), lex)
    check('friuli: the download is cached with its manifest', ['it.json.gz', 'it.manifest.json'].every(f => existsSync(join(lexCache, f))), readdirSync(lexCache))
    const case_ = await lookup(`w=case&line=${encodeURIComponent('Le case sono vecchie.')}`)
    check('friuli: a lookup finds an Italian pack word by its plural first, meant in Slovene (gloss_lang sl)', case_.entries[0]?.lemma === 'la casa' && case_.entries[0].gloss[0] === 'hiša; dom' && case_.entries[0].gloss_lang === 'sl' && case_.entries[0].grammar === 'plural' && case_.entries[0].source === 'pack', case_)
    const casa = (await lookup('w=casa')).entries.find((e: any) => e.source === 'wiktionary')
    check("friuli: it←sl: the dictionary's casa is hiša (through English), the English kept", casa?.lemma === 'casa' && casa.gloss[0] === 'hiša' && casa.gloss_lang === 'sl' && casa.gloss_via === 'pivot' && casa.gloss_en?.[0] === 'house' && !casa.fallback, casa)
    const rosaName = await lookup('w=Rosa')
    const nobody = await lookup('w=xyzzyq')
    check('friuli: a villager is found by name; a word nobody knows, not at all', rosaName.entries[0]?.lemma === 'Nonna Rosa' && rosaName.entries[0].source === 'village' && nobody.entries.length === 0, { rosaName, nobody })
    const ombra = await lookup(`w=${encodeURIComponent("l'ombra")}&line=${encodeURIComponent("All'ombra dell'albero.")}`)
    const o0 = ombra.entries[0]
    check("friuli: l'ombra is looked up as ombra: senca, through English", ombra.word === "l'ombra" && o0?.lemma === 'ombra' && o0.gloss[0] === 'senca' && o0.gloss_via === 'pivot' && o0.gloss_lang === 'sl' && o0.gender === 'f', ombra)
    const acqua = await lookup(`w=${encodeURIComponent('l’acqua')}`)
    check("friuli: l’acqua (a typographic apostrophe) is the pack's l'acqua, meant in Slovene", acqua.entries[0]?.source === 'pack' && acqua.entries[0].lemma === "l'acqua" && acqua.entries[0].gloss_lang === 'sl', acqua)

    // no Slovene meaning: the English, marked; the tutor hears of it and writes one (flagged machine-written)
    const sono = await lookup(`w=sono&line=${encodeURIComponent('Sono di Gorizia.')}`)
    const s0 = sono.entries[0]
    check("friuli: sono is essere; without a Slovene meaning, Wiktionary's English, marked fallback, with the grammar and attribution", s0?.lemma === 'essere' && s0.gloss[0] === 'to be' && s0.gloss_lang === 'en' && s0.fallback === true && s0.grammar === 'first-person singular present; third-person plural present' && sono.attribution !== null, sono)
    await Bun.sleep(500)
    const gap = notes.filter(n => n.params?.meta?.kind === 'gloss_gaps')
    check('friuli: the tutor hears of the gap: essere, in Slovene (gloss_gaps)', gap.length === 1 && gap[0].params.content.includes('sono → essere (verb; English: to be)') && gap[0].params.content.includes('gloss_lang: "sl"') && gap[0].params.content.includes('Slovene'), gap.map(n => n.params.content))
    const pub = await client.callTool({ name: 'publish_gloss', arguments: { form: 'sono', lemma: 'essere', pos: 'verb', gloss: ['biti'] } })
    const again = (await lookup(`w=sono&line=${encodeURIComponent('Sono di Gorizia.')}`)).entries[0]
    check("friuli: publish_gloss (in the child's Slovene by default) fills it: sono is essere, biti, Claude's (flagged), with the English and grammar", !pub.isError && again?.lemma === 'essere' && again.gloss[0] === 'biti' && again.gloss_lang === 'sl' && again.gloss_via === 'claude' && again.gloss_en?.[0] === 'to be' && again.grammar === s0.grammar && !again.fallback, again)

    // on a visit to a Slovene town, the child reads Slovene explained in Italian (their base is the town's language)
    const hisa = await lookup('w=hi%C5%A1o&language=sl')
    const h0 = hisa.entries[0]
    check(
      "friuli: a visit's lookup in Slovene is explained in Italian, the child's own target: the Slovene pack word, casa, with the dictionary's grammar",
      h0?.lemma === 'hiša' && h0.source === 'pack' && h0.emoji === '🏠' && h0.gloss_lang === 'it' && h0.gloss.includes('casa') && !h0.fallback && h0.grammar && hisa.attribution !== null,
      hisa,
    )

    // Jan's curated Slovene modules, role-plays and scenes aren't the child's: those of Italian are in companion/*/it
    const modules = await (await fetch(`${base}/modules`, { headers: auth })).json()
    const scenarios = await (await fetch(`${base}/scenarios`, { headers: auth })).json()
    const scenes = await (await fetch(`${base}/scenes`, { headers: auth })).json()
    check('friuli: no Slovene modules, role-plays or scenes for the Italian village', Array.isArray(modules) && !modules.some((m: any) => m.id === 'dvojina-osnove') && Array.isArray(scenarios) && !scenarios.some((s: any) => s.id === 'v-pekarni') && JSON.stringify(scenes).indexOf('ob-ognju') < 0 && !scenes.some((s: any) => s.id === 'na-gricih'), { modules: modules.length, scenarios: scenarios.length, scenes: scenes.map((s: any) => s.id) })
    // the friuli pack's own place: the sea at Grado, in Italian with Slovene and English, its words from the pack's il-mare
    const mare = scenes.find((s: any) => s.id === 'il-mare')
    check("friuli: the pack's own scene is served (Il mare, from the horizon), in Italian", mare?.culture === 'friuli' && mare.language === 'it' && mare.art === 'sea' && mare.title.it === 'Il mare' && mare.title.sl === 'Morje' && mare.dialogs.every((d: any) => d.lines.every((l: any) => !l.who || (l.it && l.sl && l.en))), mare && { title: mare.title, culture: mare.culture })
    const gabbiano = mare?.objects.find((o: any) => o.slot === 'gull')
    check('friuli: its objects are Italian words with their Slovene and English', gabbiano?.it === 'il gabbiano' && gabbiano.sl === 'galeb' && gabbiano.en === 'gull' && gabbiano.example_it && gabbiano.example_sl && gabbiano.pack === 'il-mare', gabbiano)
    // (the guest's "Sei di fuori?" isn't served to the village's own app: companion/SCENES.md, "Dialogs for guests")
    const onde = mare?.dialogs.find((d: any) => d.id === 'onde')
    check('friuli: a wrong choice explains itself in Slovene too', onde?.lines[1].choices[1].why?.sl?.includes('tutto grigio') && !mare.dialogs.some((d: any) => d.id === 'fuori'), onde?.lines[1].choices[1])
    // the campfire of the friuli village: Nonno Bepi tells his stories there in the evening, in Italian
    const fuoco = scenes.find((s: any) => s.id === 'al-fuoco')
    check(
      "friuli: Nonno Bepi tells his stories by the fire (al-fuoco comes with them, in Italian, none of Janez's)",
      fuoco?.culture === 'friuli' && fuoco.art === 'campfire' && fuoco.stories?.length === 10 && fuoco.stories.every((s: any) => s.teller === 'bepi' && s.language === 'it' && s.culture === 'friuli') &&
        fuoco.stories[0].words.every((w: any) => w.it && w.sl && w.en) && scenes.filter((s: any) => s.id !== 'al-fuoco').every((s: any) => !s.stories),
      fuoco && { stories: fuoco.stories?.map((s: any) => [s.id, s.teller]), words: fuoco.stories?.[0]?.words },
    )

    // --- the tutor is told about the village and the child ----------------------------------------------
    const instructions = await guideOf(client)
    const rules = client.getInstructions() ?? ''
    check('friuli: the instructions Claude Code shows fit, and hold the child\'s rules', rules.length <= 2048 && rules.includes('Child: the learner is a child') && rules.includes('channel_guide'), rules.length)
    check('friuli: the instructions name the child, the village\'s cast and language, and the style', instructions.includes('Child: the learner is a child') && instructions.includes('Nonna Rosa') && instructions.includes('Ricordo ancora') && instructions.includes('Friulian style') && instructions.includes('companion/cultures/friuli') && !instructions.includes('Primorska style'), instructions.slice(0, 400))
    check('friuli: packs are written in Italian for this village', instructions.includes('"language": "it"'))
    await languageBook('friuli', base, auth, 'it', 'Italian', 'Grammatica', 'sl', instructions)

    // --- the voices: the pack's cast speaks Italian (without voices yet, the phone does) -----------------------
    await checkCultureVoices({ culture: 'friuli', base, headers: auth, line: 'Ciao! Hai fame?', word: 'legna', role: /Nonna Rosa/, since: ttsBefore })

    // --- speech recognition: in the village's language when the app doesn't say --------------------------
    const take = new Uint8Array([0, 0, 0, 0x18, ...new TextEncoder().encode('ftypM4A '), 0, 0, 0, 0, 7, 7, 7, 7])
    const heard = await fetch(`${base}/stt?prompt=Ciao`, { method: 'POST', headers: { authorization: auth.authorization, 'content-type': 'audio/mp4' }, body: take })
    check('friuli: POST /stt without a language recognizes Italian, the village\'s', heard.ok && bridgeWhisper.st.calls.at(-1)?.query.language === 'it', bridgeWhisper.st.calls.at(-1)?.query)
  } finally {
    await client.close()
  }

  // --- the repository's Italian packs validate ----------------------------------------------------------
  for (const dir of [join(repo, 'companion/packs/it'), join(culturesDir, 'friuli/packs')]) {
    for (const f of new Bun.Glob('*.json').scanSync(dir)) {
      const v = validatePack(JSON.parse(readFileSync(join(dir, f), 'utf8')))
      check(`friuli: pack validates: ${f}`, v.ok && v.pack.language === 'it' && v.pack.words.every(x => !!x.it && !!x.sl && !!x.en), !v.ok && v.errors)
    }
  }
}
