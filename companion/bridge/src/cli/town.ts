#!/usr/bin/env bun
// companion/bin/lani-town: the towns a learner's town knows (companion/README.md, "Towns").
//
//   lani-town list [--profile <id>]                     this town and the towns it is linked with
//   lani-town invite [--profile <id>] [--url <URL>] [--no-qr]
//                                                         a one-time invitation (30 minutes), as a QR code
//   lani-town accept <invitation> [--profile <id>] [--url <URL>]
//                                                         accept another town's invitation (lani://town?…)
//   lani-town link <profile> <profile> [--no-guardian]  link two towns of this node directly (a parent's shortcut); an
//                                                         adult's and a child's: the adult's town is the child's guardian
//   lani-town unlink <town> [--profile <id>]            let go of a town (a profile, a town id or a name), both ways
//   lani-town name [<name> | --clear] [--profile <id>]  the village's name other towns see
//   lani-town share <town> village|summary|details [--profile <id>]
//                                                         what this town shares of its learning with that one
//   lani-town guardian <town> --profile <child> [--clear]
//                                                         that town is the child's guardian's: it sees the child's progress
//
// A child's town (lani-profile add --child) is linked only here, by a parent on the node: its app can't invite
// or accept. Its guardian's town sees its progress in detail, and its app can't lower that; `share … --profile <child>`
// can (companion/README.md, "Progress between towns"). --url is where this town's bridge answers other towns (default: http://127.0.0.1:<its port>, or the
// profile's LANI_TOWN_URL). With a relay (LANI_RELAY_URL, docs/plans/05-built/relay.md) the invitation carries the
// town's relay address too, and `list` shows it.
import '../env' // first: LANI_* from the FLUENT_* names of a node set up before the rename
import { existsSync } from 'node:fs'
import { join } from 'node:path'
import { parseArgs } from 'node:util'
import { resolveCulture } from '../cultures'
import { groupFingerprint, loadTownKey, type BridgeKey } from '../pairing'
import { LEVEL_TEXT } from '../progress'
import { SHARE_LEVELS, Towns, acceptInvite, createInvite, inviteUri, isShareLevel, parseInvite, selfDisplay, selfRelay, selfUrl, shareOf, townRequest, townUrl, type ShareLevel, type TownDisplay, type TownLink } from '../towns'
import { allProfiles, appDir, bridgeUp, die, projectDir, resolveProfile, table, type Resolved } from './common'

const { values, positionals } = parseArgs({
  args: process.argv.slice(2),
  allowPositionals: true,
  options: {
    profile: { type: 'string', short: 'p' },
    url: { type: 'string' },
    'no-qr': { type: 'boolean', default: false },
    clear: { type: 'boolean', default: false },
    'no-guardian': { type: 'boolean', default: false },
    help: { type: 'boolean', short: 'h', default: false },
  },
})

const USAGE = `usage: lani-town list [--profile <id>]
       lani-town invite [--profile <id>] [--url <this town's URL>] [--no-qr]
       lani-town accept <invitation> [--profile <id>] [--url <this town's URL>]
       lani-town link <profile> <profile> [--no-guardian]
       lani-town unlink <town> [--profile <id>]
       lani-town name [<name> | --clear] [--profile <id>]
       lani-town share <town> village|summary|details [--profile <id>]
       lani-town guardian <town> --profile <child> [--clear]`

const [cmd, ...args] = positionals
if (values.help || !cmd) {
  console.log(USAGE)
  process.exit(values.help ? 0 : 1)
}

const culturesDir = process.env.LANI_CULTURES_DIR ?? join(projectDir, 'companion/cultures')

/** A profile's town: its key, its links, where it answers (and on its relay), how it shows itself. */
type Town = { p: Resolved; key: BridgeKey; towns: Towns; url: string; relay?: string; display: () => TownDisplay }

