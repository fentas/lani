// What a tutor is told, for one synthetic learner: the channel rules (what Claude Code shows the session), the whole
// guide (channel_guide) and the tools' descriptions. Nothing of a real learner: a throwaway data directory.
//
//   bun test/tutor-text.ts <jan|teo|kaernten|lakeland|friuli-default> <out dir>
//
// jan is the default profile from English (tutor-export's learner); teo a child's Italian from German in Friuli (a
// profile); kaernten an adult's German from English; lakeland English from Slovene; friuli-default a learner set up as
// the node's default profile. Compare two runs with diff -r (before and after a change to the tutor's text).
import { mkdirSync, mkdtempSync, rmSync, writeFileSync, cpSync, readdirSync, readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'

const who = process.argv[2]
const out = process.argv[3]
const repo = resolve(import.meta.dir, '../../..')
type L = { name: string; target: string; base: string; culture: string; profile?: string; child?: boolean }
const LEARNERS: Record<string, L> = {
  jan: { name: 'Jan', target: 'sl', base: 'en', culture: 'primorska' },
  teo: { name: 'Mia', target: 'it', base: 'de', culture: 'friuli', profile: 'mia', child: true },
  kaernten: { name: 'Eva', target: 'de', base: 'en', culture: 'kaernten', profile: 'eva' },
  lakeland: { name: 'Ana', target: 'en', base: 'sl', culture: 'lakeland', profile: 'ana' },
  'friuli-default': { name: 'Lena', target: 'it', base: 'de', culture: 'friuli' },
}
const l = LEARNERS[who ?? '']
if (!l || !out) {
  console.error(`usage: bun test/tutor-text.ts <${Object.keys(LEARNERS).join('|')}> <out dir>`)
  process.exit(1)
}
const tmp = mkdtempSync(join(tmpdir(), 'lani-tutor-text-'))
process.on('exit', () => rmSync(tmp, { recursive: true, force: true }))
for (const k of Object.keys(process.env)) if (/^(LANI_|FLUENT_|ELEVENLABS_)/.test(k) || k === 'CLAUDE_PROJECT_DIR' || k === 'CLAUDE_PLUGIN_ROOT') delete process.env[k]
const data = join(tmp, 'p', 'data')
mkdirSync(data, { recursive: true })
if (who === 'jan') {
  // as tutor-export builds it: the templates, the learner Jan
  for (const f of readdirSync(join(repo, 'data-examples')).filter(f => f.endsWith('-template.json'))) cpSync(join(repo, 'data-examples', f), join(data, f.replace('-template', '')))
  const p = JSON.parse(readFileSync(join(data, 'learner-profile.json'), 'utf8'))
  writeFileSync(join(data, 'learner-profile.json'), JSON.stringify({ ...p, learner: { ...p.learner, name: 'Jan', gender: 'male' } }, null, 2))
} else {
  const { initLearnerData } = await import('../src/learners')
  rmSync(data, { recursive: true, force: true })
  initLearnerData(repo, data, { name: l.name, target: l.target, base: l.base, culture: l.culture, child: !!l.child, gender: 'male' })
}
Object.assign(process.env, {
  LANI_ENV_FILE: '/dev/null',
  LANI_DATA_DIR: data,
  CLAUDE_PROJECT_DIR: repo,
  LANI_CONFIG_DIR: join(tmp, 'config'),
  XDG_CACHE_HOME: join(tmp, 'cache'),
  XDG_STATE_HOME: join(tmp, 'state'),
  XDG_DATA_HOME: join(tmp, 'share'),
  LANI_VOICE_DESIGN: 'off',
  ...(l.culture !== 'primorska' ? { LANI_CULTURE: l.culture } : {}),
  ...(l.profile ? { LANI_PROFILE: l.profile } : {}),
  ...(l.child ? { LANI_CHILD: '1' } : {}),
})
await import('../src/env')
const { buildBridge } = await import('../src/bridge')
const error = console.error
console.error = () => {}
const b = buildBridge({ mode: 'stdio' })
console.error = error
// the throwaway directory's name is not the learner's text
const clean = (s: string) => s.split(tmp).join('<tmp>')
mkdirSync(out, { recursive: true })
writeFileSync(join(out, `${who}.rules.txt`), `${clean(b.rulesNow())}\n`)
writeFileSync(join(out, `${who}.guide.txt`), `${clean(b.guide())}\n`)
writeFileSync(join(out, `${who}.tools.txt`), `${b.tools.map(t => `## ${t.name}\n${clean(t.description ?? '')}\n${JSON.stringify(t.inputSchema)}`).join('\n\n')}\n`)
console.log(`${who}: rules ${b.rulesNow().length}, guide ${b.guide().length}, ${b.tools.length} tools → ${out}`)
process.exit(0)
