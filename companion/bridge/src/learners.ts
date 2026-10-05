// Several learners on one machine (companion/README.md, "Learners"). Each has a profile: an id, a data
// directory, a bridge port, a tmux session, their own tokens and tutor session. The default profile is
// today's setup (data/, port 8790, tmux "lani") and is not in the registry; the others are in
// profiles/profiles.json, kept by the CLIs (src/cli/profile.ts). The bridge itself only reads its
// environment (LANI_PROFILE, LANI_DATA_DIR, LANI_BRIDGE_PORT, LANI_CHILD, LANI_CULTURE),
// which companion/bin/lani-session sets from the registry.
import { spawnSync } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync, readdirSync, renameSync, writeFileSync } from 'node:fs'
import { isAbsolute, join, relative, resolve } from 'node:path'
import { CULTURE_ID, DEFAULT_CULTURE, LANDSCAPES } from './cultures'
import { setting } from './env'

export const DEFAULT_ID = 'default'

/** Today's setup: never in the registry, and nothing about it changes. */
export const DEFAULT_PROFILE = { id: DEFAULT_ID, port: 8790, https_port: 443, tmux: 'lani', remote_control: 'lani-tutor' } as const

/**
 * The real bridge (8790), the QA dev bridge (8791) and its second town (8797, test/dev-town.ts), the voice and speech
 * workers (8795, 8796), the smoke tests (8799).
 */
export const RESERVED_PORTS = new Set([8790, 8791, 8795, 8796, 8797, 8799])
const PORT_RANGE: [number, number] = [8792, 8899]

export const ID = /^[a-z][a-z0-9-]{0,23}$/

export type ProfileEntry = {
  id: string
  /** The learner's name, as the CLI was told; the learner profile (learner-profile.json) may refine it. */
  name: string
  /** ISO 639-1 codes: what they learn, and the language explanations are in. */
  target: string
  base: string
  /** Their village's culture pack (companion/cultures/<id>); primorska when not set. */
  culture?: string
  /**
   * The landscape their village is founded on (`valley`, `hills`, `lake`, `mountains`, `coast`): the app offers it first when
   * the village is founded; not set: the region's usual one.
   */
  landscape?: string
  child: boolean
  port: number
  /** `tailscale serve --https=<this>` publishes the bridge to the tailnet. */
  https_port: number
  tmux: string
  remote_control: string
  /** Relative to the registry's directory, or absolute. */
  data_dir: string
  created: string
  /** Extra environment for the session and its bridge (e.g. LANI_VOICE_DESIGN=off). */
  env?: Record<string, string>
}

export type Registry = { schema: 'lani.profiles/v1'; profiles: ProfileEntry[] }

/** Where the registry and the new learners' data live: $LANI_PROFILES_DIR, else <repo>/profiles. */
export const profilesDir = (projectDir: string, env = process.env) => resolve(env.LANI_PROFILES_DIR || join(projectDir, 'profiles'))

export function readRegistry(dir: string): Registry {
  const p = join(dir, 'profiles.json')
  if (!existsSync(p)) return { schema: 'lani.profiles/v1', profiles: [] }
  const r = JSON.parse(readFileSync(p, 'utf8'))
  return { schema: 'lani.profiles/v1', profiles: Array.isArray(r?.profiles) ? r.profiles : [] }
}

export function writeRegistry(dir: string, r: Registry) {
  mkdirSync(dir, { recursive: true })
  const p = join(dir, 'profiles.json')
  writeFileSync(`${p}.tmp`, JSON.stringify(r, null, 2) + '\n')
  renameSync(`${p}.tmp`, p)
}

export const entryDataDir = (dir: string, e: ProfileEntry) => resolve(dir, e.data_dir)

/**
 * The default profile's data directory, resolved as the bridge resolves it (lani_paths.data_dir,
 * which honours LANI_DATA_DIR). Pure: it creates nothing.
 */