/** [p]'s town; [url] (--url) overrides where it answers other towns. */
function townOf(p: Resolved, url?: string): Town {
  const key = loadTownKey(appDir(p))
  const towns = new Towns(appDir(p))
  const env = p.isDefault ? process.env : { ...(p.entry?.env ?? {}) }
  const own = url === undefined ? selfUrl(p.port, env, key.fingerprint) : townUrl(url) ?? die(`--url: an http(s) URL, e.g. http://127.0.0.1:${p.port}`)
  const relay = selfRelay(key.fingerprint, env)
  const c = resolveCulture(culturesDir, p.culture)
  return { p, key, towns, url: own, ...(relay ? { relay } : {}), display: () => selfDisplay({ dataDir: p.dataDir, profile: { id: p.id, isDefault: p.isDefault }, culture: c.id, manifest: c.manifest, towns }) }
}

/** The profiles of this node by their town id, for those whose bridge has a key already (none is made here). */
function localTowns(): Map<string, Resolved> {
  const out = new Map<string, Resolved>()
  for (const p of allProfiles()) if (['town-key.pem', 'bridge-key.pem'].some(f => existsSync(join(appDir(p), f)))) out.set(loadTownKey(appDir(p)).fingerprint, p)
  return out
}

const whose = (t: Town) => `${t.display().learner}'s town${t.p.isDefault ? '' : ` (profile ${t.p.id})`}`
const when = (iso?: string) => (iso ? iso.slice(0, 16).replace('T', ' ') : '')
const flag = (l: string) => ({ sl: '🇸🇮', it: '🇮🇹', de: '🇩🇪', en: '🇬🇧', hr: '🇭🇷', fr: '🇫🇷', es: '🇪🇸' })[l] ?? l

function list() {
  const t = townOf(resolveProfile(values.profile))
  const me = t.display()
  const local = localTowns()
  console.log(`${me.name} · ${me.learner} ${flag(me.language)} (${me.culture}${t.p.child ? ', a child' : ''})`)
  console.log(`town id ${groupFingerprint(t.key.fingerprint)}, answers on ${t.url}${t.relay && t.relay !== t.url ? ` and on the relay, ${t.relay}` : ''}\n`)
  const links = t.towns.links()
  if (!links.length) {
    console.log(`No linked towns yet. Invite one: companion/bin/lani-town invite${t.p.isDefault ? '' : ` --profile ${t.p.id}`}`)
    return
  }
  console.log(table([
    ['TOWN', 'LEARNER', 'LANG', 'ID', 'ON THIS NODE', 'URL', 'LINKED', 'HOW', 'SHARES', 'THEY SHARE'],
    ...links.map(l => [l.display?.name ?? '?', l.display?.learner ?? '', l.display?.language ?? '', l.id.slice(0, 8), local.get(l.id)?.id ?? '', l.relay ? `${l.url} (or the relay)` : l.url, when(l.linked_at), `${l.via} (${l.by})`, mineTo(t, l), theirsOf(t, l, local)]),
  ]))
  console.log(`\nSHARES: what ${whose(t)} shares of its learning with that town; THEY SHARE: what that town shares with it (another node's: as it said when asked last).
village: ${LEVEL_TEXT.village}. summary: ${LEVEL_TEXT.summary}. ${LEVEL_TEXT.details}.`)
}

/** What town [t] shares with link [l]: "summary", "details (guardian)", "village (default)". */
function mineTo(t: Town, l: TownLink): string {
  const s = shareOf(l, t.p.child)
  return `${s.level}${s.guardian ? ` (guardian${s.floor !== 'details' ? `, at least ${s.floor}` : ''})` : s.set ? '' : ' (default)'}`
}

/** What link [l]'s town shares with [t]: read from its towns.json on this node, else as it said when asked last. */
function theirsOf(t: Town, l: TownLink, local: Map<string, Resolved>): string {
  const p = local.get(l.id)
  if (p) {
    const back = new Towns(appDir(p)).get(t.key.fingerprint)
    if (!back) return '(not linked back)'
    const s = shareOf(back, p.child)
    return `${s.level}${s.guardian ? ' (you are the guardian)' : s.set ? '' : ' (default)'}`
  }
  return l.theirs && isShareLevel(l.theirs.share) ? `${l.theirs.share}${l.theirs.guardian ? ' (you are the guardian)' : ''}, ${l.theirs.at.slice(0, 10)}` : '? (not asked yet)'
}

