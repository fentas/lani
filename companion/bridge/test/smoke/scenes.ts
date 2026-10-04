// Smoke checks: scenes (lani.scene/v0): the art registry against the app, the curated content,
// validation, resolution, the routes, publishing and the voice corpus.
import { existsSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { cultureProjects } from '../../src/cultures'
import { PackStore } from '../../src/packs'
import { ScenarioStore } from '../../src/scenarios'
import { forApp, offStage, POSES, resolveScene, SCENE_ART, SCENE_ARTS_VERSION, SCENE_STAGE, validateScene, variantsOf, type LoadedScene } from '../../src/scenes'
import { VillagerStore } from '../../src/villagers'
import { corpus, PRIORITY_LABELS } from '../../src/voice'
import { reference } from './fixtures'
import { auth, base, check, client, dataDir, fam, scenesDir, guideOf } from './harness'

const android = resolve(import.meta.dir, '../../../android/app/src/main/java/si/lanisce/lani/game')
/** A tool result's text (JSON.stringify would escape the quotes in it). */
const text = (r: any) => (r.content ?? []).map((c: any) => c.text ?? '').join('\n')

/** The registry as SceneArt.kt and Model.kt declare it (comments stripped, then the listOf/enum blocks). */
function registryFromKotlin() {
  const art = readFileSync(join(android, 'scene/SceneArt.kt'), 'utf8').replace(/\/\/.*$/gm, '')
  const model = readFileSync(join(android, 'Model.kt'), 'utf8').replace(/\/\/.*$/gm, '')
  const strings = (s: string) => [...s.matchAll(/"([^"]+)"/g)].map(m => m[1])
  const between = (from: string, to: string) => art.slice(art.indexOf(from), art.indexOf(to))
  const map = (block: string) => Object.fromEntries([...block.matchAll(/"(\w+)" to listOf\(([\s\S]*?)\)/g)].map(m => [m[1], strings(m[2])]))
  const enumNames = (name: string) => [...(new RegExp(`enum class ${name}[^{]*\\{([\\s\\S]*?)\\}`).exec(model)?.[1] ?? '').matchAll(/^\s*([A-Z_]+)\(/gm)].map(m => m[1].toLowerCase())
  return {
    objects: map(between('val objects', 'val personSlots')),
    personSlots: map(between('val personSlots', 'val people')),
    people: strings(/val people[^=]*=\s*listOf\(([\s\S]*?)\)/.exec(art)?.[1] ?? ''),
    effects: map(between('val effects', 'val arts')),
    buildings: enumNames('BuildingType'),
    ages: enumNames('Age'),
  }
}

/** The arts' stages as SceneArt.kt declares them (`val stage`: art → its spots, each with its poses and cover). */
function stageFromKotlin() {
  const art = readFileSync(join(android, 'scene/SceneArt.kt'), 'utf8').replace(/\/\/.*$/gm, '')
  const block = art.slice(art.indexOf('val stage'))
  const out: Record<string, Record<string, { poses: string[]; cover?: string }>> = {}
  for (const m of block.matchAll(/"(\w+)" to listOf\(([\s\S]*?)\n\s*\),/g)) {
    const spots: Record<string, { poses: string[]; cover?: string }> = {}
    for (const s of m[2].matchAll(/Spot\(([^)]*)\)/g)) {
      const cover = /cover = "([^"]+)"/.exec(s[1])?.[1]
      const strs = [...s[1].replace(/cover = "[^"]+"/, '').matchAll(/"([^"]+)"/g)].map(x => x[1])
      spots[strs[0]] = cover ? { poses: strs.slice(1), cover } : { poses: strs.slice(1) }
    }
    out[m[1]] = spots
  }
  return out
}

