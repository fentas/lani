#!/usr/bin/env bun
// companion/bin/lani-profile: the learners on this machine (companion/README.md, "Learners").
//
//   lani-profile add <id> --name <name> --target <lang> --base <lang> [--child] [--level A1]
//                           [--culture <pack>] [--landscape valley|hills|lake|mountains|coast]
//                           [--port N] [--https-port N]
//   lani-profile list                  (also: lani-session --list)
//   lani-profile show <id>             where it lives, how to publish it, start it and pair a phone
//   lani-profile session <id> [--keys FILE] [--mode classic|service]
//                                        for lani-session: the tmux session's name, then its command (service: the
//                                        tutor with the shim, the bridge in lani-bridge; docs/plans/04-bridge-service.md)
import '../env' // first: LANI_* from the FLUENT_* names of a node set up before the rename
import { parseArgs } from 'node:util'
import { join } from 'node:path'
import { keysFile } from '../env'
import { APP_LANGUAGES, cultureIds, culturesIn, DEFAULT_CULTURE, LANDSCAPES, landscapesOf, readManifest } from '../cultures'
import { entryDataDir, initLearnerData, languageCode, LANGUAGES, newEntry, readRegistry, writeRegistry, type ProfileEntry } from '../learners'
import { allProfiles, bridgeEnv, bridgeUp, die, projectDir, q, registryDir, resolveProfile, serveCommand, serviceSocket, short, table, tailnetUrl, tmuxUp, type Resolved } from './common'

const [cmd, ...rest] = process.argv.slice(2)

/** The culture packs (companion/cultures): a learner's village is in one of them. */
const culturesDir = process.env.LANI_CULTURES_DIR || join(projectDir, 'companion/cultures')

async function list() {
  const rows = [['PROFILE', 'LEARNER', 'LEARNS', 'VILLAGE', 'PORT', 'TAILNET', 'TMUX', 'DATA', 'STATUS']]
  for (const p of allProfiles()) {
    const learns = p.target ? `${p.target} ← ${p.base}` : ''
    const status = [(await bridgeUp(p.port)) ? 'bridge up' : 'bridge down', tmuxUp(p.tmux) ? 'session up' : ''].filter(Boolean).join(', ')
    rows.push([p.id, p.name + (p.child ? ' (child)' : ''), learns, p.culture, String(p.port), `:${p.httpsPort}`, p.tmux, short(p.dataDir), status])
  }
  console.log(table(rows))
}

function describe(p: Resolved) {
  const url = tailnetUrl(p)
  const which = p.isDefault ? '' : ` --profile ${p.id}`
  const m = readManifest(culturesDir, p.culture)
  const village = m.ok ? `${p.culture}${m.manifest.status === 'stub' ? ` (a stub for now: the village plays ${DEFAULT_CULTURE})` : ''}` : `${p.culture} (unknown: the village plays ${DEFAULT_CULTURE})`
  console.log(`  data     ${short(p.dataDir)}`)
  console.log(`  village  ${village}`)
  if (p.entry?.landscape) console.log(`  land     ${p.entry.landscape} (the app offers it first when the village is founded)`)
  console.log(`  bridge   127.0.0.1:${p.port}   tmux ${p.tmux}   Remote Control ${p.remoteControl}`)
  console.log(`
Next:
  1. Publish the bridge to your tailnet (once; it stays after a reboot):
       ${serveCommand(p)}
     The app's URL is then ${url}
  2. Start the tutor (detach with Ctrl-b d; on the first start, confirm the development
     channel and approve the lani MCP server):
       companion/bin/lani-session${which}
  3. Pair the learner's phone: show them the QR code from
       companion/bin/lani-pair${which}
     The phone needs Tailscale, signed in to your tailnet, or this node shared with it.`)
}

/**
 * The culture pack a new learner of [target] lives in, when --culture doesn't say: the pack in that language (the
 * default one when it is; else the first, the others named), or a clear stop when no pack speaks it yet.
 */
