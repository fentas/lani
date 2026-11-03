// Smoke checks shared by the German and English villages (kaernten.ts, lakeland.ts): their role-plays
// (companion/scenarios/<language>, docs/plans/05-built/scenarios-de-en.md) as a bridge of that village serves them to a
// grown-up learner, each text in the learner's pair, the turn's brief for the tutor, and a tutor's own role-play.
import type { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { talkLevelOffered } from '../../src/scenarios'
import { check } from './harness'

const repo = resolve(import.meta.dir, '../../../..')

export type VillageTalks = {
  /** "kaernten" */
  village: string
  /** the village's language, "de" */
  language: string
  /** the learner's base, "sl" */
  base: string
  base_url: string
  auth: Record<string, string>
  client: Client
  /** the channel notifications the bridge sent the tutor so far */
  notes: () => any[]
  /** the new packs the role-plays brought (all the village's) */
  packs: string[]
  /** one role-play as the learner gets it: its id, title, first goal, who, the setting's start, a hint and its meaning */
  sample: { id: string; title: string; goal: string; who: string; setting: string; hint: [string, string]; brief: { title: string; goal: string; setting: string } }
  /** the role-plays that a villager of the cast plays: id → the villager's name */
  played: Record<string, string>
  /** a tutor's role-play in the village's language, without its language ({ id, title, …, packs }) */
  tutorTalk: Record<string, unknown>
  /** its title as the learner reads it */
  tutorTitle: string
  /** a turn the learner says first */
  hello: string
  /**
   * The village's talks above A2 (docs/plans/05-built/talks-en.md), checked for a learner at B1 (written into the
   * learner's profile in [dataDir] and put back): all offered then, one of them in the pair, the cast's own playing theirs
   * by name, a turn's brief with its level; a B2 one not found for the smoke's A1 learner.
   */
  above?: {
    dataDir: string
    sample: { id: string; level: string; title: string; goal: string; who: string; setting: string; hint: [string, string] }
    played: Record<string, string>
    hello: string
  }
}

export async function villageTalks(t: VillageTalks) {
  const { village: v, language, base_url: base, auth } = t
  const get = async (path: string) => (await fetch(`${base}${path}`, { headers: auth })).json() as Promise<any>
  const dir = join(repo, 'companion/scenarios', language)
  const files = readdirSync(dir).filter(f => f.endsWith('.json'))
  // the smoke's learner has no level (A1): the talks up to A2 (talkLevelOffered), never a B1 or B2 one
  const levelOf = (f: string) => JSON.parse(readFileSync(join(dir, f), 'utf8')).level
  const toA2 = files.filter(f => talkLevelOffered(levelOf(f), 'A1'))
  const scenarios = await get('/scenarios')
  check(
    `${v}: GET /scenarios serves the ${toA2.length} role-plays to A2 in ${language} for a grown-up (of ${files.length}), and nothing else`,
    toA2.length === 8 && scenarios.length === toA2.length && toA2.every(f => scenarios.some((s: any) => `${s.id}.json` === f && s.language === language && s.audience === 'adult' && s.source === 'curated' && /machine-written/.test(s.review))),
    scenarios.map((s: any) => [s.id, s.language, s.level]),
  )
  check(
    `${v}: every role-play's title, goals and who in the pair (${language} · ${t.base}), its setting and meanings strings in the base`,
    scenarios.every((s: any) => [s.title, s.who, ...s.goals].every(x => typeof x === 'string' && x.includes(' · ')) && typeof s.setting === 'string' && typeof s.opener_en === 'string' && s.vocabulary_hints.every((h: any) => typeof h.en === 'string' && h.en.length > 0)),
    scenarios.filter((s: any) => ![s.title, s.who, ...s.goals].every(x => typeof x === 'string' && x.includes(' · '))).map((s: any) => s.id),
  )
  const one = scenarios.find((s: any) => s.id === t.sample.id)
  check(
    `${v}: ${t.sample.id} in the learner's pair: "${t.sample.title}", its setting and meanings in the base`,
    one?.title === t.sample.title && one.goals[0] === t.sample.goal && one.who === t.sample.who && one.setting.startsWith(t.sample.setting) && one.vocabulary_hints.some((h: any) => h.sl === t.sample.hint[0] && h.en === t.sample.hint[1]),
    one,
  )
  const byId = await get(`/scenarios/${t.sample.id}`)
  check(`${v}: GET /scenarios/:id serves it in the pair too`, byId.title === t.sample.title && byId.packs?.length > 0, byId)
  const packs = new Set((await get('/packs')).map((p: any) => p.id))
  check(
    `${v}: every pack a role-play names is one of the village's (${t.packs.join(', ')} too)`,
    scenarios.every((s: any) => s.packs?.length && s.packs.every((p: string) => packs.has(p))) && t.packs.every(p => packs.has(p)),
    scenarios.map((s: any) => [s.id, (s.packs ?? []).filter((p: string) => !packs.has(p))]).filter(([, m]: any) => m.length),
  )
  // the cast's own people play the role-plays that name them, by their very name (the app's Cast.talker): the friendship grows
  const cast = new Set((await get('/villagers')).map((x: any) => x.name))
  const played = Object.entries(t.played)
  check(
    `${v}: ${played.map(([, n]) => n).join(', ')} play their role-plays, named as the cast names them`,
    played.every(([id, name]) => cast.has(name) && scenarios.find((s: any) => s.id === id)?.role.startsWith(`${name}, `)),
    played.map(([id]) => scenarios.find((s: any) => s.id === id)?.role.split(',')[0]),
  )
  // a turn reaches the tutor with the brief in English, the language it is played in, its packs, and no child line
  const conv = `rp-1b2c3d4e-0000-4000-8000-00000000${language === 'de' ? 'd' : 'e'}001`
  await fetch(`${base}/message`, { method: 'POST', headers: auth, body: JSON.stringify({ kind: 'roleplay', conversation_id: conv, text: t.hello, data: { scenario_id: t.sample.id, turn: 1, heard: t.hello } }) })
  await Bun.sleep(150)
  const note = t.notes().findLast(n => n.params?.meta?.kind === 'roleplay' && n.params.meta.conversation_id === conv)
  const brief = JSON.parse(/```json\n([\s\S]*)\n```/.exec(String(note?.params?.content))?.[1] ?? '{}').scenario
  check(
    `${v}: a role-play's turn carries its brief in English, played in ${language}, for a grown-up`,
    brief?.title === t.sample.brief.title && brief.goals?.[0] === t.sample.brief.goal && brief.setting?.startsWith(t.sample.brief.setting) && brief.language === language && brief.packs?.length > 0 && !brief.child,
    brief,
  )
  if (t.above) await talksAbove(t, files, get)
  // the tutor's: in the village's language (left out: written so), a Slovene one refused
  const pub = await t.client.callTool({ name: 'publish_scenario', arguments: { scenario: t.tutorTalk } })
  const id = String(t.tutorTalk.id)
  const served = (await get('/scenarios')).find((s: any) => s.id === id)
  const sl = await t.client.callTool({ name: 'publish_scenario', arguments: { scenario: { ...t.tutorTalk, id: `${id}-sl`, language: 'sl' } } })
  check(
    `${v}: a tutor's role-play is written in ${language} and served in the pair; a Slovene one is refused`,
    !pub.isError && served?.language === language && served.title === t.tutorTitle && served.source === 'tutor' && sl.isError && /this village speaks/.test(JSON.stringify(sl)),
    { pub, served, sl },
  )
}

/** The village's talks above A2 for a learner at B1 ([t].above): see VillageTalks.above. */
async function talksAbove(t: VillageTalks, files: string[], get: (path: string) => Promise<any>) {
  const { village: v, language, base_url: base, auth } = t
  const a = t.above!
  check(`${v}: a ${a.sample.level} talk is not found for an A1 learner (${a.sample.id})`, (await fetch(`${base}/scenarios/${a.sample.id}`, { headers: auth })).status === 404)
  const profile = join(a.dataDir, 'learner-profile.json')
  const before = readFileSync(profile, 'utf8')
  const p = JSON.parse(before)
  writeFileSync(profile, JSON.stringify({ ...p, learner: { ...p.learner, current_level: 'B1' } }, null, 2))
  try {
    const atB1 = await get('/scenarios')
    check(
      `${v}: a B1 learner is offered all ${files.length} role-plays, the B1 and B2 ones too`,
      atB1.length === files.length && files.every(f => atB1.some((s: any) => `${s.id}.json` === f && s.language === language && s.audience === 'adult')) && atB1.some((s: any) => s.level === 'B2'),
      atB1.map((s: any) => [s.id, s.level]),
    )
    const one = await get(`/scenarios/${a.sample.id}`)
    check(
      `${v}: ${a.sample.id} (${a.sample.level}) in the learner's pair: "${a.sample.title}", its setting and meanings in the base`,
      one?.level === a.sample.level && one.title === a.sample.title && one.goals[0] === a.sample.goal && one.who === a.sample.who && one.setting.startsWith(a.sample.setting) && one.vocabulary_hints.some((h: any) => h.sl === a.sample.hint[0] && h.en === a.sample.hint[1]),
      one,
    )
    const played = Object.entries(a.played)
    const cast = new Set((await get('/villagers')).map((x: any) => x.name))
    check(
      `${v}: ${played.map(([, n]) => n).join(', ')} play their talks above A2, named as the cast names them`,
      played.every(([id, name]) => cast.has(name) && atB1.find((s: any) => s.id === id)?.role.startsWith(`${name}, `)),
      played.map(([id]) => atB1.find((s: any) => s.id === id)?.role.split(',')[0]),
    )
    // a turn of a talk above A2 reaches the tutor with its level and packs
    const conv = `rp-1b2c3d4e-0000-4000-8000-00000000${language === 'de' ? 'd' : 'e'}0b1`
    const id = played[0]?.[0] ?? a.sample.id
    await fetch(`${base}/message`, { method: 'POST', headers: auth, body: JSON.stringify({ kind: 'roleplay', conversation_id: conv, text: a.hello, data: { scenario_id: id, turn: 1, heard: a.hello } }) })
    await Bun.sleep(150)
    const note = t.notes().findLast(n => n.params?.meta?.kind === 'roleplay' && n.params.meta.conversation_id === conv)
    const brief = JSON.parse(/```json\n([\s\S]*)\n```/.exec(String(note?.params?.content))?.[1] ?? '{}').scenario
    const want = JSON.parse(readFileSync(join(repo, 'companion/scenarios', language, `${id}.json`), 'utf8'))
    check(
      `${v}: a turn of ${id} reaches the tutor with its level (${want.level}) and packs, played in ${language}`,
      brief?.level === want.level && brief.language === language && JSON.stringify(brief.packs) === JSON.stringify(want.packs) && brief.role === want.role && !brief.child,
      brief,
    )
  } finally {
    writeFileSync(profile, before)
  }
}

/** The curated role-plays of [language] validate with every text in their bases ([bases]) and the title, goals and who per language. */
export function curatedTalksOf(language: string, bases: string[]) {
  const dir = join(repo, 'companion/scenarios', language)
  return readdirSync(dir).filter(f => f.endsWith('.json')).map(f => {
    const raw = JSON.parse(readFileSync(join(dir, f), 'utf8'))
    const texts = [raw.setting, raw.opener_en, ...raw.vocabulary_hints.map((h: any) => h.en)]
    const labels = [raw.title, raw.who, ...raw.goals]
    const missing = [...texts.filter(x => !bases.every(b => x?.[b])), ...labels.filter(x => ![language, ...bases].every(b => x?.[b]))]
    return { f, raw, missing }
  })
}
