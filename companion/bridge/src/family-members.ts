// A family on a computer, from the app (docs/plans/05-built/leftovers-app.md, "A family on a computer"). Each member is a learner
// with a bridge of their own, and until now that took `lani-profile add`, `tailscale serve` and `lani-pair` on the computer. Here
// a parent's bridge does those steps for a parent's device: only those, with nothing from the request but a name, two languages
// and whether it's for a child, and nothing a shell could be told through them:
// - the profile: the same code as `lani-profile add` (learners.ts addLearner);
// - its bridge: `companion/bin/lani-bridge start --profile <id>`, the id made here from the name (ID, so nothing a shell reads);
// - the tailnet: `tailscale serve --bg --https=<port> http://127.0.0.1:<port>`, as lani-profile prints it, never Funnel;
// - a pairing code for the new learner, as `lani-pair --profile <id>` makes it: one use, 10 minutes, a file in their own app/.
// What it can't do it says, with the command (`left`): the computer's owner may have to allow `tailscale serve` once, and a node
// behind a proxy of its own has no address this code could know (LANI_FAMILY_URL says it).
//
// Who may: a parent's device, never a child's (features/family-members.ts), and only on a member they may look after: themselves,
// a child of this computer, someone they added.
import { spawn, spawnSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { culturesIn, DEFAULT_CULTURE } from './cultures'
import { addLearner, entryDataDir, ID, profilesDir, readRegistry, tailnetHost, type ProfileEntry } from './learners'
import { createPairingCode, Devices, loadBridgeKey, pairUri, withLock, writeAtomic, type DeviceView } from './pairing'

/** At most this many learners on a computer from the app (LANI_FAMILY_MAX): each is a bridge that runs. `lani-profile add` has no limit. */
export const MAX_MEMBERS = 8
/** How long the new bridge gets to answer before the answer says it didn't (LANI_FAMILY_START_MS). */
const startMs = (env: Record<string, string | undefined>) => Number(env.LANI_FAMILY_START_MS ?? 25_000)

/** What's left to do on the computer, with the command: [what] publish, start, url or tutor. */
export type Left = { what: 'publish' | 'start' | 'url' | 'tutor'; command: string }

export type DeviceRule = { daily_cap_usd: number; by: string; at: string }
type Rules = { devices: Record<string, DeviceRule> }

export type MemberView = {
  profile: string
  name: string
  child: boolean
  self: boolean
  up: boolean
  devices: (DeviceView & { daily_cap_usd?: number })[]
}

/** A pairing code for a member: what its QR code says, and until when. */
export type MemberCode = { uri: string; url?: string; expires_at: string }

/** "Žan Novak" → "zan-novak", unique among [taken]: a profile's id, from the name alone. */
export function memberId(name: string, taken: (id: string) => boolean): string {
  const slug = name.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^[^a-z]+/, '').replace(/-+$/, '').slice(0, 20).replace(/-+$/, '')
  const first = ID.test(slug) ? slug : 'member'
  if (!taken(first)) return first
  for (let n = 2; ; n++) if (!taken(`${first}-${n}`)) return `${first}-${n}`
}

/** The culture pack of a new learner of [target]: the default one when it speaks that, else the first complete one. */
export function cultureOfTarget(culturesDir: string, target: string): string | undefined {
  const { complete } = culturesIn(culturesDir, target)
  return complete.includes(DEFAULT_CULTURE) ? DEFAULT_CULTURE : complete[0]
}

/** What a member's address is: LANI_FAMILY_URL with its {port} and {https_port}, else the tailnet's name; undefined when neither says. */
export function memberUrl(e: Pick<ProfileEntry, 'port' | 'https_port'>, env: Record<string, string | undefined> = process.env): string | undefined {
  const fixed = env.LANI_FAMILY_URL?.trim()
  if (fixed) return fixed.replaceAll('{port}', String(e.port)).replaceAll('{https_port}', String(e.https_port)).replace(/\/+$/, '')
  const host = tailnetHost(env)
  return host ? `https://${host}${e.https_port === 443 ? '' : `:${e.https_port}`}` : undefined
}

const serveCommand = (e: ProfileEntry) => `tailscale serve --bg --https=${e.https_port} http://127.0.0.1:${e.port}`
const binOf = (projectDir: string, env: Record<string, string | undefined>) => env.LANI_FAMILY_BIN?.trim() || join(projectDir, 'companion/bin')