export function defaultDataDir(projectDir: string, env = process.env): string {
  const p = spawnSync('python3', ['-c', 'import sys; sys.path.insert(0, ".claude/hooks"); from lani_paths import data_dir; print(data_dir())'], {
    cwd: projectDir,
    env: { ...env, CLAUDE_PROJECT_DIR: projectDir },
    encoding: 'utf8',
  })
  if (p.status !== 0) throw new Error(`could not resolve the data directory: ${p.stderr}`)
  return p.stdout.trim()
}

/** Whether nothing listens on 127.0.0.1:[port] right now. */
export function portFree(port: number): boolean {
  try {
    const s = Bun.listen({ hostname: '127.0.0.1', port, socket: { data() {} } })
    s.stop(true)
    return true
  } catch {
    return false
  }
}

/** The first port from 8792 up that no profile has, that isn't reserved, and that is free now. */
export function pickPort(r: Registry, free: (port: number) => boolean = portFree): number {
  const taken = new Set(r.profiles.map(p => p.port))
  for (let p = PORT_RANGE[0]; p <= PORT_RANGE[1]; p++) if (!RESERVED_PORTS.has(p) && !taken.has(p) && free(p)) return p
  throw new Error(`no free port in ${PORT_RANGE.join('-')}`)
}

/** 8443, 9443, 10443, …: the tailnet HTTPS port for the next profile (443 is the default's). */
export function pickHttpsPort(r: Registry): number {
  const taken = new Set([443, ...r.profiles.map(p => p.https_port)])
  for (let p = 8443; ; p += 1000) if (!taken.has(p)) return p
}

// --- languages ---------------------------------------------------------------------------------------

export const LANGUAGES: Record<string, string> = {
  sl: 'Slovene', it: 'Italian', en: 'English', de: 'German', hr: 'Croatian', sr: 'Serbian', bs: 'Bosnian',
  fr: 'French', es: 'Spanish', pt: 'Portuguese', nl: 'Dutch', pl: 'Polish', cs: 'Czech', sk: 'Slovak',
  hu: 'Hungarian', uk: 'Ukrainian', ru: 'Russian',
}

/** The app's languages by their names in each other (a profile a German or Italian speaker's setup wrote). */
const ALIASES: Record<string, string> = {
  slovenian: 'sl', slovenščina: 'sl', slovensko: 'sl', sloveno: 'sl', slowenisch: 'sl',
  angleščina: 'en', angleško: 'en', inglese: 'en', englisch: 'en',
  italiano: 'it', italijanščina: 'it', italijansko: 'it', italienisch: 'it',
  deutsch: 'de', nemščina: 'de', nemško: 'de', tedesco: 'de',
}

/** "it", "Italian" or "italiano" → "it"; undefined when unknown. */
export function languageCode(s: string): string | undefined {
  const t = s.trim().toLowerCase()
  if (LANGUAGES[t]) return t
  return Object.keys(LANGUAGES).find(k => LANGUAGES[k].toLowerCase() === t) ?? ALIASES[t]
}

// --- a new learner's data ------------------------------------------------------------------------------

const today = () => new Date().toISOString().slice(0, 10)
const PLACEHOLDER = /\{[^{}]*\}/

/** A template's value with its example rows gone: keys named example_*, and array items with a {placeholder}. */
function withoutExamples(v: unknown): unknown {
  if (Array.isArray(v)) return v.filter(x => !PLACEHOLDER.test(JSON.stringify(x))).map(withoutExamples)
  if (v && typeof v === 'object') return Object.fromEntries(Object.entries(v).filter(([k]) => !k.startsWith('example_')).map(([k, x]) => [k, withoutExamples(x)]))
  return v
}

function fill(v: unknown, values: Record<string, string>): unknown {
  if (typeof v === 'string') return v.replace(/\{([^{}]*)\}/g, (m, k) => values[k] ?? m)
  if (Array.isArray(v)) return v.map(x => fill(x, values))
  if (v && typeof v === 'object') return Object.fromEntries(Object.entries(v).map(([k, x]) => [k, fill(x, values)]))
  return v
}