async function invite() {
  const t = townOf(resolveProfile(values.profile), values.url)
  const { code, expiresAt } = createInvite(appDir(t.p), { by: 'cli' })
  const link = inviteUri({ url: t.url, code, fingerprint: t.key.fingerprint, relay: t.relay })
  console.log(`An invitation to ${whose(t)}, ${t.display().name}.\n`)
  if (!values['no-qr']) {
    const qrcode = (await import('qrcode-terminal').catch(() => null))?.default
    if (qrcode) {
      qrcode.setErrorLevel('M')
      qrcode.generate(link, { small: true }, qr => console.log(qr))
    } else console.log('(No QR code: run `bun install` in companion/bridge once. The text below works too.)')
  }
  const at = new Date(expiresAt)
  console.log(`${link}

The other town accepts it in its app ("Prijatelji · Friends" → "Sprejmi · Accept"), or on its node:
  companion/bin/lani-town accept '${link}' [--profile <id>]
It works once, until ${String(at.getHours()).padStart(2, '0')}:${String(at.getMinutes()).padStart(2, '0')} (30 minutes). Town id ${groupFingerprint(t.key.fingerprint)}.`)
  if (!(await bridgeUp(t.p.port))) console.log(`\n⚠️  No bridge answers on 127.0.0.1:${t.p.port}: the invitation is taken by the bridge, so start the tutor first (companion/bin/lani-session${t.p.isDefault ? '' : ` --profile ${t.p.id}`}).`)
}

async function accept() {
  const text = args[0] ?? die('lani-town accept <invitation>: the lani://town?… text of the invitation')
  const parsed = parseInvite(text)
  if (!parsed.ok) die(`Not an invitation this town can use: ${parsed.error}.`)
  const t = townOf(resolveProfile(values.profile), values.url)
  const r = await acceptInvite({ key: t.key, towns: t.towns, invite: parsed.invite, self: { url: t.url, display: t.display(), relay: t.relay }, by: 'cli' })
  if (!r.ok) die(`Not linked (${r.code}): ${r.error}`)
  console.log(`Linked ${whose(t)} with ${r.link.display?.name} · ${r.link.display?.learner} (${groupFingerprint(r.link.id)}), both ways.`)
}

function linkBoth() {
  const [a, b] = args
  if (!a || !b) die('lani-town link <profile> <profile>: two profiles of this node ("default" is the default one)')
  const ta = townOf(resolveProfile(a))
  const tb = townOf(resolveProfile(b))
  if (ta.key.fingerprint === tb.key.fingerprint) die('A town does not link itself.')
  const now = new Date().toISOString()
  // an adult's town and a child's: the adult's is the child's guardian (it sees the child's progress), unless --no-guardian
  const guardianOf = (child: Town, adult: Town) => child.p.child && !adult.p.child && !values['no-guardian']
  // linked again: what was set for it stays (Towns.upsert: what it shares, a guardian's floor, a weekly note); the guardian
  // is said each time (--no-guardian takes it away)
  const linkTo = (t: Town, from: Town): TownLink =>
    ({ id: t.key.fingerprint, public_key: t.key.spki.toString('base64'), url: t.url, linked_at: now, by: 'cli', via: 'link', display: t.display(), guardian: guardianOf(from, t) ? true : undefined })
  ta.towns.upsert(linkTo(tb, ta))
  tb.towns.upsert(linkTo(ta, tb))
  console.log(`Linked ${whose(ta)} (${ta.display().name}) and ${whose(tb)} (${tb.display().name}), both ways.`)
  for (const [child, adult] of [[ta, tb], [tb, ta]] as const) {
    if (guardianOf(child, adult)) console.log(`${whose(adult)} is ${child.display().learner}'s guardian: it sees ${child.display().learner}'s progress in detail, and ${child.display().learner}'s app can't lower that (lani-town share ${adult.p.id} summary --profile ${child.p.id} can; --no-guardian links without).`)
  }
}

/** The link [which] names: a profile of this node, a town id (or its first characters), or a town's or learner's name. */
function findLink(t: Town, which: string, local: Map<string, Resolved>): TownLink {
  const links = t.towns.links()
  const byProfile = [...local].find(([, p]) => p.id === which)?.[0]
  const w = which.toLowerCase().replace(/\s/g, '')
  const found =
    links.find(l => l.id === byProfile) ??
    (/^[0-9a-f]{4,32}$/.test(w) ? links.filter(l => l.id.startsWith(w)) : []).find((_, __, all) => all.length === 1) ??
    links.filter(l => [l.display?.name, l.display?.learner].some(n => n?.toLowerCase() === which.toLowerCase())).find((_, __, all) => all.length === 1)
  return found ?? die(`${whose(t)} has no one linked town "${which}" (see: lani-town list${t.p.isDefault ? '' : ` --profile ${t.p.id}`}).`)
}

