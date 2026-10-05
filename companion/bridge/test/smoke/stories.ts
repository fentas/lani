// Smoke checks: the storyteller's evening stories (lani.story/v0, companion/SCENES.md "Stories"): the culture packs'
// stories (Stari Janez's legends at A1 and A2, some at B1, Martin Krpan in chapters; Nonno Bepi's in Italian), the
// campfire's story happening, the format's rules, a scene served with its teller's stories, and the tutor's tools.
import { Database } from 'bun:sqlite'
import { cpSync, existsSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { cultureStories } from '../../src/cultures'
import { normalizeText } from '../../src/family'
import { ownVoicesIn } from '../../src/profiles'
import { PackStore, packsDirs } from '../../src/packs'
import { chaptersOf, curatedPictureErrors, LEVELS, levelsOf, paragraphsOf, STORIES_LOW, storiesLeft, validateStory, VIGNETTES, type Story } from '../../src/stories'
import { VillagerStore } from '../../src/villagers'
import type { ResolvedScene } from '../../src/scenes'
import { clipKey, corpus, PRIORITY_LABELS } from '../../src/voice'
import { reference } from './fixtures'
import { auth, base, channelEvents, check, client, culturesDir, dataDir, guideOf, tempDir } from './harness'
import { JAN } from '../learner-content'
import { renderDeep } from '../../src/addressee'

/** The vignette library as Vignettes.kt declares it (comments stripped, then its three lists). */
function vignettesFromKotlin() {
  const kt = readFileSync(resolve(import.meta.dir, '../../../android/app/src/main/java/si/lanisce/lani/game/scene/Vignettes.kt'), 'utf8').replace(/\/\/.*$/gm, '')
  const list = (name: string) => [...(new RegExp(`val ${name} = listOf\\(([\\s\\S]*?)\\)`).exec(kt)?.[1] ?? '').matchAll(/"([^"]+)"/g)].map(m => m[1])
  return { backgrounds: list('backgrounds'), figures: list('figures'), props: list('props') }
}

/** A tool result's text. */
const text = (r: any) => (r.content ?? []).map((c: any) => c.text ?? '').join('\n')

/** Every text a story's tellings say or offer: the teller's lines, the choices, the replies. */
function said(s: Story): Record<string, unknown>[] {
  return chaptersOf(s).flatMap(c => Object.values(c.levels).flatMap(v => v!.lines.flatMap(l => (l.choices.length ? [...l.choices, ...l.choices.flatMap(ch => (ch.reply ? [ch.reply] : []))] : [l])))) as Record<string, unknown>[]
}

export default async function stories() {
  const repo = resolve(import.meta.dir, '../../..')
  const read = (culture: string) => {
    const dir = join(culturesDir, culture, 'stories')
    return readdirSync(dir).filter(f => f.endsWith('.json')).sort().map(f => JSON.parse(readFileSync(join(dir, f), 'utf8')))
  }

  // --- the culture packs' stories -------------------------------------------------------------------
  // as the bridge serves them to Jan (said to the learner of the profile: addressee.ts)
  const jan = read('primorska').map(raw => validateStory(raw)).flatMap(v => (v.ok ? [renderDeep(v.story, JAN)] : []))
  const janIds = jan.sort((a, b) => (a.order ?? 0) - (b.order ?? 0)).map(s => s.id)
  check(
    "primorska: sixteen of Stari Janez's legends and tales, in their order",
    JSON.stringify(janIds) === JSON.stringify([
      'zlatorog', 'kralj-matjaz', 'martin-krpan', 'peter-klepec', 'lepa-vida', 'povodni-moz', 'zvon-zelja', 'desetnica', 'ajdovska-deklica',
      'zmaj-ljubljanski', 'mojca-pokrajculja', 'bogatinovo-zlato', 'pegam-in-lambergar', 'kekec', 'veronika-deseniska', 'jurij-kozjak',
    ]) && jan.every(s => s.teller === 'janez' && s.language === 'sl'),
    janIds,
  )
  check(
    '… each told at A1 and A2, seven at B1 too',
    jan.every(s => levelsOf(s).includes('A1') && levelsOf(s).includes('A2')) &&
      jan.filter(s => levelsOf(s).includes('B1')).map(s => s.id).join() === 'zlatorog,kralj-matjaz,lepa-vida,zmaj-ljubljanski,bogatinovo-zlato,pegam-in-lambergar,veronika-deseniska',
    jan.map(s => [s.id, levelsOf(s)]),
  )
  check(
    "… the newer seven: Veronika Deseniška in two evenings, Jurij Kozjak in three, the gold of Bogatin going on from Zlatorog, their words in the pack pripovedke",
    chaptersOf(jan.find(s => s.id === 'veronika-deseniska')!).length === 2 && chaptersOf(jan.find(s => s.id === 'jurij-kozjak')!).length === 3 &&
      jan.find(s => s.id === 'bogatinovo-zlato')?.continues === 'zlatorog' && jan.slice(9).every(s => s.pack === 'pripovedke' && /not reviewed by a native speaker/.test(s.about?.en ?? '')),
    jan.slice(9).map(s => [s.id, s.pack, s.continues]),
  )
  const krpan = jan.find(s => s.id === 'martin-krpan')
  check('Martin Krpan is told in four chapters, each with its teaser', chaptersOf(krpan!).length === 4 && krpan!.chapters!.every(c => c.teaser.sl?.startsWith('Janez pove') && c.teaser.en), krpan?.chapters?.map(c => c.teaser))
  check(
    '… every story says what Janez tells (for "Jutri"), what he remembers, and teaches words; every line in Slovene and English',
    jan.every(s => s.teaser.sl?.startsWith('Janez pove') && s.memory.sl && s.words.length >= 3 && said(s).every(t => typeof t.sl === 'string' && typeof t.en === 'string')),
    jan.map(s => [s.id, s.teaser.sl, s.words.length]),
  )
  check("… a story's lines are the teller's alone (no who), with a turn for the learner in every telling", jan.every(s => chaptersOf(s).every(c => Object.values(c.levels).every(v => v!.lines.every(l => !l.who) && v!.lines.some(l => l.choices.length)))))
  check('… the wishing bell rings: the campfire\'s distant bell', JSON.stringify(jan.find(s => s.id === 'zvon-zelja')).includes('"bell":1'))
  const second = read('friuli').map(raw => validateStory(raw)).flatMap(v => (v.ok ? [renderDeep(v.story, JAN)] : []))
  check(
    "friuli: ten of Nonno Bepi's legends in Italian, at A1 and A2, every text in Italian, Slovene and English, marked machine-written",
    second.length === 10 && second.every(s => s.teller === 'bepi' && s.language === 'it' && levelsOf(s).includes('A1') && levelsOf(s).includes('A2') && /not reviewed by a native speaker/.test(s.about?.en ?? '') && said(s).every(t => typeof t.it === 'string' && typeof t.sl === 'string' && typeof t.en === 'string') && !!s.teaser.it && !!s.memory.it),
    second.map(s => [s.id, levelsOf(s)]),
  )

  const carinthia = read('kaernten').map(raw => validateStory(raw)).flatMap(v => (v.ok ? [v.story] : []))
  check(
    "kaernten: ten of Opa Sepp's legends in German, in their order, at A1 and A2, every text in German, Slovene, English and Italian, marked machine-written",
    JSON.stringify(carinthia.sort((a, b) => (a.order ?? 0) - (b.order ?? 0)).map(s => s.id)) ===
      JSON.stringify(['der-lindwurm', 'der-dobratsch', 'der-woerthersee', 'koenig-matthias', 'die-riesen', 'die-maultasch-vor-hochosterwitz', 'das-kasermandl', 'die-perchtl', 'die-tausend-statuen', 'die-heilige-hemma']) &&
      carinthia.every(s => s.teller === 'sepp' && s.language === 'de' && levelsOf(s).includes('A1') && levelsOf(s).includes('A2') && /not reviewed by a native speaker/.test(s.about?.en ?? '') && said(s).every(t => typeof t.de === 'string' && typeof t.sl === 'string' && typeof t.en === 'string' && typeof t.it === 'string') && !!s.teaser.de && !!s.memory.de),
    carinthia.map(s => [s.id, levelsOf(s)]),
  )

  // --- their books' pictures ------------------------------------------------------------------------------
  const lake = read('lakeland').map(raw => validateStory(raw)).flatMap(v => (v.ok ? [v.story] : []))
  const packs4 = [...jan, ...second, ...carinthia, ...lake]
  check(
    "every curated story of the four packs has its book's pictures: 2-4, one or two an evening in chapters, 116 in all",
    packs4.length === 46 && packs4.every(s => curatedPictureErrors(s).length === 0) && packs4.reduce((n, s) => n + (s.pictures?.length ?? 0), 0) === 116,
    packs4.map(s => [s.id, s.pictures?.length, curatedPictureErrors(s)]),
  )
  check(
    "… each from the library, after a paragraph its evening has, its caption in the story's language and the three others",
    packs4.every(s => (s.pictures ?? []).every(p => {
      const ch = chaptersOf(s)[(p.chapter ?? 1) - 1]
      const most = Math.max(...Object.values(ch.levels).map(v => paragraphsOf(v!.lines)))
      return p.after >= 1 && p.after <= most && ['sl', 'en', 'de', 'it'].every(l => typeof (p.caption as Record<string, unknown>)[l] === 'string') &&
        (VIGNETTES.backgrounds as readonly string[]).includes(p.bg) && p.figures.length + p.props.length > 0
    })),
  )
  check('… Zlatorog white with his golden horns, and a picture every evening of Martin Krpan', jan.find(s => s.id === 'zlatorog')!.pictures!.some(p => p.figures.some(f => f.id === 'chamois' && f.gold)) &&
    [1, 2, 3, 4].every(c => jan.find(s => s.id === 'martin-krpan')!.pictures!.some(p => (p.chapter ?? 1) === c)))
  // --- their notes: every telling written down for the notebook ---------------------------------------------
  const tellings = (s: Story) => chaptersOf(s).flatMap((c, i) => LEVELS.filter(l => c.levels[l]).map(l => `${i + 1}/${l}`))
  const notedAs = (s: Story) => (s.notes ?? []).map(n => `${n.chapter ?? 1}/${n.level}`)
  check(
    "every curated story is written down for the notebook: notes for each of its 112 tellings, a place for each picture, in the four languages, marked machine-written",
    packs4.every(s => JSON.stringify(notedAs(s).sort()) === JSON.stringify(tellings(s).sort()) && /machine-written/.test(s.review ?? '')) &&
      packs4.reduce((n, s) => n + (s.notes?.length ?? 0), 0) === 112 &&
      packs4.every(s => (s.notes ?? []).every(n => n.after?.length === (s.pictures ?? []).filter(p => (p.chapter ?? 1) === (n.chapter ?? 1)).length &&
        n.text.every(p => ['sl', 'en', 'de', 'it'].every(l => typeof (p as Record<string, unknown>)[l] === 'string')))),
    packs4.filter(s => JSON.stringify(notedAs(s).sort()) !== JSON.stringify(tellings(s).sort())).map(s => [s.id, notedAs(s), tellings(s)]),
  )

  // --- the campfire: the storyteller's evening happening ------------------------------------------------
  const fire = JSON.parse(readFileSync(join(repo, 'scenes/ob-ognju.json'), 'utf8'))
  const zgodba = fire.happenings.find((h: any) => h.who === 'janez')
  check(
    "the campfire: Janez tells a story every evening (a stories happening, no dialog of its own), Zlatorog and King Matjaž moved into the stories",
    zgodba?.stories === true && zgodba.chance === 1 && JSON.stringify(zgodba.when) === '["evening","night"]' && !zgodba.dialog && zgodba.memory?.sl &&
      !fire.dialogs.some((d: any) => ['zlatorog', 'kralj-matjaz'].includes(d.id)) && fire.happenings.filter((h: any) => h.who === 'janez').length === 1,
    zgodba,
  )
  const fuoco = JSON.parse(readFileSync(join(culturesDir, 'friuli/scenes/al-fuoco.json'), 'utf8'))
  check("friuli's campfire: Nonno Bepi tells his in the evening", fuoco.art === 'campfire' && JSON.stringify(fuoco.from) === '["fire"]' && fuoco.language === 'it' && fuoco.happenings.some((h: any) => h.stories && fuoco.people.find((p: any) => p.id === h.who)?.villager === 'bepi'))
  const feuer = JSON.parse(readFileSync(join(culturesDir, 'kaernten/scenes/am-feuer.json'), 'utf8'))
  check("kaernten's campfire: Opa Sepp tells his in the evening", feuer.art === 'campfire' && JSON.stringify(feuer.from) === '["fire"]' && feuer.language === 'de' && feuer.happenings.some((h: any) => h.stories && feuer.people.find((p: any) => p.id === h.who)?.villager === 'sepp'))

  // --- the format's rules -----------------------------------------------------------------------------
  const refs = {
    packs: new PackStore(packsDirs(join(repo, 'packs'), culturesDir, 'primorska', 'sl'), join(dataDir, 'unused-packs-stories')).all(),
    villagers: new VillagerStore(join(culturesDir, 'primorska', 'villagers'), join(dataDir, 'unused-villagers-stories')).all(),
  }
  const line = (sl: string) => ({ sl, en: `(${sl})` })
  const turn = { choices: [{ sl: 'Kaj pa potem?', en: 'And then?', ok: true, reply: line('Poslušaj.') }, { sl: 'Kdo pa potem?', en: 'Who then?', why: 'Kdo is who.' }] }
  const telling = { lines: [line('Nekoč je živel kralj.'), turn, line('Lahko noč, fant.')] }
  const story = {
    schema: 'lani.story/v0', id: 'smoke-kralj', title: 'Kralj · The king', teller: 'janez', teaser: { sl: 'Janez pove o kralju', en: 'Janez tells of a king' },
    memory: { sl: 'zgodbo o kralju', en: 'the story of the king' }, pack: 'zgodbe', words: ['kralj', { word: 'gora', pack: 'v-gorah' }], levels: { A2: telling },
  }

  // --- the voice corpus: voice-build voices the A1 tellings before the scenes, the others after them ----------------------
  // (the car plays a telling only when its clips exist; the app by the fire and the car ask for the teller's lines in his
  // own voice first, "@janez", when he has one)
  const ownJanez = (id: string) => id === 'janez'
  const voiced = corpus({ learner: JAN, packs: [], modules: [], scenarios: [], villagers: refs.villagers, stories: [...jan, ...second], ownVoice: ownJanez })
  const told = voiced.filter(i => i.source.startsWith('story:'))
  const at = (t: string | undefined) => told.find(i => i.text === t)
  const zlatorog = jan.find(s => s.id === 'zlatorog')!
  const a1 = zlatorog.levels!.A1!.lines
  const opening = at('Dober večer, Jan. Sedi k ognju. Nocoj ti povem zgodbo.')
  check(
    "corpus: a story's teller line in the teller's own voice (Stari Janez: @janez), at A1 before the scenes ('stories A1'), its source the story",
    a1[0].sl === opening?.text && opening?.voice === '@janez' && PRIORITY_LABELS[opening.priority] === 'stories A1' && opening.priority < PRIORITY_LABELS.indexOf('scenes') && opening.source === 'story:zlatorog' &&
      // the key the app and the car ask for first (RoadGatherTest has the same): the normalized text in his own voice
      clipKey(normalizeText(opening.text), opening.voice) === '@janez:dober večer jan sedi k ognju nocoj ti povem zgodbo',
    opening,
  )
  const turn1 = a1[1].choices
  const right = turn1.find(c => c.ok)!
  const wrong = turn1.find(c => !c.ok)!
  check(
    "corpus: the learner's right choice in the default voice, the reply to it and to a wrong one in the teller's; a wrong choice itself isn't voiced",
    at(right.sl)?.voice === 'female' && at(right.reply?.sl)?.voice === '@janez' && at(wrong.reply?.sl)?.voice === '@janez' && !at(wrong.sl) && at(right.sl)?.priority === opening?.priority,
    turn1.map(c => [c.sl, at(c.sl), at(c.reply?.sl)]),
  )
  const shared = corpus({ learner: JAN, packs: [], modules: [], scenarios: [], villagers: refs.villagers, stories: [zlatorog], ownVoice: () => false })
  const ownDb = new Database(':memory:')
  ownDb.exec('CREATE TABLE profiles (person TEXT PRIMARY KEY, archetype TEXT NOT NULL, elevenlabs TEXT, status TEXT NOT NULL)')
  ownDb.exec("INSERT INTO profiles VALUES ('janez', 'grandpa', 'el-janez', 'own'), ('micka', 'grandma', NULL, 'shared'), ('luka', 'young-man', 'el-luka', 'designing')")
  check(
    "… without a voice of his own yet, in his speaker (grandpa, the scenes' mapping); a voice of one's own counts once it is ready (voice-build reads the profiles)",
    shared.find(i => i.text === opening?.text)?.voice === 'grandpa' && shared.find(i => i.text === right.reply?.sl)?.voice === 'grandpa' &&
      JSON.stringify([...ownVoicesIn(ownDb)]) === JSON.stringify([['janez', { elevenlabs: 'el-janez', archetype: 'grandpa' }]]) && ownVoicesIn(new Database(':memory:')).size === 0,
  )
  const firstWrong = told.findIndex(i => i.text === wrong.reply?.sl)
  const spoken = (lv: 'A1' | 'A2' | 'B1') => new Set(jan.flatMap(s => chaptersOf(s).flatMap(c => (c.levels[lv]?.lines ?? []).filter(l => !l.choices.length).map(l => l.sl))))
  const onlyA2 = [...spoken('A2')].find(t => !spoken('A1').has(t))
  const onlyB1 = [...spoken('B1')].find(t => !spoken('A1').has(t) && !spoken('A2').has(t))
  const [p1, pScenes, p2, p3] = ['stories A1', 'scenes', 'stories A2', 'stories B1+'].map(l => PRIORITY_LABELS.indexOf(l))
  check(
    '… A1 tellings first (the lines the car plays, then the replies to wrong choices), then the scenes, then A2, then B1; every curated story, placeholders left out',
    !!opening && p1 >= 0 && p1 < pScenes && pScenes < p2 && p2 < p3 && at(onlyA2)?.priority === p2 && at(onlyB1)?.priority === p3 &&
      told.every((x, k) => k === 0 || told[k - 1].priority <= x.priority) &&
      firstWrong > told.findIndex(i => i.text === a1.at(-1)?.sl) && firstWrong < told.findIndex(i => i.priority === p2) &&
      jan.every(s => told.some(i => i.source === `story:${s.id}`)) && !told.some(i => i.text.includes('{')),
    { onlyA2: at(onlyA2), onlyB1: at(onlyB1), firstWrong, labels: PRIORITY_LABELS },
  )
  // a scene whose dialog has the learner say what they say at the fire in Zlatorog, and a line of its own
  const fireside = {
    id: 'smoke-ogenj', language: 'sl', objects: [], people: [{ id: 'ded', villager: 'janez', art: 'grandpa' }],
    dialogs: [{ id: 'd', lines: [{ who: 'ded', sl: 'Kar sedi, fant.', en: 'Sit, lad.', choices: [] }, { choices: [{ sl: right.sl, en: right.en, ok: true }] }] }],
  } as unknown as ResolvedScene
  const inOrder = corpus({ learner: JAN, packs: [], modules: [], scenarios: [], villagers: refs.villagers, scenes: [fireside], stories: [zlatorog], ownVoice: ownJanez })
  const idx = (t: string | undefined) => inOrder.findIndex(i => i.text === t)
  check(
    "… in the prebuild's order: the A1 telling before the scene's dialog (a text of both is the telling's), the A2 telling after it",
    inOrder[idx(right.sl)]?.priority === p1 && inOrder[idx(right.sl)]?.source === 'story:zlatorog' && inOrder[idx('Kar sedi, fant.')]?.priority === pScenes &&
      idx(opening?.text) < idx('Kar sedi, fant.') && idx('Kar sedi, fant.') < inOrder.findIndex(i => i.priority === p2) && inOrder.some(i => i.priority === p2),
    inOrder.map(i => [i.priority, i.source, i.text]).slice(0, 4),
  )
  check(
    "… the village's language only (Nonno Bepi's Italian ones not in a Slovene village's), a teller not in the cast in the female narrator's voice",
    !told.some(i => second.some(s => i.source === `story:${s.id}`)) &&
      corpus({ learner: JAN, packs: [], modules: [], scenarios: [], stories: [zlatorog] }).find(i => i.text === opening?.text)?.voice === 'female' &&
      corpus({ learner: JAN, packs: [], modules: [], scenarios: [], stories: second, language: 'it' }).some(i => i.source === `story:${second[0].id}` && i.voice === 'female'),
  )
  const placeholder = { ...zlatorog, levels: { A1: { lines: [{ ...a1[0], sl: 'Imaš {n} let.' }, ...a1.slice(1)] } } } as Story
  check("… a text with a placeholder is voiced when played, as the scenes'", !corpus({ packs: [], modules: [], scenarios: [], stories: [placeholder] }).some(i => i.text.includes('{')))

  const good = validateStory(story, refs)
  check('a tutor story at one level validates', good.ok, !good.ok && good.errors)
  const example = [...reference.matchAll(/```json\n([\s\S]*?)\n```/g)].map(m => m[1]).find(x => x.includes('lani.story/v0'))
  const exampleOk = example ? validateStory(JSON.parse(example), refs) : undefined
  check('module-spec.md story example validates', exampleOk?.ok === true, exampleOk)
  const withLines = (lines: unknown[]) => ({ ...story, levels: { A2: { lines } } })
  const bad: [string, unknown, string][] = [
    ['levels and chapters both', { ...story, chapters: [{ teaser: story.teaser, levels: { A2: telling } }, { teaser: story.teaser, levels: { A2: telling } }] }, 'not both'],
    ['a chapter without a level the others have', { ...story, levels: undefined, chapters: [{ teaser: story.teaser, levels: { A1: telling, A2: telling } }, { teaser: story.teaser, levels: { A2: telling } }] }, 'same levels'],
    ['a level that is none', { ...story, levels: { A3: telling } }, 'A3'],
    ['no level at all', { ...story, levels: {} }, 'at least one level'],
    ['someone else speaking', withLines([{ ...line('Oj.'), who: 'luka' }, turn, line('Adijo.')]), 'the teller tells the story'],
    ['a telling that opens with choices', withLines([turn, line('Oj.'), line('Adijo.')]), 'first line'],
    ['no turn for the learner', withLines([line('Ena.'), line('Dve.'), line('Tri.')]), 'no turn for the learner'],
    ['a wrong choice without why', withLines([line('Oj.'), { choices: [turn.choices[0], { sl: 'Ne.', en: 'No.' }] }, line('Adijo.')]), 'needs a why'],
    ['an effect the fire does not have', withLines([{ ...line('Oj.'), fx: { oven: 1 } }, turn, line('Adijo.')]), 'no effect "oven"'],
    ['sky_stays without a sky cue', { ...story, levels: { A2: { ...telling, sky_stays: false } } }, 'sky_stays'],
    ['a line without English', withLines([{ sl: 'Oj.' }, turn, line('Adijo.')]), 'needs sl and en'],
    ['a word its pack does not have', { ...story, words: ['zmaj'] }, 'pack "zgodbe" has no word "zmaj"'],
    ['a teller not in the cast', { ...story, teller: 'bepi' }, 'not in the cast'],
    ['a teller who is not the storyteller', { ...story, teller: 'luka' }, "isn't the village's storyteller"],
  ]
  for (const [label, s, mention] of bad) {
    const v = validateStory(s, refs)
    check(`a story is rejected: ${label}`, !v.ok && v.errors.includes(mention), v.ok || v.errors)
  }

  // --- the books' pictures, and a story going on from another ------------------------------------------------
  const kt = vignettesFromKotlin()
  check('Vignettes.kt parsed: 12 backgrounds, 24 figures, 20 props', kt.backgrounds.length === 12 && kt.figures.length === 24 && kt.props.length === 20, kt)
  check('the bridge\'s vignette library matches Vignettes.kt', JSON.stringify(kt) === JSON.stringify(VIGNETTES), { kt, ts: VIGNETTES })
  check("a book's paragraphs are the teller's lines between the learner's turns", paragraphsOf(telling.lines) === 2 && paragraphsOf([line('A'), line('B'), turn, turn, line('C')] as any) === 2 && paragraphsOf([turn] as any) === 0)
  const pic = { after: 1, bg: 'mountains', figures: [{ id: 'hunter', x: 0.3 }, { id: 'chamois', x: 0.7, y: 0.3, gold: true, flip: true }], props: [{ id: 'flower', x: 0.5 }], caption: { sl: 'Lovec in Zlatorog', en: 'The hunter and Zlatorog' } }
  const pictured = validateStory({ ...story, pictures: [pic, { ...pic, after: 0, bg: 'cave', night: true, figures: [{ id: 'king', x: 0.5, size: 1.4 }], props: [] }] }, refs)
  check('a story with its pictures validates', pictured.ok && pictured.story.pictures?.length === 2, !pictured.ok && pictured.errors)
  const withPic = (p: object) => ({ ...story, pictures: [{ ...pic, ...p }] })
  const badPictures: [string, unknown, string][] = [
    ['a background the library has not', withPic({ bg: 'desert' }), 'bg'],
    ['a figure the library has not', withPic({ figures: [{ id: 'unicorn', x: 0.5 }] }), 'figures'],
    ['a prop the library has not', withPic({ props: [{ id: 'car', x: 0.5 }] }), 'props'],
    ['a figure out of the picture', withPic({ figures: [{ id: 'king', x: 1.5 }] }), 'x'],
    ['a figure too big', withPic({ figures: [{ id: 'giant', x: 0.5, size: 3 }] }), 'size'],
    ['nothing in the picture', withPic({ figures: [], props: [] }), 'a figure or a prop'],
    ['a field the picture has not', withPic({ colour: 'red' }), 'colour'],
    ['a chapter the story has not', withPic({ chapter: 2 }), 'the story has one evening'],
    ['after more paragraphs than it has', withPic({ after: 3 }), 'at most 2 paragraphs'],
    ['a caption without the story\'s language', withPic({ caption: { en: 'The hunter' } }), 'no "sl" text'],
    ['a caption without English', withPic({ caption: { sl: 'Lovec' } }), 'no "en" text'],
    ['a story going on from itself', { ...story, continues: 'smoke-kralj' }, 'the story itself'],
  ]
  for (const [label, s, mention] of badPictures) {
    const v = validateStory(s, refs)
    check(`a story is rejected: ${label}`, !v.ok && v.errors.includes(mention), v.ok || v.errors)
  }
  // an older story file, without pictures or continues, reads as it did
  check('a story from before the books validates as before', validateStory(story, refs).ok && validateStory(story, refs).ok && !(validateStory(story, refs) as any).story.pictures)
  // --- the notebook's notes: a telling written down, where its pictures go -------------------------------------
  const written = { level: 'A2', after: [1], text: [{ sl: 'Nekoč je živel kralj.', en: 'Once there lived a king.' }, { sl: 'Ponoči je spal.', en: 'At night he slept.' }] }
  const noted = validateStory({ ...story, pictures: [pic], notes: [written], review: 'notes: machine-written (Claude) in sl and en; not reviewed by a native speaker' }, refs)
  check('a story with its notes validates, and keeps them', noted.ok && noted.story.notes?.[0].text.length === 2 && !!noted.story.review, !noted.ok && noted.errors)
  const withNote = (n: object, s: object = {}) => ({ ...story, pictures: [pic], ...s, notes: [{ ...written, ...n }] })
  const badNotes: [string, unknown, string][] = [
    ['notes at a level the story is not told at', withNote({ level: 'A1' }), "isn't told at A1"],
    ['notes of an evening the story has not', withNote({ chapter: 2 }), 'the story has one evening'],
    ['a paragraph without the story\'s language', withNote({ text: [{ en: 'Once there lived a king.' }] }), 'no "sl" text'],
    ['a paragraph without English', withNote({ text: [{ sl: 'Nekoč je živel kralj.' }] }), 'no "en" text'],
    ['a place for more pictures than the evening has', withNote({ after: [1, 2] }), 'its evening has 1 pictures'],
    ['a picture after a paragraph it has not', withNote({ after: [5] }), 'it has 2 paragraphs'],
    ['a field a note has not', withNote({ mood: 'sad' }), 'mood'],
    ['the same telling written down twice', { ...story, notes: [written, written] }, 'has notes already'],
  ]
  for (const [label, s, mention] of badNotes) {
    const v = validateStory(s, refs)
    check(`a story is rejected: ${label}`, !v.ok && v.errors.includes(mention), v.ok || v.errors)
  }
  // a broken culture story says the file and what: another language, a level missing, the same order twice
  const broken = tempDir('lani-smoke-culture-stories-')
  cpSync(join(culturesDir, 'primorska'), join(broken, 'primorska'), { recursive: true })
  const zl = JSON.parse(readFileSync(join(broken, 'primorska/stories/zlatorog.json'), 'utf8'))
  delete zl.levels.A2
  // (a level taken out takes its notes with it)
  zl.notes = zl.notes?.filter((n: { level: string }) => n.level !== 'A2')
  zl.order = 2
  writeFileSync(join(broken, 'primorska/stories/zlatorog.json'), JSON.stringify(zl))
  writeFileSync(join(broken, 'primorska/stories/le-agane.json'), readFileSync(join(culturesDir, 'friuli/stories/le-agane.json')))
  // a continuation of a story told after it, and one of a story the pack hasn't
  const vida = JSON.parse(readFileSync(join(broken, 'primorska/stories/lepa-vida.json'), 'utf8'))
  writeFileSync(join(broken, 'primorska/stories/lepa-vida.json'), JSON.stringify({ ...vida, continues: 'desetnica' }))
  const moz = JSON.parse(readFileSync(join(broken, 'primorska/stories/povodni-moz.json'), 'utf8'))
  writeFileSync(join(broken, 'primorska/stories/povodni-moz.json'), JSON.stringify({ ...moz, continues: 'rusalka' }))
  const errs = cultureStories(broken, 'primorska', 'sl', refs.villagers).join('\n')
  check(
    "a culture's broken stories are named with their file: a level missing, the same order twice, another language and another cast's teller, a continuation before its story or of none",
    errs.includes('primorska/stories/zlatorog.json') && errs.includes('no A2') && errs.includes('order 2') && errs.includes('primorska/stories/le-agane.json') && errs.includes('"bepi" is not in the cast') &&
      errs.includes('primorska/stories/lepa-vida.json: continues "desetnica" (order 8), so its order comes after it') && errs.includes('continues "rusalka", which isn\'t a story of the pack'),
    errs,
  )

  // --- served: a scene where someone tells stories comes with theirs --------------------------------------
  const camp = {
    schema: 'lani.scene/v0', id: 'smoke-zgodbe', title: 'Ob ognju · At the campfire', emoji: '🔥', art: 'campfire', from: ['fire'],
    people: [{ id: 'janez', name: 'Stari Janez', emoji: '👴', art: 'grandpa', slot: 'right', villager: 'janez' }],
    happenings: [{ id: 'zgodba', title: 'Janez pripoveduje · Janez tells a story', who: 'janez', when: ['evening', 'night'], chance: 1, stories: true, reward: { wisdom: 30 }, marker: '📖', memory: line('večer ob ognju') }],
  }
  const noVillager = await client.callTool({ name: 'publish_scene', arguments: { scene: { ...camp, people: [{ ...camp.people[0], villager: undefined }] } } })
  check('publish_scene rejects a storyteller without a villager', noVillager.isError && text(noVillager).includes('needs a villager'), text(noVillager))
  const both = await client.callTool({ name: 'publish_scene', arguments: { scene: { ...camp, happenings: [{ ...camp.happenings[0], dialog: 'x' }], dialogs: [{ id: 'x', lines: [{ who: 'janez', ...line('Oj.') }, turn, { who: 'janez', ...line('Adijo.') }] }] } } })
  check('publish_scene rejects a happening that tells stories and plays a dialog', both.isError && text(both).includes('not both'), text(both))
  const pub = await client.callTool({ name: 'publish_scene', arguments: { scene: camp } })
  check('publish_scene publishes a storyteller\'s evening', !pub.isError, text(pub))
  const served = await (await fetch(`${base}/scenes/smoke-zgodbe`, { headers: auth })).json()
  check(
    "GET /scenes/:id: the scene comes with its teller's stories, in their order, Slovene only",
    JSON.stringify(served.stories?.map((s: any) => s.id)) === JSON.stringify(janIds) && served.stories.every((s: any) => s.teller === 'janez' && s.language === 'sl' && s.source === 'curated' && s.culture === 'primorska') && served.happenings[0].stories === true,
    served.stories?.map((s: any) => s.id),
  )
  const gams = served.stories?.[0]?.words.find((w: any) => w.id === 'gams')
  check("a story's words are served resolved from their packs", gams?.sl === 'gams' && gams.en === 'chamois' && gams.pack === 'zgodbe' && served.stories[0].words.every((w: any) => w.en), gams)
  const all = await (await fetch(`${base}/scenes`, { headers: auth })).json()
  check('GET /scenes: the same, and a scene where nobody tells stories has none', all.find((s: any) => s.id === 'smoke-zgodbe')?.stories?.length === janIds.length && all.filter((s: any) => s.id !== 'smoke-zgodbe').every((s: any) => s.stories === undefined), all.map((s: any) => [s.id, s.stories?.length]))
  const list = await (await fetch(`${base}/stories`, { headers: auth })).json()
  check('GET /stories serves the collection', list.length === janIds.length && list[2].id === 'martin-krpan' && list[2].chapters.length === 4)
  check('stories need the app token', (await fetch(`${base}/stories`)).status === 401)

  // --- the tutor: list, publish, remove -------------------------------------------------------------------
  const game = await (await fetch(`${base}/game`, { headers: auth })).json()
  const heard = { zlatorog: { told: 1, level: 'A2' }, 'martin-krpan': { chapter: 2 } }
  await fetch(`${base}/game`, { method: 'PUT', headers: auth, body: JSON.stringify({ rev: game.rev, state: { ...game.state, stories: heard } }) })
  const ls = JSON.parse(text(await client.callTool({ name: 'list_stories', arguments: {} })))
  const lz = ls.stories.find((s: any) => s.id === 'zlatorog')
  check(
    'list_stories: the collection in order, its levels and chapters, what the learner has heard, their level',
    ls.learner_level && ls.all_heard === false && ls.stories.length === janIds.length && JSON.stringify(lz.levels) === '["A1","A2","B1"]' && lz.heard.told === 1 && lz.heard.level === 'A2' &&
      ls.stories.find((s: any) => s.id === 'martin-krpan').heard.chapter === 2 && ls.stories.find((s: any) => s.id === 'martin-krpan').chapters === 4,
    ls,
  )
  check(
    "list_stories: those left at the learner's level (not heard to their end), each story's pictures, and the vignette library",
    JSON.stringify(ls.left_at_level) === JSON.stringify(janIds.filter(id => id !== 'zlatorog')) && ls.stories.every((s: any) => typeof s.pictures === 'number') &&
      ls.picture_library.figures.includes('chamois') && ls.picture_library.backgrounds.includes('cave') && ls.picture_library.max_figures === 6,
    { left: ls.left_at_level, library: ls.picture_library },
  )
  check('no stories_low while plenty are left', !channelEvents.some(n => n.params?.meta?.kind === 'stories_low'))
  const rejects: [string, unknown, string][] = [
    ['a teller who is not the storyteller', { ...story, teller: 'luka' }, "isn't the village's storyteller"],
    ['a story in Italian for the Slovene village', { ...story, language: 'it', title: { it: 'Il re', en: 'The king' }, teaser: { it: 'Janez racconta', en: 'x' }, memory: { it: 'x', en: 'x' }, levels: { A2: { lines: [{ it: 'Oj.', en: 'x' }, { choices: [{ it: 'A', en: 'a', ok: true }, { it: 'B', en: 'b', why: 'x' }] }, { it: 'Ciao.', en: 'x' }] } } }, 'the village speaks "sl"'],
    ['a word its pack does not have', { ...story, words: ['zmaj'] }, 'no word "zmaj"'],
  ]
  for (const [label, s, mention] of rejects) {
    const r = await client.callTool({ name: 'publish_story', arguments: { story: s } })
    check(`publish_story rejects ${label}`, r.isError && text(r).includes(mention), text(r))
  }
  const published = await client.callTool({ name: 'publish_story', arguments: { story, note: 'Janez has a new story tonight' } })
  check('publish_story publishes, and says where it is told', !published.isError && text(published).includes('published story smoke-kralj (A2; 1 evening, 2 words, 0 pictures), told in smoke-zgodbe'), text(published))
  // a continuation: of a story of the village, not in a circle
  const nowhere = await client.callTool({ name: 'publish_story', arguments: { story: { ...story, id: 'smoke-naprej', continues: 'rusalka' } } })
  check('publish_story rejects a continuation of a story the village has not', nowhere.isError && text(nowhere).includes('no story "rusalka"'), text(nowhere))
  const sequel = { ...story, id: 'smoke-naprej', title: 'Kralj se vrne · The king returns', continues: 'smoke-kralj', pictures: [pic] }
  const goesOn = await client.callTool({ name: 'publish_story', arguments: { story: sequel } })
  check('publish_story publishes a continuation with its pictures', !goesOn.isError && text(goesOn).includes('1 picture), going on from smoke-kralj'), text(goesOn))
  const circle = await client.callTool({ name: 'publish_story', arguments: { story: { ...story, continues: 'smoke-naprej' } } })
  check('publish_story rejects a circle of continuations', circle.isError && text(circle).includes('a circle'), text(circle))
  const withBoth = await (await fetch(`${base}/scenes/smoke-zgodbe`, { headers: auth })).json()
  const served2 = withBoth.stories.find((s: any) => s.id === 'smoke-naprej')
  check('the app gets a continuation with the story it goes on from and its pictures', served2?.continues === 'smoke-kralj' && served2.pictures?.[0]?.figures?.[1]?.gold === true && served2.pictures[0].caption.sl === 'Lovec in Zlatorog', served2)
  await client.callTool({ name: 'remove_story', arguments: { id: 'smoke-naprej' } })
  check('the story is written to <data>/app/stories', existsSync(join(dataDir, 'app/stories/smoke-kralj.json')))
  await Bun.sleep(50)
  const ev = await (await fetch(`${base}/events/backlog?since=0`, { headers: auth })).json()
  check('story_published event, with the scene where it is told', ev.some((b: any) => b.event.type === 'story_published' && b.event.id === 'smoke-kralj' && b.event.scene === 'smoke-zgodbe' && b.event.note === 'Janez has a new story tonight' && b.event.title === 'Kralj · The king'))
  const after = await (await fetch(`${base}/scenes/smoke-zgodbe`, { headers: auth })).json()
  check("the tutor's story joins the rotation, after the curated ones", after.stories.length === janIds.length + 1 && after.stories.at(-1).id === 'smoke-kralj' && after.stories.at(-1).source === 'tutor', after.stories.map((s: any) => s.id))
  // --- never out of stories: fewer than three left at the learner's level, the tutor is asked, once a day -----------
  const low = (from = 0) => channelEvents.slice(from).filter(n => n.params?.meta?.kind === 'stories_low')
  const put = async (stories: unknown) => {
    const g = await (await fetch(`${base}/game`, { headers: auth })).json()
    await fetch(`${base}/game`, { method: 'PUT', headers: auth, body: JSON.stringify({ rev: g.rev, state: { ...g.state, stories } }) })
    await Bun.sleep(80)
  }
  const three = Object.fromEntries(janIds.slice(0, janIds.length - STORIES_LOW).map(id => [id, { told: 1, level: 'A1' }]))
  await put(three)
  check(`with ${STORIES_LOW} left at the learner's level, no stories_low yet`, low().length === 0 && storiesLeft(jan, three, 'A1').length === STORIES_LOW, low().map(n => n.params.content))
  const before = channelEvents.length
  const two = { ...three, [janIds.at(-STORIES_LOW)!]: { told: 1, level: 'A1' } }
  await put(two)
  const note = low(before)
  const data = JSON.parse(/```json\n([\s\S]*?)\n```/.exec(note[0]?.params?.content ?? '')?.[1] ?? '{}')
  check(
    'fewer than three left: the tutor is asked for a new story or a continuation (stories_low), with what was told, the level, the weak spots, the region and the pictures',
    note.length === 1 && note[0].params.meta.conversation_id === 'main' && note[0].params.content.includes('publish_story') && note[0].params.content.includes('continuation') &&
      data.level === 'A1' && JSON.stringify(data.left) === JSON.stringify(janIds.slice(-2)) && data.told.length >= janIds.length && data.told.find((s: any) => s.id === 'zlatorog')?.heard.told === 1 &&
      Array.isArray(data.weak_patterns) && data.region.culture === 'primorska' && /Primorska/.test(data.region.name) && data.teller.id === 'janez' && data.picture_library.props.includes('bell') && data.tool === 'publish_story',
    note.map(n => n.params.content),
  )
  const allHeard = Object.fromEntries([...janIds, 'smoke-kralj'].map(id => [id, { told: 1 }]))
  await put(allHeard)
  check('… once a day at most: all heard the same day asks no more', low(before).length === 1, low(before).map(n => n.params.content))
  check("… the day it asked is kept with the learner's data", JSON.parse(readFileSync(join(dataDir, 'app/story-requests.json'), 'utf8')).left.length === 2)
  writeFileSync(join(dataDir, 'app/story-requests.json'), JSON.stringify({ date: '2020-01-01', left: [], level: 'A1' }))
  await put({ ...allHeard, 'kralj-matjaz': { told: 2 } })
  const again = low(before)
  check('… the next day it asks again: none left, the old ones retold a level up', again.length === 2 && again[1].params.content.includes('none left') && again[1].params.content.includes('retells'), again.map(n => n.params.content))
  check('list_stories says when all are heard', JSON.parse(text(await client.callTool({ name: 'list_stories', arguments: {} }))).all_heard === true)
  const gs = await client.callTool({ name: 'get_story', arguments: { id: 'smoke-kralj' } })
  check('get_story returns the spec to edit', !gs.isError && text(gs).includes('"teller": "janez"') && text(gs).includes('Nekoč je živel kralj.'))
  check('get_story of an unknown id fails', (await client.callTool({ name: 'get_story', arguments: { id: 'nope' } })).isError)
  const rm = await client.callTool({ name: 'remove_story', arguments: { id: 'smoke-kralj' } })
  check('remove_story removes the tutor story', !rm.isError && !existsSync(join(dataDir, 'app/stories/smoke-kralj.json')))
  check('remove_story of nothing fails', (await client.callTool({ name: 'remove_story', arguments: { id: 'smoke-kralj' } })).isError && (await client.callTool({ name: 'remove_story', arguments: { id: '../x' } })).isError)
  // the village as it was before the stories' checks
  const now = await (await fetch(`${base}/game`, { headers: auth })).json()
  await fetch(`${base}/game`, { method: 'PUT', headers: auth, body: JSON.stringify({ rev: now.rev, state: game.state }) })
  await client.callTool({ name: 'remove_scene', arguments: { id: 'smoke-zgodbe' } })
  const guide = await guideOf()
  check('the channel guide says when to write a story', guide.includes('publish_story') && guide.includes('all_heard'))
  check('… and what to write when they run low: a legend of the region or a continuation, with pictures', guide.includes('stories_low') && guide.includes('"continues"') && guide.includes('picture_library'))
}