export type NewLearner = {
  name: string
  target: string
  base: string
  level?: string
  child?: boolean
  culture?: string
  landscape?: string
  /** The learner's grammatical gender: Slovene (and Italian, German) agree with it ("si lačen/lačna?"). */
  gender?: 'male' | 'female'
  /** Minutes a day (default 30, a child's 15). */
  goal?: number
  /** Where the tutor's session result files go (default: next to the data, ../results). */
  results?: string
}

export const GENDERS = ['male', 'female'] as const

/**
 * The six learner databases for a new learner in [dataDir], from data-examples/ with safe defaults. The
 * tutor's /lani-setup refines them later. Refuses to overwrite a learner who already has data.
 */
export function initLearnerData(projectDir: string, dataDir: string, l: NewLearner) {
  if (existsSync(join(dataDir, 'learner-profile.json'))) throw new Error(`${dataDir} already holds a learner profile`)
  mkdirSync(join(dataDir, 'app'), { recursive: true })
  // The tutor's session result files: next to the data (the repository's results/ are the default learner's), or in
  // the learner's data repository (lani-setup).
  mkdirSync(l.results ?? resolve(dataDir, '..', 'results'), { recursive: true })
  const templates = join(projectDir, 'data-examples')
  const date = today()
  const target = LANGUAGES[l.target] ?? l.target
  const base = LANGUAGES[l.base] ?? l.base
  const level = l.level ?? 'A1'
  const values = { 'YYYY-MM-DD': date, target_language: target, name: l.name }
  for (const f of readdirSync(templates).filter(f => f.endsWith('-template.json') && f !== 'learner-profile-template.json')) {
    const t = JSON.parse(readFileSync(join(templates, f), 'utf8'))
    writeFileSync(join(dataDir, f.replace('-template', '')), JSON.stringify(fill(withoutExamples(t), values), null, 2) + '\n')
  }
  const t = JSON.parse(readFileSync(join(templates, 'learner-profile-template.json'), 'utf8'))
  const profile = {
    ...(fill(withoutExamples(t), values) as Record<string, unknown>),
    learner: {
      name: l.name,
      ...(l.gender ? { gender: l.gender } : {}),
      native_language: base,
      base_language: base,
      base_language_code: l.base,
      other_languages: [],
      target_language: target,
      target_language_code: l.target,
      current_level: level,
      target_level: ({ A1: 'A2', A2: 'B1', B1: 'B2', B2: 'C1', C1: 'C2' } as Record<string, string>)[level] ?? 'C2',
      daily_goal_minutes: l.goal ?? (l.child ? 15 : 30),
      learning_style: 'balanced',
      motivation: 'family',
    },
    // language profiles (docs/DB_SCRIPTS.md, "Languages"): the town's language is home; others come with visits
    home_language: l.target,
    languages: [],
    profile_created: date,
    last_updated: date,
    achievements: [{ id: 'profile_created', name: 'Getting Started', earned_date: date, description: 'Created your learning profile' }],
  }
  writeFileSync(join(dataDir, 'learner-profile.json'), JSON.stringify(profile, null, 2) + '\n')
}

/** A new registry entry for [id], with its ports and names picked; the caller writes the registry. */
export function newEntry(dir: string, r: Registry, id: string, l: NewLearner, o: { port?: number; httpsPort?: number; dataDir?: string } = {}): ProfileEntry {
  if (!ID.test(id) || id === DEFAULT_ID) throw new Error(`profile id must be lowercase letters, digits and dashes (not "${DEFAULT_ID}"): ${id}`)
  if (r.profiles.some(p => p.id === id)) throw new Error(`profile ${id} exists already`)
  const port = o.port ?? pickPort(r)
  if (RESERVED_PORTS.has(port) || r.profiles.some(p => p.port === port)) throw new Error(`port ${port} is reserved or taken`)
  const httpsPort = o.httpsPort ?? pickHttpsPort(r)
  if (httpsPort === 443 || r.profiles.some(p => p.https_port === httpsPort)) throw new Error(`HTTPS port ${httpsPort} is taken`)
  const data = resolve(o.dataDir ?? join(dir, id, 'data'))
  const rel = relative(dir, data)
  return {
    id,
    name: l.name,
    target: l.target,
    base: l.base,
    culture: l.culture ?? DEFAULT_CULTURE,
    ...(l.landscape ? { landscape: l.landscape } : {}),
    child: !!l.child,
    port,
    https_port: httpsPort,
    tmux: `lani-${id}`,
    remote_control: `lani-tutor-${id}`,
    data_dir: rel.startsWith('..') || isAbsolute(rel) ? data : rel,
    created: today(),
    // Voices of one's own take ElevenLabs voice slots the default profile's village uses: off until wanted.
    env: { LANI_VOICE_DESIGN: 'off' },
  }
}

