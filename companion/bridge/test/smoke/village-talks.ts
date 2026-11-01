// Smoke checks shared by the German and English villages (kaernten.ts, lakeland.ts): their role-plays
// (companion/scenarios/<language>, docs/plans/05-built/scenarios-de-en.md) as a bridge of that village serves them to a
// grown-up learner, each text in the learner's pair, the turn's brief for the tutor, and a tutor's own role-play.
import type { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { readdirSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
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
}

export async function villageTalks(t: VillageTalks) {
  const { village: v, language, base_url: base, auth } = t
  const get = async (path: string) => (await fetch(`${base}${path}`, { headers: auth })).json() as Promise<any>
  const files = readdirSync(join(repo, 'companion/scenarios', language)).filter(f => f.endsWith('.json'))
  const scenarios = await get('/scenarios')
  check(
    `${v}: GET /scenarios serves the ${files.length} role-plays in ${language} for a grown-up, and nothing else`,
    files.length === 8 && scenarios.length === 8 && files.every(f => scenarios.some((s: any) => `${s.id}.json` === f && s.language === language && s.audience === 'adult' && s.source === 'curated' && /machine-written/.test(s.review))),
    scenarios.map((s: any) => [s.id, s.language]),
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
