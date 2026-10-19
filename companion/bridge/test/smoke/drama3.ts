// Smoke checks: Drama 3 as the local engine for batch clips (companion/voice-drama3): the order of preference (ElevenLabs,
// then Drama 3, then Gepard), and companion/bin/voice-build --drama3 on a fake worker (test/fakes/drama3.ts) that it
// starts and stops on demand: the dry run, the run, the lines no take passed, the memory pause, the resume, Ctrl-C.
// The fake worker's port: LANI_SMOKE_DRAMA3_PORT (by default the harness's port + 40).
import { Database } from 'bun:sqlite'
import { appendFileSync, cpSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { normalizeText } from '../../src/family'
import { todo } from '../../src/prebuild'
import { availableGb, drama3Estimate, drama3Settings } from '../../src/drama3'
import { clipKey, Drama3, ElevenLabs, ENGINE_RANK, Gepard, outranks, VoiceStore, type Clip } from '../../src/voice'
import { check, culturesDir, fakeEleven, fakeGepard, port as smokePort, tempDir, tone } from './harness'

const d3Port = Number(process.env.LANI_SMOKE_DRAMA3_PORT ?? smokePort + 40)
const fake = resolve(import.meta.dir, '../fakes/drama3.ts')
const voiceBuildBin = resolve(import.meta.dir, '../../../bin/voice-build')
const hasFfmpeg = !!Bun.which('ffmpeg')

/** A clip row as the store keeps it, with a file. */
function putClip(db: Database, filesDir: string, c: { voice: string; text: string; engine: string; hits?: number; created?: string; engineVoice?: string | null }): Clip {
  const norm = normalizeText(c.text)
  const key = clipKey(norm, c.voice)
  const file = `${Bun.hash(`${key}|${c.engine}`).toString(16).padStart(16, '0').repeat(3).slice(0, 40)}.mp3`
  writeFileSync(join(filesDir, file), tone(1))
  db.query('INSERT OR REPLACE INTO clips (key, norm, text, voice, engine, file, chars, created_at, source, hits, engine_voice) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)')
    .run(key, norm, c.text, c.voice, c.engine, file, c.text.length, c.created ?? '2026-10-10T10:00:00.000Z', 'scene:test', c.hits ?? 0, c.engineVoice ?? null)
  return db.query('SELECT * FROM clips WHERE key = ?').get(key) as Clip
}

export default async function drama3() {
  // --- the order of preference: ElevenLabs, then Drama 3, then Gepard (in-process, against the fakes) ---
  {
    const fe = fakeEleven()
    const fg = fakeGepard()
    const vs = new VoiceStore({ appDir: tempDir('lani-d3-rank-'), engines: [new ElevenLabs({ key: 'test-key', base: fe.url }), new Gepard({ url: fg.url })], queueDelayMs: 0, level: null })
    const d3clip = putClip(vs.database, vs.filesDir, { voice: 'grandma', text: 'Kje je mama?', engine: 'drama3', hits: 4, engineVoice: 'grandma/grandma-2' })
    check('the engines\' rank: ElevenLabs, then Drama 3, then Gepard',
      ENGINE_RANK.elevenlabs > ENGINE_RANK.drama3 && ENGINE_RANK.drama3 > ENGINE_RANK.gepard && outranks('elevenlabs', d3clip) && outranks('drama3', d3clip) && !outranks('gepard', d3clip) && outranks('gepard', { engine: 'gepard' }))
    const calls = fg.st.calls.length
    const g = await vs.voice('Kje je mama?', 'grandma', 'scene:test', { engines: ['gepard'], replace: true, force: true })
    check('a Gepard take never replaces a Drama 3 clip (even forced): the Drama 3 clip stays, the worker not asked',
      g?.engine === 'drama3' && g.file === d3clip.file && fg.st.calls.length === calls && vs.get('kje je mama', 'grandma')?.engine === 'drama3', g)
    check('…and the app gets the Drama 3 clip', vs.index()['kje je mama']?.grandma?.endsWith(d3clip.file))
    const items = [{ text: 'Kje je mama?', voice: 'grandma', source: 'scene:test', priority: 0 }]
    check('voice-build --upgrade: a Drama 3 clip is upgraded to ElevenLabs like a Gepard one', todo(items, vs, true).length === 1 && todo(items, vs, false).length === 0)
    check('voice-build --elevenlabs\'s queue: the speaker\'s Drama 3 clips with their Gepard ones', vs.localQueue(['grandma']).map(c => c.key).join() === d3clip.key)
    const e = await vs.voice('Kje je mama?', 'grandma', 'scene:test', { engines: ['elevenlabs'], replace: true })
    check('ElevenLabs replaces a Drama 3 clip: a new file, the hits kept, the Drama 3 file gone',
      e?.engine === 'elevenlabs' && e.file !== d3clip.file && e.hits === 4 && !existsSync(join(vs.filesDir, d3clip.file)), e)
    // the Drama 3 queue: a speaker's Gepard clips, not a narrator's, nor ElevenLabs', nor an explanation
    putClip(vs.database, vs.filesDir, { voice: 'girl', text: 'Mami, poglej!', engine: 'gepard', hits: 1 })
    putClip(vs.database, vs.filesDir, { voice: 'girl', text: 'Kje je muca?', engine: 'gepard', hits: 3 })
    putClip(vs.database, vs.filesDir, { voice: 'girl', text: 'Kruh je topel.', engine: 'elevenlabs' })
    putClip(vs.database, vs.filesDir, { voice: 'female', text: 'kruh', engine: 'gepard', hits: 9 })
    const q = vs.drama3Queue(['girl', 'female'])
    check('Drama 3\'s queue: the speaker\'s Gepard clips, most played first; never a narrator\'s, never an ElevenLabs clip',
      q.map(c => c.text).join(' | ') === 'Kje je muca? | Mami, poglej!', q.map(c => [c.voice, c.engine, c.text]))
    const d3 = new Drama3({ url: 'http://127.0.0.1:9' })
    check('the worker\'s voice: the cast speaker\'s own (the narrators and the characters alike)',
      d3.speakerOf('grandma') === 'grandma' && d3.speakerOf('young-man') === 'young-man' && d3.speakerOf('female') === 'female')
    check('a worker that is down: not up, its make() says why, nothing thrown', !(await d3.up()) && (await d3.make('Dober dan.', 'grandma')).rejection?.status === 0)
    vs.close()
  }

  // --- the estimate and the settings ---
  {
    const est = drama3Estimate(['Dober dan, Jan.', 'Midva sva na pašniku, onidve sta v kuhinji.'], 10)
    check('the estimate: the letters at Drama 3\'s pace, the measured seconds a second of audio',
      est.audio > 5 && est.audio < 8 && Math.abs(est.seconds - est.audio * 10) < 1e-6, est)
    const s = drama3Settings({ LANI_DRAMA3_HOME: '/nowhere', LANI_HEAVY_LOCK: '/tmp/x.lock', LANI_DRAMA3_MIN_FREE_GB: '24' })
    check('the settings: the worker\'s run.sh in its home (not installed there), the heavy lock, the memory floor, Drama 3\'s port 8785',
      s.cmd.join(' ') === '/nowhere/run.sh' && !s.installed && s.lock === '/tmp/x.lock' && s.minFreeGb === 24 && s.url === 'http://127.0.0.1:8785' && drama3Settings({}).minFreeGb === 20 && drama3Settings({}).lock === '/tmp/lani-heavy.lock')
    const mi = join(tempDir('lani-d3-mem-'), 'meminfo')
    writeFileSync(mi, 'MemTotal: 134217728 kB\nMemFree: 1 kB\nMemAvailable: 44040192 kB\n')
    check('the memory: MemAvailable (free -g\'s "available") in GB', availableGb(mi) === 42 && availableGb('/nonexistent') === undefined)
  }

  // --- voice-build --drama3 on a fake worker it starts and stops ---
  const root = tempDir('lani-d3-vb-')
  const data = join(root, 'data')
  const voiceDir = join(root, 'voice')
  mkdirSync(join(voiceDir, 'files'), { recursive: true })
  mkdirSync(data)
  const cultures = join(root, 'cultures')
  mkdirSync(join(cultures, 'primorska'), { recursive: true })
  cpSync(join(culturesDir, 'primorska/culture.json'), join(cultures, 'primorska/culture.json'))
  const empty = (n: string) => {
    const d = join(root, n)
    mkdirSync(d)
    return d
  }
  {
    const vs = new VoiceStore({ appDir: join(data, 'app'), dir: voiceDir, engines: [], queueDelayMs: 0, level: null })
    putClip(vs.database, vs.filesDir, { voice: 'grandma', text: 'Dober dan, Jan.', engine: 'gepard', hits: 5, engineVoice: 'nina' })
    putClip(vs.database, vs.filesDir, { voice: 'grandma', text: 'Midva sva doma.', engine: 'gepard', hits: 2, engineVoice: 'nina' })
    putClip(vs.database, vs.filesDir, { voice: 'grandma', text: 'Pridi k peči.', engine: 'gepard', created: '2026-10-11T10:00:00.000Z' })
    putClip(vs.database, vs.filesDir, { voice: 'grandma', text: 'Kruh je topel.', engine: 'gepard', created: '2026-10-12T10:00:00.000Z' })
    putClip(vs.database, vs.filesDir, { voice: 'girl', text: 'Mami, poglej!', engine: 'gepard', hits: 1 })
    putClip(vs.database, vs.filesDir, { voice: 'girl', text: 'Kje je muca?', engine: 'gepard', created: '2026-10-13T10:00:00.000Z' })
    putClip(vs.database, vs.filesDir, { voice: 'grandpa', text: 'Lahko noč.', engine: 'gepard' })
    putClip(vs.database, vs.filesDir, { voice: 'female', text: 'kruh', engine: 'gepard', hits: 9 })
    putClip(vs.database, vs.filesDir, { voice: 'teacher', text: 'Poslušajte.', engine: 'elevenlabs' })
    vs.close()
  }
  const events = join(root, 'events.jsonl')
  const meminfo = join(root, 'meminfo')
  const lock = join(root, 'heavy.lock')
  const workerLog = join(root, 'worker.log')
  const memory = (gb: number) => writeFileSync(meminfo, `MemTotal: 134217728 kB\nMemAvailable: ${gb * 2 ** 20} kB\n`)
  memory(64)
  const env: Record<string, string> = {
    ...(process.env as Record<string, string>),
    LANI_CULTURES_DIR: cultures, LANI_DATA_DIR: data, LANI_VOICE_CACHE: voiceDir, LANI_PACKS_DIR: empty('packs'), LANI_MODULES_DIR: empty('modules'),
    LANI_SCENARIOS_DIR: empty('scenarios'), LANI_SCENES_DIR: empty('scenes'), LANI_DRILLS_DIR: empty('drills'), LANI_GRAMMAR_DIR: empty('grammar'),
    LANI_VILLAGERS_DIR: empty('villagers'), LANI_KEYS_FILE: '/nonexistent', ELEVENLABS_API_KEY: '', LANI_GEPARD_URL: 'http://127.0.0.1:9',
    LANI_DRAMA3_URL: `http://127.0.0.1:${d3Port}`, LANI_DRAMA3_CMD: `bun ${fake}`, LANI_HEAVY_LOCK: lock, LANI_DRAMA3_LOG: workerLog,
    LANI_DRAMA3_MEMINFO: meminfo, LANI_DRAMA3_PAUSE_POLL_S: '0.2', LANI_DRAMA3_PAUSE_STOP_S: '0.4', LANI_DRAMA3_RTF: '10',
    FAKE_DRAMA3_LOG: events, FAKE_DRAMA3_FAIL: 'Midva', FAKE_DRAMA3_WAIT_MS: '500',
  }
  const spawnVb = (args: string[], extra: Record<string, string> = {}) => Bun.spawn(['bun', voiceBuildBin, ...args], { env: { ...env, ...extra }, stdout: 'pipe', stderr: 'pipe' })
  const voiceBuild = async (args: string[], extra: Record<string, string> = {}) => {
    const p = spawnVb(args, extra)
    const [stdout, stderr, exitCode] = await Promise.all([new Response(p.stdout).text(), new Response(p.stderr).text(), p.exited])
    return { stdout, stderr, exitCode }
  }
  const evs = () => (existsSync(events) ? readFileSync(events, 'utf8').trim().split('\n').filter(Boolean).map(l => JSON.parse(l)) : []) as { start?: { lock: string | null }; synth?: { text: string; speaker: string }; stopped?: boolean }[]
  const rows = () => {
    const db = new Database(join(voiceDir, 'voice.db'), { readonly: true })
    try {
      return Object.fromEntries((db.query('SELECT * FROM clips').all() as Clip[]).map(c => [c.key, c]))
    } finally {
      db.close()
    }
  }
  const before = rows()
  const key = (voice: string, text: string) => clipKey(normalizeText(text), voice)
  const workerUp = async () => {
    try {
      return (await fetch(`http://127.0.0.1:${d3Port}/health`, { signal: AbortSignal.timeout(500) })).status > 0
    } catch {
      return false
    }
  }

  // the dry run
  const dry = await voiceBuild(['--drama3', 'grandma,girl', '--json'])
  const plan = JSON.parse(dry.stdout || '{}')
  check('voice-build --drama3 (dry run): the speakers\' Gepard clips (not the narrator\'s, not an ElevenLabs clip, not the other speakers\'), by voice, an estimate, the worker not running yet but installed, the heavy lock; nothing started, nothing written',
    dry.exitCode === 0 && plan.local === 6 && plan.by_voice?.grandma?.clips === 4 && plan.by_voice?.girl?.clips === 2 && plan.would?.clips === 6 && plan.would.seconds > 0 && plan.rtf === 10 &&
      plan.worker?.up === false && plan.worker.installed === true && plan.lock === lock && plan.failed_before === 0 && plan.clips?.[0]?.text === 'Dober dan, Jan.' && !existsSync(events) && JSON.stringify(rows()) === JSON.stringify(before),
    [dry.stderr.slice(-400), plan])
  const human = await voiceBuild(['--drama3', 'all'])
  check('voice-build --drama3 all (dry run): every speaker but the narrators, the time it would take, how to run it, Built with Fish Audio',
    human.exitCode === 0 && human.stdout.includes('grandpa') && human.stdout.includes('7 (') && /--run would make 7 with Drama 3/.test(human.stdout) && human.stdout.includes('Make them with: companion/bin/voice-build --drama3 all --run') && human.stdout.includes('Built with Fish Audio') && human.stdout.includes(lock),
    human.stdout)
  check('voice-build --drama3 refuses the narrators, a speaker not in the cast, --max-chars, and --retry-failed without it',
    (await voiceBuild(['--drama3', 'female'])).exitCode === 2 && (await voiceBuild(['--drama3', 'grandma,soprano'])).exitCode === 2 &&
      (await voiceBuild(['--drama3', 'grandma', '--max-chars', '5'])).exitCode === 2 && (await voiceBuild(['--retry-failed'])).exitCode === 2 && (await voiceBuild(['--drama3'])).exitCode === 2)
  const missing = await voiceBuild(['--drama3', 'grandma', '--run'], { LANI_DRAMA3_CMD: join(root, 'no/run.sh') })
  check('voice-build --drama3 --run without the worker installed: says so, changes nothing', missing.exitCode === 1 && missing.stderr.includes('not installed') && JSON.stringify(rows()) === JSON.stringify(before), missing.stderr)

  // the run: the worker started (it waits for the heavy lock first), two clips, stopped after
  const r1 = await voiceBuild(['--drama3', 'grandma,girl', '--run', '--max', '2'])
  const after1 = rows()
  const made1 = after1[key('grandma', 'Dober dan, Jan.')]
  const e1 = evs()
  check('voice-build --drama3 --run --max 2: the worker started with the heavy lock (it waited for it), the two most played asked for in the speaker\'s voice, stopped after',
    r1.exitCode === 0 && e1[0]?.start?.lock === lock && r1.stdout.includes('the worker waits for the heavy lock') && e1.filter(e => e.synth).map(e => `${e.synth!.speaker}:${e.synth!.text}`).join(' | ') === 'grandma:Dober dan, Jan. | grandma:Midva sva doma.' && e1.at(-1)?.stopped === true && !(await workerUp()),
    [r1.stdout, r1.stderr.slice(-600), e1])
  check('…the take stored as Drama 3\'s: the worker\'s voice recorded, a new file, the hits kept, the Gepard file gone' + (hasFfmpeg ? ', trimmed and levelled to −18 LUFS' : ''),
    made1?.engine === 'drama3' && made1.engine_voice === 'grandma/grandma-2' && made1.file !== before[key('grandma', 'Dober dan, Jan.')].file && made1.hits === 5 && !existsSync(join(voiceDir, 'files', before[key('grandma', 'Dober dan, Jan.')].file)) && existsSync(join(voiceDir, 'files', made1.file)) && (!hasFfmpeg || (made1.level === -18 && made1.edges === 1)),
    made1)
  check('…the line no take passed: reported with its reasons, its Gepard clip kept',
    after1[key('grandma', 'Midva sva doma.')]?.engine === 'gepard' && after1[key('grandma', 'Midva sva doma.')].file === before[key('grandma', 'Midva sva doma.')].file && r1.stdout.includes('Lines no take passed (1') && r1.stdout.includes('text: sva → smo') && /Made 1 with Drama 3.*1 failed every take/.test(r1.stdout),
    r1.stdout)

  // the resume, with a memory pause in the middle: the worker stopped while it lasts, started again after
  const p2 = spawnVb(['--drama3', 'grandma,girl', '--run'], { FAKE_DRAMA3_LOW_AFTER: '1', FAKE_DRAMA3_WAIT_MS: '0' })
  const out2 = new Response(p2.stdout).text()
  const started = Date.now()
  // once the worker is stopped for the pause, the memory comes back
  while (Date.now() - started < 20_000 && evs().filter(e => e.stopped).length < 2 && p2.exitCode === null) await Bun.sleep(100)
  memory(64)
  await p2.exited
  const o2 = await out2
  const e2 = evs().slice(e1.length)
  const after2 = rows()
  check('the next --run goes on where it stopped: the four left made (the failed one not tried again), a memory pause in between (the worker stopped while it lasted and started again), stopped at the end',
    p2.exitCode === 0 && [['grandma', 'Pridi k peči.'], ['grandma', 'Kruh je topel.'], ['girl', 'Mami, poglej!'], ['girl', 'Kje je muca?']].every(([v, t]) => after2[key(v, t)]?.engine === 'drama3') &&
      !e2.some(e => e.synth?.text.includes('Midva')) && e2.filter(e => e.start).length === 2 && e2.filter(e => e.stopped).length === 2 && e2.at(-1)?.stopped === true &&
      o2.includes('Paused: 1.0 GB available') && o2.includes('the worker is stopped for now') && o2.includes('Resumed') && /Made 4 with Drama 3/.test(o2) && !(await workerUp()),
    [o2, e2])
  const r3 = await voiceBuild(['--drama3', 'grandma,girl', '--run'])
  const dry3 = JSON.parse((await voiceBuild(['--drama3', 'grandma,girl', '--json'])).stdout || '{}')
  const retry3 = JSON.parse((await voiceBuild(['--drama3', 'grandma,girl', '--json', '--retry-failed'])).stdout || '{}')
  check('…then nothing left but the line that failed: no worker started; --retry-failed would try it again',
    r3.exitCode === 0 && r3.stdout.includes('Nothing to make') && evs().length === e1.length + e2.length && dry3.local === 0 && dry3.failed_before === 1 && dry3.failed?.[0]?.reasons?.length === 4 && retry3.local === 1 && retry3.clips?.[0]?.text === 'Midva sva doma.',
    [r3.stdout, dry3, retry3])
  const untouched = [key('female', 'kruh'), key('grandpa', 'Lahko noč.'), key('teacher', 'Poslušajte.')]
  check('…the narrator\'s, the other speaker\'s and the ElevenLabs clip untouched', untouched.every(k => !!before[k] && JSON.stringify(rows()[k]) === JSON.stringify(before[k])), untouched)

  // Ctrl-C while the worker waits for the lock: twice stops it at once
  memory(64)
  const p4 = spawnVb(['--drama3', 'grandpa', '--run'], { FAKE_DRAMA3_WAIT_MS: '60000' })
  const out4 = new Response(p4.stdout).text()
  const n4 = evs().length
  const t4 = Date.now()
  while (Date.now() - t4 < 15_000 && !evs().slice(n4).some(e => e.start)) await Bun.sleep(100)
  await Bun.sleep(300)
  p4.kill('SIGINT')
  await Bun.sleep(200)
  p4.kill('SIGINT')
  await p4.exited
  check('Ctrl-C twice: the worker stopped at once, the clip left for the next run', p4.exitCode === 130 && evs().slice(n4).at(-1)?.stopped === true && rows()[key('grandpa', 'Lahko noč.')]?.engine === 'gepard' && !(await workerUp()), [await out4, evs().slice(n4)])
  appendFileSync(events, '') // keep the file for a look after a failure
}