// --- what the bridge knows about its learner ---------------------------------------------------------

/**
 * [culture]: the culture pack the learner's village asks for (the bridge plays the default if it can't be used);
 * [landscape]: the one a new village is offered first (`lani-profile add --landscape`), when set.
 */
export type Profile = { id: string; isDefault: boolean; child: boolean; culture: string; landscape?: string }

/** The bridge's profile, from the environment lani-session sets. */
export function profileFromEnv(env = process.env): Profile {
  const id = env.LANI_PROFILE?.trim() || DEFAULT_ID
  if (id !== DEFAULT_ID && !ID.test(id)) throw new Error(`LANI_PROFILE must be lowercase letters, digits and dashes: ${id}`)
  // the default learner's village and land may be set in lani.env (lani-setup)
  const culture = setting('LANI_CULTURE', env)?.trim() || DEFAULT_CULTURE
  if (!CULTURE_ID.test(culture)) throw new Error(`LANI_CULTURE must be a culture pack id (companion/cultures): ${culture}`)
  // a landscape the app doesn't know is left out: the app offers the region's usual one
  const landscape = setting('LANI_LANDSCAPE', env)?.trim()
  const known = landscape && (LANDSCAPES as readonly string[]).includes(landscape) ? { landscape } : {}
  return { id, isDefault: id === DEFAULT_ID, child: ['1', 'true', 'yes'].includes((env.LANI_CHILD ?? '').toLowerCase()), culture, ...known }
}

/** [target] and [base] as the profile names them ("Italian"); [targetCode] and [baseCode] as codes ("it"), when known. */
export type LearnerFacts = { name: string; target: string; base: string; level?: string; targetCode?: string; baseCode?: string }

/** Name, languages and level from learner-profile.json (the tutor keeps it); blanks when unreadable. */
export function learnerFacts(dataDir: string): LearnerFacts {
  try {
    const l = JSON.parse(readFileSync(join(dataDir, 'learner-profile.json'), 'utf8'))?.learner ?? {}
    const str = (v: unknown) => (typeof v === 'string' && !PLACEHOLDER.test(v) ? v.trim() : '')
    const target = str(l.target_language)
    const base = str(l.base_language) || str(l.native_language)
    const targetCode = languageCode(str(l.target_language_code)) ?? (target ? languageCode(target) : undefined)
    const baseCode = languageCode(str(l.base_language_code)) ?? (str(l.base_language) ? languageCode(str(l.base_language)) : undefined)
    return {
      name: str(l.name),
      target,
      base,
      ...(str(l.current_level) ? { level: str(l.current_level) } : {}),
      ...(targetCode ? { targetCode } : {}),
      ...(baseCode ? { baseCode } : {}),
    }
  } catch {
    return { name: '', target: '', base: '' }
  }
}

/**
 * The two languages a bridge's texts are in: [target], what the village speaks (its culture pack's language), and
 * [base], what the learner reads explanations in (the profile's base language; English when it doesn't say, as for Jan).
 */
export function learnerLangs(dataDir: string, villageLanguage: string): { target: string; base: string } {
  const f = learnerFacts(dataDir)
  const base = f.baseCode && f.baseCode !== villageLanguage ? f.baseCode : villageLanguage === 'en' ? 'sl' : 'en'
  return { target: villageLanguage, base }
}