async function unlink() {
  const which = args[0] ?? die('lani-town unlink <town>: a profile of this node, a town id or a name')
  const t = townOf(resolveProfile(values.profile))
  const local = localTowns()
  const l = findLink(t, which, local)
  const peer = local.get(l.id)
  t.towns.remove(l.id)
  if (peer) {
    // a town of this node: let go on its side too, whether its bridge runs or not
    new Towns(appDir(peer)).remove(t.key.fingerprint)
    console.log(`Unlinked ${whose(t)} and ${l.display?.name ?? l.id} (profile ${peer.id}), both ways.`)
    return
  }
  // another node's town: tell it (a signed request); it lets go of this one
  try {
    const r = await townRequest(t.key, l, 'POST', '/town/unlink', {})
    console.log(`Unlinked ${l.display?.name ?? l.id}${r.status === 200 ? ', both ways.' : ` here; it answered ${r.status}, so it may still list this town.`}`)
  } catch (e) {
    console.log(`Unlinked ${l.display?.name ?? l.id} here; it couldn't be told (${(e as Error).message}), so it may still list this town.`)
  }
}

function name() {
  const t = townOf(resolveProfile(values.profile))
  if (values.clear) t.towns.setName(undefined)
  else if (args[0] !== undefined) t.towns.setName(args.join(' '))
  const d = t.display()
  console.log(`${whose(t)} is called "${d.name}"${t.towns.name() ? '' : ' (its culture pack\'s name: set one with lani-town name "<name>")'}.`)
}

function share() {
  const [which, level] = args
  if (!which || !isShareLevel(level)) die(`lani-town share <town> ${SHARE_LEVELS.join('|')} [--profile <id>]`)
  const t = townOf(resolveProfile(values.profile))
  const l = findLink(t, which, localTowns())
  const lv = level as ShareLevel
  // a child's guardian link: the parent sets the level and the least the child's app may set (it can raise it, not lower it)
  t.towns.patch(l.id, x => ({ ...x, share: lv, ...(t.p.child && x.guardian ? { floor: lv } : {}) }))
  const s = shareOf(t.towns.get(l.id)!, t.p.child)
  console.log(`${whose(t)} shares ${s.level} with ${l.display?.name ?? l.id} (${l.display?.learner ?? '?'}): ${LEVEL_TEXT[s.level]}.${s.guardian ? ` That town is ${t.display().learner}'s guardian: ${t.display().learner}'s app can't set less than ${s.floor}.` : ''}`)
}

function guardian() {
  const which = args[0] ?? die('lani-town guardian <town> --profile <child> [--clear]: a linked town that is the child\'s guardian\'s (a parent\'s)')
  const t = townOf(resolveProfile(values.profile))
  if (!t.p.child) die(`${whose(t)} isn't a child's: only a child's town has a guardian (lani-profile add --child).`)
  const l = findLink(t, which, localTowns())
  t.towns.patch(l.id, ({ guardian: _g, floor: _f, ...x }) => (values.clear ? x : { ...x, guardian: true }))
  const s = shareOf(t.towns.get(l.id)!, true)
  console.log(values.clear
    ? `${l.display?.name ?? l.id} isn't ${t.display().learner}'s guardian any more: it sees ${s.level}${s.set ? '' : ' (the default)'}.`
    : `${l.display?.name ?? l.id} (${l.display?.learner ?? '?'}) is ${t.display().learner}'s guardian: it sees ${s.level}, and ${t.display().learner}'s app can't lower that.`)
}

switch (cmd) {
  case 'list':
    list()
    break
  case 'invite':
    await invite()
    break
  case 'accept':
    await accept()
    break
  case 'link':
    linkBoth()
    break
  case 'unlink':
    await unlink()
    break
  case 'name':
    name()
    break
  case 'share':
    share()
    break
  case 'guardian':
    guardian()
    break
  default:
    die(USAGE)
}
