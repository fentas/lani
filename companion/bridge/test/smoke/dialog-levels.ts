// Smoke checks: dialogs by level (companion/SCENES.md, "Dialogs by level"): a dialog's and a happening's `level`, the
// validator's rules on them, what an app before the levels gets (arts 7: none of them), the dialog an older app plays, the
// tutor's ask once the learner's own dialogs are all heard, and a levelled variant the tutor publishes.
import { existsSync, readdirSync, readFileSync, unlinkSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { DIALOG_LEVELS, dialogLevel, forApp, levelPageErrors, levelRank, resolveScene, SCENE_ARTS_VERSION, validateScene, withoutLevels, type LoadedScene } from '../../src/scenes'
import { allHeard, variantNote } from '../../src/variants'
import { auth, base, check, client, scenesDir } from './harness'

/** A tool result's text. */
const text = (r: any) => (r.content ?? []).map((c: any) => c.text ?? '').join('\n')
const langs = (sl: string, en: string) => ({ sl, en })

/** A dialog of Luka's at the brook: his line, a turn (a right and a wrong choice), his line; at [level] when given. */
function talk(id: string, first: string, level?: string) {
  return {
    id,
    ...(level ? { level } : {}),
    lines: [
      { who: 'luka', ...langs(first, first) },
      { choices: [
        { ...langs('Daj mu vodo.', 'Give it water.'), ok: true, reply: langs('Saj mu jo dajem!', "I'm giving it some!") },
        { ...langs('Daj mu voda.', 'Give it water.'), why: 'Dati takes the accusative: vodo.', grammar: 'tozilnik', reply: langs('Voda? Kaj z vodo?', 'Voda? What about the water?') },
      ] },
      { who: 'luka', ...langs('Glej, pije!', "Look, it's drinking!") },
    ],
  }
}

/** Luka by the brook: the old dialogs for everyone, two B1 and two B2 variants, and a B2 talk of its own (a happening at B2). */
function brook() {
  return {
    schema: 'lani.scene/v0', id: 'smoke-ravni', title: 'Ob potoku · By the stream', emoji: '🐑', level: 'A1', art: 'stream', from: ['spot:riverbank'],
    people: [{ id: 'luka', name: 'Pastir Luka', emoji: '🐑', art: 'shepherd', slot: 'bank', villager: 'luka' }],
    happenings: [
      { id: 'ovce', title: 'Luka napaja ovce · Luka is watering the sheep', who: 'luka', when: ['evening'], chance: 1, dialog: 'ovce', dialogs: ['ovce-b1', 'ovce-b1-2', 'ovce-b2', 'ovce-b2-2', 'ovce-2', 'ovce'], reward: { wood: 15 }, marker: '🐑', memory: langs('ovce', 'the sheep') },
      { id: 'sejem', title: 'Luka o sejmu · Luka on the fair', who: 'luka', when: ['morning'], chance: 1, level: 'B2', dialog: 'sejem', reward: { wood: 5 }, marker: '🐑', memory: langs('sejem', 'the fair') },
    ],
    dialogs: [talk('ovce', 'Ovce so žejne.'), talk('sejem', 'Na sejmu so cene zrasle, ker je bila suša.')],
    variants: [
      talk('ovce-2', 'Jagnje noče piti.'),
      talk('ovce-b1', 'Če bi deževalo, bi bile ovce na paši.', 'B1'),
      talk('ovce-b1-2', 'Rekel sem ti, da bo jagnje ozdravelo.', 'B1'),
      talk('ovce-b2', 'Ko bi bil vedel, bi bil ovce prej pripeljal.', 'B2'),
      talk('ovce-b2-2', 'Ovce, ki jih je pasel moj ded, so bile drugačne.', 'B2'),
    ],
  }
}

export default async function dialogLevels() {
  // --- the format and the validator -----------------------------------------------------------------------------
  const v = validateScene(brook())
  check('a scene with levelled dialogs and a levelled happening validates, and keeps the levels', v.ok &&
    v.scene.variants.filter(d => d.level).map(d => `${d.id}:${d.level}`).join() === 'ovce-b1:B1,ovce-b1-2:B1,ovce-b2:B2,ovce-b2-2:B2' && v.scene.happenings[1].level === 'B2', v)
  check('levels rank from A1 up, none (everyone\'s) below them; a dialog is its own level\'s, else its happening\'s',
    JSON.stringify(DIALOG_LEVELS) === '["A1","A2","B1","B2","C1","C2"]' && levelRank(undefined) === -1 && levelRank('B2') === 3 &&
      dialogLevel({}, { level: 'B2' }) === 'B2' && dialogLevel({ level: 'B1' }, { level: 'B2' }) === 'B1' && dialogLevel({}, {}) === undefined)
  const errs = (s: object) => { const r = validateScene(s); return r.ok ? '' : r.errors }
  const b = brook()
  const levelledDialog = errs({ ...b, happenings: [{ ...b.happenings[0], dialog: 'ovce-b1' }, b.happenings[1]] })
  check("a happening's dialog (what an older app plays to everyone) never has a level", levelledDialog.includes('dialog "ovce-b1" is a B1 dialog') && levelledDialog.includes('older app'), levelledDialog)
  const allLevelled = errs({ ...b, happenings: [{ ...b.happenings[0], dialog: undefined, dialogs: ['ovce-b1', 'ovce-b2'] }, b.happenings[1]] })
  check('a happening whose every dialog has a level says it on the happening (an A1 learner would never meet it)', allLevelled.includes('every dialog has a level (from B1)') && allLevelled.includes('"level": "B1"'), allLevelled)
  const below = errs({ ...b, happenings: [b.happenings[0], { ...b.happenings[1], dialogs: ['sejem', 'ovce-b1'] }] })
  check("a dialog of a levelled happening is at least of its level", below.includes('dialog "ovce-b1" is B1 and the happening is on from B2'), below)
  const story = errs({ ...b, happenings: [b.happenings[0], { ...b.happenings[1], dialog: undefined, stories: true }] })
  check("a storyteller's happening has no level (its stories have theirs)", story.includes("a storyteller's happening has no level"), story)
  check('a level is one of A1 … C2', !validateScene({ ...b, variants: [...b.variants.slice(0, 4), { ...b.variants[4], level: 'B3' }] }).ok)

  // a levelled dialog's pages: up to one level above its own (the next level's, met and ripening), never two
  const pageOf: Record<string, string> = { tozilnik: 'A1', 'odvisni-govor': 'B1', 'pogojnik-pretekli': 'B2', 'c1-stran': 'C1' }
  const named = (g: string, level: string) => ({ happenings: [], dialogs: [{ ...talk('x', 'X', level), lines: [talk('x', 'X').lines[0], { choices: [talk('x', 'X').lines[1].choices![0], { ...talk('x', 'X').lines[1].choices![1], grammar: g }] }, talk('x', 'X').lines[2]] }] }) as any
  check("a levelled dialog names pages up to one level above its own, not two (the curated scenes' check)",
    levelPageErrors(named('pogojnik-pretekli', 'B1'), g => pageOf[g]).length === 0 && levelPageErrors(named('c1-stran', 'B2'), g => pageOf[g]).length === 0 &&
      levelPageErrors(named('c1-stran', 'B1'), g => pageOf[g])[0]?.includes('the page "c1-stran" is C1, more than a level above the dialog\'s: name a page of B2 or below') &&
      levelPageErrors(named('c1-stran', 'A1'), g => pageOf[g]).length === 1, levelPageErrors(named('c1-stran', 'B1'), g => pageOf[g]))
  // every curated scene: its levels hold, and its levelled dialogs' pages are within a level of theirs
  const companion = resolve(import.meta.dir, '../../..')
  const files = [
    ...readdirSync(join(companion, 'scenes')).filter(f => f.endsWith('.json')).map(f => join(companion, 'scenes', f)),
    ...readdirSync(join(companion, 'cultures')).flatMap(c => {
      const d = join(companion, 'cultures', c, 'scenes')
      return existsSync(d) ? readdirSync(d).filter(f => f.endsWith('.json')).map(f => join(d, f)) : []
    }),
  ]
  const problems: string[] = []
  let levelled = 0
  for (const f of files) {
    const s = JSON.parse(readFileSync(f, 'utf8'))
    const lang = s.language ?? 'sl'
    const page = (g: string): string | undefined => {
      try {
        return JSON.parse(readFileSync(join(companion, 'grammar', lang, `${g}.json`), 'utf8')).level
      } catch {
        return undefined
      }
    }
    levelled += [...(s.dialogs ?? []), ...(s.variants ?? [])].filter((d: any) => d.level).length
    const r = validateScene(s)
    if (!r.ok) problems.push(`${f}: ${r.errors}`)
    else problems.push(...levelPageErrors(r.scene, page).map(e => `${f}: ${e}`))
  }
  check(`the curated scenes' levels hold (${files.length} scenes, ${levelled} levelled dialogs): every levelled dialog's pages within a level of it`, files.length >= 130 && !problems.length, problems)

  // --- what an app gets ----------------------------------------------------------------------------------------
  const loaded: LoadedScene = { ...(v.ok ? v.scene : (brook() as any)), source: 'curated' }
  const r = resolveScene(loaded, [])
  const old = forApp([r], 6)[0] as any
  check('an app before the levels (arts 6) gets no levelled dialog nor the levelled happening, the old ones as they were',
    SCENE_ARTS_VERSION === 7 && JSON.stringify(old.dialogs.map((d: any) => d.id)) === '["ovce","ovce-2"]' && JSON.stringify(old.happenings.map((h: any) => h.id)) === '["ovce"]' &&
      JSON.stringify(old.happenings[0].dialogs) === '["ovce-2","ovce"]' && old.happenings[0].dialog === 'ovce' && !old.dialogs.some((d: any) => d.level), old)
  check('an app that reads levels (arts 7) gets the scene whole, with its levels', JSON.stringify(forApp([r], 7)[0]) === JSON.stringify(r) && JSON.stringify(forApp([r], 1)[0].dialogs.map(d => d.id)) === '["ovce","ovce-2"]')
  check('a scene without levels is the same scene for an older app', (() => { const plain = resolveScene({ ...loaded, happenings: [loaded.happenings[0]], variants: [loaded.variants[0]], dialogs: [loaded.dialogs[0]] }, []); return withoutLevels(plain) === plain })())
  // a file with only dialogs: an older app's dialog is the first without a count or a level
  const only = resolveScene({ ...loaded, happenings: [{ ...loaded.happenings[0], dialog: undefined }] }, [])
  check('a file with only dialogs: an older app gets the first variant without a level as its dialog', only.happenings[0].dialog === 'ovce-2', only.happenings[0])
  // a happening left with nothing an older app can play goes (a tutor's levelled variant of one is no reason to keep it)
  const bare = withoutLevels({ happenings: [{ id: 'h', dialogs: ['x'] }, { id: 'g', dialog: 'y' }], dialogs: [{ id: 'x', level: 'B1' }, { id: 'y' }] })
  check('an older app gets no happening with nothing left to play', JSON.stringify(bare) === '{"happenings":[{"id":"g","dialog":"y"}],"dialogs":[{"id":"y"}]}', bare)

  // --- the tutor's ask: once the learner's own dialogs are all heard ---------------------------------------------
  const on = (ids: string[]) => Object.fromEntries(ids.map(id => [id, '2026-11-01']))
  const heardOld = { dialogsHeard: { 'smoke-ravni/ovce': { heard: on(['ovce', 'ovce-2']) } } }
  const keys = (state: object, level?: string) => allHeard([r], state, 11, level).map(a => `${a.key}:${a.variants.length}`).join()
  check("an A2 learner who heard the old dialogs has heard all theirs: the tutor is asked (the B1 and B2 ones aren't theirs)",
    keys(heardOld, 'A2') === 'smoke-ravni/ovce:2' && keys(heardOld, 'B1') === '' && keys(heardOld) === '', [keys(heardOld, 'A2'), keys(heardOld, 'B1')])
  const heardB1 = { dialogsHeard: { 'smoke-ravni/ovce': { heard: on(['ovce', 'ovce-2', 'ovce-b1', 'ovce-b1-2']) }, 'smoke-ravni/sejem': { heard: on(['sejem']) } } }
  check('a B1 learner who heard theirs: asked about the brook, not about the B2 fair; a B2 one about the fair, not yet the brook',
    keys(heardB1, 'B1') === 'smoke-ravni/ovce:4' && keys(heardB1, 'B2') === 'smoke-ravni/sejem:1', [keys(heardB1, 'B1'), keys(heardB1, 'B2')])
  const note = variantNote(allHeard([r], heardB1, 11, 'B1')[0], 'B1', [])
  check("the note says each heard dialog's level and asks for one that says the learner's", note.includes('"level": "B1"') && note.includes('saying it ("level": "B1"'), note)

  // --- served, and the tutor's levelled variant -------------------------------------------------------------------
  writeFileSync(join(scenesDir, 'smoke-ravni.json'), JSON.stringify(brook()))
  try {
    const get = async (q: string) => ((await (await fetch(`${base}/scenes${q}`, { headers: auth })).json()) as any[]).find(s => s.id === 'smoke-ravni')
    const [none, six, seven] = [await get(''), await get('?arts=6'), await get(`?arts=${SCENE_ARTS_VERSION}`)]
    check('GET /scenes: an app that says nothing or arts 6 gets the old dialogs only; arts 7 the levelled ones too',
      none?.dialogs.length === 2 && six?.dialogs.length === 2 && six.happenings.length === 1 && seven?.dialogs.length === 7 && seven.happenings.length === 2 &&
        seven.dialogs.find((d: any) => d.id === 'ovce-b2')?.level === 'B2' && seven.happenings[1].level === 'B2', { none: none?.dialogs.length, six: six?.dialogs.length, seven: seven?.dialogs.length })
    const one = await (await fetch(`${base}/scenes/smoke-ravni?arts=6`, { headers: auth })).json()
    check('GET /scenes/<id> holds them back from an older app too', one.dialogs.length === 2, one.dialogs.map((d: any) => d.id))
    const listed = JSON.parse(text(await client.callTool({ name: 'list_scenes', arguments: {} }))).find((s: any) => s.id === 'smoke-ravni')
    check('list_scenes says the levelled dialogs and the levelled happening', listed?.dialog_levels?.['ovce-b2'] === 'B2' && listed?.happenings[1].level === 'B2', listed)
    const fresh = { ...talk('ovce-tutor-b2', 'Če bi bil ded še živ, bi se smejal tej suši.', 'B2') }
    const pv = await client.callTool({ name: 'publish_dialog_variant', arguments: { scene: 'smoke-ravni', happening: 'ovce', dialog: fresh } })
    check("publish_dialog_variant keeps a variant's level and says it waits while the learner is below it", !pv.isError && text(pv).includes('it is a B2 dialog and the learner is at') && text(pv).includes('waits until they are at B2'), text(pv))
    const after = await get('?arts=6')
    check("the tutor's levelled variant is held back from an older app as well", !after.dialogs.some((d: any) => d.id === 'ovce-tutor-b2') && !after.happenings[0].dialogs.includes('ovce-tutor-b2') &&
      (await get(`?arts=${SCENE_ARTS_VERSION}`)).dialogs.find((d: any) => d.id === 'ovce-tutor-b2')?.level === 'B2', after.happenings[0])
    await client.callTool({ name: 'remove_dialog_variant', arguments: { scene: 'smoke-ravni', happening: 'ovce', id: 'ovce-tutor-b2' } })
  } finally {
    unlinkSync(join(scenesDir, 'smoke-ravni.json'))
  }
}
