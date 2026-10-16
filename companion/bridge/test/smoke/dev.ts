// Smoke checks: the developer's data from the app (src/features/dev.ts, companion/README.md "Developer data on the node"):
// POST /dev/shadow and /dev/logs save under <data>/app/dev/ (mode 0600; the shadow's export replaced, the log appended),
// only with the app's token, within their cap, cleaned of secrets once more; the data repository's .gitignore keeps
// /app/dev/ out (the bridge adds the line at its start; lani_data_repo.py writes it and keeps it in); old days pruned.
import { existsSync, mkdirSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { DEV_IGNORE, DEV_MAX, ignoreDevData, localDay, pruneDevData, scrub, scrubJson } from '../../src/features/dev'
import { auth, base, check, dataDir, fam, sendChunked, tempDir, token } from './harness'

const repo = resolve(import.meta.dir, '../../../..')
const mode = (p: string) => statSync(p).mode & 0o777
const quiet = () => {}

export default async function dev() {
  const dir = join(dataDir, 'app/dev')
  const day = localDay()
  const shadowFile = join(dir, `shadow-${day}.json`)
  const logFile = join(dir, `app-log-${day}.txt`)
  const post = (path: string, body: string, headers: Record<string, string> = auth, type = 'application/json') =>
    fetch(`${base}${path}`, { method: 'POST', headers: { ...headers, 'content-type': type }, body })
  const exportOf = (n: number) => JSON.stringify({
    schema: 'lani.shadow-export/v0', n, app: 'si.lanisce.lani 0.2.99 (99)',
    settings: { enabled: true, token: 'leaked-token-1234' },
    days: [{ day, log: { calls: [{ usage: { input: 5 }, usageRaw: { start: { input_tokens: 5, cache_creation_input_tokens: 4000, cache_creation: { ephemeral_5m_input_tokens: 0, ephemeral_1h_input_tokens: 4000 } } } }], events: [{ text: `a line with ${token} and Bearer abcdef123456 in it` }] } }],
  })

  // --- who may send, and what ---------------------------------------------------------------------
  check('dev: POST /dev/shadow without the token: 401', (await post('/dev/shadow', exportOf(0), {})).status === 401)
  check('dev: POST /dev/logs without the token: 401', (await post('/dev/logs', 'x', {}, 'text/plain')).status === 401)
  check("dev: the family's token may not send (403)", (await post('/dev/shadow', exportOf(0), fam)).status === 403)
  check('dev: nothing saved by them', !existsSync(shadowFile) && !existsSync(logFile))
  check('dev: not JSON: 400', (await post('/dev/shadow', 'not json')).status === 400)
  check('dev: JSON that is no shadow export: 400', (await post('/dev/shadow', JSON.stringify({ schema: 'lani.other/v0' }))).status === 400 && (await post('/dev/shadow', '[1]')).status === 400)
  check('dev: an empty log: 400', (await post('/dev/logs', '\n', auth, 'text/plain')).status === 400)

  // --- the shadow's export: the day's latest replaces the one before ------------------------------
  const first = await post('/dev/shadow', exportOf(1))
  const firstBody = await first.json()
  check('dev: POST /dev/shadow saves app/dev/shadow-<day>.json', first.status === 200 && firstBody.file === `app/dev/shadow-${day}.json` && existsSync(shadowFile), firstBody)
  check('dev: the directory and the file are readable by this user only (0700, 0600)', mode(dir) === 0o700 && mode(shadowFile) === 0o600, [mode(dir).toString(8), mode(shadowFile).toString(8)])
  await post('/dev/shadow', exportOf(2))
  const saved = JSON.parse(readFileSync(shadowFile, 'utf8'))
  check("dev: the day's latest export replaces the one before", saved.n === 2 && saved.schema === 'lani.shadow-export/v0', saved.n)
  check('dev: the raw usage kept as sent', saved.days[0].log.calls[0].usageRaw.start.cache_creation.ephemeral_1h_input_tokens === 4000 && saved.days[0].log.calls[0].usageRaw.start.input_tokens === 5)
  const savedText = readFileSync(shadowFile, 'utf8')
  check("dev: no secret in it: the request's own token, a key named token, a Bearer token", !savedText.includes(token) && !savedText.includes('leaked-token-1234') && !savedText.includes('abcdef123456') && saved.settings.token === '‹…›', savedText.slice(0, 400))

  // --- the app's log: appended ------------------------------------------------------------------------
  const log1 = [
    '=== upload 1 ===',
    '2026-10-16 12:00:00.000  1234  1250 W Clips: no clip of a line: java.io.IOException: HTTP 404',
    `2026-10-16 12:00:01.000  1234  1250 I ShadowTutor: Authorization: Bearer ${token} x-api-key: sk-ant-api03-SMOKEKEY0123456789 url /x?token=querysecret99&y=1`,
  ].join('\n')
  const l1 = await post('/dev/logs', log1, auth, 'text/plain; charset=utf-8')
  const l1Body = await l1.json()
  check('dev: POST /dev/logs saves app/dev/app-log-<day>.txt', l1.status === 200 && l1Body.file === `app/dev/app-log-${day}.txt` && l1Body.lines === 3 && mode(logFile) === 0o600, l1Body)
  await post('/dev/logs', '=== upload 2 ===\n2026-10-16 12:30:00.000  1234  1250 E Speaker: said «x»: offline', auth, 'text/plain')
  const logText = readFileSync(logFile, 'utf8')
  check("dev: a day's logs are appended", logText.includes('=== upload 1 ===') && logText.includes('=== upload 2 ===') && logText.indexOf('upload 1') < logText.indexOf('upload 2') && logText.includes('no clip of a line'), logText)
  check('dev: no secret in the log: the token, the Bearer, the key, a query token', !logText.includes(token) && !logText.includes('SMOKEKEY') && !logText.includes('querysecret99') && logText.includes('x-api-key: ‹…›') && logText.includes('token=‹…›&y=1'), logText)

  // --- the cap ------------------------------------------------------------------------------------------
  const big = await sendChunked(`${base}/dev/shadow`, 'POST', { ...auth }, DEV_MAX + 1024, new TextEncoder().encode('{"schema":"lani.shadow-export/v0","x":"'))
  check(`dev: an export over ${DEV_MAX} bytes: 413, nothing replaced`, (big.status === 413 || big.status === 0) && JSON.parse(readFileSync(shadowFile, 'utf8')).n === 2, big)
  const bigLog = await sendChunked(`${base}/dev/logs`, 'POST', { ...auth, 'content-type': 'text/plain' }, DEV_MAX + 1024, new TextEncoder().encode('x'))
  check(`dev: a log over ${DEV_MAX} bytes: 413, nothing appended`, (bigLog.status === 413 || bigLog.status === 0) && readFileSync(logFile, 'utf8') === logText, bigLog)

  // --- the redaction, on its own ---------------------------------------------------------------------------
  check('dev: scrub: Bearer, sk-ant-, named values, query keys, known values', scrub('Bearer abcdefgh1 sk-ant-xyz123456 "api_key": "k1" token=t1 ?key=k2&sig=s3 devicetokenvalue', ['devicetokenvalue']) === 'Bearer ‹…› sk-ant-‹…› "api_key": "‹…›" token=‹…› ?key=‹…›&sig=‹…› ‹secret›')
  check('dev: scrub leaves the counts of tokens alone', scrub('"input_tokens":12 "cache_creation_input_tokens": 4000 max_tokens=8') === '"input_tokens":12 "cache_creation_input_tokens": 4000 max_tokens=8')
  check('dev: scrubJson keeps numbers and replaces a secret key\'s string', JSON.stringify(scrubJson({ token: 'a', bridge_token: 'b', input_tokens: 3, nested: [{ text: 'Bearer zzzzzzzz' }] })) === '{"token":"‹…›","bridge_token":"‹…›","input_tokens":3,"nested":[{"text":"Bearer ‹…›"}]}')

  // --- never committed -----------------------------------------------------------------------------------------
  const ignore = readFileSync(join(dataDir, '.gitignore'), 'utf8')
  check("dev: the bridge added /app/dev/ to the learner's .gitignore at its start, once, keeping their lines", ignore.startsWith('# the smoke learner\n/app/bridge-token\n') && ignore.split('\n').filter(l => l === DEV_IGNORE).length === 1, ignore)
  check('dev: … and not again', !ignoreDevData(dataDir, quiet) && readFileSync(join(dataDir, '.gitignore'), 'utf8') === ignore)
  const plain = tempDir('lani-smoke-dev-plain-')
  check('dev: a plain data directory without a .gitignore gets none', !ignoreDevData(plain, quiet) && !existsSync(join(plain, '.gitignore')))
  const own = tempDir('lani-smoke-dev-own-')
  writeFileSync(join(own, '.gitignore'), '/app/dev\n*.tmp')
  check("dev: a learner's own line that keeps it out is enough", !ignoreDevData(own, quiet) && readFileSync(join(own, '.gitignore'), 'utf8') === '/app/dev\n*.tmp')
  const noNewline = tempDir('lani-smoke-dev-nl-')
  writeFileSync(join(noNewline, '.gitignore'), '*.tmp')
  check('dev: the line goes on a line of its own', ignoreDevData(noNewline, quiet) && readFileSync(join(noNewline, '.gitignore'), 'utf8').split('\n').includes(DEV_IGNORE) && readFileSync(join(noNewline, '.gitignore'), 'utf8').startsWith('*.tmp\n'))
  const git = Bun.which('git')
  if (git) {
    const wide = tempDir('lani-smoke-dev-git-')
    Bun.spawnSync(['git', 'init', '-q', wide])
    writeFileSync(join(wide, '.gitignore'), '/app/*\n')
    check('dev: a repository whose .gitignore keeps app/ out already (git check-ignore) is left alone', !ignoreDevData(wide, quiet) && readFileSync(join(wide, '.gitignore'), 'utf8') === '/app/*\n')
    const repoDir = tempDir('lani-smoke-dev-repo-')
    Bun.spawnSync(['git', 'init', '-q', repoDir])
    check('dev: a repository without a .gitignore gets one with the line', ignoreDevData(repoDir, quiet) && readFileSync(join(repoDir, '.gitignore'), 'utf8').includes(DEV_IGNORE))
  }
  const template = Bun.spawnSync(['python3', join(repo, '.claude/hooks/lani_data_repo.py'), 'gitignore'], { stdout: 'pipe' }).stdout.toString()
  check("dev: lani-setup's .gitignore (lani_data_repo.py) keeps /app/dev/ out", template.split('\n').includes(DEV_IGNORE), template.slice(-400))

  // --- 30 days kept --------------------------------------------------------------------------------------------
  const old = tempDir('lani-smoke-dev-prune-')
  mkdirSync(old, { recursive: true })
  for (const f of ['shadow-2026-09-15.json', 'app-log-2026-09-15.txt', 'shadow-2026-09-16.json', 'app-log-2026-10-16.txt', 'notes.txt']) writeFileSync(join(old, f), 'x')
  const gone = pruneDevData(old, '2026-10-16')
  check('dev: files of days more than 30 days back go, the rest stay', gone.sort().join() === 'app-log-2026-09-15.txt,shadow-2026-09-15.json' && ['shadow-2026-09-16.json', 'app-log-2026-10-16.txt', 'notes.txt'].every(f => existsSync(join(old, f))), gone)
}
