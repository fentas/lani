// Smoke checks: villagers (lani.villager/v0): the spots against the app, the curated cast against
// the app's quest givers, validation, the routes, the role-play built for the friendship, the
// villager persona on roleplay turns, villager_arrived, publishing and the voice corpus.
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { cast as castOf } from '../../src/cast'
import { BOND_NAMES, GIFT_KINDS, JAN_PLACE, SLEEP_ROOFS, SPOT_IDS, allLines, helloFor, minutesOf, readsWhen, speakerErrors, validateVillager, villagerScenario, withoutWhen, bondOf, bondLevel } from '../../src/villagers'
import { labelEn, labelShown, labelTarget, meant, partNow, partOf, PARTS, said, type Part, type SpokenLine } from '../../src/langs'
import { Names } from '../../src/mentions'
import { learnPayload, meaningOf, titleFor, validatePack } from '../../src/packs'
import { SPOT_NAMES } from '../../src/scenes'
import { corpus } from '../../src/voice'
import { reference } from './fixtures'
import { auth, base, channelEvents, check, client, dataDir, fam, guideOf } from './harness'

const android = resolve(import.meta.dir, '../../../android/app/src/main/java/si/lanisce/lani/game')
const text = (r: any) => (r.content ?? []).map((c: any) => c.text ?? '').join('\n')

/**
 * Who a text names (companion/VILLAGERS.md, "Who a text may name"): the bridge finds it as the app does (Mentions.kt, the
 * same sentences as MentionsTest); get_village says who is here and met; publish_scene says which happening waits. Runs
 * in the village the villagers checks saved: Pastir Luka and Ana Furlan live there.
 */
async function mentions() {
  const kt = readFileSync(join(android, 'villagers/Mentions.kt'), 'utf8')
  const ktPossessive = /POSSESSIVE = listOf\(([^)]*)\)/.exec(kt)?.[1].match(/"(\w*)"/g)?.map(s => s.slice(1, -1))
  const ktSentence = /SENTENCE = "((?:[^"\\]|\\.)*)"/.exec(kt)?.[1].replace(/\\"/g, '"').replace(/\\n/g, '\n')
  const ts = readFileSync(resolve(import.meta.dir, '../../src/mentions.ts'), 'utf8')
  const tsPossessive = /POSSESSIVE = \[([^\]]*)\]/.exec(ts)?.[1].match(/'(\w*)'/g)?.map(s => s.slice(1, -1))
  const tsSentence = /SENTENCE = '((?:[^'\\]|\\.)*)'/.exec(ts)?.[1].replace(/\\'/g, "'").replace(/\\n/g, '\n')
  check("a name's forms and a sentence's start are the app's", JSON.stringify(ktPossessive) === JSON.stringify(tsPossessive) && !!ktSentence && ktSentence === tsSentence, { ktPossessive, tsPossessive, ktSentence, tsSentence })
  const v = (id: string, name: string, source?: string) => ({ id, name, ...(source ? { source } : {}) })
  const sl = new Names([v('micka', 'Babica Micka'), v('janez', 'Stari Janez'), v('france', 'Mlinar France'), v('anton', 'Čebelar Anton'), v('tone', 'Kovač Tone'), v('marko', 'Vinar Marko'), v('vida', 'Gostilničarka Vida'), v('nejc', 'Nejc'), v('mojca', 'Učiteljica Mojca'), v('n-ana-furlan', 'Ana Furlan', 'village')], 'sl')
  const of = (names: Names, s: string) => [...names.of(s)].sort().join(',')
  const cases: [Names, string, string][] = [
    [sl, 'Letijo na lipo. Hitro, pokliči Antona!', 'anton'], [sl, 'Antonov med je najboljši.', 'anton'], [sl, 'Kosilo je pri Micki. Mickina kuhinja!', 'micka'],
    [sl, 'Nesi to Tonetu. Markov kombi je tu.', 'marko,tone'], [sl, 'Nejčeva žoga. Mojčina tabla.', 'mojca,nejc'], [sl, 'Jan! Jana ni doma, Janu pa pišem.', ''],
    [sl, 'Ladja je v luki. Ali vidi tole?', ''], [sl, 'France Prešeren je največji slovenski pesnik.', ''], [sl, '20. maja se je rodil Anton Janša.', ''],
    [sl, 'Janez pozna pesem o Lepi Vidi.', 'janez'], [sl, 'Pomagaj Staremu Janezu, prosim.', 'janez'], [sl, 'Ana Furlan je prinesla jajca. Ani je ime Ana.', 'n-ana-furlan'],
    [new Names([v('arthur', 'Beekeeper Arthur')], 'en'), "They say King Arthur's sword went back. Beekeeper Arthur's honey.", 'arthur'],
    [new Names([v('resi', 'Oma Resi'), v('florian', 'Hirte Florian')], 'de'), 'Resis Küche. Hat Florian Hunger?', 'florian,resi'],
  ]
  const off = cases.filter(([names, s, want]) => of(names, s) !== want)
  check('names found as the app finds them: declined, possessive, not Jan, not a harbour, not Prešeren, not King Arthur', off.length === 0, off.map(([n, s, w]) => [s, of(n, s), w]))

  const village = text(await client.callTool({ name: 'get_village', arguments: {} }))
  const people = JSON.parse(village).people
  check('get_village says who is here and met: all a scene, a request or a line may name', JSON.stringify(people) === '{"known":["Pastir Luka","Ana Furlan"],"to_meet":[]}', people)

  const spec = JSON.parse(text(await client.callTool({ name: 'get_scene', arguments: { id: 'smoke-ogenj' } })))
  const turn = { choices: [
    { sl: 'Kje?', en: 'Where?', ok: true, reply: { sl: 'Tam, pri lipi.', en: 'There, by the linden.' } },
    { sl: 'Kdo?', en: 'Who?', why: 'You ask where: kje.', reply: { sl: 'Kdo? Hm, čebele.', en: 'Who? Hm, the bees.' } },
  ] }
  const bees = {
    ...spec, id: 'smoke-cebele', title: 'Pri čebelah · At the bees',
    people: [{ id: 'zala', name: 'Zala', emoji: '👧', art: 'child2', slot: 'right', villager: 'zala' }, { id: 'luka', name: 'Pastir Luka', emoji: '🐑', art: 'shepherd', slot: 'left', villager: 'luka' }],
    happenings: [
      { id: 'med', title: 'Zala in med · Zala and the honey', who: 'zala', dialog: 'med' },
      { id: 'volna', title: 'Luka in volna · Luka and the wool', who: 'luka', dialog: 'volna' },
    ],
    dialogs: [
      { id: 'med', lines: [{ who: 'zala', sl: 'Jan! Anton toči med!', en: 'Jan! Anton is extracting honey!' }, turn, { who: 'zala', sl: 'Hitro, pokliči Antona.', en: 'Quick, call Anton.' }] },
      { id: 'volna', lines: [{ who: 'luka', sl: 'Ej, Jan! Ana ima novo volno.', en: 'Hey, Jan! Ana has new wool.' }, turn, { who: 'luka', sl: 'Adijo!', en: 'Bye!' }] },
    ],
  }
  const pub = text(await client.callTool({ name: 'publish_scene', arguments: { scene: bees } }))
  check('publish_scene says which happening waits for someone who is not here and met (Anton), and not the one naming who is (Ana)', pub.startsWith('published scene smoke-cebele') && pub.includes('happening "med" names Čebelar Anton (anton)') && !pub.includes('"volna"'), pub)
  check('… and the scene is published all the same', (await fetch(`${base}/scenes/smoke-cebele`, { headers: auth })).status === 200)
  const plain = text(await client.callTool({ name: 'publish_scene', arguments: { scene: { ...bees, happenings: [bees.happenings[1]], dialogs: [bees.dialogs[1]], people: [bees.people[1]] } } }))
  check('publish_scene says nothing when everyone named is here and met', !plain.includes('⚠'), plain)
  check('remove the mentions scene', !(await client.callTool({ name: 'remove_scene', arguments: { id: 'smoke-cebele' } })).isError)
}

