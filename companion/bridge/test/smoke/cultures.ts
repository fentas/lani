// Smoke checks: culture packs (lani.culture/v0, companion/cultures): every pack validates against its cast, a stub
// is its manifest alone, a broken pack says where, the bridge plays the profile's pack (or the default) and tells the
// app which (GET /culture, GET /cultures), and what the pack names by class (prices, tool effects) is what the app's
// Catalog has numbers for.
import { cpSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { cultureIds, cultureKeepers, cultureReadings, cultureScenes, DEFAULT_CULTURE, FILES, PRICES, PRIMORSKA_TUTOR, READING_KINDS, READING_LEVELS, keeperTellings, readingRefs, readManifest, resolveCulture, TOOL_EFFECTS, validateCulture } from '../../src/cultures'
import { speakerFor } from '../../src/cast'
import { corpus } from '../../src/voice'
import { PackStore, packsDirs } from '../../src/packs'
import { ScenarioStore } from '../../src/scenarios'
import { SCENE_ART, validateScene } from '../../src/scenes'
import { profileFromEnv } from '../../src/learners'
import { cultureSky, MOON_PHASES, SKY_FIGURES, SKY_SHOWERS, SKY_STARS } from '../../src/sky'
import { VillagerStore } from '../../src/villagers'
import { auth, base, check, culturesDir, dataDir, fam, tempDir } from './harness'
import { JAN } from '../learner-content'

const android = resolve(import.meta.dir, '../../../android/app/src/main/java/si/lanisce/lani/game')

export default async function cultures() {
  // --- the packs in the repository -----------------------------------------------------------------
  const ids = cultureIds(culturesDir)
  check('the culture packs: primorska, friuli, kaernten and lakeland among them', ['friuli', 'kaernten', 'lakeland', 'primorska'].every(id => ids.includes(id)), ids)
  const repo = resolve(import.meta.dir, '../../..')
  for (const id of ids) {
    const cast = new VillagerStore(join(culturesDir, id, 'villagers'), join(dataDir, `unused-villagers-${id}`)).all()
    const lang = readManifest(culturesDir, id)
    const language = lang.ok ? lang.manifest.language : 'sl'
    // its scenes' words in the packs its village is served: its language's, then its own
    const refs = {
      packs: new PackStore(packsDirs(join(repo, 'packs'), culturesDir, id, language), join(dataDir, `unused-packs-${id}`)).all(),
      scenarios: new ScenarioStore(language === 'sl' ? join(repo, 'scenarios') : join(repo, 'scenarios', language), join(dataDir, `unused-scenarios-${id}`)).all(),
    }
    const v = validateCulture(culturesDir, id, cast, refs)
    check(`culture pack validates, its people in its cast, its scenes' words in its packs: ${id}`, v.ok, !v.ok && v.errors)
  }

  // --- every village's charcoal pile (world.json `spots.kopa`): its name and story in its language, its burner and his talk,
  // its words a pack of the culture's (validateCulture above checks the rest)
  for (const [id, name, burner, pack] of [
    ['primorska', 'Oglarska kopa', 'Oglar Miha', 'pri-kopi'], ['friuli', 'La carbonaia', 'Carbonaio Pieri', 'alla-carbonaia'],
    ['kaernten', 'Der Kohlenmeiler', 'Köhler Michl', 'am-meiler'], ['lakeland', 'The charcoal pit', 'Collier Ned', 'at-the-pit'],
  ]) {
    const world = JSON.parse(readFileSync(join(culturesDir, id, 'world.json'), 'utf8'))
    const kopa = world.spots?.kopa
    const lang = readManifest(culturesDir, id)
    const l = lang.ok ? lang.manifest.language : 'sl'
    const packFile = JSON.parse(readFileSync(join(culturesDir, id, 'packs', `${pack}.json`), 'utf8'))
    check(
      `${id}'s charcoal pile: ${name}, its story at A1 and A2, ${burner} there some evenings with a talk at A1 and A2, its words in ${pack}`,
      kopa?.name?.[l] === name && !!kopa.story?.A1?.[l] && !!kopa.story?.A2?.[l] && kopa.pack === pack && kopa.keeper?.name === burner &&
        kopa.keeper.art === 'burner' && JSON.stringify(kopa.keeper.when) === JSON.stringify(['evening', 'night']) && !!kopa.keeper.levels.A1 && !!kopa.keeper.levels.A2 &&
        packFile.id === pack && packFile.words.length >= 8,
      kopa,
    )
  }

  // --- a culture pack's own places: its landscape at the horizon, in its language, with its own people -------------------
  for (const [id, sceneId, art, language, from] of [
    ['primorska', 'v-gorah', 'alps', 'sl', 'spot:horizon'], ['primorska', 'na-gricih', 'hills', 'sl', 'project:vinograd'], ['friuli', 'il-mare', 'sea', 'it', 'spot:horizon'],
    ['kaernten', 'am-see', 'alps', 'de', 'spot:horizon'],
    ['lakeland', 'on-the-fell', 'alps', 'en', 'spot:horizon'],
  ]) {
    const raw = JSON.parse(readFileSync(join(culturesDir, id, 'scenes', `${sceneId}.json`), 'utf8'))
    const v = validateScene(raw)
    const s = v.ok ? v.scene : undefined
    // what is said: the spoken lines (not the learner's turns themselves), every choice and every reply
    const texts = (s?.dialogs ?? []).flatMap(d => d.lines.flatMap(l => [...(l.choices.length ? [] : [l]), ...l.choices, ...l.choices.map(c => c.reply).filter(Boolean)])) as any[]
    check(
      `${id}'s ${sceneId}: the ${art} opens from ${from}, in ${language}, every slot a word, a dialog with a sky or an fx cue, everyone of the cast with a memory, a reaction to every wrong choice`,
      !!s && s.art === art && s.language === language && JSON.stringify(s.from) === JSON.stringify([from]) && SCENE_ART.objects[art].every(x => s.objects.some(o => o.slot === x)) &&
        s.dialogs.some(d => d.lines.some(l => l.sky || l.fx || l.choices.some(c => c.reply?.sky || c.reply?.fx))) &&
        s.people.every(p => !!p.villager) && s.happenings.every(h => !!(h.memory as any)?.[language]) && texts.every(t => typeof t[language] === 'string' && typeof t.en === 'string') &&
        s.dialogs.every(d => d.lines.every(l => l.choices.every(c => c.ok || !!c.reply))),
      !v.ok && v.errors,
    )
  }
  // a broken culture scene says the file and what: another language, someone of another pack's cast
  const brokenScenes = tempDir('lani-smoke-culture-scenes-')
  cpSync(join(culturesDir, 'friuli'), join(brokenScenes, 'friuli'), { recursive: true })
  const mare = JSON.parse(readFileSync(join(brokenScenes, 'friuli/scenes/il-mare.json'), 'utf8'))
  mare.people[0].villager = 'micka'
  writeFileSync(join(brokenScenes, 'friuli/scenes/il-mare.json'), JSON.stringify(mare))
  writeFileSync(join(brokenScenes, 'friuli/scenes/na-gricih.json'), readFileSync(join(culturesDir, 'primorska/scenes/na-gricih.json')))
  const friuliCast = new VillagerStore(join(culturesDir, 'friuli', 'villagers'), join(dataDir, 'unused-villagers-friuli-b')).all()
  const bs = cultureScenes(brokenScenes, 'friuli', 'it', friuliCast)
  check("a culture pack's scene in another language or with someone of another cast is named, with the file", bs.some(e => e.includes('friuli/scenes/na-gricih.json') && e.includes('not the pack\'s language "it"')) && bs.some(e => e.includes('friuli/scenes/il-mare.json') && e.includes('no villager "micka"')), bs)
  const friuli = readManifest(culturesDir, 'friuli')
  check(
    'friuli is complete, in Italian, for Friuli, marked machine-written, with its tutor texts',
    friuli.ok && friuli.manifest.status === 'complete' && friuli.manifest.language === 'it' && friuli.manifest.region.it === 'Friuli' && /not reviewed by a native speaker/.test(friuli.manifest.review ?? '') && /Gorizia/.test(friuli.manifest.tutor?.setting ?? ''),
    friuli,
  )
  const kaernten = readManifest(culturesDir, 'kaernten')
  check(
    'kaernten is complete, in German, for Carinthia, marked machine-written, with its tutor texts',
    kaernten.ok && kaernten.manifest.status === 'complete' && kaernten.manifest.language === 'de' && kaernten.manifest.region.de === 'Kärnten' && kaernten.manifest.region.sl === 'Koroška' && !!kaernten.manifest.region.it && /not reviewed by a native speaker/.test(kaernten.manifest.review ?? '') && /Villach/.test(kaernten.manifest.tutor?.setting ?? ''),
    kaernten,
  )
  const lakeland = readManifest(culturesDir, 'lakeland')
  check(
    'lakeland is complete, in English, for the Lake District, marked machine-written, with its tutor texts',
    lakeland.ok && lakeland.manifest.status === 'complete' && lakeland.manifest.language === 'en' && lakeland.manifest.region.en === 'The Lake District' && /not reviewed by a native speaker/.test(lakeland.manifest.review ?? '') && /Lake District/.test(lakeland.manifest.tutor?.setting ?? ''),
    lakeland,
  )
  const pm = readManifest(culturesDir, 'primorska')
  check("primorska's tutor texts are what the bridge always said", pm.ok && JSON.stringify(pm.manifest.tutor) === JSON.stringify(PRIMORSKA_TUTOR), pm.ok && pm.manifest.tutor)
  // the app's test pack (CultureSwitchTest's tinyland) is a second complete pack both validators accept
  const tiny = validateCulture(resolve(android, '../../../../../../test/resources/cultures-test'), 'tinyland')
  check("the app's test pack validates here too", tiny.ok && tiny.pack.manifest.language === 'it', !tiny.ok && tiny.errors)
  // a spot's texts (world.json `spots`, the charcoal pile) are checked with the pack: every language, the story at A1 and A2,
  // its pack there, the keeper's talk as an introduction's (a reaction and a why in every base to each wrong choice, his
  // lines his own); a spot the game hasn't got is refused
  const spotted = tempDir('lani-smoke-culture-spots-')
  cpSync(resolve(android, '../../../../../../test/resources/cultures-test/tinyland'), join(spotted, 'tinyland'), { recursive: true })
  const tw = JSON.parse(readFileSync(join(spotted, 'tinyland/world.json'), 'utf8'))
  const tinyKeeper = tw.spots.kopa.keeper
  check("the test pack's charcoal pile has its story at A1 and A2 and a keeper who talks at both", !!tw.spots.kopa.story.A1 && !!tw.spots.kopa.story.A2 && !!tinyKeeper.levels.A1 && !!tinyKeeper.levels.A2, tw.spots)
  // and after his meeting the story he tells across visits (talks): each talk at A1 and A2, a later round's telling (again)
  // by the same rules
  const [tk] = cultureKeepers(resolve(android, '../../../../../../test/resources/cultures-test'), 'tinyland')
  check(
    "the test pack's keeper tells a story across visits: two talks, the first told again on the next round, every telling at A1 and A2",
    tk?.talks.map(t => t.id).join() === 'fuoco,carbone' && tk.talks[0].again.length === 1 && !tk.talks[1].title && keeperTellings(tk).length === 2 + 4 + 2,
    tk?.talks,
  )
  // voice-build voices his talks (the corpus's keepers): his lines and his replies in his voice, the learner's choices in
  // the default one, each telling's; Primorska's burner in his speaker's
  const tinyVoiced = corpus({ learner: JAN, packs: [], modules: [], scenarios: [], keepers: [tk].map(k => ({ ...k, tellings: keeperTellings(k) })), language: 'it' })
  const miha = cultureKeepers(culturesDir, 'primorska')[0]
  const mihaVoiced = corpus({ learner: JAN, packs: [], modules: [], scenarios: [], keepers: [miha].map(k => ({ ...k, tellings: keeperTellings(k) })) })
  check(
    "… voiced by voice-build: his lines and replies in his voice (Miha in the grandpa's), the learner's choices in the default one, every telling",
    ['Buonasera!', 'Stasera accendo la carbonaia.', 'Una carbonaia nuova! Stasera piove, ma la accendo.', 'Nero e leggero.'].every(t => tinyVoiced.some(i => i.text === t && i.voice === 'male' && i.source === 'keeper:gino')) &&
      tinyVoiced.some(i => i.text === 'Posso guardare?' && i.voice === 'female') && mihaVoiced.some(i => i.source === 'keeper:oglar' && i.voice === speakerFor(miha) && i.text.startsWith('Dober večer')),
    { tinyVoiced: tinyVoiced.length, miha: mihaVoiced.slice(0, 3) },
  )
  const twTalks = JSON.parse(JSON.stringify(tinyKeeper.talks))
  delete tinyKeeper.levels.A1.lines[1].choices[1].reply
  delete tinyKeeper.levels.A1.lines[1].choices[1].why.de
  tinyKeeper.levels.A1.lines[2].who = 'nobody'
  delete tw.spots.kopa.story.A2
  delete tw.spots.kopa.name.de
  tw.spots.kopa.pack = 'nowhere'
  writeFileSync(join(spotted, 'tinyland/world.json'), JSON.stringify(tw))
  const bsp = validateCulture(spotted, 'tinyland', undefined, { packs: [], scenarios: [] })
  const spotWrong = [
    'tinyland/world.json: spots.kopa.name: no "de" text', 'spots.kopa.story: no A2', 'spots.kopa.pack: no pack "nowhere"',
    'spots.kopa.keeper.levels.A1.lines[1].choices[1]: a wrong choice needs a reaction', 'spots.kopa.keeper.levels.A1.lines[1].choices[1].why: no "de" why',
    'spots.kopa.keeper.levels.A1.lines[2]: who "nobody" isn\'t in it (gino)',
  ]
  check("… a spot's text without a language, a story without A2, a pack that isn't there, a wrong choice without its reaction or its why, a line that isn't the keeper's", !bsp.ok && spotWrong.every(e => bsp.errors.includes(e)), !bsp.ok && bsp.errors)
  // a talk of his story the same way, in each telling: an id twice, a talk without A2, a title without a language, a wrong
  // choice of a later round's telling without its reaction, its why without a base; an unknown key of a talk is refused
  const told = JSON.parse(JSON.stringify(twTalks))
  told[1].id = 'fuoco'
  delete told[1].levels.A2
  delete told[0].title.de
  delete told[0].again[0].levels.A2.lines[1].choices[1].reply
  delete told[0].again[0].levels.A1.lines[1].choices[1].why.sl
  const tws = JSON.parse(readFileSync(resolve(android, '../../../../../../test/resources/cultures-test/tinyland/world.json'), 'utf8'))
  tws.spots.kopa.keeper.talks = told
  writeFileSync(join(spotted, 'tinyland/world.json'), JSON.stringify(tws))
  const btk = validateCulture(spotted, 'tinyland')
  const talkWrong = [
    'tinyland/world.json: spots.kopa.keeper.talks[1].id: "fuoco" twice (each talk of his story once)', 'spots.kopa.keeper.talks[1].levels: no A2',
    'spots.kopa.keeper.talks[0].title: no "de" text', 'spots.kopa.keeper.talks[0].again[0].levels.A2.lines[1].choices[1]: a wrong choice needs a reaction',
    'spots.kopa.keeper.talks[0].again[0].levels.A1.lines[1].choices[1].why: no "sl" why',
  ]
  check("… a talk of his story: an id twice, a talk without A2, a title without a language, a later telling's wrong choice without its reaction or its why", !btk.ok && talkWrong.every(e => btk.errors.includes(e)), !btk.ok && btk.errors)
  tws.spots.kopa.keeper.talks = [{ ...twTalks[0], when: 'rain' }]
  writeFileSync(join(spotted, 'tinyland/world.json'), JSON.stringify(tws))
  const loose = validateCulture(spotted, 'tinyland')
  check('… and a key a talk has not got', !loose.ok && loose.errors.includes('talks') && loose.errors.includes('when'), !loose.ok && loose.errors)
  // the grammar book's page a turn of his names is one of the village's language, as a scene's
  const itPages = new Set(readdirSync(join(repo, 'grammar/it')).filter(f => f.endsWith('.json')).map(f => f.slice(0, -5)))
  const paged = validateCulture(resolve(android, '../../../../../../test/resources/cultures-test'), 'tinyland', undefined, undefined, itPages)
  const twp = JSON.parse(readFileSync(resolve(android, '../../../../../../test/resources/cultures-test/tinyland/world.json'), 'utf8'))
  twp.spots.kopa.keeper.talks[0].levels.A2.lines[1].choices[1].grammar = 'nowhere'
  writeFileSync(join(spotted, 'tinyland/world.json'), JSON.stringify(twp))
  const unpaged = validateCulture(spotted, 'tinyland', undefined, undefined, itPages)
  check(
    "… a turn of his talks names a page of the grammar book in the village's language",
    paged.ok && !unpaged.ok && unpaged.errors.includes('tinyland/world.json: spots.kopa.keeper: dialogs[3] (talks[0].levels.A2).lines[1].choices[1].grammar: no grammar page "nowhere" in companion/grammar/it'),
    { paged: !paged.ok && paged.errors, unpaged: !unpaged.ok && unpaged.errors },
  )
  tw.spots = { nowhere: tw.spots.kopa }
  writeFileSync(join(spotted, 'tinyland/world.json'), JSON.stringify(tw))
  const nowhere = validateCulture(spotted, 'tinyland')
  check('… and a spot the game hasn\'t got', !nowhere.ok && nowhere.errors.includes('tinyland/world.json') && nowhere.errors.includes('spots'), !nowhere.ok && nowhere.errors)
  const primorska = readManifest(culturesDir, 'primorska')
  check('primorska is complete, in Slovene, with its names in English and Italian', primorska.ok && primorska.manifest.status === 'complete' && primorska.manifest.language === 'sl' && !!primorska.manifest.name.en && !!primorska.manifest.name.it, primorska)

  // --- what the pack names by class is what the app has numbers for (Catalog.kt) ---------------------
  const catalog = readFileSync(join(android, 'Catalog.kt'), 'utf8')
  const prices = [...(catalog.match(/GOOD_PRICES[^\n]*/)?.[0] ?? '').matchAll(/"(\w+)" to \d+/g)].map(m => m[1])
  const effects = [...(catalog.match(/TOOL_EFFECTS[\s\S]*?\n {4}\)/)?.[0] ?? '').matchAll(/"(\w+)" to ToolEffect/g)].map(m => m[1])
  check('the price classes match Catalog.GOOD_PRICES', JSON.stringify(prices) === JSON.stringify(PRICES), prices)
  check('the tool effects match Catalog.TOOL_EFFECTS', JSON.stringify(effects) === JSON.stringify(TOOL_EFFECTS), effects)
  const cultures = readFileSync(join(android, 'culture/Cultures.kt'), 'utf8')
  const appFiles = [...(cultures.match(/val FILES = listOf\(([^)]*)\)/)?.[1] ?? '').matchAll(/"(\w+)"/g)].map(m => m[1])
  check("the pack's files are the app's (Cultures.FILES)", JSON.stringify(appFiles) === JSON.stringify(FILES), appFiles)

  // --- a broken pack says what is wrong, and where ------------------------------------------------
  const broken = tempDir('lani-smoke-cultures-')
  cpSync(join(culturesDir, 'primorska'), join(broken, 'primorska'), { recursive: true })
  const quests = JSON.parse(readFileSync(join(broken, 'primorska/quests.json'), 'utf8'))
  quests.requests[0].title = { en: "Micka's kitchen" }
  quests.requests[1].skill = 'gold'
  writeFileSync(join(broken, 'primorska/quests.json'), JSON.stringify(quests))
  const chest = JSON.parse(readFileSync(join(broken, 'primorska/chest.json'), 'utf8'))
  chest.thanks_default = 'gubana'
  chest.titel = 'x'
  writeFileSync(join(broken, 'primorska/chest.json'), JSON.stringify(chest))
  const b1 = validateCulture(broken, 'primorska')
  check('a pack with a wrong field names the file and the field', !b1.ok && b1.errors.includes('primorska/quests.json') && b1.errors.includes('requests[1].skill') && b1.errors.includes('primorska/chest.json') && b1.errors.includes('titel'), !b1.ok && b1.errors)
  delete chest.titel
  writeFileSync(join(broken, 'primorska/chest.json'), JSON.stringify(chest))
  quests.requests[1].skill = 'food'
  writeFileSync(join(broken, 'primorska/quests.json'), JSON.stringify(quests))
  const b2 = validateCulture(broken, 'primorska', [{ id: 'micka', name: 'Babica Micka' }])
  check('… a text without the pack\'s language, a good that isn\'t there, someone who isn\'t in the cast', !b2.ok && b2.errors.includes('quests.requests[0].title: no "sl" text') && b2.errors.includes('chest.thanks_default: no good "gubana"') && b2.errors.includes('no villager named "Pastir Luka"') && b2.errors.includes('no villager "luka"'), !b2.ok && b2.errors)
  // the pack's own people need their gift lines, in its language; the tutor's may go without (the app has plain ones)
  const l = (sl: string, en: string) => ({ sl, en, level: 0 })
  const cast0 = new VillagerStore(join(culturesDir, 'primorska', 'villagers'), join(dataDir, 'unused-villagers-primorska-gifts')).all()
  const gifts = { liked: [l('O, hvala!', 'Oh, thanks!')], ordinary: [{ it: 'Grazie.', en: 'Thanks.', level: 0 }], rare: [] }
  const b3 = validateCulture(culturesDir, 'primorska', [
    ...cast0.map(v => ({ id: v.id, name: v.name })),
    { id: 'micka', name: 'Babica Micka', source: 'curated', lines: {} },
    { id: 'luka', name: 'Pastir Luka', source: 'curated', lines: { gift: gifts } },
    { id: 'n-ana', name: 'Ana', source: 'tutor', lines: {} },
  ])
  check('… a villager of the pack without gift lines, a kind without a line, a gift line not in its language (not a tutor\'s villager)',
    !b3.ok && b3.errors.includes('villagers/micka.json: lines.gift: missing') && b3.errors.includes('villagers/luka.json: lines.gift.rare: no line') &&
      b3.errors.includes('villagers/luka.json: lines.gift.ordinary[0]: no "sl" text') && !b3.errors.includes('n-ana'), !b3.ok && b3.errors)

  // --- what the chest's things hold to read (readings/<id>.json) -------------------------------------
  const kt = readFileSync(join(android, 'Readings.kt'), 'utf8')
  const listed = (name: string) => [...(kt.match(new RegExp(`val ${name} = listOf\\(([^)]*)\\)`))?.[1] ?? '').matchAll(/"(\w+)"/g)].map(m => m[1])
  check("the reading kinds and levels are the app's (Readings.KINDS, LEVELS)", JSON.stringify(listed('KINDS')) === JSON.stringify(READING_KINDS) && JSON.stringify(listed('LEVELS')) === JSON.stringify(READING_LEVELS), [listed('KINDS'), listed('LEVELS')])
  const readable = (id: string) => {
    const v = validateCulture(culturesDir, id)
    return v.ok && 'files' in v.pack ? readingRefs(v.pack.files.chest).map(([where, r]) => `${where.replace(/^chest\./, '')} ${r}`) : []
  }
  check(
    "primorska's readable things: Micka's recipes (frtalja, potica), Janez's books, Mojca's textbook, Prešeren's poems",
    JSON.stringify(readable('primorska')) === JSON.stringify([
      'tools[3].first.read stara-pratika', 'tools[3].better.read knjiga-pregovorov', 'tools[4].first.read frtalja', 'tools[4].better.read potica',
      'tools[7].better.read ucbenik-dvojina', 'goods[15].read zdravljica',
    ]),
    readable('primorska'),
  )
  check("friuli's: Rosa's recipes (frico, gubana), Bepi's books", JSON.stringify(readable('friuli')) === JSON.stringify([
    'tools[3].first.read libro-antico', 'tools[3].better.read proverbi', 'tools[4].first.read frico', 'tools[4].better.read gubana',
  ]), readable('friuli'))
  check("kaernten's: Resi's recipes (Kasnudeln, Reindling), Sepp's books", JSON.stringify(readable('kaernten')) === JSON.stringify([
    'tools[3].first.read altes-buch', 'tools[3].better.read sprichwoerter', 'tools[4].first.read kasnudeln', 'tools[4].better.read reindling',
  ]), readable('kaernten'))
  const pr = cultureReadings(culturesDir, 'primorska', 'sl')
  const potica = pr.readings.get('potica')
  check(
    'the potica recipe: dough and filling, numbered steps, questions with the answer first',
    !!potica && potica.kind === 'recipe' && potica.ingredients[0].part?.sl === 'Testo' && potica.ingredients.some(i => i.part?.sl === 'Nadev') && potica.steps.length >= 10 && potica.questions.every(q => q.options.length === 3),
    potica,
  )
  const proverbs = pr.readings.get('knjiga-pregovorov')
  check('the book of proverbs: 10 to 12 proverbs, each with its meaning in Slovene and English', !!proverbs && proverbs.lines.length >= 10 && proverbs.lines.length <= 12 && proverbs.lines.every(l => l.means?.sl && l.means?.en), proverbs?.lines.length)
  check("lakeland's: Maggie's recipes (tatie pot, sticky toffee pudding), Wilf's books, Mrs Hartley's schoolbook", JSON.stringify(readable('lakeland')) === JSON.stringify([
    'tools[3].first.read counting-sheep', 'tools[3].better.read sayings', 'tools[4].first.read tatie-pot', 'tools[4].better.read sticky-toffee-pudding', 'tools[7].better.read plurals',
  ]), readable('lakeland'))
  const lr = cultureReadings(culturesDir, 'lakeland', 'en')
  check("lakeland's readings are in English with Slovene, Italian and German on every line", lr.errors.length === 0 && [...lr.readings.values()].every(r => [...r.steps, ...r.lines.map(l => l.text)].every(t => t.en && t.sl && t.it && t.de)), lr.errors)
  const fr = cultureReadings(culturesDir, 'friuli', 'it')
  check("friuli's readings are in Italian with Slovene and English on every line", fr.errors.length === 0 && [...fr.readings.values()].every(r => [...r.steps, ...r.lines.map(l => l.text)].every(t => t.it && t.sl && t.en)), fr.errors)

  const kr = cultureReadings(culturesDir, 'kaernten', 'de')
  check("kaernten's readings are in German with Slovene, English and Italian on every line", kr.errors.length === 0 && kr.readings.size === 4 && [...kr.readings.values()].every(r => [...r.steps, ...r.lines.map(l => l.text)].every(t => t.de && t.sl && t.en && t.it)), kr.errors)

  const badReads = tempDir('lani-smoke-readings-')
  cpSync(join(culturesDir, 'primorska'), join(badReads, 'primorska'), { recursive: true })
  const frtalja = JSON.parse(readFileSync(join(badReads, 'primorska/readings/frtalja.json'), 'utf8'))
  frtalja.steps[1] = { sl: 'Dodaj moko.' }
  frtalja.questions[0].options = ['8', '4']
  frtalja.by = 'nobody'
  writeFileSync(join(badReads, 'primorska/readings/frtalja.json'), JSON.stringify(frtalja))
  const rb1 = cultureReadings(badReads, 'primorska', 'sl', [{ id: 'micka' }])
  check('a reading with two options says so, with the file', rb1.errors.some(e => e.includes('primorska/readings/frtalja.json') && e.includes('questions[0].options')), rb1.errors)
  frtalja.questions[0].options = ['8', '4', '2']
  writeFileSync(join(badReads, 'primorska/readings/frtalja.json'), JSON.stringify(frtalja))
  const rb2 = cultureReadings(badReads, 'primorska', 'sl', [{ id: 'micka' }])
  check(
    '… a line without its translation, a reader not in the cast',
    rb2.errors.some(e => e.includes('reading.steps[1]: no "en"')) && rb2.errors.some(e => e.includes('by: no villager "nobody"')),
    rb2.errors,
  )
  const badChest = JSON.parse(readFileSync(join(badReads, 'primorska/chest.json'), 'utf8'))
  badChest.tools[4].better.read = 'strudel'
  writeFileSync(join(badReads, 'primorska/chest.json'), JSON.stringify(badChest))
  const badRead = validateCulture(badReads, 'primorska')
  check('a chest item that reads a reading the pack lacks names it', !badRead.ok && badRead.errors.includes('chest.tools[4].better.read: no reading "strudel"'), !badRead.ok && badRead.errors)

  // --- the night sky (sky.json, sky.ts) -------------------------------------------------------------------
  const skyRes = resolve(import.meta.dir, '../../../android/app/src/main/resources/sky')
  const figureIds = readFileSync(join(skyRes, 'figures.tsv'), 'utf8').split('\n').filter(l => l && !l.startsWith('#')).map(l => l.split('\t')[0])
  const starIds = readFileSync(join(skyRes, 'stars.tsv'), 'utf8').split('\n').filter(l => l && !l.startsWith('#')).map(l => l.split('\t')[6] ?? '').filter(Boolean).map(n => n.toLowerCase().replace(/ /g, '-'))
  check("sky.ts's figures and named stars are the app's catalog's (resources/sky)", JSON.stringify(figureIds) === JSON.stringify(SKY_FIGURES) && JSON.stringify(starIds) === JSON.stringify(SKY_STARS), { figureIds, starIds })
  const meteorsKt = readFileSync(resolve(android, 'sky/Meteors.kt'), 'utf8')
  const showerIds = [...meteorsKt.matchAll(/Shower\("(\w+)"/g)].map(m => m[1])
  check("sky.ts's meteor showers are the app's (Meteors.SHOWERS)", JSON.stringify(showerIds) === JSON.stringify(SKY_SHOWERS), showerIds)
  // a sky in a copy of primorska: a good one validates; a text without the pack's language, a thing the app doesn't draw,
  // a reading and a word pack the pack lacks are named
  const skies = tempDir('lani-smoke-culture-sky-')
  cpSync(join(culturesDir, 'primorska'), join(skies, 'primorska'), { recursive: true })
  const phases = Object.fromEntries(MOON_PHASES.map(p => [p, { sl: p, en: p }]))
  const goodSky = {
    schema: 'lani.sky/v0', language: 'sl', where: { lat: 45.99, lon: 13.75 },
    moon: { name: { sl: 'Luna', en: 'The Moon' }, phases, waxing: { sl: 'Luna narašča.', en: 'The moon is waxing.' }, waning: { sl: 'Luna pojema.', en: 'The moon is waning.' }, reading: 'stara-pratika', words: ['luna'] },
    things: [{ id: 'ori', name: { sl: 'Orion', en: 'Orion' }, folk: { sl: 'Kosci', en: 'The Mowers' }, lore: [{ level: 'A1', text: { sl: 'Tri zvezde so Kosci.', en: 'Three stars are the Mowers.' } }] }],
    meteors: { name: { sl: 'Utrinek', en: 'A shooting star' }, wish: { sl: 'Utrinek! Zaželi si nekaj.', en: 'A shooting star! Make a wish.' }, showers: [{ id: 'perseids', name: { sl: 'Perzeidi', en: 'The Perseids' }, tonight: { sl: 'Nocoj padajo Perzeidi.', en: 'Tonight the Perseids fall.' } }] },
  }
  writeFileSync(join(skies, 'primorska/sky.json'), JSON.stringify(goodSky))
  const sky1 = cultureSky(skies, 'primorska', 'sl', new Set(['stara-pratika']), new Set(['na-gricih']))
  check('a culture pack\'s sky.json validates: its place, the moon\'s phases, a constellation, a shower', sky1.errors.length === 0 && sky1.sky?.things[0].id === 'ori', sky1.errors)
  const badSky = structuredClone(goodSky) as any
  badSky.things.push({ id: 'unicorn', name: { sl: 'Samorog' } })
  badSky.moon.waxing = { en: 'The moon is waxing.' }
  badSky.moon.reading = 'almanah'
  badSky.pack = 'zvezde'
  writeFileSync(join(skies, 'primorska/sky.json'), JSON.stringify(badSky))
  const sky2 = cultureSky(skies, 'primorska', 'sl', new Set(['stara-pratika']), new Set(['na-gricih']))
  check('… a thing of the sky the app doesn\'t draw is named', sky2.errors.some(e => e.includes('things') && e.includes('a thing of the sky the app draws')), sky2.errors)
  delete badSky.things[1]
  badSky.things = badSky.things.filter(Boolean)
  writeFileSync(join(skies, 'primorska/sky.json'), JSON.stringify(badSky))
  const sky3 = cultureSky(skies, 'primorska', 'sl', new Set(['stara-pratika']), new Set(['na-gricih']))
  check('… a text without the pack\'s language, a reading and a word pack the pack lacks',
    sky3.errors.some(e => e.includes('sky.moon.waxing: no "sl"')) && sky3.errors.some(e => e.includes('no reading "almanah"')) && sky3.errors.some(e => e.includes('no word pack "zvezde"')), sky3.errors)
  const badPack = validateCulture(skies, 'primorska')
  check('… and validateCulture says so for the pack', !badPack.ok && badPack.errors.includes('primorska/sky.json'), !badPack.ok && badPack.errors)

  // --- which pack a bridge plays --------------------------------------------------------------------
  check('a profile asks for primorska unless it says otherwise', profileFromEnv({}).culture === DEFAULT_CULTURE && profileFromEnv({ LANI_CULTURE: 'friuli' }).culture === 'friuli')
  const stubs = tempDir('lani-smoke-cultures-stub-')
  cpSync(join(culturesDir, 'primorska'), join(stubs, 'primorska'), { recursive: true })
  mkdirSync(join(stubs, 'lagoon'))
  writeFileSync(join(stubs, 'lagoon/culture.json'), JSON.stringify({ schema: 'lani.culture/v0', id: 'lagoon', language: 'it', status: 'stub', region: { it: 'Laguna' }, name: { it: 'Laguna' } }))
  const r1 = resolveCulture(stubs, 'lagoon')
  const r2 = resolveCulture(culturesDir, 'atlantis')
  check('a stub or an unknown pack leaves the default, and says why', r1.id === 'primorska' && r1.wanted === 'lagoon' && /stub/.test(r1.why ?? '') && r2.id === 'primorska' && /missing/.test(r2.why ?? '') && !resolveCulture(culturesDir, 'primorska').why, { r1, r2 })
  const r3 = resolveCulture(culturesDir, 'friuli')
  check('the friuli pack is played for a profile that asks for it', r3.id === 'friuli' && !r3.why && r3.manifest?.language === 'it', r3)
  const r4 = resolveCulture(culturesDir, 'kaernten')
  check('the kaernten pack is played for a profile that asks for it', r4.id === 'kaernten' && !r4.why && r4.manifest?.language === 'de', r4)
  const r5 = resolveCulture(culturesDir, 'lakeland')
  check('the lakeland pack is played for a profile that asks for it', r5.id === 'lakeland' && !r5.why && r5.manifest?.language === 'en', r5)

  const c = await (await fetch(`${base}/culture`, { headers: auth })).json()
  check('GET /culture: the default profile plays primorska, in Slovene, with its places', c.id === 'primorska' && c.language === 'sl' && c.status === 'complete' && !c.wanted && c.places.some((p: any) => p.id === 'gorica' && p.name.en === 'Gorizia'), c)
  const all = await (await fetch(`${base}/cultures`, { headers: auth })).json()
  const served = all.map((p: any) => JSON.stringify([p.id, p.status, p.language]))
  check(
    'GET /cultures lists the packs and their status',
    JSON.stringify(all.map((p: any) => p.id)) === JSON.stringify(ids) && [['friuli', 'complete', 'it'], ['kaernten', 'complete', 'de'], ['lakeland', 'complete', 'en'], ['primorska', 'complete', 'sl']].every(p => served.includes(JSON.stringify(p))),
    all,
  )
  check('the culture needs the app token', (await fetch(`${base}/culture`)).status === 401 && (await fetch(`${base}/culture`, { headers: fam })).status === 403)
}