/**
 * What decides whose bridge it is, by either name (lani-env.sh turns a FLUENT_* name back into its LANI_* one): a parent's must not
 * reach the new learner's, whose lani-bridge sets their own. Their data's remote is here too, or their data would be pushed to the
 * parent's. The learner's settings of bin/lani-env.sh, the tokens, and what says who started this process.
 */
const LEARNER_ENV = /^(?:(?:LANI|FLUENT)_(?:PROFILE|DATA_DIR|RESULTS_DIR|VOICE_CACHE|CULTURE|LANDSCAPE|BRIDGE_PORT|BRIDGE_SOCKET|BRIDGE_TOKEN|FAMILY_TOKEN|BRIDGE_SUPERVISOR|PUBLIC_URL|TOWN_URL|DATA_AUTOCOMMIT|DATA_REMOTE|DATA_PUSH|CHILD)|INVOCATION_ID)$/

export function cleanEnv(env: Record<string, string | undefined>): Record<string, string> {
  return Object.fromEntries(Object.entries(env).filter((e): e is [string, string] => e[1] !== undefined && !LEARNER_ENV.test(e[0])))
}

/** Whether a bridge answers on [port] within [ms]. */
export async function bridgeAnswers(port: number, ms: number): Promise<boolean> {
  const until = Date.now() + ms
  for (;;) {
    try {
      if ((await fetch(`http://127.0.0.1:${port}/health`, { signal: AbortSignal.timeout(1_500) })).ok) return true
    } catch {}
    if (Date.now() >= until) return false
    await Bun.sleep(300)
  }
}

/** The new learner's bridge started, its own process and session; it goes on when this one stops. False: it didn't answer in time. */
async function startBridge(projectDir: string, e: ProfileEntry, env: Record<string, string | undefined>): Promise<boolean> {
  if (await bridgeAnswers(e.port, 0)) return true
  try {
    const p = spawn(join(binOf(projectDir, env), 'lani-bridge'), ['start', '--profile', e.id], { cwd: projectDir, env: cleanEnv(env), detached: true, stdio: 'ignore' })
    p.on('error', () => {})
    p.unref()
  } catch {
    return false
  }
  return bridgeAnswers(e.port, startMs(env))
}

/** The tailnet's way to the member, once; false when it can't be done from here (the owner allows it, or runs it). */
function publish(e: ProfileEntry, env: Record<string, string | undefined>): boolean {
  if (env.LANI_FAMILY_URL?.trim()) return true // the owner's proxy: nothing to publish from here
  const bin = env.LANI_FAMILY_TAILSCALE?.trim() || 'tailscale'
  const p = spawnSync(bin, ['serve', '--bg', `--https=${e.https_port}`, `http://127.0.0.1:${e.port}`], { encoding: 'utf8', timeout: 10_000 })
  return p.status === 0
}

/** Whether something on this computer starts a learner's tutor by itself (companion/bin/lani-watchdog's timer). */
function watchdogRuns(env: Record<string, string | undefined>): boolean {
  if (env.LANI_FAMILY_WATCHDOG) return env.LANI_FAMILY_WATCHDOG === 'on'
  return spawnSync('systemctl', ['--user', 'is-active', '--quiet', 'lani-watchdog.timer'], { timeout: 3_000 }).status === 0
}

/** Why no new member can be added from the app now; undefined when one can. */
export function addRefusal(projectDir: string, env: Record<string, string | undefined> = process.env): 'off' | 'full' | undefined {
  if (env.LANI_FAMILY_ADD?.trim().toLowerCase() === 'off') return 'off'
  const max = Number(env.LANI_FAMILY_MAX ?? MAX_MEMBERS)
  return readRegistry(profilesDir(projectDir, env as NodeJS.ProcessEnv)).profiles.length >= max ? 'full' : undefined
}

/** A one-time code for [e]'s bridge, as lani-pair makes one, and the QR code's text. */
export function memberCode(dir: string, e: ProfileEntry, env: Record<string, string | undefined> = process.env): MemberCode | undefined {
  const url = memberUrl(e, env)
  if (!url) return undefined
  const appDir = join(entryDataDir(dir, e), 'app')
  const key = loadBridgeKey(appDir)
  const { code, expiresAt } = createPairingCode(appDir)
  return { uri: pairUri({ url, code, profile: e.id, fingerprint: key.fingerprint }), url, expires_at: new Date(expiresAt).toISOString() }
}

