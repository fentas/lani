// Smoke checks: arrivals (lani.arrival/v0, lani.arrivals/v0; companion/VILLAGERS.md "Arrivals"): the culture packs'
// arrivals validate, the format's rules (who speaks, the late line, a reaction to every wrong choice, a template's
// placeholders, the four languages), the tutor's (publish_arrival, GET /arrivals, get_arrival, remove_arrival), what
// villager_arrived brings the tutor, and the app's side can't drift (the levels, the template's arguments).
import { readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { arrivalsFileErrors, arrivalsFileSpec, cultureArrivals, messageArgs, TEMPLATE_ARGS, validateArrival, type ArrivalsFile } from '../../src/arrivals'
import { cultureIds, readManifest } from '../../src/cultures'
import { LEVELS } from '../../src/stories'
import { VillagerStore } from '../../src/villagers'
import { auth, base, channelEvents, check, client, culturesDir, dataDir, guideOf } from './harness'

const android = resolve(import.meta.dir, '../../../android/app/src/main/java/si/lanisce/lani/game')
const text = (r: any) => (r.content ?? []).map((c: any) => c.text ?? '').join('\n')
/** A text in the app's four languages. */
const t = (sl: string, rest?: Partial<Record<'en' | 'de' | 'it', string>>) => ({ sl, en: rest?.en ?? `${sl} (en)`, de: rest?.de ?? `${sl} (de)`, it: rest?.it ?? `${sl} (it)` })
const why = (en: string) => ({ en, de: `${en} (de)`, it: `${en} (it)` })
const said = (who: string, sl: string) => ({ who, ...t(sl) })
const turn = () => ({ choices: [{ ...t('Živjo!'), ok: true }, { ...t('Dober večer!'), why: why('It is morning.'), reply: t('Večer? Sonce sije!') }] })
const level = (who: string[]) => ({ late: said(who[0], 'Nisva se še spoznala.'), lines: [said(who[0], 'Dober dan!'), turn(), said(who.at(-1)!, 'Adijo!')] })
const body = (who: string[], by?: string) => ({ ...(by ? { by } : {}), memory: t('dan, ko sva se spoznala'), levels: { A1: level(who), A2: level(who) } })

export default async function arrivals() {
  // --- the culture packs' ---------------------------------------------------------------------------------------
  for (const id of cultureIds(culturesDir)) {
    const m = readManifest(culturesDir, id)
    if (!m.ok || m.manifest.status !== 'complete') continue
    const cast = new VillagerStore(join(culturesDir, id, 'villagers'), join(dataDir, `unused-villagers-arrivals-${id}`)).all()
    const r = cultureArrivals(culturesDir, id, m.manifest.language, cast)
    check(`${id}: its arrivals validate (each of its cast at A1 and A2, a late line, a reaction to every wrong choice)`, r.errors.length === 0, r.errors.slice(0, 12))
    const file = r.file
    check(
      `${id}: every one of its cast is introduced, and a newcomer, a baby and a mover by its templates, at A1 and A2`,
      !!file && cast.filter(v => !v.extra).every(v => !!file.cast[v.id]) && (['newcomer', 'birth', 'moved'] as const).every(k => !!file.templates[k]?.levels.A1 && !!file.templates[k]?.levels.A2),
      file ? Object.keys(file.cast) : 'no arrivals.json',
    )
  }
  const zala = cultureArrivals(culturesDir, 'primorska', 'sl').file?.cast.zala
  check('Primorska: Micka introduces her granddaughter Zala', zala?.by === 'micka' && zala.levels.A1?.lines[0].who === 'micka' && zala.levels.A1.lines.some(l => l.who === 'zala'), zala?.levels.A1?.lines[0])

  // --- the format --------------------------------------------------------------------------------------------
  const cast = [{ id: 'micka', order: 1 }, { id: 'zala', order: 3 }, { id: 'joze', order: 50, extra: true }]
  const good: ArrivalsFile = arrivalsFileSpec.parse({
    schema: 'lani.arrivals/v0', language: 'sl', review: 'smoke',
    cast: { micka: body(['micka']), zala: body(['micka', 'zala'], 'micka') },
    templates: {
      newcomer: { memory: t('dan, ko sem se {g, select, f {preselila} other {preselil}} v vas'), levels: { A1: { late: said('self', 'Nisva se še spoznala.'), lines: [said('self', 'Jaz sem {name}, {role}.'), turn(), said('self', 'Adijo!')] }, A2: level(['self']) } },
      birth: { memory: t('dan, ko si me prvič videl'), levels: { A1: { late: said('parent', 'Še nisi videl dojenčka!'), lines: [said('parent', 'To je {first}.'), turn(), said('parent2', 'Hvala, ker si prišel!')] }, A2: level(['parent', 'parent2']) } },
    },
  })
  check('a pack\'s arrivals: the cast\'s (an extra may go without) and the templates, with their placeholders', arrivalsFileErrors(good, 'sl', cast).length === 0, arrivalsFileErrors(good, 'sl', cast))
  const bad: [string, (f: any) => void, string][] = [
    ['someone of the cast without one', f => delete f.cast.zala, 'no arrival for "zala"'],
    ['an introducer who comes later', f => { f.cast.micka.by = 'zala'; f.cast.micka.levels.A1.lines[0].who = 'zala' }, 'comes after'],
    ['a speaker who isn\'t in it', f => (f.cast.zala.levels.A1.lines[0].who = 'luka'), 'who "luka" isn\'t in it'],
    ['a level without its late line', f => delete f.cast.zala.levels.A2.late, 'late: missing'],
    ['no A2', f => delete f.cast.zala.levels.A2, 'no A2'],
    ['a wrong choice without a reaction', f => delete f.cast.zala.levels.A1.lines[1].choices[1].reply, 'needs a reaction'],
    ['a wrong choice without a why', f => delete f.cast.zala.levels.A1.lines[1].choices[1].why, 'needs a why'],
    ['a turn without a wrong choice', f => (f.cast.zala.levels.A1.lines[1].choices[1].ok = true), 'keeps a wrong one'],
    ['a line in three languages', f => delete f.cast.zala.levels.A1.lines[0].de, 'no "de" text'],
    ['a why without Italian', f => delete f.cast.zala.levels.A1.lines[1].choices[1].why.it, 'no "it" why'],
    ['the learner\'s turn first', f => f.cast.zala.levels.A1.lines.unshift(turn()), 'the first line is someone\'s'],
    ['a placeholder in the cast\'s', f => (f.cast.zala.levels.A1.lines[0].sl = 'Jaz sem {name}.'), 'names people as they are'],
    ['a placeholder a template isn\'t given', f => (f.templates.birth.levels.A1.lines[0].sl = 'To je {name}.'), 'which a template isn\'t given'],
    ['a message that doesn\'t parse', f => (f.templates.newcomer.levels.A1.lines[0].sl = 'Jaz sem {g, select, f {nova}}.'), 'without an other branch'],
    ['a baby that talks', f => (f.templates.birth.levels.A1.lines[2].who = 'self'), 'who "self" isn\'t in it'],
    ['a sky over an arrival', f => (f.cast.zala.levels.A1.lines[0].sky = { rain: 0.5 }), 'no sky or fx cue'],
  ]
  for (const [label, change, mention] of bad) {
    const f = structuredClone(good) as any
    change(f)
    const parsed = arrivalsFileSpec.safeParse(f)
    const errs = parsed.success ? arrivalsFileErrors(parsed.data, 'sl', cast) : [parsed.error.message]
    check(`a pack's arrivals are refused with ${label}`, errs.some(e => e.includes(mention)), errs)
  }
  check('the placeholders are read as the app reads its messages', [...messageArgs('{g, select, f {Ime ji je} other {Ime mu je}} {first}.')].join() === 'g,first' && (() => { try { messageArgs('{g, select, f {x}'); return false } catch { return true } })())

  // --- the app's side can't drift ------------------------------------------------------------------------------
  const kt = readFileSync(join(android, 'villagers/Arrivals.kt'), 'utf8')
  const ktLevels = /val LEVELS = listOf\(([^)]*)\)/.exec(kt)?.[1].match(/"(\w+)"/g)?.map(s => s.slice(1, -1))
  check('the levels an arrival is written for are the app\'s', JSON.stringify(ktLevels) === JSON.stringify(LEVELS), { ktLevels, LEVELS })
  const ktArgs = new Set([...kt.matchAll(/"(\w+)" to /g)].map(m => m[1]))
  const allArgs = [...new Set(Object.values(TEMPLATE_ARGS).flat())]
  check('every argument a template may use is one the app puts in', allArgs.every(a => ktArgs.has(a)), { allArgs, ktArgs: [...ktArgs] })

  // --- the tutor's ---------------------------------------------------------------------------------------------
  const game = await (await fetch(`${base}/game`, { headers: auth })).json()
  const residents: string[] = (game.state?.residents ?? []).map((r: any) => r.id)
  check('the village has a newcomer to introduce (from the villagers checks)', residents.includes('n-ana-furlan'), residents)
  const ana = {
    schema: 'lani.arrival/v0', id: 'n-ana-furlan', language: 'sl',
    memory: { sl: 'dan, ko sem prinesla statve v vas', en: 'the day I brought my loom to the village' },
    levels: {
      A1: {
        lines: [
          { who: 'n-ana-furlan', sl: 'Dober dan! Jaz sem Ana, tkalka.', en: 'Good day! I am Ana, a weaver.' },
          { choices: [
            { sl: 'Dober dan! Me veseli.', en: 'Good day! Nice to meet you.', ok: true, reply: { sl: 'Tudi mene.', en: 'Me too.' } },
            { sl: 'Živjo, Ana! Kako si?', en: 'Hi, Ana! How are you? (ti)', why: 'You have just met: say vi.', reply: { sl: 'Kako si? Hm, saj se še ne poznava.', en: 'How are you? Hm, we don\'t know each other yet.' } },
          ] },
          { who: 'n-ana-furlan', sl: 'Iz Solkana sem prišla, s statvami.', en: 'I came from Solkan, with my loom.' },
        ],
      },
    },
  }
  const refused: [string, unknown, string][] = [
    ['someone who doesn\'t live in the village', { ...ana, id: 'n-nobody' }, 'doesn\'t live in the village'],
    ['a speaker who doesn\'t live there', { ...ana, levels: { A1: { lines: [{ ...ana.levels.A1.lines[0], who: 'n-nobody' }, ...ana.levels.A1.lines.slice(1)] } } }, 'who "n-nobody"'],
    ['a wrong choice without a reaction', { ...ana, levels: { A1: { lines: [ana.levels.A1.lines[0], { choices: [(ana.levels.A1.lines[1] as any).choices[0], { ...(ana.levels.A1.lines[1] as any).choices[1], reply: undefined }] }, ana.levels.A1.lines[2]] } } }, 'needs a reaction'],
    ['a placeholder', { ...ana, levels: { A1: { lines: [{ ...ana.levels.A1.lines[0], sl: 'Jaz sem {first}.' }, ...ana.levels.A1.lines.slice(1)] } } }, 'names people as they are'],
    ['another language than the village\'s', { ...ana, language: 'it', levels: { A1: { lines: ana.levels.A1.lines.map((l: any) => (l.choices ? l : { who: l.who, it: l.sl, en: l.en })) } } }, 'the village speaks "sl"'],
    ['no level', { ...ana, levels: {} }, 'at least one level'],
  ]
  for (const [label, arrival, mention] of refused) {
    const r = await client.callTool({ name: 'publish_arrival', arguments: { arrival } })
    check(`publish_arrival refuses ${label}`, !!r.isError && text(r).includes(mention), text(r))
  }
  check('an arrival checked alone (no village known) lets its speakers be', validateArrival(ana).ok)
  const pub = await client.callTool({ name: 'publish_arrival', arguments: { arrival: ana, note: 'Ana has her own welcome now' } })
  check('publish_arrival publishes a newcomer\'s introduction', !pub.isError && text(pub).includes('published the arrival of n-ana-furlan (A1)'), text(pub))
  const served = await (await fetch(`${base}/arrivals`, { headers: auth })).json()
  check('GET /arrivals serves the tutor\'s, for the app', served.language === 'sl' && served.arrivals.length === 1 && served.arrivals[0].id === 'n-ana-furlan' && served.arrivals[0].source === 'tutor', served)
  check('GET /arrivals needs the app token', (await fetch(`${base}/arrivals`)).status === 401)
  const got = await client.callTool({ name: 'get_arrival', arguments: { id: 'n-ana-furlan' } })
  check('get_arrival shows the tutor\'s', !got.isError && text(got).includes('"source": "tutor"'), text(got).slice(0, 200))
  const nobody = await client.callTool({ name: 'get_arrival', arguments: { id: 'n-nobody' } })
  check('get_arrival: nobody of that id in the village', !!nobody.isError, text(nobody))
  const removed = await client.callTool({ name: 'remove_arrival', arguments: { id: 'n-ana-furlan' } })
  const again = await client.callTool({ name: 'remove_arrival', arguments: { id: 'n-ana-furlan' } })
  const after = await (await fetch(`${base}/arrivals`, { headers: auth })).json()
  check('remove_arrival takes it back: the template plays again', !removed.isError && !!again.isError && after.arrivals.length === 0, [text(removed), text(again), after])
  const template = await client.callTool({ name: 'get_arrival', arguments: { id: 'n-ana-furlan' } })
  check('get_arrival without the tutor\'s: the pack\'s template for a newcomer, with who she is', !template.isError && text(template).includes('"kind": "newcomer"') && text(template).includes('Ana Furlan') && text(template).includes('"source": "template"'), text(template).slice(0, 300))
  const luka = await client.callTool({ name: 'get_arrival', arguments: { id: 'luka' } })
  check('get_arrival for one of the cast: the culture pack\'s', !luka.isError && text(luka).includes('"source": "culture"') && text(luka).includes('"culture": "primorska"'), text(luka).slice(0, 200))

  // --- villager_arrived: what the tutor writes it around --------------------------------------------------------
  const arrived = await fetch(`${base}/message`, {
    method: 'POST', headers: auth,
    body: JSON.stringify({ kind: 'villager_arrived', conversation_id: 'main', text: 'Pri Furlanovih se je rodila Mia!', data: { id: 'n-mia-furlan', name: 'Mia Furlan', family: 'Furlan', kind: 'birth', parents: ['n-ana-furlan'] } }),
  })
  await Bun.sleep(100)
  const ev = channelEvents.findLast(n => n.params?.meta?.kind === 'villager_arrived')
  const content: string = ev?.params?.content ?? ''
  const data = JSON.parse(/```json\n([\s\S]*)\n```/.exec(content)?.[1] ?? '{}')
  check('villager_arrived brings the learner\'s level, their weak patterns and a baby\'s parents, and names the tool', arrived.ok && data.arrival?.level === 'A1' && Array.isArray(data.arrival.weak_patterns) && data.arrival.parents?.[0]?.name === 'Ana Furlan' && data.arrival.tool === 'publish_arrival', data)
  const guide = await guideOf()
  check('the guide says how to write an arrival', guide.includes('Arrivals (lani.arrival/v0') && guide.includes('publish_arrival') && guide.includes('never the right') && guide.includes('Either way, write how Jan meets them'))
}