export default async function villagers() {
  // --- the registry can't drift from the app ---------------------------------------------------
  const markers = readFileSync(join(android, 'scene/TownMarkers.kt'), 'utf8').replace(/\/\/.*$/gm, '')
  const ktSpots = [...markers.matchAll(/Info\("(\w+)"/g)].map(m => m[1])
  check('TownMarkers.kt parsed: 10 spots (the horizon last)', ktSpots.length === 10 && ktSpots.at(-1) === 'horizon', ktSpots)
  check('the bridge spots match TownSpots and the scene places', JSON.stringify(ktSpots) === JSON.stringify(SPOT_IDS) && JSON.stringify(ktSpots) === JSON.stringify(SPOT_NAMES), { ktSpots, SPOT_IDS })
  const bonds = readFileSync(join(android, 'villagers/Bonds.kt'), 'utf8')
  // Bonds.NAMES are string-table keys (bi("bonds.stranger"), …), shown "Slovene · English" for Jan
  const tables = resolve(android, '../../../../../resources/l10n')
  const [sl, en] = ['sl', 'en'].map(l => JSON.parse(readFileSync(join(tables, `${l}.json`), 'utf8')) as Record<string, string>)
  const names = [...(bonds.match(/val NAMES[\s\S]*?\)\n/)?.[0] ?? '').matchAll(/bi\("([\w.]+)"\)/g)].map(m => `${sl[m[1]]} · ${en[m[1]]}`)
  check('the friendship levels match Bonds.kt', bonds.includes(`listOf(${[0, 10, 30, 70, 150].join(', ')})`) && JSON.stringify(names) === JSON.stringify(BOND_NAMES), { names, BOND_NAMES })

  // --- the curated cast ---------------------------------------------------------------------------
  const repo = resolve(import.meta.dir, '../../..')
  const castDir = join(repo, 'cultures/primorska/villagers')
  const files = readdirSync(castDir).filter(f => f.endsWith('.json'))
  check('13 curated villagers and the extras (the hunter)', files.length === 14, files)
  const cast = files.flatMap(f => {
    const v = validateVillager(JSON.parse(readFileSync(join(castDir, f), 'utf8')))
    check(`curated villager validates: ${f}`, v.ok && `${v.villager.id}.json` === f, v)
    return v.ok ? [v.villager] : []
  })
  check('the cast of 13 plays the roles; Lovec Jože is an extra, after them, at the high seat', cast.filter(v => !v.extra).length === 13 && JSON.stringify(cast.filter(v => v.extra).map(v => [v.id, v.home, v.art])) === '[["joze",["spot:highseat"],"hunter"]]' && cast.find(v => v.extra)!.order > Math.max(...cast.filter(v => !v.extra).map(v => v.order)), cast.map(v => [v.id, v.order, v.extra]))
  const requests = JSON.parse(readFileSync(join(repo, 'cultures/primorska/quests.json'), 'utf8')).requests as { giver: string }[]
  const givers = [...new Set(requests.map(r => r.giver))]
  check('10 quest givers in the culture pack, every one a villager by name', givers.length === 10 && givers.every(g => cast.some(v => v.name === g)), givers.filter(g => !cast.some(v => v.name === g)))
  check('the children are here: Nejc, Zala and Tine say ti', ['nejc', 'zala', 'tine'].every(id => cast.find(v => v.id === id)?.register === 'ti'))
  check('older people say vi, Luka ti', ['micka', 'janez', 'tone', 'mojca', 'vida', 'anton', 'ancka', 'france'].every(id => cast.find(v => v.id === id)?.register === 'vi') && cast.find(v => v.id === 'luka')?.register === 'ti')
  check('every villager has a distinct arrival order; Micka and Luka live at the camp from the start', new Set(cast.map(v => v.order)).size === cast.length && cast.filter(v => v.order <= 2).every(v => !v.since && v.home.some(h => h.startsWith('spot:'))), cast.map(v => [v.order, v.id, v.since, v.home]))
  check('the teacher waits for the school, the smith for the smithy', JSON.stringify(cast.find(v => v.id === 'mojca')?.home) === '["school"]' && cast.find(v => v.id === 'mojca')?.since === 'vas' && JSON.stringify(cast.find(v => v.id === 'tone')?.home) === '["smithy"]')
  check('lines grow warmer: every villager has greetings at level 0 and at level 3 or more, remember lines carry {memory}', cast.every(v => v.lines.greet.some(l => l.level === 0) && v.lines.greet.some(l => l.level >= 3) && v.lines.remember.every(l => l.sl.includes('{memory}'))))
  check('likes are Slovene and English (and the other bases), so a talk can hint at them', cast.every(v => v.likes.length >= 3 && v.likes.every(l => (typeof l === 'string' ? l.includes(' · ') : !!l.sl && !!l.en))))
  check('every villager thanks for a gift in their words: 3 lines for a good they like, 2 for an ordinary one, 1 for a rare one', cast.every(v => v.lines.gift?.liked.length === 3 && v.lines.gift.ordinary.length === 2 && v.lines.gift.rare.length === 1 && GIFT_KINDS.every(k => v.lines.gift![k].every(l => !!l.sl && !!l.en))), cast.filter(v => !v.lines.gift).map(v => v.id))
  // --- a villager's day: when they get up and go to bed, their night rituals (VILLAGERS.md, "A day in the village") ---------
  const bedOf = (id: string) => minutesOf(cast.find(v => v.id === id)!.routine!.bed!)
  check('the cast has its day: everyone a bedtime, the children first, Stari Janez last',
    cast.every(v => !!v.routine?.up && !!v.routine?.bed) && cast.every(v => v.id === 'janez' || bedOf(v.id) < bedOf('janez')) && ['zala', 'tine', 'nejc'].every(c => cast.every(v => ['zala', 'tine', 'nejc'].includes(v.id) || bedOf(c) < bedOf(v.id))),
    cast.map(v => [v.id, v.routine?.up, v.routine?.bed]))
  const rituals = cast.flatMap(v => (v.routine?.rituals ?? []).map(r => [v.id, r.id, r.at, r.until, r.to, r.doing]))
  check('the night rituals: Micka lights the stove before dawn, Luka checks the sheep, Tone banks the forge, Anton listens at the hives, Vida locks up, Jože on the tower',
    JSON.stringify(rituals.map(r => `${r[0]}:${r[4]}`).sort()) === JSON.stringify(['anton:beehive', 'joze:watchtower', 'luka:spot:meadow', 'micka:spot:woodpile', 'tone:smithy', 'vida:market']), rituals)
  const lukaFile = JSON.parse(readFileSync(join(castDir, 'luka.json'), 'utf8'))
  const withDay = (routine: unknown) => validateVillager({ ...lukaFile, routine })
  const refused = [
    { rituals: [{ id: 'x', at: '12:00', until: '12:20', to: 'fire' }] }, { rituals: [{ id: 'x', at: '22:00', until: '23:30', to: 'fire' }] },
    { rituals: [{ id: 'x', at: '22:00', until: '22:05', to: 'fire' }] }, { rituals: [{ id: 'x', at: '22:00', until: '22:20', to: 'nowhere' }] },
    { rituals: [{ id: 'x', at: '22:00', until: '22:20', to: 'fire' }, { id: 'x', at: '23:00', until: '23:20', to: 'fire' }] },
    { bed: '25:00' }, { bed: '14:00' }, { up: '14:00' }, { up: '5:00', rituals: [{ id: 'x', at: '4:50', until: '5:20', to: 'spot:woodpile' }] },
  ].filter(r => withDay(r).ok)
  check('a ritual is at night, 10 to 60 minutes, somewhere, once, over before they get up; a bedtime in the evening, a getting up in the morning',
    withDay({ up: '5:00', bed: '0:30', rituals: [{ id: 'x', at: '23:50', until: '0:10', to: 'fire', doing: 'watch' }] }).ok && refused.length === 0, refused)
  const lenient = withDay({ bed: '21:50', nap: '13:00', rituals: [{ id: 'x', at: '22:00', until: '22:20', to: 'fire', song: 'yes' }] })
  check('an unknown key in a routine is left out, not refused (a newer file on this bridge: the villager still loads)', lenient.ok && !('nap' in (lenient as any).villager.routine), lenient)
  // --- where a villager sleeps, when it isn't where they are by day (VILLAGERS.md, "A day in the village") ----------------
  const sleepKt = readFileSync(join(android, 'scene/Sleep.kt'), 'utf8').replace(/\/\/.*$/gm, '')
  const ktRoofs = [...(/ROOFS = setOf\(([^)]*)\)/.exec(sleepKt)?.[1] ?? '').matchAll(/BuildingType\.(\w+)/g)].map(m => m[1].toLowerCase())
  check('the roofs one can sleep under are Sleep.kt\'s', JSON.stringify(ktRoofs) === JSON.stringify(SLEEP_ROOFS), { ktRoofs, SLEEP_ROOFS })
  const sleepsIn = (sleeps: unknown) => validateVillager({ ...lukaFile, sleeps })
  const badSleeps = ['spot:pond', 'cellar', 'field', 'house:nowhere', 'House', 'house:'].filter(s => sleepsIn(s).ok)
  check('sleeps: a roof or a room of one by its art (the smith by his forge, the beekeeper in the cellar), nothing else',
    sleepsIn('smithy').ok && (sleepsIn('house:cellar') as any).villager.sleeps === 'house:cellar' && sleepsIn('hut:kitchen').ok && badSleeps.length === 0, badSleeps)
  const newer = validateVillager({ ...lukaFile, snores: true })
  check('an unknown key of a villager is left out, not refused (as an older bridge does with sleeps: the villager still loads)', newer.ok && !('snores' in (newer as any).villager), newer)
  const sleepers = Object.fromEntries(cast.filter(v => v.sleeps).map(v => [v.id, v.sleeps]))
  check('the four whose work is no home say where they sleep: Tone by his forge, Mojca in the school, Anton in Marko\'s cellar, Jože in Janez\'s living room',
    JSON.stringify(sleepers) === JSON.stringify({ anton: 'house:cellar', joze: 'house:livingroom', mojca: 'school', tone: 'smithy' }), sleepers)
  const counterparts = ['friuli', 'kaernten', 'lakeland'].map(c => {
    const dir = join(repo, 'cultures', c, 'villagers')
    return readdirSync(dir).filter(f => f.endsWith('.json')).map(f => validateVillager(JSON.parse(readFileSync(join(dir, f), 'utf8')))).flatMap(v => (v.ok && v.villager.sleeps ? [`${v.villager.art}:${v.villager.sleeps}`] : [])).sort().join(' ')
  })
  check('… and their counterparts in the other villages (the smith, the teacher, the beekeeper: none has a hunter)', counterparts.every(s => s === 'beekeeper:house:cellar smith:smithy teacher:school'), counterparts)
  const villagerExample = [...reference.matchAll(/```json\n([\s\S]*?)\n```/g)].map(m => m[1]).find(s => s.includes('lani.villager/v0'))
  check('module-spec.md villager example validates', villagerExample && validateVillager(JSON.parse(villagerExample)).ok, villagerExample && validateVillager(JSON.parse(villagerExample)))

  // --- the role-play built from a villager --------------------------------------------------------
  const luka = cast.find(v => v.id === 'luka')!
  const stranger = villagerScenario(luka, bondOf(undefined, 'luka'))
  check('a stranger gets the level-0 greeting, ti hints, open goals, the male voice and his speaker', stranger.id === 'villager:luka' && stranger.opener_sl === luka.lines.greet[0].sl && stranger.role.startsWith('Pastir Luka,') && stranger.goals.length === 4 && stranger.vocabulary_hints[0].sl === 'Kako si?' && stranger.voice === 'male' && stranger.speaker === 'young-man' && stranger.setting.includes('Stranger'), stranger)
  const friend = villagerScenario(luka, { points: 35, memories: [{ sl: 'ko si mi pomagal najti Belo' }] })
  check('a friend gets a warmer greeting and the setting says so', bondLevel(35) === 2 && friend.opener_sl === 'Jan! Ravno prav. Si videl Belo?' && friend.setting.includes('Prijatelj') && friend.setting.includes('1 shared memory'), friend)
  const micka = villagerScenario(cast.find(v => v.id === 'micka')!, bondOf(undefined, 'micka'), 'A2')
  check('a vi villager: polite goal and hints, the learner\'s level', micka.goals[0].includes('(vi)') && micka.vocabulary_hints[0].sl === 'Kako ste?' && micka.vocabulary_hints.at(-1)?.sl === 'Na svidenje!' && micka.level === 'A2' && micka.role.length <= 300 && micka.setting.length <= 400, micka)

  // --- the time of day: a line's `when` (VILLAGERS.md) ----------------------------------------------------------------
  const opener = (v: (typeof cast)[number], part: Part, points = 0, place = JAN_PLACE) => villagerScenario(v, { points, memories: [] }, 'A1', place, part).opener_sl
  const timedLuka = {
    ...luka,
    lines: {
      ...luka.lines,
      greet: [
        { sl: 'Dober dan!', en: 'Good day!', level: 0, when: ['morning', 'afternoon'] as Part[] },
        { sl: 'Dober večer, Jan!', en: 'Good evening, Jan!', level: 0, when: ['evening', 'night'] as Part[] },
        { sl: 'Ej, Jan! Kako si?', en: 'Hey, Jan! How are you?', level: 1 },
      ],
    },
  }
  check('the opener greets for the time of day: a "Dober dan" by day, a "Dober večer" from the evening, a warmer line of any time for a friend',
    opener(timedLuka, 'morning') === 'Dober dan!' && opener(timedLuka, 'afternoon') === 'Dober dan!' && opener(timedLuka, 'evening') === 'Dober večer, Jan!' && opener(timedLuka, 'night') === 'Dober večer, Jan!' && opener(timedLuka, 'night', 10) === 'Ej, Jan! Kako si?',
    PARTS.map(p => opener(timedLuka, p)))
  const dayOnly = { ...timedLuka, lines: { ...timedLuka.lines, greet: timedLuka.lines.greet.slice(0, 1) } }
  check('… someone with no greeting for the time says a plain one for it, in the village\'s language', opener(dayOnly, 'night') === 'Dober večer!' && opener(dayOnly, 'afternoon') === 'Dober dan!' && opener(dayOnly, 'evening', 0, { ...JAN_PLACE, target: 'it', base: 'en' }) === 'Buonasera!' && helloFor('morning').de === 'Guten Tag!', [opener(dayOnly, 'night'), opener(dayOnly, 'evening', 0, { ...JAN_PLACE, target: 'it', base: 'en' })])
  check('the part of the day: as the app\'s TimeOfDay (morning 5–11, afternoon 11–17, evening 17–22, night), the phone\'s when it says', [4, 5, 10, 11, 16, 17, 21, 22, 0].map(partOf).join() === 'night,morning,morning,afternoon,afternoon,evening,evening,night,night' && partNow('evening') === 'evening' && partNow('noon', new Date(2026, 8, 27, 9)) === 'morning' && partNow(undefined, new Date(2026, 8, 27, 23)) === 'night')
  const bare = withoutWhen(timedLuka)
  check('an app that doesn\'t say its time gets the lines without "when" (an older app refuses them); one that says it, with', readsWhen(new URL('http://x/villagers?time=evening')) && !readsWhen(new URL('http://x/villagers')) && bare.lines.greet.every(l => !('when' in l)) && bare.lines.greet[1].sl === 'Dober večer, Jan!' && JSON.stringify(timedLuka.lines.greet[1].when) === '["evening","night"]' && bare.lines.bye.length === luka.lines.bye.length, bare.lines.greet)
  const noWhen = (vs: typeof cast) => JSON.parse(JSON.stringify(vs, (k, x) => (k === 'when' ? undefined : x)))
  check('the time of day changes no voice clip: the voice corpus is the same without "when"', JSON.stringify(corpus({ packs: [], modules: [], scenarios: [], villagers: cast })) === JSON.stringify(corpus({ packs: [], modules: [], scenarios: [], villagers: noWhen(cast) })))
  // --- the packs' lines by the time of day ------------------------------------------------------------------------
  const anton = cast.find(v => v.id === 'anton')!
  check('the opener greets for the time of day: Anton by day with "Dober dan", from the evening with his own "Dober večer"',
    opener(anton, 'morning') === 'Dober dan. Tiho, prosim. Čebele.' && opener(anton, 'afternoon') === 'Dober dan. Tiho, prosim. Čebele.' && opener(anton, 'evening') === 'Dober večer. Tiho, prosim. Čebele spijo.' && opener(anton, 'night') === 'Dober večer. Tiho, prosim. Čebele spijo.',
    PARTS.map(p => opener(anton, p)))
  const babica = cast.find(v => v.id === 'micka')!
  check('… Micka in the evening with a greeting of any time, not her "Dober dan"; a friend at night still the warmest', opener(babica, 'evening') === 'O, pridi, pridi. Sedi.' && opener(babica, 'morning') === 'Dober dan. Si lačen?' && opener(babica, 'night', 35) === 'Jan, fant moj! Pridi, juha je vroča.', PARTS.map(p => opener(babica, p)))
  // every culture pack's lines: a goodnight is the evening's and the night's, a good day the day's, by what the line says
  const NIGHT = /Lahko noč|Dober večer|Buonanotte|Buonasera|Gute Nacht|Guten Abend|Good night|Good evening/
  const DAY = /Dober dan|Dobro jutro|Buongiorno|Guten Tag|Guten Morgen|Good morning|Good afternoon|Good day/
  const culturesDir = resolve(repo, 'cultures')
  const wrongTime: string[] = []
  let tagged = 0
  for (const c of ['primorska', 'friuli', 'kaernten', 'lakeland']) {
    const lang = JSON.parse(readFileSync(join(culturesDir, c, 'culture.json'), 'utf8')).language
    const people = JSON.parse(readFileSync(join(culturesDir, c, 'people.json'), 'utf8')).lines
    const strangers = JSON.parse(readFileSync(join(culturesDir, c, 'surprises.json'), 'utf8')).strangers
    const everyone = [
      ...readdirSync(join(culturesDir, c, 'villagers')).filter(f => f.endsWith('.json')).map(f => JSON.parse(readFileSync(join(culturesDir, c, 'villagers', f), 'utf8')).lines),
      people.adult, people.child, ...Object.values(strangers).map((s: any) => s.lines),
    ]
    for (const lines of everyone) {
      for (const l of allLines(lines) as SpokenLine[]) {
        const s = String(l[lang])
        if (l.when) tagged++
        if (NIGHT.test(s) && (!l.when || l.when.some(p => p === 'morning' || p === 'afternoon'))) wrongTime.push(`${c}: ${s} ${JSON.stringify(l.when)}`)
        if (DAY.test(s) && (!l.when || l.when.some(p => p === 'evening' || p === 'night'))) wrongTime.push(`${c}: ${s} ${JSON.stringify(l.when)}`)
      }
    }
  }
  check('every culture pack\'s lines say good night only in the evening and at night, good day only by day', wrongTime.length === 0 && tagged >= 100, { wrongTime, tagged })

  // --- a learner of Slovene from German or Italian: the cast and the word packs explain themselves in their base -------
  const mickaV = cast.find(v => v.id === 'micka')!
  const mickaDe = villagerScenario(mickaV, bondOf(undefined, 'micka'), 'A1', { ...JAN_PLACE, base: 'de' }, 'afternoon')
  const likeDe = (mickaV.likes[0] as Record<string, string>).de
  check('a learner from German: the opener is meant in German, the hints too (how are you, what they like)', mickaDe.opener_sl === mickaV.lines.greet[0].sl && mickaDe.opener_en === mickaV.lines.greet[0].de && mickaDe.vocabulary_hints[0].en === 'Wie geht es Ihnen?' && !!likeDe && mickaDe.vocabulary_hints.some(h => h.en === likeDe), mickaDe)
  check('every line of the cast is meant in German and in Italian, and said in Slovene as before', cast.every(v => allLines(v.lines).every(l => !!l.de && !!l.it && said(l, 'sl') === l.sl && meant(l, 'sl', 'de') === l.de && meant(l, 'sl', 'it') === l.it && meant(l, 'sl', 'en') === l.en)))
  check('the roles and likes read as ever for Jan, and in German for a learner from German', cast.every(v => labelShown(v.role, 'sl', 'en') === `${labelTarget(v.role, 'sl')} · ${labelEn(v.role)}` && v.likes.every(l => typeof l !== 'string' && !!l.de && labelShown(l, 'sl', 'de') === `${l.sl} · ${l.de}`)) && labelShown(mickaV.role, 'sl', 'en') === 'Babica · Grandmother', cast.map(v => v.role))
  const withoutBases = (vs: typeof cast) => JSON.parse(JSON.stringify(vs, (k, x) => ((k === 'de' || k === 'it') && typeof x === 'string' ? undefined : x)))
  check('the translations change no voice clip: the voice corpus is the same without them', JSON.stringify(corpus({ packs: [], modules: [], scenarios: [], villagers: cast })) === JSON.stringify(corpus({ packs: [], modules: [], scenarios: [], villagers: withoutBases(cast) })))
  const druzina = validatePack(JSON.parse(readFileSync(join(repo, 'packs/druzina.json'), 'utf8')))
  if (!druzina.ok) throw new Error(druzina.errors)
  const dp = druzina.pack
  check('a Slovene word pack means its words in German and in Italian, and says a machine wrote them', dp.words.every(w => !!w.de && w.de !== w.en && meaningOf(dp, w, 'de') === w.de && meaningOf(dp, w, 'it') === w.it && meaningOf(dp, w) === w.en) && titleFor(dp, 'sl', 'en') === 'Družina · Family' && titleFor(dp, 'sl', 'de') === `Družina · ${(dp.title as Record<string, string>).de}` && /^de, it: machine-written/.test(dp.review ?? ''), dp)
  const learnedDe = learnPayload(dp, [{ word_id: 'mama', quality: 4 }], { session_id: 'session-001', date: '2026-09-26', duration_minutes: 2 }, 'de')
  check('… and its words, learned by a learner from German, are answered in German', learnedDe.new_vocabulary[0].content === 'mama' && learnedDe.new_vocabulary[0].answer === dp.words.find(w => w.id === 'mama')!.de, learnedDe.new_vocabulary)

  // --- routes -------------------------------------------------------------------------------------
  const list = await (await fetch(`${base}/villagers`, { headers: auth })).json()
  check('GET /villagers serves the cast in arrival order, curated, every voice set', list.length === 14 && list.at(-1).id === 'joze' && list.at(-1).extra === true && list[0].id === 'micka' && list[1].id === 'luka' && list.every((v: any) => v.source === 'curated' && ['female', 'male'].includes(v.voice)), list.map((v: any) => [v.order, v.id, v.voice]))
  const voiceCast = castOf()
  check('every curated villager has a speaker from the voice cast, of their gender', list.every((v: any) => voiceCast[v.speaker]?.gender === v.voice && speakerErrors(v).length === 0), list.map((v: any) => [v.id, v.voice, v.speaker]))
  check('the cast: the narrators and a voice per role, each with an ElevenLabs voice and a Gepard fallback', voiceCast.female?.gender === 'female' && voiceCast.male?.gender === 'male' && ['grandma', 'grandpa', 'young-man', 'gruff', 'woman', 'teacher', 'young-woman', 'girl', 'boy'].every(s => !!voiceCast[s]?.elevenlabs && !!voiceCast[s].gepard), Object.keys(voiceCast))
  const one = await (await fetch(`${base}/villagers/tone`, { headers: auth })).json()
  check('GET /villagers/:id serves one villager', one.id === 'tone' && one.lines.cheer.length >= 2 && one.register === 'vi', one)
  check('GET /villagers/:id 404s', (await fetch(`${base}/villagers/nope`, { headers: auth })).status === 404 && (await fetch(`${base}/villagers/nope/scenario`, { headers: auth })).status === 404)
  check('villagers need the app token', (await fetch(`${base}/villagers`)).status === 401 && (await fetch(`${base}/villagers`, { headers: fam })).status === 403 && (await fetch(`${base}/villagers/luka/scenario`, { headers: fam })).status === 403)
  const sc0 = await (await fetch(`${base}/villagers/luka/scenario`, { headers: auth })).json()
  check('GET /villagers/:id/scenario: a stranger\'s talk before any friendship', sc0.id === 'villager:luka' && sc0.opener_sl === 'Ej, živjo! Si ti novi?' && sc0.source === 'curated', sc0)

  // the friendship lives in the village state: with points and a memory, the talk changes
  const current = await (await fetch(`${base}/game`, { headers: auth })).json()
  const state = {
    ...current.state,
    bonds: { luka: { points: 35, memories: [{ on: '2026-09-20', sl: 'ko si mi pomagal najti Belo', en: 'when you helped me find Bela', kind: 'quest' }], met: '2026-09-18', seen: '2026-09-20' } },
    residents: [{ id: 'luka', since: '2026-09-18' }, { id: 'n-ana-furlan', since: '2026-09-23', name: 'Ana Furlan', emoji: '👩', art: 'woman', voice: 'female', role: 'Tkalka · Weaver', family: 'Furlan' }],
  }
  const put = await fetch(`${base}/game`, { method: 'PUT', headers: auth, body: JSON.stringify({ rev: current.rev, state }) })
  check('village state with bonds and residents saved', put.ok, await put.clone().text())
  await mentions()
  const sc2 = await (await fetch(`${base}/villagers/luka/scenario`, { headers: auth })).json()
  check('the talk is built for the friendship: a friend\'s greeting, the memory counted', sc2.opener_sl === 'Jan! Ravno prav. Si videl Belo?' && sc2.setting.includes('Prijatelj') && sc2.setting.includes('1 shared memory'), sc2)
  const ana = await (await fetch(`${base}/villagers/n-ana-furlan/scenario?time=afternoon`, { headers: auth })).json()
  check('a resident who is not in the cast can be talked to (built from the village state), in the young woman\'s voice', ana.id === 'villager:n-ana-furlan' && ana.role.startsWith('Ana Furlan, Weaver') && ana.opener_sl === 'Dober dan!' && ana.voice === 'female' && ana.speaker === 'young-woman' && ana.source === 'village', ana)
  const anaLate = await (await fetch(`${base}/villagers/n-ana-furlan/scenario?time=night`, { headers: auth })).json()
  const antonAt = async (time: string) => (await (await fetch(`${base}/villagers/anton/scenario?time=${time}`, { headers: auth })).json()).opener_sl
  const [antonDay, antonEvening, antonNoon] = [await antonAt('afternoon'), await antonAt('evening'), await antonAt('noon')]
  check('GET /villagers/:id/scenario?time=: the phone\'s part of the day picks the opener (the node\'s for a time it doesn\'t know)', anaLate.opener_sl === 'Dober večer!' && antonDay === 'Dober dan. Tiho, prosim. Čebele.' && antonEvening === 'Dober večer. Tiho, prosim. Čebele spijo.' && [antonDay, antonEvening].includes(antonNoon), [anaLate.opener_sl, antonDay, antonEvening, antonNoon])
  const [castOld, castNew] = [await (await fetch(`${base}/villagers`, { headers: auth })).json(), await (await fetch(`${base}/villagers?time=afternoon`, { headers: auth })).json()]
  const antonGreet = (list: any[]) => list.find((x: any) => x.id === 'anton').lines.greet
  check('the tagged cast: its "when" for an app that says its time, none for an older app', JSON.stringify(antonGreet(castNew)[0].when) === '["morning","afternoon"]' && castOld.every((x: any) => allLines(x.lines).every((l: any) => !('when' in l))) && antonGreet(castOld).length === antonGreet(castNew).length, antonGreet(castOld).slice(0, 3))
  const lukaServed = castNew.find((x: any) => x.id === 'luka')?.routine
  check('GET /villagers serves each one\'s day (an older app ignores it): Luka up at five, the sheep at night', lukaServed?.up === '5:00' && lukaServed?.rituals?.[0]?.id === 'ovce' && lukaServed.rituals[0].title?.sl === 'Luka pogleda ovce v staji' && castOld.find((x: any) => x.id === 'luka')?.routine?.bed === '21:50', lukaServed)
  const sleepsServed = (list: any[], id: string) => list.find((x: any) => x.id === id)?.sleeps
  check('GET /villagers serves where they sleep (an older app ignores it): Tone by his forge, Jože with Janez; Luka says nothing',
    sleepsServed(castNew, 'tone') === 'smithy' && sleepsServed(castOld, 'joze') === 'house:livingroom' && sleepsServed(castNew, 'luka') === undefined, castNew.map((x: any) => [x.id, x.sleeps]))

  // --- the villager persona on a role-play turn ------------------------------------------------------
  const rp = await fetch(`${base}/message`, {
    method: 'POST',
    headers: auth,
    body: JSON.stringify({ kind: 'roleplay', conversation_id: 'rp-1f8b8e2c-5a1d-4d6b-9b8e-2f3c4d5e6f71', text: 'Živjo, Luka!', data: { scenario_id: 'villager:luka', turn: 1, heard: 'Živjo, Luka!' } }),
  })
  check('villager roleplay message accepted', rp.ok, await rp.clone().text())
  await Bun.sleep(100)
  const rpEv = channelEvents.findLast(n => n.params?.meta?.kind === 'roleplay')
  const content: string = rpEv?.params?.content ?? ''
  check('the tutor gets the scenario brief, the persona, the friendship level and the memories', content.includes('"villager"') && content.includes('Pastir Luka, Shepherd') && content.includes('"level": 2') && content.includes('Prijatelj · Friend') && content.includes('ko si mi pomagal najti Belo') && content.includes('"register": "ti"') && content.includes('his sheep'), content.slice(0, 600))
  const rpRes = await fetch(`${base}/message`, {
    method: 'POST',
    headers: auth,
    body: JSON.stringify({ kind: 'roleplay', conversation_id: 'rp-1f8b8e2c-5a1d-4d6b-9b8e-2f3c4d5e6f72', text: 'Dober dan!', data: { scenario_id: 'villager:n-ana-furlan', turn: 1, heard: 'Dober dan!' } }),
  })
  await Bun.sleep(100)
  const rpRes2 = channelEvents.findLast(n => n.params?.meta?.kind === 'roleplay')
  check('a talk with a resident carries her persona too', rpRes.ok && rpRes2?.params?.content.includes('Ana Furlan') && rpRes2.params.content.includes('Tujec · Stranger'), rpRes2?.params?.content?.slice(0, 300))
  const unknown = await fetch(`${base}/message`, { method: 'POST', headers: auth, body: JSON.stringify({ kind: 'roleplay', conversation_id: 'rp-1f8b8e2c-5a1d-4d6b-9b8e-2f3c4d5e6f73', text: 'x', data: { scenario_id: 'villager:nobody', turn: 1 } }) })
  check('a talk with nobody still reaches the tutor, without a persona', unknown.ok, await unknown.clone().text())

  // --- villager_arrived --------------------------------------------------------------------------------
  const arrived = await fetch(`${base}/message`, {
    method: 'POST',
    headers: auth,
    body: JSON.stringify({ kind: 'villager_arrived', conversation_id: 'main', text: 'Ana Furlan, tkalka, se je priselila v vas.', data: { id: 'n-ana-furlan', name: 'Ana Furlan', role: 'Tkalka · Weaver', family: 'Furlan', kind: 'newcomer' } }),
  })
  check('villager_arrived accepted', arrived.ok, await arrived.clone().text())
  await Bun.sleep(100)
  const arrivedEv = channelEvents.findLast(n => n.params?.meta?.kind === 'villager_arrived')
  check('villager_arrived reaches the tutor with who came', arrivedEv?.params?.content.includes('Ana Furlan') && arrivedEv.params.content.includes('"kind": "newcomer"'), arrivedEv)
  check('instructions say how to play a villager and welcome a newcomer', JSON.stringify(await guideOf()).includes('villager_arrived') && JSON.stringify(await guideOf()).includes('data.memory') && JSON.stringify(await guideOf()).includes('publish_villager'))
  check('… and to tag a line of a time of day with "when", a level-0 greeting for every part of the day', JSON.stringify(await guideOf()).includes('keep a level-0 greeting for every part of the day') && JSON.stringify(await guideOf()).includes('Lahko noč'))

  // --- validation --------------------------------------------------------------------------------------
  const l = (sl: string, en: string, level = 0) => ({ sl, en, level })
  const anaSpec = {
    schema: 'lani.villager/v0', id: 'n-ana-furlan', name: 'Ana Furlan', emoji: '👩', art: 'woman', voice: 'female', role: 'Tkalka · Weaver',
    home: ['house', 'hut'], register: 'vi', order: 40,
    personality: 'A weaver from Solkan, calm and precise, says vi. Talks about wool, the burja and her loom.',
    story: 'Ana moved up from Solkan with her loom.', likes: ['volna · wool', 'burja · the burja'],
    lines: {
      greet: [l('Dober dan.', 'Good day.'), l('O, Jan! Pridite noter.', 'Oh, Jan! Come in.', 2)],
      thanks: [l('Hvala vam.', 'Thank you.'), l('Hvala, Jan.', 'Thanks, Jan.')],
      remember: [l('Še vedno mislim na {memory}.', 'I still think of {memory}.', 1), l('Ne pozabim na {memory}.', 'I do not forget {memory}.', 2)],
      idle: [l('Volna je mehka.', 'The wool is soft.'), l('Burja piha.', 'The burja is blowing.')],
      cheer: [l('Odlično.', 'Excellent.'), l('Tako.', 'There.')],
      comfort: [l('Nič hudega.', 'Never mind.'), l('Še enkrat.', 'Once more.')],
      listen: [l('Poslušajte.', 'Listen.'), l('Tiho.', 'Quiet.')],
      bye: [l('Na svidenje.', 'Goodbye.'), l('Adijo.', 'Bye.')],
    },
  }
  const bad: [string, unknown, string][] = [
    ['an unknown sprite', { ...anaSpec, art: 'dragon' }, 'art'],
    ['a home that is no place', { ...anaSpec, home: ['castle'] }, 'home'],
    ['a spot that does not exist', { ...anaSpec, home: ['spot:moon'] }, 'home'],
    ['a home listed twice', { ...anaSpec, home: ['house', 'house'] }, 'twice'],
    ['a register that is not ti or vi', { ...anaSpec, register: 'du' }, 'register'],
    ['a remember line without {memory}', { ...anaSpec, lines: { ...anaSpec.lines, remember: [l('Se spomniš?', 'Remember?'), l('Ne pozabim.', 'I do not forget.')] } }, 'must contain {memory}'],
    ['a greet line with {memory}', { ...anaSpec, lines: { ...anaSpec.lines, greet: [l('Dober dan, {memory}.', 'Good day, {memory}.'), l('Živjo.', 'Hi.')] } }, 'only remember lines'],
    ['no level-0 greeting', { ...anaSpec, lines: { ...anaSpec.lines, greet: [l('Živjo.', 'Hi.', 1), l('O, Jan!', 'Oh, Jan!', 2)] } }, 'level 0'],
    ['a kind with one line', { ...anaSpec, lines: { ...anaSpec.lines, bye: [l('Adijo.', 'Bye.')] } }, 'bye'],
    ['a level above 4', { ...anaSpec, lines: { ...anaSpec.lines, bye: [l('Adijo.', 'Bye.', 5), l('Čav.', 'Bye.')] } }, 'level'],
    ['a bad id', { ...anaSpec, id: 'Ana Furlan' }, 'id'],
    ['an unknown skill', { ...anaSpec, skill: 'gold' }, 'skill'],
    ['an unknown age', { ...anaSpec, since: 'empire' }, 'since'],
    ['a line without English', { ...anaSpec, lines: { ...anaSpec.lines, idle: [{ sl: 'Volna.' }, l('Burja.', 'Burja.')] } }, 'en'],
    ['gift lines without a kind', { ...anaSpec, lines: { ...anaSpec.lines, gift: { liked: [l('Hvala!', 'Thanks!')], rare: [l('Joj!', 'Oh my!')] } } }, 'ordinary'],
    ['a gift line with a {placeholder}', { ...anaSpec, lines: { ...anaSpec.lines, gift: { liked: [l('Hvala za {item}!', 'Thanks for {item}!')], ordinary: [l('Hvala.', 'Thanks.')], rare: [l('Joj!', 'Oh my!')] } } }, 'said as it is'],
    ['an unknown kind of gift line', { ...anaSpec, lines: { ...anaSpec.lines, gift: { liked: [l('Hvala!', 'Thanks!')], ordinary: [l('Hvala.', 'Thanks.')], rare: [l('Joj!', 'Oh my!')], cheap: [l('Hm.', 'Hm.')] } } }, 'cheap'],
    ['a "when" that is no part of the day', { ...anaSpec, lines: { ...anaSpec.lines, bye: [{ ...l('Adijo.', 'Bye.'), when: ['dawn'] }, l('Čav.', 'Bye.')] } }, 'isn\'t a part of the day'],
    ['an empty "when"', { ...anaSpec, lines: { ...anaSpec.lines, bye: [{ ...l('Adijo.', 'Bye.'), when: [] }, l('Čav.', 'Bye.')] } }, 'leave "when" out'],
    ['a "when" that is no list', { ...anaSpec, lines: { ...anaSpec.lines, bye: [{ ...l('Lahko noč.', 'Good night.'), when: 'night' }, l('Čav.', 'Bye.')] } }, 'when'],
    ['a part of the day twice', { ...anaSpec, lines: { ...anaSpec.lines, bye: [{ ...l('Lahko noč.', 'Good night.'), when: ['night', 'night'] }, l('Čav.', 'Bye.')] } }, 'twice'],
    ['greetings for a stranger only by day', { ...anaSpec, lines: { ...anaSpec.lines, greet: [{ ...l('Dober dan.', 'Good day.'), when: ['morning', 'afternoon'] }, l('O, Jan!', 'Oh, Jan!', 2)] } }, "isn't greeted in the evening, night"],
    ['seven lines of a kind for one time of day', { ...anaSpec, lines: { ...anaSpec.lines, idle: ['a', 'b', 'c', 'd', 'e', 'f', 'g'].map(x => l(`Volna ${x}.`, `Wool ${x}.`)) } }, 'at most 6'],
  ]
  for (const [label, villager, mention] of bad) {
    const r = await client.callTool({ name: 'publish_villager', arguments: { villager } })
    check(`publish_villager rejects ${label}`, r.isError && text(r).includes(mention), r)
  }
  const timedSpec = {
    ...anaSpec,
    lines: {
      ...anaSpec.lines,
      greet: [{ ...l('Dober dan.', 'Good day.'), when: ['morning', 'afternoon'] }, { ...l('Dober večer.', 'Good evening.'), when: ['evening', 'night'] }, l('O, Jan! Pridite noter.', 'Oh, Jan! Come in.', 2)],
      idle: [...['a', 'b', 'c', 'd', 'e'].map(x => l(`Volna ${x}.`, `Wool ${x}.`)), { ...l('Sonce suši volno.', 'The sun dries the wool.'), when: ['morning', 'afternoon'] }, { ...l('Volna počiva.', 'The wool is resting.'), when: ['evening', 'night'] }],
    },
  }
  const timed = validateVillager(timedSpec)
  check('a line with "when" validates and keeps it; a line for another time of day doesn\'t count toward the six', timed.ok && JSON.stringify(timed.villager.lines.greet[1].when) === '["evening","night"]' && timed.villager.lines.idle.length === 7 && !('when' in timed.villager.lines.greet[2]), timed)
  // served with "when" only to an app that says its time of day (?time=): an older app (0.1.402) refuses such a line
  const marta = await client.callTool({ name: 'publish_villager', arguments: { villager: { ...timedSpec, id: 'n-marta-kos', name: 'Marta Kos' } } })
  const greetOf = (list: any[]) => list.find((v: any) => v.id === 'n-marta-kos')?.lines.greet
  const [oldList, newList] = [await (await fetch(`${base}/villagers`, { headers: auth })).json(), await (await fetch(`${base}/villagers?time=evening`, { headers: auth })).json()]
  const [oldOne, newOne] = [await (await fetch(`${base}/villagers/n-marta-kos`, { headers: auth })).json(), await (await fetch(`${base}/villagers/n-marta-kos?time=morning`, { headers: auth })).json()]
  check('GET /villagers and /villagers/:id: "when" only for an app that says its time; without it every line, none bound to a time',
    !marta.isError && greetOf(oldList)?.length === 3 && greetOf(oldList).every((l: any) => !('when' in l)) && oldOne.lines.greet.every((l: any) => !('when' in l)) && oldList.every((v: any) => allLines(v.lines).every((l: any) => !('when' in l))) &&
      JSON.stringify(greetOf(newList)?.[1]?.when) === '["evening","night"]' && JSON.stringify(newOne.lines.idle.at(-1).when) === '["evening","night"]' && newOne.lines.greet[0].sl === 'Dober dan.',
    { old: greetOf(oldList), new: greetOf(newList) })
  check('… (the tutor\'s villager removed again)', !(await client.callTool({ name: 'remove_villager', arguments: { id: 'n-marta-kos' } })).isError)
  // a villager of another village: lines, role and likes in its language with translations ({"it", "sl", "en"})
  const withIt = (ls: { sl: string; en: string; level: number }[]) => ls.map(x => ({ it: `${x.sl} (it)`, ...x }))
  const italian = {
    ...anaSpec, role: { it: 'Tessitrice', sl: 'Tkalka', en: 'Weaver' }, likes: [{ it: 'la lana', sl: 'volna', en: 'wool' }, 'the bora'],
    lines: Object.fromEntries(Object.entries(anaSpec.lines).map(([k, ls]) => [k, withIt(ls)])),
  }
  const vIt = validateVillager(italian)
  check('a villager in the new format validates: lines {it, sl, en}, a role and likes per language', vIt.ok && (vIt.villager.lines.greet[0] as any).it === 'Dober dan. (it)', vIt)
  const noMemory = validateVillager({ ...italian, lines: { ...italian.lines, remember: [{ it: 'Ricordo.', sl: 'Mislim na {memory}.', en: 'I think of {memory}.' }, ...italian.lines.remember] } })
  check('… every language of a remember line needs {memory}', !noMemory.ok && noMemory.errors.includes('the "it" needs {memory} too'), noMemory)
  const noEnglish = validateVillager({ ...italian, lines: { ...italian.lines, idle: [{ it: 'Lana.', sl: 'Volna.' }, ...italian.lines.idle] } })
  const onlyEnglish = validateVillager({ ...italian, lines: { ...italian.lines, idle: [{ en: 'Wool.' }, ...italian.lines.idle] } })
  const oddKey = validateVillager({ ...italian, lines: { ...italian.lines, idle: [{ it: 'Lana.', en: 'Wool.', note: 'x' }, ...italian.lines.idle] } })
  check('… a line needs English and the village\'s language, and only language codes', !noEnglish.ok && noEnglish.errors.includes('"en"') && !onlyEnglish.ok && !oddKey.ok && oddKey.errors.includes('"note"'), [noEnglish, onlyEnglish, oddKey])

  // --- publishing: a newcomer gets a personality; a curated id can be replaced and restored -----------
  check('publish_villager refuses a long note', (await client.callTool({ name: 'publish_villager', arguments: { villager: anaSpec, note: 'x'.repeat(501) } })).isError)
  const pub = await client.callTool({ name: 'publish_villager', arguments: { villager: anaSpec, note: 'Ana Furlan, the weaver, has a story now' } })
  check('publish_villager publishes a newcomer', !pub.isError && text(pub).includes('published villager n-ana-furlan (Ana Furlan, 16 lines)') && !text(pub).includes('replaces'), pub)
  const withGifts = validateVillager({ ...anaSpec, lines: { ...anaSpec.lines, gift: { liked: [l('O, hvala! To imam rada.', 'Oh, thanks! I like this.')], ordinary: [l('Hvala vam.', 'Thank you.')], rare: [l('Joj, kaj takega!', 'Oh my, something like this!')] } } })
  check('gift lines are optional, and kept when given (liked, ordinary, rare)', withGifts.ok && withGifts.villager.lines.gift?.rare[0].sl === 'Joj, kaj takega!' && validateVillager(anaSpec).ok && !(validateVillager(anaSpec) as any).villager.lines.gift, withGifts)
  check('villager file written to <data>/app/villagers', existsSync(join(dataDir, 'app/villagers/n-ana-furlan.json')))
  const list2 = await (await fetch(`${base}/villagers`, { headers: auth })).json()
  const anaServed = list2.find((v: any) => v.id === 'n-ana-furlan')
  check('the newcomer is served as tutor, in arrival order after the cast (before the hunter, an extra with order 50)', list2.length === 15 && anaServed?.source === 'tutor' && anaServed.published_at > 0 && anaServed.skill === undefined && list2.at(-2).id === 'n-ana-furlan' && list2.at(-1).id === 'joze', list2.map((v: any) => [v.order, v.id]))
  const anaTalk = await (await fetch(`${base}/villagers/n-ana-furlan/scenario`, { headers: auth })).json()
  check('her talk now comes from her own lines and likes', anaTalk.source === 'tutor' && anaTalk.role.includes('weaver from Solkan') && anaTalk.vocabulary_hints.some((h: any) => h.sl === 'volna'), anaTalk)
  await Bun.sleep(100)
  const ev = await (await fetch(`${base}/events/backlog?since=0`, { headers: auth })).json()
  check('villager_published event', ev.some((b: any) => b.event.type === 'villager_published' && b.event.id === 'n-ana-furlan' && b.event.name === 'Ana Furlan' && b.event.note === 'Ana Furlan, the weaver, has a story now'))
  for (let i = 0; i < 50; i++) {
    if ((await (await fetch(`${base}/voice/status`, { headers: auth })).json()).queued === 0) break
    await Bun.sleep(100)
  }
  const vIdx = await (await fetch(`${base}/voice/index`, { headers: auth })).json()
  check("a published villager's lines are voiced in her voice; remember lines are not prebuilt", !!vIdx['volna je mehka']?.female && !vIdx['volna je mehka']?.male && !Object.keys(vIdx).some(k => k.includes('memory')), Object.keys(vIdx).filter(k => k.includes('volna') || k.includes('memory')))

  const noSpeaker = await client.callTool({ name: 'publish_villager', arguments: { villager: { ...anaSpec, speaker: 'soprano' } } })
  const wrongGender = await client.callTool({ name: 'publish_villager', arguments: { villager: { ...anaSpec, speaker: 'gruff' } } })
  check('publish_villager refuses a speaker not in the voice cast, or of the other gender', noSpeaker.isError && text(noSpeaker).includes('not in the voice cast') && wrongGender.isError && text(wrongGender).includes('"gruff" is male'), [noSpeaker, wrongGender])
  const over = await client.callTool({ name: 'publish_villager', arguments: { villager: { ...anaSpec, id: 'luka', name: 'Pastir Luka', art: 'shepherd', voice: 'male', order: 2 } } })
  check('publish_villager replaces a curated villager and says so', !over.isError && text(over).includes('replaces the curated villager "luka"'), over)
  const lukaOver = await (await fetch(`${base}/villagers/luka`, { headers: auth })).json()
  check('the tutor villager shadows the curated one (one entry per id)', lukaOver.source === 'tutor' && lukaOver.role === 'Tkalka · Weaver' && (await (await fetch(`${base}/villagers`, { headers: auth })).json()).filter((v: any) => v.id === 'luka').length === 1, lukaOver)
  const gv = await client.callTool({ name: 'get_villager', arguments: { id: 'luka' } })
  check('get_villager returns the spec to edit', !gv.isError && text(gv).includes('"schema": "lani.villager/v0"') && text(gv).includes('Tkalka'), gv)
  check('get_villager of an unknown id fails', (await client.callTool({ name: 'get_villager', arguments: { id: 'nope' } })).isError)
  const rm = await client.callTool({ name: 'remove_villager', arguments: { id: 'luka' } })
  check('remove_villager removes the tutor villager, the curated one is back', !rm.isError && text(rm).includes('curated one is back') && (await (await fetch(`${base}/villagers/luka`, { headers: auth })).json()).source === 'curated', rm)
  check('remove_villager of nothing fails', (await client.callTool({ name: 'remove_villager', arguments: { id: 'luka' } })).isError && (await client.callTool({ name: 'remove_villager', arguments: { id: '../x' } })).isError)
  await Bun.sleep(50)
  const ev2 = await (await fetch(`${base}/events/backlog?since=0`, { headers: auth })).json()
  check('villager_removed event', ev2.some((b: any) => b.event.type === 'villager_removed' && b.event.id === 'luka'))
  const ls = await client.callTool({ name: 'list_villagers', arguments: {} })
  check('list_villagers shows the cast and the newcomer with their homes and line counts', !ls.isError && text(ls).includes('Babica Micka') && text(ls).includes('Ana Furlan') && text(ls).includes('"spot:meadow"') && text(ls).includes('"greet": 6'), ls)

  // --- the voice corpus --------------------------------------------------------------------------------
  const items = corpus({ packs: [], modules: [], scenarios: [], villagers: cast })
  const of = (t: string) => items.find(i => i.text === t)
  check('corpus: villager lines in their speaker\'s voice, first, remember lines skipped', of('Ej, živjo! Si ti novi?')?.voice === 'young-man' && of('Ej, živjo! Si ti novi?')?.priority === 0 && of('Dober dan. Si lačen?')?.voice === 'grandma' && of('Živjo! Jaz sem Nejc. Igraš nogomet?')?.voice === 'boy' && !items.some(i => i.text.includes('{memory}')) && of('Ej, živjo! Si ti novi?')?.source === 'villager:luka', items.slice(0, 4))
  const lost = corpus({ packs: [], modules: [], scenarios: [], villagers: [{ ...cast.find(v => v.id === 'luka')!, speaker: 'nobody' }] })
  check('corpus: a speaker the cast lost falls back to the gender voice', lost.every(i => i.voice === 'male') && lost.length > 0, lost.slice(0, 2))
  const st = await (await fetch(`${base}/voice/status`, { headers: auth })).json()
  check('voice status counts the villagers in the corpus', st.corpus?.by_source?.villager?.texts >= 300, st.corpus?.by_source)
}
