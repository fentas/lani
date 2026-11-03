// Smoke checks: every publishing tool on a bridge whose learner doesn't learn Slovene (docs/plans/05-built/tutor-languages.md).
// friuli.ts and kaernten.ts call it on their own bridges: what the studio writes in that language is taken, and the app
// gets it in that language. Each text is machine-written for this test; no learner sees it.
import type { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { check } from './harness'

type Said = { hello: string; helloBase: string; word: string; wordBase: string; wordEn: string; bye: string; byeBase: string; wrong: string; note: string; example: string; exampleBase: string; exampleEn: string }

/** The language's few words, the base's beside them. Machine-written. */
const SAID: Record<string, Said> = {
  it: {
    hello: 'Buongiorno, {learner}!', helloBase: 'Guten Morgen, {learner}!', word: 'la scala', wordBase: 'die Leiter', wordEn: 'the ladder',
    bye: 'Arrivederci!', byeBase: 'Auf Wiedersehen!', wrong: 'Buonanotte!', note: 'Scala ist weiblich.',
    example: 'La scala è alta.', exampleBase: 'Die Leiter ist hoch.', exampleEn: 'The ladder is tall.',
  },
  de: {
    hello: 'Grüß Gott, {learner}!', helloBase: 'Hello, {learner}!', word: 'die Leiter', wordBase: 'the ladder', wordEn: 'the ladder',
    bye: 'Auf Wiedersehen!', byeBase: 'Goodbye!', wrong: 'Gute Nacht!', note: 'Leiter is feminine.',
    example: 'Die Leiter ist hoch.', exampleBase: 'The ladder is tall.', exampleEn: 'The ladder is tall.',
  },
  en: {
    hello: 'Morning, {learner}!', helloBase: 'Dobro jutro, {learner}!', word: 'the ladder', wordBase: 'lestev', wordEn: 'the ladder',
    bye: 'Goodbye!', byeBase: 'Nasvidenje!', wrong: 'Good night!', note: 'Lestev je v angleščini the ladder.',
    example: 'The ladder is tall.', exampleBase: 'Lestev je visoka.', exampleEn: 'The ladder is tall.',
  },
}

const text = (r: any) => String(r?.content?.[0]?.text ?? '')
const parsed = (r: any) => {
  try {
    return JSON.parse(text(r))
  } catch {
    return {}
  }
}

/** Each publish_* tool, its content in [lang] explained in [base], on the bridge of [client] ([url], [headers]: the app's side). */
export async function publishEach(client: Client, url: string, headers: Record<string, string>, village: string, lang: string, base: string) {
  const s = SAID[lang]!
  const t = (target: string, inBase: string, en: string) => ({ [lang]: target, [base]: inBase, en })
  const call = (name: string, args: object) => client.callTool({ name, arguments: args as Record<string, unknown> })
  const ok = (what: string, r: any) => check(`${village}: ${what}`, !r.isError, text(r).slice(0, 600))
  const get = async (path: string) => (await fetch(`${url}${path}`, { headers })).json() as Promise<any>

  ok('publish_module takes a drill in its language', await call('publish_module', {
    note: 'a drill',
    spec: {
      schema: 'lani.module/v0', id: `smoke-${lang}-drill`, title: s.word, description: s.note, level: 'A1', language: lang, targets: [], tags: ['grammar'],
      exercises: [
        { type: 'choice', prompt: s.exampleBase, options: [s.example, s.wrong, s.bye], answer: 0, say_options: true, explain: `**${s.word}**` },
        { type: 'translate', prompt: s.exampleBase, accept: [s.example], explain: `**${s.word}**` },
        { type: 'speak', prompt: s.byeBase, say: s.bye, accept: [s.bye], show: false, explain: `**${s.bye}**` },
      ],
    },
  }))
  ok('publish_pack takes its words', await call('publish_pack', {
    note: 'new words',
    pack: {
      schema: 'lani.pack/v0', id: `smoke-${lang}-pack`, language: lang, title: t(s.word, s.wordBase, s.wordEn), emoji: '🪜', level: 'A1',
      description: t(s.example, s.exampleBase, s.exampleEn),
      words: Array.from({ length: 8 }, (_, i) => ({
        id: `w${i}`, [lang]: i ? `${s.word} ${i}` : s.word, [base]: i ? `${s.wordBase} ${i}` : s.wordBase, en: i ? `${s.wordEn} ${i}` : s.wordEn,
        [`example_${lang}`]: s.example, [`example_${base}`]: s.exampleBase, example_en: s.exampleEn,
      })),
    },
  }))
  const packs = await get('/packs')
  check(`${village}: … and the app gets the pack in its language`, JSON.stringify(packs).includes(`smoke-${lang}-pack`), Object.keys(packs))

  const book = parsed(await call('list_grammar', {}))
  const pages: any[] = Array.isArray(book) ? book : (book.pages ?? [])
  ok('publish_grammar takes a new page', await call('publish_grammar', {
    note: 'a page',
    page: {
      schema: 'lani.grammar/v0', id: `smoke-${lang}-page`, language: lang, level: 'A1', emoji: '📖', title: t(s.word, s.wordBase, s.wordEn),
      rule: { [base]: `**${s.word}** ${s.note}`, en: `**${s.word}** is the rule.` },
      examples: [{ [lang]: s.example, [base]: s.exampleBase, en: s.exampleEn }],
    },
  }))
  ok('publish_card takes a card', await call('publish_card', { title: s.word, markdown: `**${s.word}**\n\n- ${s.example}`, grammar: pages.slice(0, 1).map(p => p.id), level: 'A1', note: 'a card' }))

  const course = await get('/course')
  const unit = course.units?.find((u: any) => !u.planned)?.id
  ok('publish_worksheet takes homework for its course', await call('publish_worksheet', {
    note: 'homework',
    worksheet: {
      schema: 'lani.worksheet/v0', unit, id: `${unit}-domaca-smoke`, title: { sl: 'Domača naloga', en: 'Homework', de: 'Hausaufgabe', it: 'Compiti' },
      exercises: [{ type: 'choice', prompt: s.exampleBase, options: [s.example, s.wrong], answer: 0, explain: `**${s.word}**` }],
    },
  }))

  ok('publish_scenario takes a role-play', await call('publish_scenario', {
    note: 'a talk',
    scenario: {
      schema: 'lani.scenario/v0', id: `smoke-${lang}-talk`, language: lang, title: t(s.word, s.wordBase, s.wordEn), emoji: '🪜', level: 'A1',
      setting: { [base]: s.exampleBase, en: s.exampleEn }, role: 'A neighbour, kind, one or two short sentences a turn.', who: t(s.word, s.wordBase, s.wordEn), voice: 'female', speaker: 'woman', place: 'square',
      goals: [t(s.bye, s.byeBase, 'Say goodbye'), t(s.example, s.exampleBase, s.exampleEn), t(s.wrong, s.byeBase, 'Say good night')],
      opener_sl: s.hello, opener_en: { [base]: s.helloBase, en: 'Hello!' },
      vocabulary_hints: [{ sl: s.bye, en: { [base]: s.byeBase, en: 'Goodbye!' } }],
    },
  }))

  const scenes = parsed(await call('list_scenes', {}))
  const list: any[] = Array.isArray(scenes) ? scenes : (scenes.scenes ?? [])
  const sceneId = list.find(x => (x.happenings ?? []).length)?.id ?? list[0]?.id
  const scene = parsed(await call('get_scene', { id: sceneId }))
  const spec = scene.spec ?? scene.scene ?? scene
  ok(`publish_scene takes a curated scene back (${sceneId})`, await call('publish_scene', { scene: spec, note: 'a scene' }))
  const h = spec.happenings?.[0]
  const person = spec.people?.find((p: any) => p.id === h?.who)
  const line = (a: string, b: string, en: string, who?: string) => ({ ...(who ? { who } : {}), [lang]: a, [base]: b, en })
  ok(`publish_dialog_variant adds a dialog to its happening (${h?.id}, ${person?.id})`, await call('publish_dialog_variant', {
    scene: sceneId, happening: h?.id, note: 'a variant',
    dialog: {
      id: `smoke-${lang}-variant`,
      lines: [
        line(s.hello, s.helloBase, 'Hello!', h?.who),
        line(s.example, s.exampleBase, s.exampleEn, h?.who),
        { choices: [
          { ...line(s.bye, s.byeBase, 'Goodbye!'), ok: true, reply: line(s.example, s.exampleBase, s.exampleEn) },
          { ...line(s.wrong, s.byeBase, 'Good night!'), ok: false, why: { [base]: s.note, en: 'It is morning.' }, reply: line(s.bye, s.byeBase, 'Goodbye!') },
        ] },
      ],
    },
  }))

  const stories = parsed(await call('list_stories', {}))
  const storyId = (Array.isArray(stories) ? stories : (stories.stories ?? []))[0]?.id
  const story = parsed(await call('get_story', { id: storyId }))
  ok(`publish_story takes a story (${storyId}, as a new one)`, await call('publish_story', { story: { ...(story.spec ?? story.story ?? story), id: `smoke-${lang}-story`, continues: undefined, order: undefined }, note: 'a story' }))

  ok('publish_reading takes a reading', await call('publish_reading', {
    note: 'a reading',
    reading: {
      id: `smoke-${lang}-reading`, kind: 'page', level: 'A1', title: t(s.word, s.wordBase, s.wordEn), by: spec.people?.[0]?.villager ?? spec.people?.[0]?.id,
      lines: [{ text: t(s.example, s.exampleBase, s.exampleEn) }, { text: t(s.bye, s.byeBase, 'Goodbye!') }],
      questions: [{ type: 'true_false', ask: t(s.example, s.exampleBase, s.exampleEn), answer: true, explain: t(s.example, s.exampleBase, s.exampleEn) }],
    },
  }))

  const cast = parsed(await call('list_villagers', {}))
  const castList: any[] = Array.isArray(cast) ? cast : (cast.villagers ?? [])
  const v = parsed(await call('get_villager', { id: castList[0]?.id }))
  const villager = v.spec ?? v.villager ?? v
  ok(`publish_villager takes a villager back (${castList[0]?.id})`, await call('publish_villager', { villager: { ...villager, lines: { ...villager.lines, greet: [{ [lang]: s.hello, [base]: s.helloBase, en: 'Hello!' }, ...villager.lines.greet.slice(1)] } }, note: 'a line' }))
  const arrival = parsed(await call('get_arrival', { id: castList[1]?.id }))
  const a = arrival.spec ?? arrival.arrival ?? arrival
  check(`${village}: get_arrival gives what the app would play (${castList[1]?.id})`, !!a && typeof a === 'object' && Object.keys(a).length > 0, text(await call('get_arrival', { id: castList[1]?.id })).slice(0, 300))
  ok('publish_arrival takes an arrival in its language', await call('publish_arrival', { arrival: { ...a, schema: 'lani.arrival/v0', id: castList[1]?.id }, note: 'an arrival' }))

  const g = { it: { form: 'scalette', lemma: 'scaletta', gender: 'f' }, de: { form: 'Leiterchen', lemma: 'Leiterchen', gender: 'n' }, en: { form: 'stepladders', lemma: 'stepladder', gender: undefined } }[lang]!
  ok('publish_gloss takes a word of its language, in its base', await call('publish_gloss', { form: g.form, lemma: g.lemma, pos: 'noun', gloss: [s.wordBase], gloss_lang: base, gender: g.gender, grammar: s.note }))
  const look = await get(`/lookup?w=${encodeURIComponent(g.form)}`)
  check(`${village}: … and the app's lookup shows it`, JSON.stringify(look).includes(s.wordBase), look)
  ok('publish_sentence_note takes a note on its sentence', await call('publish_sentence_note', { sentence: s.example, text: s.note, gloss_lang: base }))

  // "language" left out is the village's, never Slovene
  const unsaid = await call('publish_pack', {
    pack: {
      schema: 'lani.pack/v0', id: `smoke-${lang}-pack-2`, title: t(s.word, s.wordBase, s.wordEn), emoji: '🪜', level: 'A1', description: t(s.example, s.exampleBase, s.exampleEn),
      words: Array.from({ length: 8 }, (_, i) => ({ id: `w${i}`, [lang]: `${s.word} ${i + 10}`, [base]: `${s.wordBase} ${i + 10}`, en: `${s.wordEn} ${i + 10}` })),
    },
  })
  ok('… a pack that leaves its "language" out is in the village\'s', unsaid)
  const { language: _s, ...storyUnsaid } = (story.spec ?? story.story ?? story) as any
  ok('… a story too', await call('publish_story', { story: { ...storyUnsaid, id: `smoke-${lang}-story-2`, continues: undefined, order: undefined } }))
  const { language: _c, ...sceneUnsaid } = spec
  ok('… a scene too', await call('publish_scene', { scene: sceneUnsaid }))
}
