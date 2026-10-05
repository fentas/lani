// Smoke checks: a happening's variants and counting dialogs (companion/SCENES.md, "Variants", "Numbers"): the number
// categories against Numbers.kt, validation, serving (the variants among the dialogs, a dialog for an older app, guests),
// the voice corpus, the tutor's variants (publish_dialog_variant, remove_dialog_variant) and the note to the tutor when a
// happening's dialogs are all heard (dialog_variants_heard).
import { existsSync, readFileSync, unlinkSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { category, renderForms, slovene, WRONG_SL, wrongCategory } from '../../src/counts'
import { forHost, resolveScene, validateScene, type LoadedScene } from '../../src/scenes'
import { allHeard, VariantAsks, weakSpots } from '../../src/variants'
import { corpus } from '../../src/voice'
import { auth, base, channelEvents, check, client, dataDir, scenesDir, tempDir } from './harness'
import { JAN } from '../learner-content'

const android = resolve(import.meta.dir, '../../../android/app/src/main/java/si/lanisce/lani/game/scene')
/** A tool result's text. */
const text = (r: any) => (r.content ?? []).map((c: any) => c.text ?? '').join('\n')

/** Luka's sheep by the brook: a plain dialog (what an older app plays), a counting variant and another plain one. */
function potok() {
  const langs = (sl: string, en: string) => ({ sl, en })
  return {
    schema: 'lani.scene/v0', id: 'smoke-potok', title: 'Ob potoku · By the stream', emoji: '🐑', level: 'A1', art: 'stream', from: ['spot:riverbank'],
    review: 'reactions: machine-written, not reviewed by a native speaker',
    people: [{ id: 'luka', name: 'Pastir Luka', emoji: '🐑', art: 'shepherd', slot: 'bank', villager: 'luka' }],
    happenings: [
      { id: 'ovce', title: 'Luka napaja ovce · Luka is watering the sheep', who: 'luka', when: ['evening'], chance: 1, dialog: 'ovce', dialogs: ['ovce-stejem', 'ovce-2', 'ovce'], reward: { wood: 15 }, marker: '🐑' },
    ],
    dialogs: [{
      id: 'ovce',
      lines: [
        { who: 'luka', ...langs('Ej, Jan! Ovce so žejne.', 'Hey, Jan! The sheep are thirsty.') },
        { choices: [
          { ...langs('Koliko ovc imaš?', 'How many sheep do you have?'), ok: true, reply: langs('Dvajset.', 'Twenty.') },
          { ...langs('Koliko ovce imaš?', 'How many sheep do you have?'), why: 'Koliko takes the genitive plural: koliko ovc?', reply: langs('Koliko ovce? Bela, si razumela?', 'Koliko ovce? Bela, did you get that?') },
        ] },
        { who: 'luka', ...langs('Mrak je. Greva.', "It's getting dark. Let's go.") },
      ],
    }],
    variants: [
      {
        id: 'ovce-stejem',
        count: { from: 'dice', min: 12, max: 24, gender: 'f' },
        memory: langs('večer, ko sva štela ovce', 'the evening we counted the sheep'),
        lines: [
          { who: 'luka', ...langs('Jan, preštej ovce pri vodi!', 'Jan, count the sheep at the water!') },
          { choices: [
            { ...langs('Pri vodi {je|sta|so|je} {n} {!ovca|ovci|ovce|ovc}.', 'There {is|are} {n} {sheep|sheep} at the water.'), wrong_form: 1,
              why: '{One takes the singular|Two take the dual|Three and four take the plural|From five on, the genitive plural}: {n} {ovca|ovci|ovce|ovc}.',
              reply: langs('Hm? Štej še enkrat, počasi.', 'Hm? Count again, slowly.') },
            { ...langs('Pri vodi {je|sta|so|je} {n} {ovca|ovci|ovce|ovc}.', 'There {is|are} {n} {sheep|sheep} at the water.'), ok: true, reply: langs('Tako je!', "That's right!") },
            { ...langs('Pri vodi {je|sta|so|je} {n} {!ovca|ovci|ovce|ovc}.', 'There {is|are} {n} {sheep|sheep} at the water.'), wrong_form: 2,
              why: '{One takes the singular|Two take the dual|Three and four take the plural|From five on, the genitive plural}: {n} {ovca|ovci|ovce|ovc}.',
              reply: langs('Koliko? Ovce se premikajo, veš.', 'How many? The sheep keep moving, you know.') },
          ] },
          { who: 'luka', ...langs('Dobro si štel.', 'You counted well.') },
        ],
      },
      {
        id: 'ovce-2',
        lines: [
          { who: 'luka', ...langs('Jan! Jagnje noče piti.', "Jan! The lamb won't drink.") },
          { choices: [
            { ...langs('Daj mu vodo.', 'Give it water.'), ok: true, reply: langs('Saj mu jo dajem!', "I'm giving it some!") },
            { ...langs('Daj mu voda.', 'Give it water.'), why: 'Dati takes the accusative: vodo.', reply: langs('Voda? Kaj z vodo?', 'Voda? What about the water?') },
          ] },
          { who: 'luka', ...langs('Glej, pije!', "Look, it's drinking!") },
        ],
      },
    ],
  }
}

export default async function variants() {
  // --- the number categories, and the same wrong forms as the app -------------------------------------------------
  check('Slovene categories by the last two figures: 1, 101 → 1; 2, 102 → 2; 3, 4, 103 → 3–4; 0, 5, 11, 21, 111 → 5+',
    [1, 101, 201].every(n => slovene(n) === 0) && [2, 102].every(n => slovene(n) === 1) && [3, 4, 103, 104].every(n => slovene(n) === 2) &&
      [0, 5, 11, 12, 13, 14, 19, 20, 21, 22, 100, 111, 112, 121].every(n => slovene(n) === 3))
  check('two forms: one and more', category(1, 2) === 0 && [0, 2, 21, 101].every(n => category(n, 2) === 1))
  const kt = readFileSync(join(android, 'Numbers.kt'), 'utf8')
  const names: Record<string, number> = { ONE: 0, TWO: 1, FEW: 2, MANY: 3 }
  const ktWrong = Object.fromEntries([...(/WRONG_SL = mapOf\(([\s\S]*?)\)\n/.exec(kt)?.[1] ?? '').matchAll(/(\w+) to listOf\((\w+), (\w+)\)/g)].map(m => [names[m[1]], [names[m[2]], names[m[3]]]]))
  check('the wrong forms are the app\'s (Numbers.kt WRONG_SL)', JSON.stringify(ktWrong) === JSON.stringify(WRONG_SL), { kt: ktWrong, ts: WRONG_SL })
  check('wrong forms: 5 → the 3–4 form, then the singular; 2 → 3–4, then 5+; in two forms the other',
    wrongCategory(5, 4, 1) === 2 && wrongCategory(5, 4, 2) === 0 && wrongCategory(2, 4, 1) === 2 && wrongCategory(2, 4, 2) === 3 && wrongCategory(1, 2, 1) === 1 && wrongCategory(7, 2, 2) === 0)
  const t = 'Pri vodi {je|sta|so|je} {n} {!ovca|ovci|ovce|ovc}.'
  check('forms agree, a wrong choice takes the wrong one where it is marked',
    renderForms(t, 20, 4) === 'Pri vodi je 20 ovc.' && renderForms(t, 2, 4) === 'Pri vodi sta 2 ovci.' && renderForms(t, 103, 4) === 'Pri vodi so 103 ovce.' &&
      renderForms(t, 20, 4, 1) === 'Pri vodi je 20 ovce.' && renderForms(t, 20, 4, 2) === 'Pri vodi je 20 ovca.' && renderForms('{sheep|sheep} {egg|eggs}', 3, 4, 1) === 'sheep eggs')

  // --- validation -------------------------------------------------------------------------------------------------
  const good = potok()
  const v = validateScene(good)
  check('a scene with variants and a counting dialog validates', v.ok, v)
  const scene = good as any
  const counting = () => structuredClone(scene.variants[0])
  const bad: [string, (s: any) => void, string][] = [
    ['a counting dialog as what an older app plays', s => { s.happenings[0].dialog = 'ovce-stejem' }, 'an older app plays'],
    ['a variant that does not exist', s => { s.happenings[0].dialogs = ['ovce-9', 'ovce'] }, 'dialog "ovce-9" does not exist'],
    ['a variant listed twice', s => { s.happenings[0].dialogs = ['ovce', 'ovce'] }, 'duplicate variant'],
    ['a variant with the id of a dialog', s => { s.variants[1].id = 'ovce' }, 'duplicate dialog id'],
    ['{n} without a count', s => { s.variants[1].lines[0].sl = 'Imam {n} ovc.' }, "need the dialog's count"],
    ['three forms', s => { s.variants[0].lines[0].sl = 'Glej {ovca|ovci|ovce}!' }, 'two ({one|more}) or four'],
    ['an unknown placeholder', s => { s.variants[0].lines[0].sl = 'Glej, {ime}!' }, 'unknown placeholder {ime}'],
    ['wrong_form on a right choice', s => { s.variants[0].lines[1].choices[1].wrong_form = 1 }, 'wrong_form is for a wrong choice'],
    ['wrong_form without forms', s => { s.variants[0].lines[1].choices[0].sl = 'Pri vodi je {n}.' }, 'has no forms'],
    ['a reaction that could say the number', s => { s.variants[0].lines[1].choices[0].reply.sl = 'Ne {n}!' }, 'a reaction has no {…}'],
    ['a count without a gender in Slovene', s => { delete s.variants[0].count.gender }, 'the gender of what it counts'],
    ['dice without a max', s => { delete s.variants[0].count.max }, 'the dice need a min and a max'],
    ['a count from nowhere', s => { s.variants[0].count.from = 'sheep' }, 'from'],
    ['a count over 999', s => { s.variants[0].count.max = 1000 }, 'max'],
    ['wrong forms that all come out right', s => {
      const d = counting()
      const same = 'Pri vodi {je|sta|so|je} {n} {ovc|ovc|ovc|ovc}.'
      d.lines[1].choices = [{ ...d.lines[1].choices[1], sl: same }, { ...d.lines[1].choices[0], sl: same.replace('{ovc|', '{!ovc|') }]
      s.variants[0] = d
    }, 'no wrong choice is left'],
    ["a variant's memory without English", s => { s.variants[0].memory = { sl: 'ovce' } }, 'memory: no "en" text'],
    ['a storyteller with variants', s => { s.happenings[0].stories = true; delete s.happenings[0].dialog }, 'not both'],
    ['37 variants', s => { s.variants = Array.from({ length: 37 }, (_, i) => ({ ...s.variants[1], id: `x-${i}` })) }, 'variants'],
  ]
  for (const [label, change, mention] of bad) {
    const s = structuredClone(good) as any
    change(s)
    const r = validateScene(s)
    check(`validateScene rejects ${label}`, !r.ok && r.errors.includes(mention), r)
  }
  // an English village counts too: no gender for "one", and a wrong form that comes out right (sheep) leaves the plain wrong choice
  const english = structuredClone(good) as any
  english.language = 'en'
  english.title = 'By the stream'
  english.happenings[0].title = 'Luka is watering the sheep'
  for (const d of [...english.dialogs, ...english.variants]) {
    delete d.memory
    for (const l of d.lines) { delete l.sl; for (const c of l.choices ?? []) { delete c.sl; if (c.reply) delete c.reply.sl } }
  }
  const turn = english.variants[0].lines[1]
  turn.choices = [
    { en: 'There are {n} {!sheep|sheep} at the water.', wrong_form: 1, why: 'Sheep is sheep.', reply: { en: 'Hm? Count again.' } },
    { en: 'There are {n} {sheep|sheep} at the water.', ok: true, reply: { en: "That's right!" } },
    { en: 'There are {n} cows at the water.', why: 'These are sheep.', reply: { en: 'Cows? These are sheep, Jan!' } },
  ]
  delete english.variants[0].count.gender
  const eng = validateScene(english)
  check('an English counting dialog needs no gender, and a plain wrong choice keeps the turn when sheep is sheep', eng.ok, eng)

  // --- served: the variants among the dialogs, a dialog for an older app, guests -----------------------------------
  const loaded = { ...(v.ok ? v.scene : (good as any)), source: 'curated' } as LoadedScene
  const r = resolveScene(loaded, [])
  check('served: the variants among the dialogs, no "variants" key, the happening keeps its dialog and its variants',
    JSON.stringify(r.dialogs.map(d => d.id)) === '["ovce","ovce-stejem","ovce-2"]' && !('variants' in r) && r.happenings[0].dialog === 'ovce' && JSON.stringify(r.happenings[0].dialogs) === '["ovce-stejem","ovce-2","ovce"]', r.happenings)
  const only = resolveScene({ ...loaded, happenings: [{ ...loaded.happenings[0], dialog: undefined }] }, [])
  check('a file with only dialogs: an older app gets the first variant without a count as its dialog', only.happenings[0].dialog === 'ovce-2', only.happenings[0])
  const guest = forHost(resolveScene({ ...loaded, happenings: [{ ...loaded.happenings[0], guests: true }] }, []))
  check("a guests' happening goes from its own village's app with all its variants", guest.happenings.length === 0 && guest.dialogs.length === 0, guest)
  const items = corpus({ learner: JAN, packs: [], modules: [], scenarios: [], scenes: [r] })
  check('the voice corpus leaves the counted texts out (voiced when played) and keeps the rest of a variant',
    !items.some(i => i.text.includes('{')) && items.some(i => i.text === 'Jan, preštej ovce pri vodi!') && items.some(i => i.text === "Jan! Jagnje noče piti."), items.map(i => i.text))

  // --- the tutor's variants ---------------------------------------------------------------------------------------
  writeFileSync(join(scenesDir, 'smoke-potok.json'), JSON.stringify(good))
  const listed = await (await fetch(`${base}/scenes`, { headers: auth })).json()
  const s0 = listed.find((s: any) => s.id === 'smoke-potok')
  check('GET /scenes serves a curated scene with its variants among the dialogs', s0 && s0.dialogs.length === 3 && !s0.variants && s0.happenings[0].dialog === 'ovce' && s0.happenings[0].dialogs.length === 3, s0)
  const fresh = {
    id: 'ovce-tutor',
    lines: [
      { who: 'luka', sl: 'Jan, danes so ovce lačne.', en: 'Jan, the sheep are hungry today.' },
      { choices: [
        { sl: 'Dam jim seno.', en: "I'll give them hay.", ok: true, reply: { sl: 'Bravo, fant.', en: 'Well done, lad.' } },
        { sl: 'Dam jim sena.', en: "I'll give them some hay.", why: 'Dati takes the accusative here: seno.', reply: { sl: 'Sena? Koliko sena?', en: 'Sena? How much hay?' } },
      ] },
      { who: 'luka', sl: 'Hvala, Jan.', en: 'Thanks, Jan.' },
    ],
  }
  const pv = await client.callTool({ name: 'publish_dialog_variant', arguments: { scene: 'smoke-potok', happening: 'ovce', dialog: fresh } })
  check('publish_dialog_variant adds a fresh dialog to a happening and says where it plays', !pv.isError && text(pv).includes('published variant ovce-tutor of smoke-potok/ovce') && text(pv).includes('ovce-stejem, ovce-2, ovce, ovce-tutor'), pv)
  check('the variant is kept in <data>/app/variants', existsSync(join(dataDir, 'app/variants/smoke-potok.ovce.ovce-tutor.json')))
  const s1 = await (await fetch(`${base}/scenes/smoke-potok`, { headers: auth })).json()
  const served = s1.dialogs.find((d: any) => d.id === 'ovce-tutor')
  check("the app gets it among the dialogs, marked as the tutor's, last in the happening's", served?.source === 'tutor' && JSON.stringify(s1.happenings[0].dialogs) === '["ovce-stejem","ovce-2","ovce","ovce-tutor"]' && s1.dialogs.find((d: any) => d.id === 'ovce')?.source === undefined, s1.happenings[0])
  await Bun.sleep(50)
  const ev = await (await fetch(`${base}/events/backlog?since=0`, { headers: auth })).json()
  check('variant_published event (the app reloads the scene)', ev.some((b: any) => b.event.type === 'variant_published' && b.event.scene === 'smoke-potok' && b.event.id === 'ovce-tutor'), ev.slice(-3))
  const again = await client.callTool({ name: 'publish_dialog_variant', arguments: { scene: 'smoke-potok', happening: 'ovce', dialog: { ...fresh, lines: [{ ...fresh.lines[0], sl: 'Jan, ovce so danes lačne.' }, fresh.lines[1], fresh.lines[2]] } } })
  check("a tutor variant with the same id is replaced", !again.isError && (await (await fetch(`${base}/scenes/smoke-potok`, { headers: auth })).json()).dialogs.filter((d: any) => d.id === 'ovce-tutor').length === 1, again)
  const taken = await client.callTool({ name: 'publish_dialog_variant', arguments: { scene: 'smoke-potok', happening: 'ovce', dialog: { ...fresh, id: 'ovce-2' } } })
  check('publish_dialog_variant refuses an id the scene has', taken.isError && text(taken).includes('already'), taken)
  const invalid = await client.callTool({ name: 'publish_dialog_variant', arguments: { scene: 'smoke-potok', happening: 'ovce', dialog: { ...fresh, id: 'ovce-x', lines: [fresh.lines[0], { choices: [fresh.lines[1].choices[0], { sl: 'Dam jim sena.', en: 'x' }] }, fresh.lines[2]] } } })
  check('publish_dialog_variant validates the dialog with the scene (a wrong choice needs a why)', invalid.isError && text(invalid).includes('needs a why') && text(invalid).includes('nothing published'), invalid)
  const nowhere = await client.callTool({ name: 'publish_dialog_variant', arguments: { scene: 'smoke-potok', happening: 'nope', dialog: fresh } })
  const noScene = await client.callTool({ name: 'publish_dialog_variant', arguments: { scene: 'nope', happening: 'ovce', dialog: fresh } })
  check('publish_dialog_variant of a happening or a scene that does not exist fails', nowhere.isError && noScene.isError && text(nowhere).includes('has no happening'), [nowhere, noScene])
  const ls = text(await client.callTool({ name: 'list_scenes', arguments: {} }))
  check("list_scenes shows a happening's variants and the tutor's among them", ls.includes('"tutor_variants"') && ls.includes('"ovce-tutor"') && ls.includes('"variants"'), ls.slice(0, 200))

  // --- the note: every dialog of a happening heard -----------------------------------------------------------------
  const doc = await (await fetch(`${base}/game`, { headers: auth })).json()
  const heard = (ids: string[]) => ({ 'smoke-potok/ovce': { heard: Object.fromEntries(ids.map(id => [id, '2026-09-20'])), last: ids.at(-1) } })
  const put = async (dialogsHeard: object) => {
    const cur = await (await fetch(`${base}/game`, { headers: auth })).json()
    return fetch(`${base}/game`, { method: 'PUT', headers: auth, body: JSON.stringify({ rev: cur.rev ?? 0, state: { ...(cur.state ?? doc.state ?? {}), dialogsHeard } }) })
  }
  const notes = () => channelEvents.filter(n => n.params?.meta?.kind === 'dialog_variants_heard' && String(n.params?.content).includes('smoke-potok'))
  await put(heard(['ovce-stejem', 'ovce-2', 'ovce']))
  await Bun.sleep(1500)
  check('no note while a variant (the tutor\'s) is not heard yet', notes().length === 0, notes())
  await put(heard(['ovce-stejem', 'ovce-2', 'ovce', 'ovce-tutor']))
  for (let i = 0; i < 40 && !notes().length; i++) await Bun.sleep(100)
  const note = notes()[0]?.params
  check('dialog_variants_heard: the bridge asks the tutor for a fresh dialog, with the happening, its person, what was heard and the learner\'s level',
    !!note && note.content.includes('publish_dialog_variant') && note.content.includes('"happening": "ovce"') && note.content.includes('Pastir Luka') &&
      note.content.includes('"first_line": "Jan, preštej ovce pri vodi!"') && note.content.includes('"counts": true') && note.content.includes('"tutor": true') &&
      note.content.includes('"learner_level"') && note.content.includes('"weak_spots"') && note.meta?.conversation_id === 'main', note)
  await Bun.sleep(1200)
  check('once: not asked again about the same dialogs', notes().length === 1, notes().length)
  // a fresh one heard as well: asked again, with the new list
  const second = await client.callTool({ name: 'publish_dialog_variant', arguments: { scene: 'smoke-potok', happening: 'ovce', dialog: { ...fresh, id: 'ovce-tutor-2' } } })
  await put(heard(['ovce-stejem', 'ovce-2', 'ovce', 'ovce-tutor', 'ovce-tutor-2']))
  for (let i = 0; i < 40 && notes().length < 2; i++) await Bun.sleep(100)
  check('a happening is asked about again once its new variant is heard too', !second.isError && notes().length === 2 && String(notes()[1].params.content).includes('ovce-tutor-2'), notes().length)

  // the pure parts: all heard (not a guest's, not a storyteller's), a few a day, the weak spots
  const rs = resolveScene(loaded, [])
  const all = allHeard([rs], { dialogsHeard: heard(['ovce-stejem', 'ovce-2', 'ovce']) })
  check('allHeard: every variant heard', all.length === 1 && all[0].key === 'smoke-potok/ovce' && allHeard([rs], { dialogsHeard: heard(['ovce', 'ovce-2']) }).length === 0 && allHeard([rs], {}).length === 0, all)
  const asks = new VariantAsks(join(tempDir('lani-smoke-asks-'), 'asks.json'))
  const many = [1, 2, 3, 4, 5].map(i => ({ ...all[0], key: `s/h${i}` }))
  const due = asks.due(many, '2026-09-27')
  due.forEach(a => asks.mark(a, '2026-09-27'))
  check('at most three happenings a day; the next day the rest', due.length === 3 && asks.due(many, '2026-09-27').length === 0 && asks.due(many, '2026-09-28').length === 2)
  const weak = weakSpots({ error_patterns: { dual: { category: 'grammar', frequency: 5, consecutive_incorrect: 2, examples: [{ incorrect: 'dve ovce', correct: 'dve ovci' }] }, done: { frequency: 9, consecutive_incorrect: 0 }, genitive: { frequency: 2, consecutive_incorrect: 1 } } })
  check('weak spots: the patterns still going wrong, the most frequent first, with an example', JSON.stringify(weak.map(w => w.pattern)) === '["dual","genitive"]' && weak[0].example?.correct === 'dve ovci', weak)

  // take the tutor's back, and the scene
  const rm = await client.callTool({ name: 'remove_dialog_variant', arguments: { scene: 'smoke-potok', happening: 'ovce', id: 'ovce-tutor' } })
  const s2 = await (await fetch(`${base}/scenes/smoke-potok`, { headers: auth })).json()
  check('remove_dialog_variant takes one back', !rm.isError && !s2.dialogs.some((d: any) => d.id === 'ovce-tutor') && !s2.happenings[0].dialogs.includes('ovce-tutor'), s2.happenings[0])
  check('remove_dialog_variant of nothing fails', (await client.callTool({ name: 'remove_dialog_variant', arguments: { scene: 'smoke-potok', happening: 'ovce', id: 'ovce-tutor' } })).isError)
  await client.callTool({ name: 'remove_dialog_variant', arguments: { scene: 'smoke-potok', happening: 'ovce', id: 'ovce-tutor-2' } })
  unlinkSync(join(scenesDir, 'smoke-potok.json'))
}
