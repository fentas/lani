// Smoke checks: channel capabilities, the tool list, auth.
import { mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { spawn } from '../../src/config'
import { bearer, loadToken } from '../../src/http'
import { auth, base, check, client } from './harness'

export default async function core() {
  const caps = client.getServerCapabilities() as any
  check('declares claude/channel', caps?.experimental?.['claude/channel'])
  check('declares permission relay', caps?.experimental?.['claude/channel/permission'])
  const { tools } = await client.listTools()
  check('exposes 40 tools, the guide first', tools.length === 40 && ['publish_dialog_variant', 'remove_dialog_variant'].every(n => tools.some(t => t.name === n)) && tools[0]?.name === 'channel_guide' && tools.some(t => t.name === 'publish_gloss') && tools.some(t => t.name === 'publish_sentence_note') && ['publish_grammar', 'list_grammar', 'get_grammar'].every(n => tools.some(t => t.name === n)) && tools.some(t => t.name === 'voice_profiles') && tools.some(t => t.name === 'voice_redesign') && tools.some(t => t.name === 'voice_status') && tools.some(t => t.name === 'publish_scenario') && tools.some(t => t.name === 'set_rhythm') && tools.some(t => t.name === 'publish_scene') && tools.some(t => t.name === 'publish_villager') && tools.some(t => t.name === 'publish_story') && tools.some(t => t.name === 'list_stories') && ['get_arrival', 'publish_arrival', 'remove_arrival', 'list_readings', 'get_reading', 'publish_reading', 'remove_reading'].every(n => tools.some(t => t.name === n)), tools.map(t => t.name))

  check('rejects missing token', (await fetch(`${base}/modules`)).status === 401)

  // tokens: never empty, compared in constant time, every answer with nosniff
  const health = await fetch(`${base}/health`)
  check('GET /health says only that it is up', JSON.stringify(await health.json()) === '{"ok":true}')
  const listed = await fetch(`${base}/modules`, { headers: auth })
  check('API answers carry nosniff, errors too', listed.headers.get('x-content-type-options') === 'nosniff' && (await fetch(`${base}/modules`)).headers.get('x-content-type-options') === 'nosniff')
  const tokenFile = join(mkdtempSync(join(tmpdir(), 'lani-token-')), 'bridge-token')
  writeFileSync(tokenFile, '\n')
  const regenerated = loadToken(tokenFile, undefined, () => {}, 'test')
  check('an empty token file is replaced by a new token', regenerated.length >= 32 && readFileSync(tokenFile, 'utf8').trim() === regenerated.toString())
  const req = (h?: string) => new Request('http://x/', { headers: h === undefined ? {} : { authorization: h } })
  check('an empty secret matches no request', !bearer(Buffer.from(''))(req()) && !bearer(Buffer.from(''))(req('Bearer ')))
  const isApp = bearer(Buffer.from('s3cret-token'))
  const secrets = ['ELEVENLABS_API_KEY', 'LANI_BRIDGE_TOKEN', 'LANI_FAMILY_TOKEN']
  const before = secrets.map(k => process.env[k])
  for (const k of secrets) process.env[k] = 'child-secret'
  const seen = spawn(tmpdir(), ['sh', '-c', 'echo "${ELEVENLABS_API_KEY:-none} ${LANI_BRIDGE_TOKEN:-none} ${LANI_FAMILY_TOKEN:-none}"']).stdout.trim()
  secrets.forEach((k, i) => (before[i] === undefined ? delete process.env[k] : (process.env[k] = before[i])))
  check("the helper scripts don't get the ElevenLabs key or the tokens", seen === 'none none none', seen)
  check('bearer: the token matches; a wrong, longer, shorter or missing one does not', isApp(req('Bearer s3cret-token')) && !isApp(req('Bearer s3cret-tokem')) && !isApp(req('Bearer s3cret-token2')) && !isApp(req('Bearer s3cret')) && !isApp(req()))
}