export default async function scenes() {
  // --- the registry can't drift from the app ---------------------------------------------------
  const ktStage = stageFromKotlin()
  check('the bridge stages match SceneArt.kt (the living room, the tent)', JSON.stringify(ktStage) === JSON.stringify(SCENE_STAGE) && Object.keys(ktStage).length >= 2, { kt: ktStage, ts: SCENE_STAGE })
  check("every stage has its art's person spots, standing, and only the poses the app knows",
    Object.entries(SCENE_STAGE).every(([art, spots]) => SCENE_ART.personSlots[art].every(p => spots[p]?.poses.includes('stand')) &&
      Object.values(spots).every(s => s.poses.length && s.poses.every(p => (POSES as readonly string[]).includes(p)) && (!s.cover || SCENE_ART.objects[art].includes(s.cover)))) &&
      JSON.stringify(POSES) === JSON.stringify([...readFileSync(join(android, 'scene/Stage.kt'), 'utf8').matchAll(/[A-Z]+\("(\w+)"\)/g)].map(m => m[1])),
    SCENE_STAGE)
  const kt = registryFromKotlin()
  check('SceneArt.kt parsed: 21 arts, 20 sprites, effects for every art, 14 buildings, 6 ages', Object.keys(kt.objects).length === 21 && kt.people.length === 20 && Object.keys(kt.effects).length === 21 && kt.buildings.length === 14 && kt.ages.length === 6, kt)
  // an app says which arts it draws (?arts=): an older one gets no room of a house and no kitchen hearth (forApp)
  const older = forApp([{ art: 'kitchen', objects: [{ slot: 'stove' }, { slot: 'hearth' }] }, { art: 'cellar', objects: [{ slot: 'barrel' }] }], 1)
  check('an older app gets the kitchen without its new slots and no cellar; a current one all', JSON.stringify(older) === '[{"art":"kitchen","objects":[{"slot":"stove"}]}]' &&
    forApp([{ art: 'cellar', objects: [] }], SCENE_ARTS_VERSION).length === 1 && SCENE_ARTS_VERSION === Number(/const val VERSION = (\d+)/.exec(readFileSync(join(android, 'scene/SceneArt.kt'), 'utf8'))?.[1]), older)
  check('the bridge registry matches SceneArt.kt and Model.kt', JSON.stringify(kt) === JSON.stringify(SCENE_ART), { kt, ts: SCENE_ART })

  // --- the curated content, against the repo's packs and scenarios ------------------------------
  const repo = resolve(import.meta.dir, '../../..')
  const cast = new VillagerStore(join(repo, 'cultures/primorska/villagers'), join(dataDir, 'unused-villagers')).all()
  const projects = cultureProjects(join(repo, 'cultures'), 'primorska')
  check("the culture's projects are read with their steps: the bridge (6), the mill (6)", projects?.find(p => p.id === 'most')?.steps === 6 && projects?.find(p => p.id === 'mlin')?.steps === 6, projects)
  const refs = {
    packs: new PackStore(join(repo, 'packs'), join(dataDir, 'unused-packs')).all(),
    scenarios: new ScenarioStore(join(repo, 'scenarios'), join(dataDir, 'unused-scenarios')).all(),
    villagers: cast,
    projects,
  }
  const curatedFiles = readdirSync(join(repo, 'scenes')).filter(f => f.endsWith('.json'))
  check('18 curated scenes', curatedFiles.length === 18, curatedFiles)
  const curated: LoadedScene[] = []
  for (const f of curatedFiles) {
    const v = validateScene(JSON.parse(readFileSync(join(repo, 'scenes', f), 'utf8')), refs)
    check(`curated scene validates: ${f}`, v.ok && `${v.scene.id}.json` === f, v)
    if (!v.ok) continue
    const s = v.scene
    curated.push({ ...s, source: 'curated' })
    const slots = SCENE_ART.objects[s.art]
    check(`${s.id}: every slot of "${s.art}" has a word`, slots.every(x => s.objects.some(o => o.slot === x)), slots.filter(x => !s.objects.some(o => o.slot === x)))
    check(`${s.id}: everyone is a villager of the cast and every happening leaves a memory`, s.people.every(p => p.villager && cast.some(v => v.id === p.villager)) && s.happenings.every(h => h.memory?.sl && h.memory.en), { people: s.people.map(p => p.villager), memories: s.happenings.map(h => h.memory?.sl) })
    // every dialog and every variant is some happening's (its dialog, or one of its dialogs)
    const everyDialog = [...s.dialogs, ...s.variants]
    check(`${s.id}: 3-8 happenings with varied times, every dialog staged`, s.happenings.length >= 3 && s.happenings.length <= 8 && new Set(s.happenings.flatMap(h => h.when)).size >= 3 && everyDialog.every(d => s.happenings.some(h => h.dialog === d.id || h.dialogs?.includes(d.id))), s.happenings.map(h => h.when))
    // a turn teaches something: a wrong choice in each, beside one right one (or two, where people would accept either)
    check(`${s.id}: dialogs have 3-8 lines and every turn teaches something`, everyDialog.every(d => d.lines.length >= 3 && d.lines.length <= 8 && d.lines.some(l => l.choices.length) && d.lines.every(l => !l.choices.length || (l.choices.some(c => c.ok) && l.choices.some(c => !c.ok)))), everyDialog.map(d => d.id))
    const unanswered = everyDialog.flatMap(d => d.lines.flatMap(l => l.choices.filter(c => !c.ok && !(c.reply?.sl && c.reply.en && c.reply.de && c.reply.it)).map(c => `${d.id}: ${c.sl}`)))
    check(`${s.id}: the person reacts to every wrong choice, in every language`, !unanswered.length, unanswered)
    check(`${s.id}: rewards stay small (at most 30 of one resource)`, s.happenings.every(h => Object.values(h.reward).every(n => n <= 30)), s.happenings.map(h => h.reward))
    const r = resolveScene({ ...s, source: 'curated' }, refs.packs)
    check(`${s.id}: every object resolves to its pack word`, r.objects.every(o => o.en && o.sl !== o.word || refs.packs.some(p => p.words.some(w => w.sl === o.sl))), r.objects.filter(o => !o.en))
  }
  check('the campfire is open from the start and the kitchen from a hut or house', curated.find(s => s.id === 'ob-ognju')?.from[0] === 'fire' && !curated.find(s => s.id === 'ob-ognju')?.needs && JSON.stringify(curated.find(s => s.id === 'kuhinja')?.from) === '["hut","house"]' && curated.find(s => s.id === 'na-vasi')?.needs === 'vas')
  // what the village has built: a thing drawn only once it's there says so, and so does what happens by it
  const thingNeeds = (id: string, slot: string) => JSON.stringify(curated.find(s => s.id === id)?.objects.find(o => o.slot === slot)?.needs)
  check(
    "fixtures need what the village builds: the kozolec and its hay, the tent, the well and the church, France's mill; the footbridge is always there",
    thingNeeds('na-njivi', 'kozolec') === '{"building":"KOZOLEC"}' && thingNeeds('na-njivi', 'hay') === '{"building":"KOZOLEC"}' && thingNeeds('ob-ognju', 'tent') === '{"building":"TENT"}' &&
      thingNeeds('na-vasi', 'well') === '{"building":"WELL"}' && thingNeeds('na-vasi', 'tower') === '{"building":"CHURCH"}' && thingNeeds('ob-potoku', 'mill') === '{"project":"mlin"}' &&
      thingNeeds('ob-potoku', 'bridge') === undefined,
    curated.map(s => [s.id, s.objects.filter(o => o.needs).map(o => [o.slot, o.needs])]),
  )
  // a room grows with its building's level: the kitchen's hearth and straw bed at first, the stove and the table from the
  // second, the clock, the dresser and the holy corner from the third; the smithy's bellows, then its wheel; the school's globe
  check(
    "the kitchen, the smithy and the school grow with their building's level",
    thingNeeds('kuhinja', 'hearth') === '{"level":2,"before":true}' && thingNeeds('kuhinja', 'bed') === '{"level":2,"before":true}' && thingNeeds('kuhinja', 'stove') === '{"level":2}' &&
      thingNeeds('kuhinja', 'table') === '{"level":2}' && thingNeeds('kuhinja', 'clock') === '{"level":3}' && thingNeeds('kuhinja', 'corner') === '{"level":3}' &&
      thingNeeds('kuhinja', 'pot') === undefined && thingNeeds('v-kovacnici', 'bellows') === '{"level":2}' && thingNeeds('v-kovacnici', 'wheel') === '{"level":3}' &&
      thingNeeds('v-soli', 'globe') === '{"level":2}' && JSON.stringify(curated.find(s => s.id === 'kuhinja')?.happenings.find(h => h.id === 'potica')?.needs) === '{"level":2}',
    curated.filter(s => ['kuhinja', 'v-kovacnici', 'v-soli'].includes(s.id)).map(s => [s.id, s.objects.filter(o => o.needs).map(o => [o.slot, o.needs])]),
  )
  const happeningNeeds = (id: string, h: string) => JSON.stringify(curated.find(s => s.id === id)?.happenings.find(x => x.id === h)?.needs)
  check(
    'France mills once the mill grinds and talks of the mill before; Luka brings the hay in where a kozolec stands; Mojca teaches by the well',
    happeningNeeds('ob-potoku', 'mlin') === '{"project":"mlin"}' && happeningNeeds('ob-potoku', 'potok') === '{"project":"mlin","before":true}' &&
      happeningNeeds('na-njivi', 'seno') === '{"building":"KOZOLEC"}' && happeningNeeds('na-vasi', 'dvojina') === '{"building":"WELL"}',
    curated.map(s => [s.id, s.happenings.filter(h => h.needs).map(h => [h.id, h.needs])]),
  )
  const from = (id: string) => JSON.stringify(curated.find(s => s.id === id)?.from)
  check('the interiors open from their buildings: the tent, the field or hayrack, the smithy, the school, the church, the bee house; the square from the linden or the well', from('v-sotoru') === '["tent"]' && from('na-njivi') === '["field","kozolec"]' && from('v-kovacnici') === '["smithy"]' && from('v-soli') === '["school"]' && from('v-cerkvi') === '["church"]' && from('pri-cebelnjaku') === '["beehive"]' && from('na-vasi') === '["lipa","well"]', curated.map(s => [s.id, s.from]))
  check('the stream and the pond open from their spots of the landscape', from('ob-potoku') === '["spot:riverbank"]' && from('pri-ribniku') === '["spot:pond"]', curated.map(s => [s.id, s.from]))
  check('the market and the watchtower open from their buildings', from('na-trznici') === '["market"]' && from('na-stolpu') === '["watchtower"]', curated.map(s => [s.id, s.from]))
  // the rooms of the houses (a house opens one, its own): Janez's living room, France's workshop, Marko's cellar, Vida and
  // Nejc's attic; the kitchen is the hut's and anyone's
  const rooms = curated.filter(s => s.from.includes('house')).sort((a, b) => a.id.localeCompare(b.id))
  check(
    'the rooms of the houses open from a house, with who lives there, and grow with its level',
    JSON.stringify(rooms.map(s => [s.id, s.art, s.household ?? []])) ===
      '[["kuhinja","kitchen",[]],["v-delavnici","workshop",["france","tine"]],["v-hisi","livingroom",["janez"]],["v-kleti","cellar",["marko"]],["v-podstresju","attic",["vida","nejc"]]]' &&
      rooms.every(s => s.id === 'kuhinja' || from(s.id) === '["house"]') &&
      rooms.every(s => [2, 3].every(l => s.objects.some(o => o.needs?.level === l))) &&
      thingNeeds('v-hisi', 'stove') === undefined && thingNeeds('v-hisi', 'photo') === '{"level":3}' && thingNeeds('v-kleti', 'ham') === '{"level":3}' &&
      happeningNeeds('v-hisi', 'fotografija') === '{"level":3}',
    rooms.map(s => [s.id, s.household, s.objects.filter(o => o.needs).map(o => [o.slot, o.needs])]),
  )
  const watch = curated.find(s => s.id === 'na-stolpu')?.dialogs.find(d => d.id === 'straza')
  check("Luka lights the watch's torch with his reply, blows the horn at the wolves, and the torch stays lit", watch?.lines[0].fx?.torch === 0 && watch?.lines.some(l => l.choices.some(c => c.reply?.fx?.torch === 1)) && watch?.lines.some(l => l.choices.some(c => c.reply?.fx?.horn === 1)) && watch?.fx_stays === true, watch?.lines.map(l => l.fx))
  check('the tent is the learner\'s: its happenings are visitors', curated.find(s => s.id === 'v-sotoru')?.happenings.map(h => h.who).every(w => ['luka', 'zala', 'nejc', 'babica'].includes(w)))
  check('Sunday lunch goes on with the tutor', curated.find(s => s.id === 'kuhinja')?.dialogs.some(d => d.talk === 'nedeljsko-kosilo'))
  const seno = curated.find(s => s.id === 'na-njivi')?.dialogs.find(d => d.id === 'seno')
  const senoEnd = seno?.lines.at(-1)?.choices.find(c => c.ok)?.reply?.sky
  check('the hay dialog clouds over, rains at the end, and the rain stays', !!seno?.lines[0].sky?.gloom && (senoEnd?.rain ?? 0) >= 0.6 && seno?.sky_stays !== false, seno?.lines.map(l => l.sky))
  const lamp = curated.find(s => s.id === 'v-sotoru')?.dialogs.find(d => d.id === 'svetilka')
  check("Luka lights the tent's lantern with his reply, and it stays lit", lamp?.lines[0].fx?.lantern === 0 && lamp?.lines.some(l => l.choices.some(c => c.reply?.fx?.lantern === 1)) && lamp?.fx_stays === true, lamp?.lines.map(l => l.fx))
  const potica = curated.find(s => s.id === 'kuhinja')?.dialogs.find(d => d.id === 'potica')
  check('Micka fires up the oven for the potica', potica?.lines[0].fx?.oven === 1, potica?.lines[0])
  const sceneExample = [...reference.matchAll(/```json\n([\s\S]*?)\n```/g)].map(m => m[1]).find(s => s.includes('lani.scene/v0'))
  check('module-spec.md scene example validates', sceneExample && validateScene(JSON.parse(sceneExample), refs).ok, sceneExample && validateScene(JSON.parse(sceneExample), refs))

  // the voice corpus: each person's lines in their sprite's voice, replies too, the learner's choices female
  const fire = curated.find(s => s.id === 'ob-ognju')!
  const items = corpus({ packs: [], modules: [], scenarios: [], scenes: [resolveScene(fire, refs.packs)] })
  const of = (text: string) => items.find(i => i.text === text)
  check('corpus: scene objects are pack words (priority 1) and examples (2)', of('ogenj')?.priority === 1 && of('Ogenj gori.')?.priority === 2, items.slice(0, 3))
  check('corpus: dialog lines in the speaker\'s voice, replies too, choices female', of('Dober večer, Jan! Si lačen?')?.voice === 'female' && of('Dober dan, Jan! Drva sem prinesel. Suha so, dobro gorijo.')?.voice === 'male' && of('Nič. Sosedje smo.')?.voice === 'male' && of('Dober dan! Hvala lepa. Koliko sem dolžan?')?.voice === 'female' && of('Dober dan! Hvala lepa. Koliko sem dolžan?')?.priority === PRIORITY_LABELS.indexOf('scenes'), items.filter(i => i.priority === PRIORITY_LABELS.indexOf('scenes')).slice(0, 6))
  // with the cast, a person speaks with their villager's speaker: the children (child sprites, female by default) are Nejc, the boy voice
  const withCast = corpus({ packs: [], modules: [], scenarios: [], scenes: [resolveScene(fire, refs.packs)], villagers: cast })
  const inCast = (t: string) => withCast.find(i => i.text === t && i.source.startsWith('scene:'))
  check("corpus: a person's villager decides the voice (Otroci is Nejc: the boy, Micka the grandma, France the grandpa), the choices stay female", of('Jan, Jan! Pridi, lovimo se!')?.voice === 'female' && inCast('Jan, Jan! Pridi, lovimo se!')?.voice === 'boy' && inCast('Dober večer, Jan! Si lačen?')?.voice === 'grandma' && inCast('Dober dan, Jan! Drva sem prinesel. Suha so, dobro gorijo.')?.voice === 'grandpa' && inCast('Prav, pridem! Kdo lovi?')?.voice === 'female', withCast.filter(i => i.text.includes('lovi')))

  // --- validation --------------------------------------------------------------------------------
  const kitchen = {
    schema: 'lani.scene/v0', id: 'smoke-kuhinja', title: 'V kuhinji · In the kitchen', emoji: '🍳', level: 'A1', art: 'kitchen', from: ['hut', 'house'], pack: 'smoke-kitchen',
    objects: [
      { slot: 'bread', word: 'kruh' }, { slot: 'milk', word: 'mleko' }, { slot: 'eggs', word: 'jajce' }, { slot: 'pot', word: 'juha' },
      { slot: 'fork', word: 'vilice' }, { slot: 'knife', word: 'noz' }, { slot: 'spoon', word: 'zlica' }, { slot: 'plate', word: 'kroznik' },
      { slot: 'bowl', word: 'g-sir', pack: 'curated-greetings' },
    ],
    people: [{ id: 'babica', name: 'Babica Micka', emoji: '👵', art: 'grandma', slot: 'stove' }],
    happenings: [{ id: 'juha', title: 'Babica kuha juho · Grandma is cooking soup', who: 'babica', when: ['evening'], chance: 0.6, dialog: 'juha', reward: { food: 15 }, marker: '🍲' }],
    dialogs: [{
      id: 'juha', talk: 'nedeljsko-kosilo',
      lines: [
        { who: 'babica', sl: 'Dober večer! Si lačen?', en: 'Good evening! Are you hungry?' },
        { choices: [
          { sl: 'Ja, zelo.', en: 'Yes, very.', ok: true, reply: { sl: 'Potem sedi k ognju.', en: 'Then sit by the fire.' } },
          { sl: 'Dobro jutro!', en: 'Good morning!', why: "It's evening: dober večer." },
        ] },
        { who: 'babica', sl: 'Izvoli, juha je vroča.', en: 'Here you are, the soup is hot.' },
      ],
    }],
  }
  const withLines = (lines: unknown[]) => ({ ...kitchen, dialogs: [{ ...kitchen.dialogs[0], lines }] })
  const [l1, l2, l3] = kitchen.dialogs[0].lines
  const bad: [string, unknown, string][] = [
    ['unknown art', { ...kitchen, art: 'castle' }, 'art'],
    ['a slot the art does not have', { ...kitchen, objects: [{ slot: 'moon', word: 'kruh' }] }, 'no slot "moon"'],
    ['a slot used twice', { ...kitchen, objects: [{ slot: 'bread', word: 'kruh' }, { slot: 'bread', word: 'mleko' }] }, 'duplicate object slot'],
    ['a word the pack does not have', { ...kitchen, objects: [{ slot: 'bread', word: 'pizza' }] }, 'no word "pizza"'],
    ['an unknown pack', { ...kitchen, objects: [{ slot: 'bread', word: 'kruh', pack: 'nope' }] }, 'unknown pack "nope"'],
    ['an object without any pack', { ...kitchen, pack: undefined }, 'no pack for word'],
    ['a person on a spot the art does not have', { ...kitchen, people: [{ ...kitchen.people[0], slot: 'left' }] }, 'no person spot "left"'],
    ['an unknown sprite', { ...kitchen, people: [{ ...kitchen.people[0], art: 'dragon' }] }, 'art'],
    ['a happening of nobody', { ...kitchen, happenings: [{ ...kitchen.happenings[0], who: 'dedek' }] }, 'not a person'],
    ['a happening with a missing dialog', { ...kitchen, happenings: [{ ...kitchen.happenings[0], dialog: 'potica' }] }, 'does not exist'],
    ['a chance above 1', { ...kitchen, happenings: [{ ...kitchen.happenings[0], chance: 1.5 }] }, 'chance'],
    ['a weekday 8', { ...kitchen, happenings: [{ ...kitchen.happenings[0], weekdays: [8] }] }, 'weekdays'],
    ['an unknown resource', { ...kitchen, happenings: [{ ...kitchen.happenings[0], reward: { gold: 5 } }] }, 'reward'],
    ['an unknown age', { ...kitchen, needs: 'empire' }, 'needs'],
    ['an unknown place', { ...kitchen, from: ['castle'] }, 'from'],
    ['a talk that is no scenario', { ...kitchen, dialogs: [{ ...kitchen.dialogs[0], talk: 'na-luni' }] }, 'not a scenario'],
    ['a dialog of 2 lines', withLines([l1, l2]), 'lines'],
    ['a turn without an ok choice', withLines([l1, { choices: [{ sl: 'A', en: 'a', why: 'x' }, { sl: 'B', en: 'b', why: 'y' }] }, l3]), 'no choice is ok'],
    ['a wrong choice without why', withLines([l1, { choices: [{ sl: 'A', en: 'a', ok: true }, { sl: 'B', en: 'b' }] }, l3]), 'needs a why'],
    ['a turn with one choice', withLines([l1, { choices: [{ sl: 'A', en: 'a', ok: true }] }, l3]), '2-3 choices'],
    ['a spoken line by nobody', withLines([{ who: 'dedek', sl: 'Oj.', en: 'Hi.' }, l2, l3]), 'not a person'],
    ['a dialog that opens with choices', withLines([l2, l1, l3]), 'first line'],
    ['a line over 200 characters', withLines([{ ...l1, sl: 'x'.repeat(201) }, l2, l3]), 'sl'],
    ['a sky level above 1', withLines([{ ...l1, sky: { rain: 1.5 } }, l2, l3]), 'rain'],
    ['a sky level below 0 in a reply', withLines([l1, { choices: [{ ...l2.choices![0], reply: { ...l2.choices![0].reply!, sky: { fog: -0.2 } } }, l2.choices![1]] }, l3]), 'fog'],
    ['a sky cue the scenes do not know', withLines([{ ...l1, sky: { sun: 1 } }, l2, l3]), 'sun'],
    ['an empty sky cue', withLines([{ ...l1, sky: {} }, l2, l3]), 'at least one'],
    ['lightning that is not true or false', withLines([{ ...l1, sky: { lightning: 1 } }, l2, l3]), 'lightning'],
    ['sky_stays without a sky cue', { ...kitchen, dialogs: [{ ...kitchen.dialogs[0], sky_stays: true }] }, 'sky_stays'],
    ['an effect the art does not have', withLines([{ ...l1, fx: { lantern: 1 } }, l2, l3]), 'no effect "lantern"'],
    ['an effect of another art in a reply', withLines([l1, { choices: [{ ...l2.choices![0], reply: { ...l2.choices![0].reply!, fx: { bell: true } } }, l2.choices![1]] }, l3]), 'no effect "bell"'],
    ['an effect level above 1', withLines([{ ...l1, fx: { oven: 2 } }, l2, l3]), 'oven'],
    ['an empty fx cue', withLines([{ ...l1, fx: {} }, l2, l3]), 'at least one effect'],
    ['fx_stays without an fx cue', { ...kitchen, dialogs: [{ ...kitchen.dialogs[0], fx_stays: true }] }, 'fx_stays'],
    // a wrong choice's reply is the person's reaction: it passes when the learner picks again, so no weather, and its effect doesn't stay
    ['a sky cue on a reaction to a wrong choice', withLines([l1, { choices: [l2.choices![0], { ...l2.choices![1], reply: { sl: 'Jutro? Zdaj?', en: 'Morning? Now?', sky: { fog: 0.5 } } }] }, l3]), 'no sky cue'],
    ['fx_stays with only a reaction\'s effect', { ...kitchen, dialogs: [{ ...kitchen.dialogs[0], fx_stays: true, lines: [l1, { choices: [l2.choices![0], { ...l2.choices![1], reply: { sl: 'Jutro? Zdaj?', en: 'Morning? Now?', fx: { oven: 1 } } }] }, l3] }] }, 'fx_stays without an fx cue'],
    ['a reaction without English', withLines([l1, { choices: [l2.choices![0], { ...l2.choices![1], reply: { sl: 'Jutro? Zdaj?' } }] }, l3]), 'reply: no "en" text'],
    // the culture packs' landscapes have effects of their own: the hills' bell, tractor and starlings, the sea's waves, gulls and sail
    ['the oven on the hills', { ...kitchen, art: 'hills', objects: [], people: [{ ...kitchen.people[0], slot: 'wall' }], dialogs: [{ ...kitchen.dialogs[0], lines: [{ ...l1, fx: { tractor: 1, oven: 1 } }, l2, l3] }] }, 'the art "hills" has no effect "oven" (effects: bell, tractor, starlings)'],
    ['the bell at the sea', { ...kitchen, art: 'sea', objects: [], people: [{ ...kitchen.people[0], slot: 'pier' }], dialogs: [{ ...kitchen.dialogs[0], lines: [{ ...l1, fx: { waves: 1, bell: true } }, l2, l3] }] }, 'the art "sea" has no effect "bell" (effects: waves, gulls, sail)'],
    ['the tractor in the mountains', { ...kitchen, art: 'alps', objects: [], people: [{ ...kitchen.people[0], slot: 'wall' }], dialogs: [{ ...kitchen.dialogs[0], lines: [{ ...l1, fx: { cowbells: 1, tractor: 1 } }, l2, l3] }] }, 'the art "alps" has no effect "tractor" (effects: cowbells, bell, fish, birds, glow)'],
    // a project's landmark as the place: one of the culture's projects
    ['a landmark of a project the culture does not have', { ...kitchen, from: ['project:grad'] }, 'from[0]: no project "grad"'],
    ['a place that is no project id', { ...kitchen, from: ['project:Grad Vas'] }, 'from'],
    // what a thing or a happening needs of the village: the game's buildings, the culture's projects and their steps
    ['a building the game does not have', { ...kitchen, objects: [{ slot: 'bread', word: 'kruh', needs: { building: 'CASTLE' } }] }, 'not a building type'],
    ['a project the culture does not have', { ...kitchen, happenings: [{ ...kitchen.happenings[0], needs: { project: 'grad' } }] }, 'no project "grad"'],
    ['a step the project does not have', { ...kitchen, objects: [{ slot: 'bread', word: 'kruh', needs: { project: 'mlin', step: 7 } }] }, 'has 6 steps, not 7'],
    ['a step without a project', { ...kitchen, objects: [{ slot: 'bread', word: 'kruh', needs: { building: 'TENT', step: 2 } }] }, 'a step goes with a project'],
    ['before without a building or a project', { ...kitchen, happenings: [{ ...kitchen.happenings[0], needs: { age: 'vas', before: true } }] }, 'before goes with'],
    ['needs that name nothing', { ...kitchen, objects: [{ slot: 'bread', word: 'kruh', needs: {} }] }, 'needs names an age, a building, a level or a project'],
    // a building's level: 1 up to the top one (Catalog.MAX_LEVEL); alone it is the place's own, so the scene opens from a building
    ['a level past the top', { ...kitchen, objects: [{ slot: 'bread', word: 'kruh', needs: { building: 'HUT', level: 6 } }] }, 'level'],
    ['a level 0', { ...kitchen, objects: [{ slot: 'bread', word: 'kruh', needs: { level: 0 } }] }, 'level'],
    ['a level that is no whole number', { ...kitchen, objects: [{ slot: 'bread', word: 'kruh', needs: { level: 1.5 } }] }, 'level'],
    ['a level with a project', { ...kitchen, happenings: [{ ...kitchen.happenings[0], needs: { project: 'mlin', level: 2 } }] }, 'a level goes with a building'],
    ['a level alone in a scene of no building', { ...kitchen, from: ['fire'], objects: [{ slot: 'bread', word: 'kruh', needs: { level: 2 } }] }, 'a level alone is the level of the building the scene opens from'],
    ['a need the scenes do not know', { ...kitchen, objects: [{ slot: 'bread', word: 'kruh', needs: { building: 'TENT', season: 'winter' } }] }, 'season'],
    ["an unknown age as a happening's needs", { ...kitchen, happenings: [{ ...kitchen.happenings[0], needs: 'empire' }] }, 'needs'],
    ['a scene in Italian with Slovene lines', { ...kitchen, language: 'it', title: { it: 'In cucina', en: 'In the kitchen' } }, 'no "it" text'],
    ['a scene in a language the app lacks', { ...kitchen, language: 'fr' }, 'language'],
    // adaptive turns and own traps (companion/SCENES.md): a wrong choice's grammar page is an id, a turn's puzzled reaction has its languages
    ['a grammar page that is no id', withLines([l1, { choices: [l2.choices![0], { ...l2.choices![1], grammar: 'Dvojina!' }] }, l3]), 'grammar'],
    ['a puzzled reaction without English', withLines([l1, { ...l2, puzzled: { sl: 'Hm?' } }, l3]), 'puzzled: no "en" text'],
    ["a puzzled reaction on someone's line", withLines([{ ...l1, puzzled: { sl: 'Hm?', en: 'Hm?' } }, l2, l3]), "puzzled is a learner's turn's"],
  ]
  for (const [label, scene, mention] of bad) {
    const r = await client.callTool({ name: 'publish_scene', arguments: { scene } })
    check(`publish_scene rejects ${label}`, r.isError && text(r).includes(mention), r)
  }
  // what a room gets with its building's level: alone (the hut or house it is opened from), with a building, until it
  const levels = { ...kitchen, objects: [
    { slot: 'bread', word: 'kruh', needs: { level: 2 } }, { slot: 'milk', word: 'mleko', needs: { level: 2, before: true } },
    { slot: 'eggs', word: 'jajce', needs: { building: 'SMITHY', level: 3 } }, { slot: 'pot', word: 'juha', needs: { building: 'house', level: 5, before: true } },
  ] }
  check('validateScene takes a level alone, with a building, and until it (before)', validateScene(levels).ok, validateScene(levels))
  const adaptive = validateScene(withLines([l1, { ...l2, puzzled: { sl: 'Kako, prosim?', en: 'Pardon?' }, choices: [l2.choices![0], { ...l2.choices![1], grammar: 'tozilnik' }] }, l3]))
  check("validateScene keeps a wrong choice's grammar page and a turn's puzzled reaction", adaptive.ok && adaptive.scene.dialogs[0].lines[1].puzzled?.en === 'Pardon?' && adaptive.scene.dialogs[0].lines[1].choices[1].grammar === 'tozilnik', adaptive)

  // --- curated files are served resolved; broken ones skipped ---------------------------------
  writeFileSync(join(scenesDir, 'smoke-kuhinja.json'), JSON.stringify(kitchen))
  writeFileSync(join(scenesDir, 'broken.json'), '{"schema":"lani.scene/v0","id":"broken"}')
  const list = await (await fetch(`${base}/scenes`, { headers: auth })).json()
  const served = list.find((s: any) => s.id === 'smoke-kuhinja')
  check('GET /scenes serves the curated scene, skips the broken one', list.filter((s: any) => !s.culture).length === 1 && served?.source === 'curated', list.map((s: any) => s.id))
  // the village's culture pack brings its own places: Primorska's hills, in Slovene, not Friuli's sea
  const hills = list.find((s: any) => s.id === 'na-gricih')
  const alps = list.find((s: any) => s.id === 'v-gorah')
  check("GET /scenes serves the culture pack's own scenes too (Primorska's vineyard from its terraces, its mountains at the horizon), not another pack's", hills?.culture === 'primorska' && hills.source === 'curated' && hills.language === 'sl' && hills.art === 'hills' && JSON.stringify(hills.from) === '["project:vinograd"]' &&
    alps?.culture === 'primorska' && alps.art === 'alps' && JSON.stringify(alps.from) === '["spot:horizon"]' && !list.some((s: any) => s.id === 'il-mare'), list.map((s: any) => [s.id, s.culture]))
  const zvonec = alps?.objects.find((o: any) => o.slot === 'cowbell')
  check("the mountains' objects resolve from their own word pack", zvonec?.sl === 'zvonec' && zvonec.en === 'bell' && zvonec.pack === 'v-gorah' && zvonec.plural === 'zvonci', zvonec)
  const grozd = hills?.objects.find((o: any) => o.slot === 'grapes')
  check("the culture scene's objects resolve from the culture pack's own word pack", grozd?.sl === 'grozd' && grozd.en === 'bunch of grapes' && grozd.pack === 'na-gricih' && grozd.plural === 'grozdi', grozd)
  const bread = served?.objects.find((o: any) => o.slot === 'bread')
  check('objects are resolved from the pack', bread?.sl === 'kruh' && bread.en === 'kruh (en)' && bread.gender === 'm' && bread.plural === 'kruhi' && bread.example_sl === 'Kruh je še topel.' && bread.example_en === 'The bread is still warm.' && bread.emoji === '🍞' && bread.pack === 'smoke-kitchen', bread)
  const bowl = served?.objects.find((o: any) => o.slot === 'bowl')
  check("an object's own pack wins", bowl?.sl === 'sir' && bowl.pack === 'curated-greetings', bowl)
  check('defaults are filled in', served?.people[0].always === false && served.happenings[0].needs === undefined && served.dialogs[0].lines[1].choices[1].ok === false && served.dialogs[0].talk === 'nedeljsko-kosilo', served?.happenings)
  const one = await (await fetch(`${base}/scenes/smoke-kuhinja`, { headers: auth })).json()
  check('GET /scenes/:id serves one scene', one.id === 'smoke-kuhinja' && one.objects.length === 9)
  check('GET /scenes/:id 404s', (await fetch(`${base}/scenes/nope`, { headers: auth })).status === 404)
  check('scenes need the app token', (await fetch(`${base}/scenes`)).status === 401 && (await fetch(`${base}/scenes`, { headers: fam })).status === 403 && (await fetch(`${base}/scenes/smoke-kuhinja`, { headers: fam })).status === 403)

  // --- publishing --------------------------------------------------------------------------------
  const camp = {
    ...kitchen, id: 'smoke-ogenj', title: 'Ob ognju · At the campfire', emoji: '🔥', art: 'campfire', from: ['fire'],
    objects: [{ slot: 'kettle', word: 'juha' }, { slot: 'fire', word: 'kruh', needs: { building: 'tent' } }],
    people: [{ id: 'janez', name: 'Stari Janez', emoji: '👴', art: 'grandpa', slot: 'right' }],
    happenings: [{ id: 'zgodba', title: 'Janez pripoveduje · Janez tells a story', who: 'janez', when: ['night'], chance: 0.5, dialog: 'zgodba', reward: { wisdom: 20 }, needs: { project: 'most', step: 2 } }],
    dialogs: [{
      id: 'zgodba',
      sky_stays: false,
      lines: [
        { who: 'janez', sl: 'Dober večer, Jan. Sedi.', en: 'Good evening, Jan. Sit down.', sky: { fog: 0.4, gloom: 0.3 } },
        { choices: [
          { sl: 'Hvala, Janez.', en: 'Thanks, Janez.', ok: true, reply: { sl: 'Poslušaj zgodbo.', en: 'Listen to the story.', sky: { rain: 0.5, lightning: true } } },
          { sl: 'Dobro jutro, Janez.', en: 'Good morning, Janez.', why: 'Evening: dober večer.', reply: { sl: 'Jutro? Saj je že tema, fant.', en: 'Morning? But it is dark already, lad.', fx: { flare: 1 } } },
        ] },
        { who: 'janez', sl: 'Lahko noč, Jan.', en: 'Good night, Jan.', fx: { flare: 0.5, steam: true } },
      ],
    }],
  }
  const badNote = await client.callTool({ name: 'publish_scene', arguments: { scene: camp, note: 'x'.repeat(501) } })
  check('publish_scene refuses a long note', badNote.isError)
  const pub = await client.callTool({ name: 'publish_scene', arguments: { scene: camp, note: 'Janez tells stories by the fire' } })
  check('publish_scene publishes', !pub.isError && JSON.stringify(pub).includes('2 objects, 1 happenings, 1 dialogs'), pub)
  check('scene file written to <data>/app/scenes', existsSync(join(dataDir, 'app/scenes/smoke-ogenj.json')))
  const pubServed = await (await fetch(`${base}/scenes/smoke-ogenj`, { headers: auth })).json()
  check('published scene served resolved as tutor', pubServed.source === 'tutor' && pubServed.published_at > 0 && pubServed.objects[0].sl === 'juha' && pubServed.happenings[0].marker === '💬', pubServed)
  check("what a thing and a happening need of the village is served as published", JSON.stringify(pubServed.objects[1].needs) === '{"building":"tent"}' && JSON.stringify(pubServed.happenings[0].needs) === '{"project":"most","step":2}' && pubServed.objects[0].needs === undefined, pubServed.objects)
  const pubDialog = pubServed.dialogs?.[0]
  check('sky cues and sky_stays are served as published', JSON.stringify(pubDialog?.lines[0].sky) === '{"fog":0.4,"gloom":0.3}' && JSON.stringify(pubDialog?.lines[1].choices[0].reply.sky) === '{"rain":0.5,"lightning":true}' && pubDialog?.sky_stays === false && pubDialog?.lines[2].sky === undefined, pubDialog)
  check('fx cues are served as published', JSON.stringify(pubDialog?.lines[2].fx) === '{"flare":0.5,"steam":true}' && pubDialog?.lines[0].fx === undefined && pubDialog?.fx_stays === undefined, pubDialog?.lines)
  check("a wrong choice's reaction is served with its passing effect", pubDialog?.lines[1].choices[1].reply?.sl === 'Jutro? Saj je že tema, fant.' && JSON.stringify(pubDialog?.lines[1].choices[1].reply?.fx) === '{"flare":1}' && pubDialog?.lines[1].choices[1].ok === false, pubDialog?.lines[1])
  await Bun.sleep(100)
  const ev = await (await fetch(`${base}/events/backlog?since=0`, { headers: auth })).json()
  check('scene_published event', ev.some((b: any) => b.event.type === 'scene_published' && b.event.id === 'smoke-ogenj' && b.event.note === 'Janez tells stories by the fire' && b.event.emoji === '🔥'))
  for (let i = 0; i < 50; i++) {
    if ((await (await fetch(`${base}/voice/status`, { headers: auth })).json()).queued === 0) break
    await Bun.sleep(100)
  }
  const vIdx = await (await fetch(`${base}/voice/index`, { headers: auth })).json()
  check("a published scene's dialog is voiced: Janez male, the learner's line female, the reply and the reaction male", !!vIdx['dober večer jan sedi']?.male && !vIdx['dober večer jan sedi']?.female && !!vIdx['hvala janez']?.female && !!vIdx['poslušaj zgodbo']?.male && !!vIdx['jutro saj je že tema fant']?.male, Object.keys(vIdx).filter(k => k.includes('janez') || k.includes('zgodbo') || k.includes('tema')))
  const st = await (await fetch(`${base}/voice/status`, { headers: auth })).json()
  check('voice status counts the scenes in the corpus', st.corpus?.by_source?.scene?.texts >= 3, st.corpus?.by_source)

  // a tutor scene with a curated id replaces it, and remove_scene brings the curated one back
  const over = await client.callTool({ name: 'publish_scene', arguments: { scene: { ...kitchen, title: 'Kuhinja po novem · The kitchen, changed' } } })
  check('publish_scene replaces a curated scene and says so', !over.isError && JSON.stringify(over).includes('replaces the curated scene'), over)
  const overList = await (await fetch(`${base}/scenes`, { headers: auth })).json()
  const overKitchen = overList.find((s: any) => s.id === 'smoke-kuhinja')
  check('the tutor scene shadows the curated one (one entry per id)', overList.filter((s: any) => s.id === 'smoke-kuhinja').length === 1 && overKitchen?.source === 'tutor' && overKitchen.title.startsWith('Kuhinja po novem'), overList.map((s: any) => [s.id, s.source]))
  const gs = await client.callTool({ name: 'get_scene', arguments: { id: 'smoke-kuhinja' } })
  check('get_scene returns the spec to edit (unresolved objects)', !gs.isError && JSON.stringify(gs).includes('Kuhinja po novem') && !JSON.stringify(gs).includes('kruhi'), gs)
  check('get_scene of an unknown id fails', (await client.callTool({ name: 'get_scene', arguments: { id: 'nope' } })).isError)
  const rm = await client.callTool({ name: 'remove_scene', arguments: { id: 'smoke-kuhinja' } })
  check('remove_scene removes the tutor scene, the curated one is back', !rm.isError && JSON.stringify(rm).includes('curated one is back') && (await (await fetch(`${base}/scenes/smoke-kuhinja`, { headers: auth })).json()).source === 'curated', rm)
  check('remove_scene of nothing fails', (await client.callTool({ name: 'remove_scene', arguments: { id: 'smoke-kuhinja' } })).isError && (await client.callTool({ name: 'remove_scene', arguments: { id: '../x' } })).isError)
  await Bun.sleep(50)
  const ev2 = await (await fetch(`${base}/events/backlog?since=0`, { headers: auth })).json()
  check('scene_removed event', ev2.some((b: any) => b.event.type === 'scene_removed' && b.event.id === 'smoke-kuhinja'))
  const ls = await client.callTool({ name: 'list_scenes', arguments: {} })
  const lsText = text(ls)
  check('list_scenes shows both scenes with their happenings', !ls.isError && lsText.includes('smoke-ogenj') && lsText.includes('smoke-kuhinja') && lsText.includes('Janez pripoveduje') && lsText.includes('"when"'), ls)
  check('instructions mention the scenes', JSON.stringify(await guideOf()).includes('publish_scene'))
  await stage(kitchen, curated, refs)
}