function cultureFor(target: string): string {
  const { complete, stubs } = culturesIn(culturesDir, target)
  const lang = LANGUAGES[target] ?? target
  if (complete.length) {
    const pick = complete.includes(DEFAULT_CULTURE) ? DEFAULT_CULTURE : complete[0]
    const others = complete.filter(c => c !== pick)
    if (others.length) console.log(`The village is in ${pick}; other ${lang} culture packs: ${others.join(', ')} (--culture to choose one).`)
    return pick
  }
  const packs = cultureIds(culturesDir).map(c => {
    const m = readManifest(culturesDir, c)
    return m.ok ? `${c} (${LANGUAGES[m.manifest.language] ?? m.manifest.language}${m.manifest.status === 'stub' ? ', a stub' : ''})` : c
  })
  return die(
    [
      `No culture pack speaks ${lang} yet: there is no ${lang} village to start the learner in.`,
      `  The packs here: ${packs.join(', ') || 'none'}.`,
      stubs.length ? `  ${stubs.join(', ')}: ${lang}, but only a stub (a manifest alone) for now.` : '',
      `  A ${lang} pack is a directory in companion/cultures with "language": "${target}" in its culture.json (companion/README.md, "Culture packs").`,
      `  Until there is one: pass --culture <pack> to give the learner a village in that pack's language, or add them once the pack is there.`,
    ].filter(Boolean).join('\n'),
  )
}

async function add() {
  const { values, positionals } = parseArgs({
    args: rest,
    allowPositionals: true,
    options: {
      name: { type: 'string' },
      target: { type: 'string' },
      base: { type: 'string' },
      child: { type: 'boolean', default: false },
      level: { type: 'string', default: 'A1' },
      culture: { type: 'string' },
      landscape: { type: 'string' },
      port: { type: 'string' },
      'https-port': { type: 'string' },
    },
  })
  const id = positionals[0] ?? die('usage: lani-profile add <id> --name <name> --target <lang> --base <lang> [--child]')
  // The app has labels, grading and speech settings for these (l10n/Lang.kt): any two of them are a pair.
  const langs = APP_LANGUAGES.map(k => `${k} (${LANGUAGES[k]})`).join(', ')
  const appLanguage = (flag: string, v: string | undefined, what: string) => {
    const code = languageCode(v ?? '')
    if (!code || !(APP_LANGUAGES as readonly string[]).includes(code)) die(`--${flag}: ${what}, one of the app's languages: ${langs}`)
    return code
  }
  const target = appLanguage('target', values.target, 'the language they learn')
  const base = appLanguage('base', values.base, 'the language explanations are in')
  if (target === base) die(`--target and --base: two different languages (both are ${LANGUAGES[target]})`)
  const name = values.name?.trim() || die('--name: the learner\'s name')
  const level = values.level.toUpperCase()
  if (!/^[ABC][12]$/.test(level)) die('--level: A1, A2, B1, B2, C1 or C2')
  const culture = values.culture?.trim() || cultureFor(target)
  const manifest = readManifest(culturesDir, culture)
  if (!manifest.ok) die(`--culture: a culture pack, one of ${cultureIds(culturesDir).join(', ')}\n${manifest.errors}`)
  // The land a new village is offered first (the learner still chooses in the app): one the region has, the coast only
  // where it has the sea.
  const landscape = values.landscape?.trim().toLowerCase()
  if (landscape !== undefined) {
    if (!(LANDSCAPES as readonly string[]).includes(landscape)) die(`--landscape: one of ${LANDSCAPES.join(', ')}`)
    const offered = landscapesOf(culturesDir, culture)
    if (!offered.includes(landscape)) die(`--landscape ${landscape}: ${culture} has no sea; its landscapes are ${offered.join(', ')}`)
  }
  const r = readRegistry(registryDir)
  let entry: ProfileEntry
  try {
    entry = newEntry(registryDir, r, id, { name, target, base, child: values.child, culture, landscape }, {
      port: values.port ? Number(values.port) : undefined,
      httpsPort: values['https-port'] ? Number(values['https-port']) : undefined,
    })
  } catch (e) {
    die((e as Error).message)
  }
  try {
    initLearnerData(projectDir, entryDataDir(registryDir, entry), { name, target, base, level, child: values.child })
  } catch (e) {
    die((e as Error).message)
  }
  writeRegistry(registryDir, { ...r, profiles: [...r.profiles, entry] })
  console.log(`Added ${id}: ${name}${values.child ? ' (child)' : ''}, learns ${LANGUAGES[target]} from ${LANGUAGES[base]}, level ${level}.`)
  if (manifest.manifest.language !== target) console.log(`  The ${culture} culture pack is in ${LANGUAGES[manifest.manifest.language] ?? manifest.manifest.language}, not ${LANGUAGES[target]}: its village speaks that.`)
  describe(resolveProfile(id))
  if (values.child) console.log(`
  A child profile: the tutor's instructions say so (age-appropriate, no personal data), and tool
  approvals are not relayed to the phone; answer them in the terminal or through Remote Control.`)
}

