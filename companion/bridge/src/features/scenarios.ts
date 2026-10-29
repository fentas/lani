// Role-play scenarios (lani.scenario/v0): the tutor plays a character, Jan talks. The turns
// themselves arrive as /message kind roleplay (features/chat.ts adds the scenario brief).
import { badNote, fail, noteIn, ok } from '../tool'
import type { FeatureFactory } from '../feature'
import { json } from '../http'
import { LANGUAGES } from '../learners'
import { validateScenario } from '../scenarios'
import { corpus } from '../voice'
import { talkPlace } from './villagers'

/** What a character says to a line they didn't understand, in each language: a friendly "What?". */
const WHAT: Record<string, string> = { sl: 'Prosim?', it: 'Come?', de: 'Wie bitte?', en: 'Sorry?' }

/** A mishearing in the course's language: a near-miss matched to the conversation, a lost ending, and its recast. */
const MISHEARD: Record<string, { near: string; ending: string; recast: string }> = {
  sl: { near: 'prijatel → prijatelj', ending: 'lepi/lepa', recast: '"Ja, tvoj prijatelj!"' },
  it: { near: 'amigo → amico', ending: 'piccoli/piccola', recast: '"Sì, il tuo amico!"' },
  de: { near: 'Freunt → Freund', ending: 'kleine/kleiner', recast: '"Ja, dein Freund!"' },
  en: { near: 'frient → friend', ending: 'friend/friends', recast: '"Yes, your friend!"' },
}

/**
 * The role-play paragraph in the course's language ([target]), with translations in the learner's base ([base]). The
 * data keys stay "sl" and "en" in every language (the app reads them). What heard is: the speech recognizer's reading
 * of a learner, maybe a child, which mishears (docs/plans/05-built/stt-talk.md): a word it may have misheard is
 * recast, never corrected.
 */
export function scenariosInstructions(target = 'sl', base = 'en'): string {
  const lang = LANGUAGES[target] ?? target
  const baseName = LANGUAGES[base] ?? base
  const m = MISHEARD[target] ?? MISHEARD.it
  const keys = target === 'sl' && base === 'en'
    ? '{"sl": "...", "en": "<translation>"'
    : `{"sl": "<your line, in ${lang}: the key is sl in every language>", "en": "<its translation, in ${baseName}>"`
  return `
Role-play ("Pogovor · Talk"): kind roleplay carries {scenario_id, turn, heard, alternatives?, unclear?, takes?,
typed?} plus the scenario brief (role, setting, goals). heard is what Jan typed (typed: true) or what speech
recognition made of Jan's line: Jan is a learner, maybe a child, and the recognizer mishears. alternatives are its
other readings of the same take (one may have a heard non-word matched to a word of the conversation: ${m.near}),
unclear the words it was unsure of, takes how many times Jan said the line before it went out (more than 2: Jan
struggled to be understood; keep your next line easy). Never correct a word the recognizer may have misheard: one the
alternatives read otherwise, one in unclear, a word a letter or two from one of the conversation's, an ending lost in
a mumble (${m.ending}); recast it naturally in your reply instead (${m.recast}). Correct only what every reading agrees on and is clearly Jan's own (a typed line is). A line that is only a
sound ("Ooooh", "mmm") gets a friendly "${WHAT[target] ?? WHAT.en}" in character, never a correction.
Stay in character as the role and answer in simple ${lang} at Jan's level, 1-2 short sentences, as the text and in
data: ${keys}, "correction"?: "<only for a real mistake: Jan's line corrected, ${lang} + short ${baseName} why>",
"goals_done": [indices of goals reached so far], "end"?: true when the scene reaches a natural end}. data.hint = true
means Jan is stuck: reply {"hint": true, "sl": "<one line Jan could say next${target === 'sl' ? '' : `, in ${lang}`}>", "en": "..."}
without moving the scene on. kind roleplay_end carries the transcript: reply out of character with a short debrief
(what went well, 2 things to practise; a word only the recognizer got wrong is no mistake) and data {"debrief":
true}, then persist it with the lani-db-updater skill as a speaking session. To write new scenarios ("let's practise
at the post office"), load lani-studio and use publish_scenario.
`.trim()
}

export const scenarios: FeatureFactory = ({ scenarios, events, voice, addressee, cfg, culture, profile }) => ({
  instructions: (p => scenariosInstructions(p.target, p.base))(talkPlace({ cfg, culture, profile })),
  routes: [
    { method: 'GET', path: '/scenarios', addressed: true, handle: () => json(scenarios.all()) },
    {
      method: 'GET',
      path: /^\/scenarios\/([a-z0-9-]+)$/,
      addressed: true,
      handle: ({ params: [id] }) => {
        const s = scenarios.get(id)
        return s ? json(s) : json({ error: 'not found' }, 404)
      },
    },
  ],
  tools: [
    {
      name: 'publish_scenario',
      description:
        'Validate a lani.scenario/v0 role-play scenario and publish it to the app (replaces a tutor scenario with the same id). Returns validation errors instead of publishing if invalid. Load the lani-studio skill before using. Say the learner {learner} and what agrees with them {m:…|f:…} (companion/SCENES.md, the learner in the content).',
      inputSchema: {
        type: 'object',
        properties: {
          scenario: { type: 'object', description: 'The scenario (lani.scenario/v0)' },
          note: { type: 'string', description: 'One-line announcement shown in the app' },
        },
        required: ['scenario'],
      },
      handle: args => {
        const v = validateScenario(args.scenario)
        if (!v.ok) return fail(`scenario invalid, nothing published:\n${v.errors}`)
        const note = noteIn.safeParse(args.note)
        if (!note.success) return badNote()
        const err = scenarios.publish(v.scenario)
        if (err) return fail(err)
        events.emit({ type: 'scenario_published', id: v.scenario.id, title: v.scenario.title, emoji: v.scenario.emoji, note: note.data ?? undefined })
        voice.enqueue(corpus({ packs: [], modules: [], scenarios: [v.scenario], learner: addressee() }))
        return ok(`published scenario ${v.scenario.id} (${v.scenario.goals.length} goals); curated and published: ${scenarios.all().map(s => s.id).join(', ')}`)
      },
    },
  ],
})