/**
 * Stage directions and tap turns (companion/SCENES.md): what the curated hide-and-seek and Luka's shelter do, what the
 * validator refuses, and what publish_scene and publish_dialog_variant keep.
 */
async function stage(kitchen: any, curated: LoadedScene[], refs: Parameters<typeof validateScene>[1]) {
  // the curated showcases: Zala hides in Janez's living room and is found by a tap; Luka comes into the tent out of the rain
  const hisa = curated.find(s => s.id === 'v-hisi')
  const seek = [...(hisa?.dialogs ?? []), ...(hisa?.variants ?? [])].filter(d => d.id.startsWith('skrivalnice'))
  const hides = (d: (typeof seek)[number]) => d.lines.some(l => l.choices.some(c => c.ok && Object.values(c.reply?.act ?? {}).some(a => a.pose === 'hide')))
  const tapped = (d: (typeof seek)[number]) => d.lines.some(l => l.choices.length >= 3 && l.choices.every(c => c.tap) && l.choices.filter(c => c.ok).length === 1)
  check("hide-and-seek in the living room: Zala hides with the count's reply and is found by a tap on one of three places",
    seek.length >= 2 && seek.every(d => hides(d) && tapped(d)) && hisa?.happenings.some(h => h.who === 'zala' && h.dialog === 'skrivalnice' && variantsOf(h).length === seek.length) === true,
    seek.map(d => d.id))
  check('its tap turns name the page of pod, za … (orodnik) on the right place, and a wrong place gets a playful reaction', seek.every(d => d.lines.filter(l => l.choices.some(c => c.tap)).every(l =>
    l.choices.find(c => c.ok)?.grammar === 'orodnik' &&l.choices.filter(c => !c.ok).every(c => c.reply?.sl && !c.grammar))), seek.map(d => d.id))
  const tent = curated.find(s => s.id === 'v-sotoru')
  const shelter = [...(tent?.dialogs ?? []), ...(tent?.variants ?? [])].filter(d => d.lines.some(l => l.choices.some(c => c.ok && c.reply?.act?.luka?.to === 'lantern')))
  check('Luka comes into the tent and sits by the lantern when the learner asks him in; in one it keeps raining and he stays',
    shelter.length >= 2 && shelter.some(d => d.act_stays === true && d.sky_stays !== false) && shelter.every(d => d.lines.some(l => l.choices.some(c => c.ok && c.reply?.act?.luka?.pose === 'sit'))),
    shelter.map(d => [d.id, d.act_stays]))

  // --- validation ---------------------------------------------------------------------------------
  const room = {
    ...kitchen, id: 'smoke-hisa', title: 'V hiši · In the living room', art: 'livingroom', from: ['house'], objects: [],
    people: [{ id: 'zala', name: 'Zala', emoji: '👧', art: 'child2', slot: 'stove' }, { id: 'janez', name: 'Janez', emoji: '👴', art: 'grandpa', slot: 'table' }],
    happenings: [{ id: 'skrivalnice', title: 'Skrivalnice · Hide-and-seek', who: 'zala', dialog: 'skrivalnice', reward: { wood: 15 } }],
    dialogs: [{
      id: 'skrivalnice',
      lines: [
        { who: 'zala', sl: 'Se greva skrivalnice?', en: 'Shall we play hide-and-seek?' },
        { choices: [
          { sl: '… deset! Grem iskat!', en: '… ten! Here I come!', ok: true, reply: { sl: 'Skrila sem se!', en: "I've hidden!", act: { zala: { to: 'behind-door', pose: 'hide' } } } },
          { sl: '… deset! Greš iskat!', en: "… ten! You're coming!", why: 'You seek: grem.', reply: { sl: 'Jaz? Ne!', en: 'Me? No!' } },
        ] },
        { choices: [
          { sl: 'Za vrati si!', en: "You're behind the door!", ok: true, tap: 'behind-door', grammar: 'kje-mestnik-orodnik', reply: { sl: 'Našel si me!', en: 'You found me!', act: { zala: { to: 'door' } } } },
          { sl: 'Pod mizo si!', en: "You're under the table!", why: 'Look for a clue.', tap: 'under-table', reply: { sl: 'Tu me ni!', en: "I'm not here!", act: { zala: { pose: 'peek' } } } },
          { sl: 'Za pečjo si!', en: "You're behind the stove!", why: 'Look for a clue.', tap: 'stove', reply: { sl: 'Mrzlo!', en: 'Cold!' } },
          { sl: 'Janez, kje je?', en: 'Janez, where is she?', why: 'Find her yourself.', tap: 'janez', reply: { sl: 'Ne povem!', en: "I won't tell!" } },
        ] },
        { who: 'zala', sl: 'Še enkrat!', en: 'Once more!', act: { janez: { to: 'bench', pose: 'sit' } } },
      ],
    }],
  }
  const good = validateScene(room, refs)
  check('a living room with stage directions and a tap turn of four places validates', good.ok, good)
  const example = [...reference.matchAll(/```json\n([\s\S]*?)\n```/g)].map(m => m[1]).find(s => s.includes('"tap"'))
  const withExample = example && validateScene({ ...room, dialogs: [...room.dialogs, JSON.parse(example)] }, refs)
  check("module-spec.md's hide-and-seek example validates in a living room", !!withExample && withExample.ok, withExample)
  const lines = room.dialogs[0].lines as any[]
  const withLines = (ls: unknown[], extra: object = {}) => ({ ...room, dialogs: [{ ...room.dialogs[0], ...extra, lines: ls }] })
  const count = lines[1]
  const tapTurn = lines[2]
  const bad: [string, unknown, string][] = [
    ['stage directions in an art without a stage', { ...kitchen, dialogs: [{ ...kitchen.dialogs[0], lines: [{ ...kitchen.dialogs[0].lines[0], act: { babica: { to: 'door' } } }, kitchen.dialogs[0].lines[1], kitchen.dialogs[0].lines[2]] }] }, 'has no stage'],
    ['someone the scene lacks', withLines([{ ...lines[0], act: { tine: { to: 'door' } } }, ...lines.slice(1)]), '"tine" is not a person of this scene'],
    ['a spot the stage lacks', withLines([{ ...lines[0], act: { zala: { to: 'attic' } } }, ...lines.slice(1)]), 'no spot "attic"'],
    ['a pose the spot lacks', withLines([{ ...lines[0], act: { janez: { to: 'bench', pose: 'hide' } } }, ...lines.slice(1)]), 'no pose "hide"'],
    ['a pose where one is that the spot lacks', withLines([{ ...lines[0], act: { zala: { pose: 'peek' } } }, ...lines.slice(1)]), 'the spot "stove" has no pose "peek"'],
    ['a pose the app does not know', withLines([{ ...lines[0], act: { zala: { to: 'door', pose: 'dance' } } }, ...lines.slice(1)]), 'pose'],
    ['a stage direction of nothing', withLines([{ ...lines[0], act: { zala: {} } }, ...lines.slice(1)]), 'names a spot'],
    ['an empty act cue', withLines([{ ...lines[0], act: {} }, ...lines.slice(1)]), 'at least one person'],
    ['act_stays without an act cue', { ...kitchen, dialogs: [{ ...kitchen.dialogs[0], act_stays: true }] }, 'act_stays'],
    ['a tap turn with a choice that stands for no place', withLines([lines[0], count, { choices: [tapTurn.choices[0], { ...tapTurn.choices[1], tap: undefined }] }, lines[3]]), 'every choice stands for a place'],
    ['a tap on nothing in the picture', withLines([lines[0], count, { choices: [tapTurn.choices[0], { ...tapTurn.choices[1], tap: 'chimney' }] }, lines[3]]), '"chimney" is no spot of the stage'],
    ['the same place twice', withLines([lines[0], count, { choices: [tapTurn.choices[0], { ...tapTurn.choices[1], tap: 'behind-door' }] }, lines[3]]), 'twice in the turn'],
    ['four choices without a tap', withLines([lines[0], { choices: [...count.choices, { ...count.choices[1], sl: 'A', en: 'a' }, { ...count.choices[1], sl: 'B', en: 'b' }] }, tapTurn, lines[3]]), '2-3 choices'],
  ]
  for (const [label, scene, mention] of bad) {
    const r = await client.callTool({ name: 'publish_scene', arguments: { scene } })
    check(`publish_scene rejects ${label}`, r.isError && text(r).includes(mention), r)
  }
  const told = offStage({ lines: [{ act: { luka: { to: 'lantern' } }, choices: [] }, { choices: [{ tap: 'door' }, { tap: 'table' }] }] }, 'levels.A1', 'a story')
  check('a story, an introduction or a keeper has no stage: no act, no tap', told.length === 2 && told[0].includes('no act cue') && told[1].includes('no tap'), told)

  // --- published and served as written; a variant of a curated scene may use them too -----------------------
  const pub = await client.callTool({ name: 'publish_scene', arguments: { scene: room } })
  check('publish_scene publishes a scene with stage directions and a tap turn', !pub.isError, pub)
  // the living room is an art of the second version: the app asks for it (?arts=2)
  const served = await (await fetch(`${base}/scenes/smoke-hisa?arts=2`, { headers: auth })).json()
  const sd = served.dialogs?.[0]
  check('act, act on a reaction and tap are served as published',
    JSON.stringify(sd?.lines[1].choices[0].reply.act) === '{"zala":{"to":"behind-door","pose":"hide"}}' && JSON.stringify(sd?.lines[2].choices[1].reply.act) === '{"zala":{"pose":"peek"}}' &&
      sd?.lines[2].choices.map((c: any) => c.tap).join() === 'behind-door,under-table,stove,janez' && JSON.stringify(sd?.lines[3].act) === '{"janez":{"to":"bench","pose":"sit"}}',
    served)
  const variant = { ...room.dialogs[0], id: 'skrivalnice-smoke', act_stays: true }
  const v = await client.callTool({ name: 'publish_dialog_variant', arguments: { scene: 'smoke-hisa', happening: 'skrivalnice', dialog: variant } })
  const withVariant = await (await fetch(`${base}/scenes/smoke-hisa?arts=2`, { headers: auth })).json()
  check('publish_dialog_variant takes a variant with stage directions, act_stays and a tap turn', !v.isError && withVariant.dialogs?.find((d: any) => d.id === 'skrivalnice-smoke')?.act_stays === true, v)
  const wrong = await client.callTool({ name: 'publish_dialog_variant', arguments: { scene: 'smoke-hisa', happening: 'skrivalnice', dialog: { ...variant, id: 'skrivalnice-smoke2', lines: [{ ...lines[0], act: { zala: { to: 'lantern' } } }, ...lines.slice(1)] } } })
  check("publish_dialog_variant refuses a spot the art lacks (the lantern is the tent's)", wrong.isError && text(wrong).includes('no spot "lantern"'), wrong)
  await client.callTool({ name: 'remove_dialog_variant', arguments: { scene: 'smoke-hisa', happening: 'skrivalnice', id: 'skrivalnice-smoke' } })
  await client.callTool({ name: 'remove_scene', arguments: { id: 'smoke-hisa' } })
}
