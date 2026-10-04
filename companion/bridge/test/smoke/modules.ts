// Smoke checks: publishing, serving, exercise types, quests, curated modules.
import { existsSync, mkdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { validateModule } from '../../src/spec'
import { reference, spec } from './fixtures'
import { auth, base, check, client, dataDir } from './harness'

const text = (r: any) => (r.content ?? []).map((c: any) => c.text ?? '').join('\n')

export default async function modules() {
  // the tutor publishes a module; the app reads it
  const bad = await client.callTool({ name: 'publish_module', arguments: { spec: { schema: 'lani.module/v0', id: 'X' } } })
  check('invalid spec rejected', bad.isError, bad)
  const pub = await client.callTool({ name: 'publish_module', arguments: { spec, note: 'first drill' } })
  check('valid spec published', !pub.isError, pub)

  const mod = await (await fetch(`${base}/modules/clitic-se`, { headers: auth })).json()
  check('GET /modules/:id serves v1', mod.version === 1 && mod.exercises.length === 2, mod)

  const extra = await client.callTool({
    name: 'publish_module',
    arguments: {
      spec: {
        ...spec,
        id: 'bakery',
        exercises: [
          { type: 'scenario', scene: '🥖 Bakery, 8:00', speaker: 'Baker', line: 'Dober dan! Kaj bo?', prompt: 'Ask for bread, politely.', accept: ['Kruh, prosim.'] },
          { type: 'multi', prompt: 'Which are polite greetings?', options: ['Dober dan', 'Živjo', 'Dobro jutro', 'Oj'], answers: [0, 2], say_options: true },
          { type: 'dictation', audio: 'Dober večer.', accept: ['Dober večer.'] },
          { type: 'choice', instruction: 'Kaj pomeni?', prompt: 'hvala', say: 'hvala', options: ['thanks', 'please'], answer: 0 },
        ],
      },
    },
  })
  check('scenario, multi, dictation, layout fields accepted', !extra.isError, extra)
  const both = await client.callTool({
    name: 'publish_module',
    arguments: { spec: { ...spec, id: 'bad', exercises: [{ type: 'scenario', scene: 's', prompt: 'p', options: ['a', 'b'], answer: 0, accept: ['a'] }] } },
  })
  check('scenario with both answer kinds rejected', both.isError)

  // speaking
  const spoken = await client.callTool({
    name: 'publish_module',
    arguments: {
      spec: {
        ...spec,
        id: 'speak-bakery',
        exercises: [
          { type: 'speak', say: 'Dober dan, jaz sem Jan.', prompt: 'Greet the baker and introduce yourself.' },
          { type: 'speak', say: 'Kruh, prosim.', prompt: 'Ask for bread, politely.', accept: ['En kruh, prosim.'], show: false, instruction: 'Povej · Say it', explain: 'prosim = please' },
        ],
      },
    },
  })
  check('speak exercises accepted', !spoken.isError, spoken)
  const speakServed = await (await fetch(`${base}/modules/speak-bakery`, { headers: auth })).json()
  check('speak defaults show to true', speakServed.exercises?.[0]?.show === true && speakServed.exercises?.[1]?.show === false, speakServed)
  for (const [label, bad] of [
    ['speak without say rejected', { type: 'speak', prompt: 'Greet the baker.' }],
    ['speak with empty accept entry rejected', { type: 'speak', say: 'Dober dan.', prompt: 'Greet.', accept: [''] }],
    ['speak with non-boolean show rejected', { type: 'speak', say: 'Dober dan.', prompt: 'Greet.', show: 'no' }],
  ] as const) {
    const r = await client.callTool({ name: 'publish_module', arguments: { spec: { ...spec, id: 'bad-speak', exercises: [bad] } } })
    check(label, r.isError, r)
  }
  // Every speak example in the module-spec reference must validate.
  const speakExamples = [...reference.matchAll(/```json\n([\s\S]*?)\n```/g)].map(m => m[1]).filter(s => s.includes('"type": "speak"'))
  check('module-spec.md has speak examples', speakExamples.length >= 2, speakExamples.length)
  for (const ex of speakExamples) {
    const v = validateModule({ ...spec, id: 'doc-speak', exercises: [JSON.parse(ex)] })
    check(`module-spec example validates: ${ex.slice(0, 50)}…`, v.ok, v)
  }

  // tutor-written sidequests
  const questMod = await client.callTool({
    name: 'publish_module',
    arguments: {
      spec: {
        ...spec,
        id: 'micka-kitchen',
        quest: { giver: 'Babica Micka', emoji: '👵', story: 'Micka forgot the kitchen words.', skill: 'food', reward: { food: 40, wisdom: 5 } },
      },
    },
  })
  check('module with quest block published', !questMod.isError, questMod)
  const listed = await (await fetch(`${base}/modules`, { headers: auth })).json()
  check('GET /modules includes the quest block', listed.some((m: any) => m.id === 'micka-kitchen' && m.quest?.giver === 'Babica Micka'))
  const badQuest = await client.callTool({
    name: 'publish_module',
    arguments: { spec: { ...spec, id: 'bad-quest', quest: { giver: 'X', story: 'Y', reward: { gold: 5 } } } },
  })
  check('unknown quest reward resource rejected', badQuest.isError)

  // a smaller drill for a task Jan keeps failing: quest.helps names that task's module, at the same giver
  const micka = { giver: 'Babica Micka', emoji: '👵', story: 'Micka starts with the pots.', skill: 'food' }
  const drill = await client.callTool({ name: 'publish_module', arguments: { spec: { ...spec, id: 'micka-pots', quest: { ...micka, helps: 'micka-kitchen' } } } })
  check('module with quest.helps published, without a note', !drill.isError && !text(drill).includes('quest.helps'), drill)
  const listed3 = await (await fetch(`${base}/modules`, { headers: auth })).json()
  check('GET /modules lists quest.helps', listed3.some((m: any) => m.id === 'micka-pots' && m.quest?.helps === 'micka-kitchen'), listed3)
  const itself = await client.callTool({ name: 'publish_module', arguments: { spec: { ...spec, id: 'micka-self', quest: { ...micka, helps: 'micka-self' } } } })
  check('quest.helps naming its own module rejected', itself.isError && text(itself).includes('quest.helps names this module itself') && (await fetch(`${base}/modules/micka-self`, { headers: auth })).status === 404, itself)
  const slug = await client.callTool({ name: 'publish_module', arguments: { spec: { ...spec, id: 'micka-slug', quest: { ...micka, helps: 'Not A Slug' } } } })
  check('malformed quest.helps rejected', slug.isError && text(slug).includes('quest.helps must be a module id'), slug)
  // a task that isn't published, or is another villager's: published, and said back
  const lost = await client.callTool({ name: 'publish_module', arguments: { spec: { ...spec, id: 'micka-lost', quest: { ...micka, helps: 'no-such-task' } } } })
  check("quest.helps naming no published module: published with a note", !lost.isError && text(lost).includes(`"no-such-task", which isn't published`), lost)
  const tone = await client.callTool({ name: 'publish_module', arguments: { spec: { ...spec, id: 'tone-drill', quest: { ...micka, giver: 'Kovač Tone', helps: 'micka-kitchen' } } } })
  check("quest.helps naming another giver's task: published with a note", !tone.isError && text(tone).includes("Babica Micka's, not Kovač Tone's"), tone)

  // curated modules (companion/modules) — a throwaway dir via LANI_MODULES_DIR
  const listed2 = await (await fetch(`${base}/modules`, { headers: auth })).json()
  check('curated module is listed', listed2.some((m: any) => m.id === 'curated-demo' && m.version === 1), listed2)
  const cur = await (await fetch(`${base}/modules/curated-demo`, { headers: auth })).json()
  check('curated module is served', cur.exercises?.length === 2)
  const over = await client.callTool({ name: 'publish_module', arguments: { spec: { ...spec, id: 'curated-demo', title: 'Rewritten' } } })
  check('tutor rewrite of a curated module becomes v2', JSON.stringify(over).includes('v2'), over)

  // the session can't reach outside the module store (<data>/app/modules) through an id or a version
  const outside = join(dataDir, 'outside')
  mkdirSync(outside, { recursive: true })
  writeFileSync(join(outside, 'v1.json'), JSON.stringify({ ...spec, id: 'outside', version: 1 }))
  const rb = await client.callTool({ name: 'rollback_module', arguments: { id: '../../outside', version: 1 } })
  check('rollback_module refuses an id outside the store, and writes nothing', rb.isError && !existsSync(join(outside, 'current.json')), rb)
  const gm = await client.callTool({ name: 'get_module', arguments: { id: '../../outside', version: 1 } })
  check('get_module refuses an id outside the store', gm.isError && !JSON.stringify(gm).includes('clitic_placement_se'), gm)
  const gv = await client.callTool({ name: 'get_module', arguments: { id: 'clitic-se', version: '1/../../../../outside/v1' } })
  check('get_module refuses a version that is not a number', gv.isError, gv)
  check('GET /modules/:id?version= must be a whole number', (await fetch(`${base}/modules/clitic-se?version=1.5`, { headers: auth })).status === 404)
  const odd = await client.callTool({ name: 'publish_module', arguments: { spec: { ...spec, id: 'noted' }, note: { html: '<b>x</b>' } } })
  check('publish_module refuses a note that is not a string', odd.isError && (await fetch(`${base}/modules/noted`, { headers: auth })).status === 404, odd)
}