export type NewMember = { name: string; target: string; base: string; child: boolean }

/** A new member of the family on this computer, from a parent's [addedBy]: profile, bridge, address, code; what's left says itself. */
export async function addMember(
  o: { projectDir: string; culturesDir: string; addedBy: string },
  m: NewMember,
  env: Record<string, string | undefined> = process.env,
): Promise<{ entry: ProfileEntry; code?: MemberCode; left: Left[]; up: boolean } | { problem: 'no_culture' }> {
  const dir = profilesDir(o.projectDir, env as NodeJS.ProcessEnv)
  const culture = cultureOfTarget(o.culturesDir, m.target)
  if (!culture) return { problem: 'no_culture' }
  const entry = addLearner(dir, o.projectDir, taken => memberId(m.name, taken), { ...m, culture, goal: 15 }, { addedBy: o.addedBy })
  return { entry, ...(await bring(o.projectDir, dir, entry, env)) }
}

/** [e]'s bridge up, its way out and a code: what was asked for a new member, and again for "another code". */
export async function bring(projectDir: string, dir: string, e: ProfileEntry, env: Record<string, string | undefined> = process.env) {
  const up = await startBridge(projectDir, e, env)
  const left: Left[] = []
  if (!up) left.push({ what: 'start', command: `companion/bin/lani-bridge start --profile ${e.id}` })
  const url = memberUrl(e, env)
  if (url && !publish(e, env)) left.push({ what: 'publish', command: serveCommand(e) })
  if (!url) left.push({ what: 'url', command: `companion/bin/lani-pair --profile ${e.id} --url https://…` })
  if (!watchdogRuns(env)) left.push({ what: 'tutor', command: `companion/bin/lani-session --profile ${e.id}` })
  return { code: memberCode(dir, e, env), left, up }
}

// --- who a parent's device looks after --------------------------------------------------------------------------------

/** Whether [self]'s devices look after [e]: a child of this computer, someone they added, themselves. Another adult's devices are that adult's. */
export const looksAfter = (self: string, e: ProfileEntry) => e.id === self || e.child === true || e.added_by === self

const rulesFile = (appDir: string) => join(appDir, 'family-rules.json')

export function readRules(appDir: string): Rules {
  try {
    const r = JSON.parse(readFileSync(rulesFile(appDir), 'utf8'))
    return { devices: r?.devices && typeof r.devices === 'object' ? r.devices : {} }
  } catch {
    return { devices: {} }
  }
}

/** A device's daily budget set from a parent's device; null takes it back. Kept with the learner the device belongs to. */
export function setRule(appDir: string, device: string, cap: number | null, by: string, now = new Date()) {
  withLock(`${rulesFile(appDir)}.lock`, () => {
    const r = readRules(appDir)
    if (cap === null) delete r.devices[device]
    else r.devices[device] = { daily_cap_usd: cap, by, at: now.toISOString() }
    writeAtomic(rulesFile(appDir), JSON.stringify(r, null, 2) + '\n')
  })
}

/** The members [self] looks after, themselves first: who they are, whether their bridge runs, their devices and each device's budget. */
export async function membersOf(o: { projectDir: string; self: string; selfName: string; selfAppDir: string; child: boolean }, env: Record<string, string | undefined> = process.env): Promise<MemberView[]> {
  const dir = profilesDir(o.projectDir, env as NodeJS.ProcessEnv)
  const view = (appDir: string) => {
    const rules = readRules(appDir).devices
    return new Devices(appDir).list().map(d => ({ ...d, ...(rules[d.id] ? { daily_cap_usd: rules[d.id].daily_cap_usd } : {}) }))
  }
  const self: MemberView = { profile: o.self, name: o.selfName, child: o.child, self: true, up: true, devices: view(o.selfAppDir) }
  const others = readRegistry(dir).profiles.filter(e => e.id !== o.self && looksAfter(o.self, e) && existsSync(entryDataDir(dir, e)))
  const rows = await Promise.all(
    others.map(async (e): Promise<MemberView> => ({
      profile: e.id, name: e.name, child: !!e.child, self: false, up: await bridgeAnswers(e.port, 0), devices: view(join(entryDataDir(dir, e), 'app')),
    })),
  )
  return [self, ...rows]
}
