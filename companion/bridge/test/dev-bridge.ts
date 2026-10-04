// Dev bridge for emulator QA: the real bridge on a COPY of the learner data, with a canned tutor.
// Run: bun test/dev-bridge.ts   → http://127.0.0.1:8791 (emulator: http://10.0.2.2:8791), token "dev-token".
// Env: LANI_DEV_PORT its port; LANI_DEV_DATA the data to copy (default: the repo's data/); LANI_DEV_KEEP_TOWNS=1
// keeps the copy's town links (by default QA's town knows only the towns QA links, e.g. test/dev-town.ts, and never
// asks a real learner's town).
import '../src/env' // first: LANI_* from the FLUENT_* names of a node set up before the rename
import { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js'
import { cpSync, existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'

const port = process.env.LANI_DEV_PORT ?? '8791'
const dataDir = mkdtempSync(join(tmpdir(), 'lani-dev-'))
// The copy leaves out release APKs and backups (large, and QA doesn't need them), and is deleted on exit.
const skip = [/\/app\/release\//, /\/\.backups\//, /\.json\.backup-/]
cpSync(resolve(process.env.LANI_DEV_DATA || resolve(import.meta.dir, '../../../data')), dataDir, { recursive: true, filter: src => !skip.some(r => r.test(src)) })
const townsFile = join(dataDir, 'app/towns.json')
if (process.env.LANI_DEV_KEEP_TOWNS !== '1' && existsSync(townsFile)) {
  // the village's name stays; its links go (in the copy)
  const t = JSON.parse(readFileSync(townsFile, 'utf8'))
  writeFileSync(townsFile, JSON.stringify({ ...t, links: [] }, null, 2) + '\n')
}
console.log(`data copy: ${dataDir}`)
const cleanup = () => rmSync(dataDir, { recursive: true, force: true })
process.on('exit', cleanup)
for (const sig of ['SIGINT', 'SIGTERM', 'SIGHUP'] as const) process.on(sig, () => process.exit(0))

const client = new Client({ name: 'dev-tutor', version: '0' })
const reply = (conversation_id: string, text: string, data?: object) =>
  client.callTool({ name: 'reply', arguments: { conversation_id, text, ...(data ? { data } : {}) } })

client.fallbackNotificationHandler = async n => {
  const p = n.params as any
  if (n.method === 'notifications/claude/channel/permission_request') return
  if (n.method !== 'notifications/claude/channel') return
  const { kind, conversation_id } = p.meta
  console.log(`← ${kind} [${conversation_id}] ${String(p.content).slice(0, 160).replace(/\n/g, ' ')}`)
  if (kind === 'answer') await reply(conversation_id, '**Dobro!** 🟢 (dev tutor) Nice sentence.', { score: 8 })
  else if (kind === 'chat' && /tabela/i.test(String(p.content)))
    // Markdown QA: a table, a heading, a numbered list and tappable Slovene.
    await reply(conversation_id, [
      '## Glagol *biti* · to be',
      '',
      '| oseba | ednina | dvojina | množina |',
      '|---|---|---|---|',
      '| 1. | *sem* | *sva* | *smo* |',
      '| 2. | *si* | *sta* | *ste* |',
      '| 3. | *je* | *sta* | *so* |',
      '',
      '1. Say „Jaz sem Jan.“ out loud.',
      '2. Then try *Midva sva iz Gorice.*',
    ].join('\n'))
  else if (kind === 'chat') await reply(conversation_id, `(dev tutor) Prejel sem: *${String(p.content).split('\n')[0].slice(0, 60)}*`)
  else if (kind === 'session_end') await reply(conversation_id, '(dev tutor) 🎉 Session saved.')
  else if (kind === 'roleplay') {
    const d = JSON.parse(/```json\n([\s\S]*)\n```/.exec(String(p.content))?.[1] ?? '{}')
    await Bun.sleep(2500) // the real tutor takes 10-30 s
    if (d.hint) await reply(conversation_id, 'Hvala, zelo dobro je.', { hint: true, sl: 'Hvala, zelo dobro je.', en: 'Thanks, it is very good.' })
    else {
      const turn = Number(d.turn ?? 1)
      const goals = Array.from({ length: Math.min(turn, d.scenario?.goals?.length ?? 0) }, (_, i) => i)
      const correction = turn === 2 ? 'Rekli bi: **Zelo je dobro.** · "dobro" agrees with the neuter "it".' : undefined
      await reply(conversation_id, 'Me veseli! Še malo juhe?', { sl: 'Me veseli! Še malo juhe?', en: "I'm glad! A bit more soup?", goals_done: goals, correction, end: turn >= 4 || undefined })
    }
  } else if (kind === 'roleplay_end') {
    await Bun.sleep(2500)
    await reply(conversation_id, '(dev tutor) **Bravo!** 🎉\n\n✅ You greeted politely.\n\n📌 Practise: *sit sem*, *še malo*.', { debrief: true })
  }
  else if (kind === 'partner_answer') await reply(conversation_id, '**Lepo!** 🟢 (dev tutor) Nice answer.', { score: 8, partner_note: '(dev tutor) Jan answered well.' })
}

await client.connect(
  new StdioClientTransport({
    command: 'bun',
    args: [resolve(import.meta.dir, '../src/index.ts')],
    env: { ...process.env, LANI_DATA_DIR: dataDir, LANI_BRIDGE_PORT: port, LANI_BRIDGE_TOKEN: 'dev-token', LANI_FAMILY_TOKEN: 'dev-family', LANI_RHYTHM: 'off',
      // QA must not spend the ElevenLabs quota; LANI_DEV_ELEVENLABS=1 allows it. Cached clips still play.
      ELEVENLABS_API_KEY: process.env.LANI_DEV_ELEVENLABS === '1' ? (process.env.ELEVENLABS_API_KEY ?? '') : '' } as Record<string, string>,
    stderr: 'inherit',
  }),
)
console.log(`dev bridge up on :${port} (token dev-token; family page http://127.0.0.1:${port}/family#t=dev-family)`)
// Never outlive QA: exit when the bridge closes, or when whoever started us is gone (we'd be re-parented to init).
// Orphaned dev bridges once piled up for days, each spinning a core.
client.onclose = () => process.exit(1)
const parent = process.ppid
setInterval(() => { if (process.ppid !== parent) process.exit(0) }, 2000)
await new Promise(() => {})