export const CHILD_INSTRUCTIONS = `
Child: the learner is a child. Keep everything age-appropriate: warm, patient and encouraging, simple
words, short sentences, one small step at a time, praise for effort. No frightening, violent, romantic
or grown-up topics in examples, role-plays or villager talk. Never ask for personal data (full name,
address, school, phone number, photos, where they are), and don't keep any they give you. If they tell
you something worrying (they are sad or hurt, or someone bothers them), answer kindly and tell them to
talk to a parent. Send no links. Tool approvals are not relayed to their app: a parent answers them in
the terminal or through Remote Control.
`.trim()

/**
 * The channel instructions for this bridge's learner. The default profile's paragraphs stay exactly as
 * they are. For another learner, the paragraphs (written for Jan and Slovene) name that learner, and a
 * paragraph first says whose tutor this is and where their data is; a child's profile adds the child
 * paragraph.
 */
/** Claude Code keeps only this much of a server's instructions; the rest is the channel_guide tool. */
export const INSTRUCTIONS_LIMIT = 2048

/**
 * The instructions Claude Code actually shows the session: who the learner is, the two rules nothing
 * works without (reply with the tool, read the guide) and, for a child, the child's rules. Always
 * within INSTRUCTIONS_LIMIT; the full text (learnerInstructions) comes from the channel_guide tool.
 */
/** Jan's base language when it isn't English (the default profile's paragraphs are written for English), else undefined. */
const otherBase = (p: Profile, facts: LearnerFacts) => (p.isDefault && facts.base && facts.baseCode && facts.baseCode !== 'en' ? facts.base : undefined)

/**
 * The default learner as lani-setup sets them up, their data a git repository of its own: the bridge passes
 * [resultsDir] then. The instructions name them from their profile (the paragraphs are written for Jan) and say where
 * their data and session results are. Without it, the default profile's instructions stay exactly as they are.
 */
export type DataPlace = { resultsDir?: string }

/** The learner's name: the default's is Jan, or (lani-setup) their profile's first name; another's, else the profile id. */
const nameOf = (p: Profile, facts: LearnerFacts, o: DataPlace) =>
  p.isDefault ? (o.resultsDir && facts.name.trim().split(/\s+/)[0]) || 'Jan' : facts.name || p.id

/** The default learner's data and results outside the checkout, in a sentence; empty otherwise. */
const placeOf = (p: Profile, name: string, dataDir: string, o: DataPlace) =>
  p.isDefault && o.resultsDir
    ? `\n${name}'s data is in ${dataDir} (a git repository of its own; read-db.py and update-db.py find it). Write session result files to ${o.resultsDir}/, not to the repository's data/ or results/.`
    : ''

export function channelRules(p: Profile, facts: LearnerFacts, dataDir: string, o: DataPlace = {}): string {
  const name = nameOf(p, facts, o)
  const pair = facts.target ? ` ${name} learns ${facts.target}${facts.level ? ` (${facts.level})` : ''}${facts.base ? ` from ${facts.base}` : ''}.` : ''
  const base = otherBase(p, facts)
  const who = p.isDefault
    ? (base ? `\n${name} learns ${facts.target || 'Slovene'} from ${base}: explain, translate and give feedback in ${base}.` : '') + placeOf(p, name, dataDir, o)
    : `\nLearner profile "${p.id}": this session is ${name}'s tutor.${pair} ${name}'s data is in ${dataDir}; the repository's data/ and results/ belong to another learner: never read or change them.`
  const out = [
    `You are ${name}'s Lani language tutor. This channel connects you to the Lani Android app.${who}`,
    `Before anything else:
1. The app never sees terminal output. Answer every <channel source="lani"> message with the reply
   tool, passing the conversation_id from its tag. Keep replies phone-sized; Markdown is rendered.
2. Call the channel_guide tool once before your first reply, and again after the conversation is
   compacted, and follow it. It holds the rest of these instructions: each kind of inbound message
   (chat, answers, session ends, reviews, role-play, villagers, family), the village, packs, the
   grammar book (keep the rules you explain there), scenes, voices, and when to use each tool. An item
   ${name} finds wrong or confusing: the lani-fix skill.`,
  ]
  if (p.child) out.push(CHILD_INSTRUCTIONS)
  return out.join('\n\n')
}