function session() {
  const { values, positionals } = parseArgs({ args: rest, allowPositionals: true, options: { keys: { type: 'string' }, mode: { type: 'string', default: 'classic' } } })
  const p = resolveProfile(positionals[0])
  if (p.isDefault || !p.entry) die('the default profile is started by lani-session itself')
  if (values.mode !== 'classic' && values.mode !== 'service') die('--mode: classic (the bridge inside the session) or service (the shim; lani-bridge runs the bridge)')
  // classic: the bridge inside the session serves the app (LANI_TUTOR=1). service: the session's shim reaches the
  // learner's bridge service (docs/plans/04-bridge-service.md), and without the port a bridge started here by mistake
  // never takes it.
  const { LANI_BRIDGE_PORT: _port, ...withoutPort } = bridgeEnv(p)
  const env: Record<string, string> = values.mode === 'service' ? { ...withoutPort, LANI_BRIDGE_SHIM: '1', LANI_BRIDGE_SOCKET: serviceSocket(p) } : { ...bridgeEnv(p), LANI_TUTOR: '1' }
  const keys = values.keys ?? keysFile()
  // CLAUDE.md is written for the default learner (it points at data/): this session's own learner comes first.
  const results = join(p.dataDir, '..', 'results')
  const learns = p.target ? ` ${p.name} learns ${LANGUAGES[p.target] ?? p.target} from ${LANGUAGES[p.base ?? ''] ?? p.base}.` : ''
  const prompt = [
    `This tutor session is for ${p.name} (learner profile "${p.id}"), not for Jan.${learns}`,
    `${p.name}'s data is in ${p.dataDir}: read learner-profile.json and the other databases there (the helper scripts use it through LANI_DATA_DIR), and write session results to ${results}.`,
    `Wherever CLAUDE.md or a skill says data/ or results/, it means these directories; the repository's own data/ and results/ are Jan's: never read or change them.`,
    ...(p.child ? [`${p.name} is a child: keep everything age-appropriate, simple and encouraging, and never ask for personal information.`] : []),
  ].join(' ')
  // The keys first, then the profile's environment, so a keys file can't point the session at other data.
  const command = [
    `set -a; if [ -r ${q(keys)} ]; then . ${q(keys)}; fi; set +a`,
    `export ${Object.entries(env).map(([k, v]) => `${k}=${q(v)}`).join(' ')}`,
    `exec claude --dangerously-load-development-channels server:lani --remote-control ${q(p.remoteControl)} --append-system-prompt ${q(prompt)}`,
  ].join('; ')
  console.log(p.tmux)
  console.log(command)
}

function show() {
  const p = resolveProfile(rest[0])
  console.log(`${p.id}: ${p.name}${p.child ? ' (child)' : ''}${p.target ? `, learns ${LANGUAGES[p.target] ?? p.target} from ${LANGUAGES[p.base ?? ''] ?? p.base}` : ''}`)
  describe(p)
}

switch (cmd) {
  case 'add':
    await add()
    break
  case 'list':
  case undefined:
    await list()
    break
  case 'show':
    show()
    break
  case 'session':
    session()
    break
  default:
    die(`unknown command: ${cmd}\nusage: lani-profile add|list|show|session …`)
}
