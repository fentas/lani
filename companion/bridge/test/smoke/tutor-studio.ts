// Smoke checks: the app tutor's studio on the bridge (src/tutor-studio.ts, src/overrides.ts, docs/plans/05-built/
// studio.md): the file tools over the learner's data folder (paths that leave it, links, private files and the six
// databases refused; writes only in the content areas, each checked by its kind's validator first: refused with the
// validator's message, nothing written, then accepted; str_replace, insert, view ranges, search), POST /tutor/check, the
// studio's jobs, the notebook's versions, the reports, the local overrides of curated content (recorded at a publish,
// retired when the curated file changes, the report resolved, the channel told), and the lease refusing a file write.
import { existsSync, mkdirSync, readFileSync, rmSync, symlinkSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { Overrides } from '../../src/overrides'
import { auth, base, channelEvents, check, dataDir, modulesDir, tempDir } from './harness'

const post = (path: string, body: unknown, headers: Record<string, string> = auth) => fetch(`${base}${path}`, { method: 'POST', headers, body: JSON.stringify(body) })
const put = (path: string, body: unknown) => fetch(`${base}${path}`, { method: 'PUT', headers: auth, body: JSON.stringify(body) })
const del = (path: string) => fetch(`${base}${path}`, { method: 'DELETE', headers: auth })
const get = (path: string) => fetch(`${base}${path}`, { headers: auth })
const call = async (name: string, args: Record<string, unknown>, client_id?: string) => {
  const r = await post(`/tutor/tools/${name}`, { args, ...(client_id ? { client_id } : {}) })
  const body: any = await r.json()
  return { status: r.status, body, text: String(body?.content?.[0]?.text ?? ''), error: body?.isError === true }
}
const edit = (args: Record<string, unknown>) => call('str_replace_based_edit_tool', args)

/** A module the validator takes, pretty-printed (one field a line at the top, for insert's line numbers). */
const moduleText = (version: number, title = 'Studio smoke drill') =>
  JSON.stringify(
    {
      schema: 'lani.module/v0',
      id: 'smoke-studio',
      version,
      title,
      level: 'A1',
      exercises: [
        { type: 'choice', prompt: 'Which is polite?', options: ['Živjo', 'Dober dan'], answer: 1 },
        { type: 'cloze', text: 'Jaz ___ Jan.', accept: ['sem'] },
      ],
    },
    null,
    2,
  )

export default async function tutorStudio() {
  const session: any = await (await get('/tutor/session')).json()
  const names = (session.app_tools ?? []).map((t: any) => t.name)
  check("the file tools are the app session's own (str_replace_based_edit_tool, search), with the app's specs", names.includes('str_replace_based_edit_tool') && names.includes('search') && !(session.tools ?? []).some((t: any) => t.name === 'search' || t.name === 'str_replace_based_edit_tool') && session.app_tools.find((t: any) => t.name === 'str_replace_based_edit_tool')?.inputSchema?.properties?.command?.enum?.length === 4, names)

  // --- paths: nothing outside the data folder, no links, nothing private --------------------------------------------
  const outside = tempDir('lani-smoke-outside-')
  writeFileSync(join(outside, 'secret.txt'), 'outside the data folder')
  mkdirSync(join(dataDir, 'app'), { recursive: true })
  rmSync(join(dataDir, 'app/link-out'), { force: true })
  symlinkSync(outside, join(dataDir, 'app/link-out'))
  const escapes = await Promise.all([
    edit({ command: 'view', path: '../outside.txt' }),
    edit({ command: 'view', path: 'app/../../etc/passwd' }),
    edit({ command: 'view', path: 'app/%2e%2e/%2e%2e/etc/passwd' }),
    edit({ command: 'view', path: 'app/link-out/secret.txt' }),
    edit({ command: 'create', path: 'app/link-out/new.json', file_text: '{}' }),
    edit({ command: 'view', path: '~/x' }),
  ])
  check('a path with "..", "%", "~" or through a link is refused', escapes.every(r => r.error) && /no "\.\."/.test(escapes[0].text) && /"%"/.test(escapes[2].text) && /is a link/.test(escapes[3].text) && !existsSync(join(outside, 'new.json')), escapes.map(r => r.text))
  const abs = await edit({ command: 'view', path: '/etc/passwd' })
  check('an absolute path is the data folder\'s ("/" is its root): /etc/passwd is not there', abs.error && !abs.text.includes('root:') && abs.text.includes('no such file'), abs.text)
  rmSync(join(dataDir, 'app/link-out'), { force: true })
  writeFileSync(join(dataDir, 'app/lease-free-marker.json'), '{}') // a plain file: readable
  const privates = await Promise.all([edit({ command: 'view', path: 'app/bridge-key.pem' }), edit({ command: 'view', path: 'app/lease.json' }), edit({ command: 'view', path: 'app/bridge-token' }), edit({ command: 'view', path: '.git/config' })])
  check('private files are out of reach (a .pem, the lease, the bridge token, .git)', privates.every(r => r.error && /private/.test(r.text)), privates.map(r => r.text))
  const root = await edit({ command: 'view', path: '/' })
  check("view / lists the data folder: the six databases, app/, no private file", !root.error && root.text.includes('learner-profile.json') && root.text.includes('app/') && !root.text.includes('bridge-key.pem') && !root.text.includes('tutor-inbox.json'), root.text.slice(0, 400))

  // --- writes: only the content areas, checked first -------------------------------------------------------------------
  const db = await edit({ command: 'create', path: 'learner-profile.json', file_text: '{}' })
  check('the six databases are read-only to the file tools (record_session and card write them)', db.error && db.text.startsWith('Nothing written.') && /read-only/.test(db.text) && JSON.parse(readFileSync(join(dataDir, 'learner-profile.json'), 'utf8')).learner?.name === 'Jan', db.text)
  const elsewhere = await edit({ command: 'create', path: 'app/notes.md', file_text: 'hi' })
  check('a file outside the content areas is refused, naming them', elsewhere.error && /content areas/.test(elsewhere.text) && !existsSync(join(dataDir, 'app/notes.md')), elsewhere.text)
  const bad = JSON.parse(moduleText(1))
  bad.exercises[0].answer = 5
  const refused = await edit({ command: 'create', path: 'app/modules/smoke-studio/v1.json', file_text: JSON.stringify(bad, null, 2) })
  check("a module the validator refuses: the validator's message, nothing written", refused.error && /answer index out of range/.test(refused.text) && !existsSync(join(dataDir, 'app/modules/smoke-studio/v1.json')), refused.text)
  const notJson = await edit({ command: 'create', path: 'app/modules/smoke-studio/v1.json', file_text: '{"schema": ' })
  check('…and text that is not JSON', notJson.error && /not JSON/.test(notJson.text), notJson.text)
  const created = await edit({ command: 'create', path: 'app/modules/smoke-studio/v1.json', file_text: moduleText(1) })
  check('…then fixed: written, checked by its validator', !created.error && /created app\/modules\/smoke-studio\/v1\.json: a module \(smoke-studio\)/.test(created.text) && existsSync(join(dataDir, 'app/modules/smoke-studio/v1.json')), created.text)
  const wrongVersion = await edit({ command: 'create', path: 'app/modules/smoke-studio/v2.json', file_text: moduleText(1) })
  check('a module version file holds its own version', wrongVersion.error && /v2\.json holds "version": 2/.test(wrongVersion.text), wrongVersion.text)
  const since = (await (await get('/events/backlog?since=0')).json()).at(-1)?.id ?? 0
  const current = await edit({ command: 'create', path: 'app/modules/smoke-studio/current.json', file_text: '{"version": 1}' })
  const served: any = await (await get('/modules/smoke-studio')).json()
  await Bun.sleep(100)
  const told = ((await (await get(`/events/backlog?since=${since}`)).json()) as any[]).some(e => e.event?.type === 'module_published' && e.event.id === 'smoke-studio')
  check('current.json written: the app serves the module and hears of it (module_published)', !current.error && served.version === 1 && served.title === 'Studio smoke drill' && told, { current: current.text, served: served.title, told })
  const missing = await edit({ command: 'create', path: 'app/modules/smoke-studio/current.json', file_text: '{"version": 7}' })
  check("…a current.json naming a version that isn't there is refused", missing.error && /v7\.json isn't there/.test(missing.text), missing.text)

  // --- str_replace, insert, view ---------------------------------------------------------------------------------------
  const replaced = await edit({ command: 'str_replace', path: 'app/modules/smoke-studio/v1.json', old_str: '"title": "Studio smoke drill"', new_str: '"title": "Studio smoke drill (edited)"' })
  check('str_replace: one occurrence replaced, checked again', !replaced.error && JSON.parse(readFileSync(join(dataDir, 'app/modules/smoke-studio/v1.json'), 'utf8')).title === 'Studio smoke drill (edited)', replaced.text)
  const nowhere = await edit({ command: 'str_replace', path: 'app/modules/smoke-studio/v1.json', old_str: 'not in the file', new_str: 'x' })
  const twice = await edit({ command: 'str_replace', path: 'app/modules/smoke-studio/v1.json', old_str: '"type"', new_str: '"kind"' })
  check('…not there, or more than once: refused, saying so', nowhere.error && /nowhere/.test(nowhere.text) && twice.error && /2 times/.test(twice.text), [nowhere.text, twice.text])
  const breaking = await edit({ command: 'str_replace', path: 'app/modules/smoke-studio/v1.json', old_str: '"answer": 1', new_str: '"answer": 9' })
  check('…a replacement that breaks it is refused by the validator, the file unchanged', breaking.error && /answer index out of range/.test(breaking.text) && JSON.parse(readFileSync(join(dataDir, 'app/modules/smoke-studio/v1.json'), 'utf8')).exercises[0].answer === 1, breaking.text)
  const inserted = await edit({ command: 'insert', path: 'app/modules/smoke-studio/v1.json', insert_line: 1, insert_text: '  "tags": ["inserted"],' })
  check('insert: a line after insert_line, checked', !inserted.error && JSON.parse(readFileSync(join(dataDir, 'app/modules/smoke-studio/v1.json'), 'utf8')).tags?.[0] === 'inserted', inserted.text)
  const ranged = await edit({ command: 'view', path: 'app/modules/smoke-studio/v1.json', view_range: [2, 3] })
  check('view with view_range: those lines, numbered', !ranged.error && ranged.text.split('\n').length === 2 && /^\s+2\t {2}"tags"/.test(ranged.text) && /^\s+3\t/m.test(ranged.text), ranged.text)
  const whole = await edit({ command: 'view', path: 'app/modules/smoke-studio/v1.json', view_range: [1, -1] })
  check('…[1, -1] to the end', !whole.error && whole.text.includes('"exercises"') && whole.text.trim().endsWith('}'), whole.text.slice(-80))
  const dir = await edit({ command: 'view', path: 'app/modules' })
  check('view of a directory: its entries, directories with a trailing /', !dir.error && dir.text.includes('smoke-studio/') && dir.text.includes('v1.json'), dir.text.slice(0, 300))
  const history = existsSync(join(dataDir, 'app/tutor/file-history')) && readFileSync(join(dataDir, '.gitignore'), 'utf8').includes('/app/tutor/file-history/')
  check('the old texts are kept in app/tutor/file-history/ (kept out of git)', history)

  // --- search -------------------------------------------------------------------------------------------------------
  const found = await call('search', { pattern: 'studio smoke drill', path: 'app/modules' })
  check('search: path:line: the line, case-insensitive', !found.error && /^app\/modules\/smoke-studio\/v1\.json:\d+: .*Studio smoke drill/m.test(found.text), found.text)
  const rx = await call('search', { pattern: '"answer":\\s*\\d', regex: true, path: 'app/modules/smoke-studio' })
  check('search: regex: true for a regular expression', !rx.error && /v1\.json:\d+:/.test(rx.text), rx.text)
  const secret = await call('search', { pattern: 'PRIVATE KEY' })
  check('search never reads a private file (the bridge key)', !secret.error && !secret.text.includes('bridge-key.pem'), secret.text.slice(0, 200))
  const badRx = await call('search', { pattern: '(', regex: true })
  check('search: a broken regular expression says so', badRx.error && /not a regular expression/.test(badRx.text), badRx.text)

  // --- POST /tutor/check ------------------------------------------------------------------------------------------------
  const okCheck: any = await (await post('/tutor/check', { path: 'app/modules/smoke-studio/v1.json', text: moduleText(1) })).json()
  const kindCheck: any = await (await post('/tutor/check', { kind: 'module', text: JSON.stringify(bad) })).json()
  const areaCheck: any = await (await post('/tutor/check', { path: 'app/notes.md', text: '{}' })).json()
  const noWhat = await post('/tutor/check', { text: '{}' })
  check('POST /tutor/check: ok for a good file, the kind from its path', okCheck.ok === true && okCheck.kind === 'module' && okCheck.id === 'smoke-studio', okCheck)
  check('…the validator\'s errors for a bad one (by kind)', kindCheck.ok === false && kindCheck.kind === 'module' && /answer index out of range/.test(kindCheck.errors), kindCheck)
  check('…a path outside the content areas, and neither kind nor path (400)', areaCheck.ok === false && /content areas/.test(areaCheck.errors) && noWhat.status === 400, [areaCheck, noWhat.status])

  // --- the studio's jobs ------------------------------------------------------------------------------------------------
  const job = { schema: 'lani.studio-job/v1', id: 'j_smoke01', title: 'Smoke job', status: 'running', urgency: 'now', origin: 'request', created_at: Date.now(), updated_at: Date.now(), published: [], messages: [{ role: 'user', content: 'x'.repeat(1000) }], steps: [{ at: 1 }] }
  const saved: any = await (await put('/tutor/jobs/j_smoke01', job)).json()
  const one: any = await (await get('/tutor/jobs/j_smoke01')).json()
  const listed: any = await (await get('/tutor/jobs')).json()
  const summary = listed.jobs?.find((j: any) => j.id === 'j_smoke01')
  check('PUT /tutor/jobs/:id saves a job, GET gives it back whole', saved.ok && Number.isFinite(saved.updated_at) && one.messages?.length === 1 && one.title === 'Smoke job', { saved, one: one.title })
  check('GET /tutor/jobs: the summaries, without the thread and the steps', !!summary && summary.messages === undefined && summary.steps === undefined && summary.status === 'running', summary)
  check('the jobs are kept out of git', readFileSync(join(dataDir, '.gitignore'), 'utf8').includes('/app/tutor/jobs/'))
  const wrong = await Promise.all([put('/tutor/jobs/j_smoke01', { ...job, schema: 'x' }), put('/tutor/jobs/j_smoke01', { ...job, id: 'j_other1' }), put('/tutor/jobs/BAD', job)])
  check('a job without its schema, with another id, or a bad id: 400', wrong.every(r => r.status === 400), wrong.map(r => r.status))
  await put('/tutor/jobs/j_smoke02', { ...job, id: 'j_smoke02', status: 'done', updated_at: Date.now() - 8 * 86_400_000 })
  const pruned: any = await (await get('/tutor/jobs')).json()
  check('an ended job older than a week is pruned from the list', !pruned.jobs.some((j: any) => j.id === 'j_smoke02') && (await get('/tutor/jobs/j_smoke02')).status === 404, pruned.jobs.map((j: any) => j.id))
  const gone: any = await (await del('/tutor/jobs/j_smoke01')).json()
  check('DELETE /tutor/jobs/:id', gone.ok && gone.deleted === true && (await get('/tutor/jobs/j_smoke01')).status === 404, gone)

  // --- the notebook's versions ---------------------------------------------------------------------------------------
  await call('notebook_write', { text: '# Notebook\n- version A (smoke)' })
  await call('notebook_write', { text: '# Notebook\n- version B (smoke)' })
  const versions: any = await (await get('/tutor/notebook/versions')).json()
  const a = versions.versions?.find((v: any) => v.text.includes('version A (smoke)'))
  check('GET /tutor/notebook/versions: the old texts, newest first, with when and why', !!a && typeof a.at === 'string' && a.why && a.chars === a.text.length && versions.versions[0].text.includes('version A (smoke)'), versions.versions?.slice(0, 2))
  const restored: any = await (await post('/tutor/notebook/restore', { at: a.at })).json()
  const now: any = await (await get('/tutor/notebook')).json()
  const after: any = await (await get('/tutor/notebook/versions')).json()
  check('POST /tutor/notebook/restore: that text is the notebook again, the one before kept', restored.text.includes('version A (smoke)') && now.text.includes('version A (smoke)') && now.why === `restored (${a.at})` && after.versions[0].text.includes('version B (smoke)'), { restored: restored.text, why: now.why })
  check('…an unknown version is a 404', (await post('/tutor/notebook/restore', { at: '1999-01-01T00:00:00.000Z' })).status === 404)

  // --- reports ----------------------------------------------------------------------------------------------------------
  const rep: any = await (await post('/tutor/reports', { title: 'Fix: module curated-demo, the polite greeting', kind: 'fix', item: { kind: 'module', module_id: 'curated-demo' }, url: 'https://github.com/fentas/lani/discussions/9999', number: 9999, state: 'posted', via: 'discussion' })).json()
  const again: any = await (await post('/tutor/reports', { title: 'Fix: module curated-demo, the polite greeting', kind: 'fix', item: { kind: 'module', module_id: 'curated-demo' }, url: 'https://github.com/fentas/lani/discussions/9999', number: 9999, state: 'upvoted', via: 'discussion' })).json()
  const reps: any = await (await get('/tutor/reports')).json()
  check('POST /tutor/reports keeps a report in app/reports.json; the same url updates it', rep.ok && again.report.state === 'upvoted' && reps.reports.filter((r: any) => r.url === 'https://github.com/fentas/lani/discussions/9999').length === 1, reps)
  check('…a report without its fields is a 400', (await post('/tutor/reports', { title: 'x' })).status === 400)

  // --- a local override of a curated module: recorded, then retired when the curated file changes -----------------
  const curatedFile = join(modulesDir, 'curated-demo.json')
  const curatedText = readFileSync(curatedFile, 'utf8')
  const fixed = await call('publish_module', { spec: { ...JSON.parse(curatedText), title: 'Curated demo (the learner\'s fix)' }, note: 'a local fix' })
  const reg: any = await (await get('/tutor/overrides')).json()
  const entry = reg.items?.find((e: any) => e.kind === 'module' && e.id === 'curated-demo' && e.state === 'active')
  check("an override recorded: the tutor's module under a curated id, the curated file's hash", !fixed.error && !!entry && /^[0-9a-f]{64}$/.test(entry.hash) && entry.curated.endsWith('curated-demo.json'), reg)
  const heard = channelEvents.length
  writeFileSync(curatedFile, JSON.stringify({ ...JSON.parse(curatedText), title: 'Curated demo (fixed upstream)' }))
  try {
    const reg2: any = await (await get('/tutor/overrides')).json()
    const retired = reg2.items?.find((e: any) => e.kind === 'module' && e.id === 'curated-demo' && e.state === 'retired')
    await Bun.sleep(200)
    const served2: any = await (await get('/modules/curated-demo')).json()
    const reps2: any = await (await get('/tutor/reports')).json()
    const said = channelEvents.slice(heard).filter(n => n.method === 'notifications/claude/channel' && n.params?.meta?.kind === 'override_retired')
    check('the curated file changed: the override retired, its versions moved aside, the curated module served again', !!retired && retired.moved_to?.startsWith('overrides/retired/module/curated-demo-') && existsSync(join(dataDir, 'app', retired.moved_to, 'current.json')) && !existsSync(join(dataDir, 'app/modules/curated-demo')) && served2.version === 1, { retired, served: served2.version })
    check('…the report naming it resolved', reps2.reports.find((r: any) => r.url === 'https://github.com/fentas/lani/discussions/9999')?.state === 'resolved', reps2.reports)
    check('…and the channel told (kind override_retired, the report\'s link)', said.length === 1 && String(said[0].params.content).includes('curated-demo') && String(said[0].params.content).includes('discussions/9999'), said)
  } finally {
    writeFileSync(curatedFile, curatedText)
  }

  // the registry's rules on the file layout (scenes, stories, villagers): recorded, retired, dropped
  const cur = tempDir('lani-smoke-ovc-')
  const tut = tempDir('lani-smoke-ovt-')
  const app = tempDir('lani-smoke-ova-')
  writeFileSync(join(cur, 'kuhinja.json'), JSON.stringify({ id: 'kuhinja', v: 1 }))
  writeFileSync(join(cur, 'other.json'), JSON.stringify({ id: 'trg', v: 1 }))
  writeFileSync(join(tut, 'kuhinja.json'), JSON.stringify({ id: 'kuhinja', v: 2 }))
  writeFileSync(join(tut, 'mine.json'), JSON.stringify({ id: 'mine' }))
  const named = { dirs: () => ({ curated: [cur], tutor: tut }), layout: 'file' as const }
  const none = { dirs: () => ({ curated: [], tutor: join(app, 'none') }), layout: 'file' as const }
  const gave: string[] = []
  const ov = new Overrides({ appDir: app, projectDir: cur, log: () => {}, sources: { scene: named, module: { ...none, layout: 'dir' }, story: none, villager: none }, onRetired: e => void gave.push(e.id) })
  const first = ov.check()
  check('overrides (files): a tutor scene under a curated id is recorded, one of its own is not', first.recorded.length === 1 && first.recorded[0].id === 'kuhinja' && first.recorded[0].curated === 'kuhinja.json', first)
  check('…a second check changes nothing', JSON.stringify(ov.check()) === JSON.stringify({ recorded: [], retired: [], dropped: [] }))
  writeFileSync(join(cur, 'kuhinja.json'), JSON.stringify({ id: 'kuhinja', v: 3 }))
  const second = ov.check()
  check('…the curated file changes: retired, the tutor file moved aside, told', second.retired.length === 1 && !existsSync(join(tut, 'kuhinja.json')) && existsSync(join(app, second.retired[0].moved_to!)) && gave.join() === 'kuhinja', second)
  writeFileSync(join(tut, 'trg.json'), JSON.stringify({ id: 'trg', v: 2 }))
  ov.check()
  rmSync(join(tut, 'trg.json'))
  const third = ov.check()
  check('…a tutor item removed (remove_scene): its record dropped', third.dropped.length === 1 && third.dropped[0].id === 'trg' && !ov.read().items.some(e => e.id === 'trg'), third)

  // --- the lease: a file write refused while the phone holds the data, a view goes on -----------------------------
  const leaseFile = join(dataDir, 'app/lease.json')
  const leaseBefore = existsSync(leaseFile) ? readFileSync(leaseFile, 'utf8') : null
  const stamp = new Date().toISOString().replace(/\.\d+Z$/, 'Z')
  writeFileSync(leaseFile, JSON.stringify({ schema: 'lani.lease/v1', holder: { side: 'phone', device: 'p_0123abcd', name: 'Pixel 8', mode: 'hosted' }, offer: null, since: stamp, renewed: stamp, expires: new Date(Date.now() + 14 * 86_400_000).toISOString().replace(/\.\d+Z$/, 'Z') }))
  try {
    const blocked = await edit({ command: 'create', path: 'app/modules/smoke-studio/v1.json', file_text: moduleText(1, 'under the lease') })
    const viewing = await edit({ command: 'view', path: 'app/modules/smoke-studio/v1.json' })
    const searching = await call('search', { pattern: 'smoke' , path: 'app/modules' })
    const checking = await post('/tutor/check', { kind: 'module', text: moduleText(1) })
    check("lease: the file tools' writes fail while the phone holds the data, saying why; view, search and /tutor/check go on", blocked.error && blocked.text.startsWith('Nothing written.') && blocked.text.includes('Pixel 8') && !viewing.error && !searching.error && checking.status === 200 && !readFileSync(join(dataDir, 'app/modules/smoke-studio/v1.json'), 'utf8').includes('under the lease'), [blocked.text, viewing.error, checking.status])
    check('lease: PUT /tutor/jobs is refused (423), GET goes on', (await put('/tutor/jobs/j_smoke03', { ...job, id: 'j_smoke03' })).status === 423 && (await get('/tutor/jobs')).status === 200)
  } finally {
    if (leaseBefore === null) rmSync(leaseFile, { force: true })
    else writeFileSync(leaseFile, leaseBefore)
  }
  rmSync(join(dataDir, 'app/lease-free-marker.json'), { force: true })
}