export function learnerInstructions(
  paragraphs: string[],
  p: Profile,
  facts: LearnerFacts,
  dataDir: string,
  village?: { id: string; language: string; region?: string; village: string; style: string },
  o: DataPlace = {},
): string {
  const out: string[] = []
  let body = paragraphs
  const janBase = otherBase(p, facts)
  // The default learner is Jan in the paragraphs; another name (a learner lani-setup set up) replaces it.
  const first = nameOf(p, facts, o)
  if (p.isDefault && first !== 'Jan') body = paragraphs.map(t => t.replace(/\bJan\b/g, first))
  if (janBase) {
    out.push(
      `${first}'s base language is ${janBase} now (learner-profile.json): wherever the paragraphs below say English for meanings, explanations, translations and feedback, use ${janBase}. The app shows its labels as "Slovene · ${janBase}"; the curated Slovene content has English meanings, which you translate when ${first} asks.`,
    )
  }
  const place = placeOf(p, first, dataDir, o).trim()
  if (!p.isDefault) {
    const name = facts.name || p.id
    body = paragraphs.map(t => t.replace(/\bJan\b/g, name))
    const pair = facts.target ? ` ${name} learns ${facts.target}${facts.level ? ` (${facts.level})` : ''}${facts.base ? ` from ${facts.base}` : ''}.` : ''
    const root = resolve(dataDir, '..')
    const base = facts.base || 'their base language'
    // Their village: a culture pack in another language has its own people, words and feasts (plan 2, step 3b).
    const own = village && village.language !== 'sl'
    const where = own
      ? `${name}'s village, ${village.village}, is the ${village.region ?? village.id} culture pack (companion/cultures/${village.id}): its villagers, word packs, feasts and names are in ${LANGUAGES[village.language] ?? village.language}, with translations; newcomers you give lines to are in ${village.style}. The app's other labels come from its ${village.language} string table, and voices from the phone's text-to-speech until the pack has voices of its own.`
      : `${name}'s village is in the Slovene culture pack (${village?.id ?? 'primorska'}): its villagers, word packs and curated modules are Slovene, with English meanings, which you explain in ${base}. The app shows its labels as "Slovene · ${base}" from its string tables.`
    // A target no culture pack speaks yet (lani-profile refuses one, but a profile can be edited): the village stays
    // in its pack's language, and the tutor teaches the target in chat and exercises.
    const village_ = village ? (LANGUAGES[village.language] ?? village.language) : 'Slovene'
    const mismatch = facts.targetCode && facts.targetCode !== (village?.language ?? 'sl')
      ? ` Note: no culture pack in ${facts.target} is played for ${name} yet, so the village speaks ${village_}. Teach ${facts.target} in chat, exercises and packs you publish; the village's own content stays ${village_} until the profile's culture is a ${facts.target} pack (companion/bin/lani-profile).`
      : ''
    out.push(
      [
        `Learner profile "${p.id}": this session is ${name}'s tutor.${pair}`,
        `The paragraphs below were written for a Slovene learner: apply them to ${name} and ${facts.target || 'their target language'},`,
        `and explain in ${base}. ${where}${mismatch}`,
        `${name}'s learner data is in ${dataDir}. LANI_DATA_DIR points there, so read-db.py and update-db.py use it:`,
        `always go through them. The repository's data/ and results/ directories belong to another learner: never`,
        `read or change them. Write session result files to ${join(root, 'results')}/.`,
      ].join('\n'),
    )
  }
  if (p.child) out.push(CHILD_INSTRUCTIONS)
  return [...out, ...body, ...(place ? [place] : [])].join('\n\n')
}
