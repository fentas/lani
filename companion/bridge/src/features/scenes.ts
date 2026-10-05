// Scenes (lani.scene/v0): close-ups of the village the learner steps into. The app gets them
// resolved (every object with its word); the tutor lists, reads, publishes and removes them.
import { join } from 'node:path'
import { badNote, fail, noteIn, ok } from '../channel'
import { cultureProjects } from '../cultures'
import type { FeatureFactory } from '../feature'
import type { GameDoc } from '../game'
import { json } from '../http'
import { labelShown } from '../langs'
import { learnerLangs } from '../learners'
import { localDate } from '../rhythm'
import { knownIds, Names, residentsNamed, sceneMentions } from '../mentions'
import { artsOf, dialogGrammar, forApp, forHost, validateScene, variantsOf, type Dialog, type LoadedScene, type Scene } from '../scenes'
import { attachStories } from '../stories'
import { allHeard, variantNote, VariantAsks, weakSpots } from '../variants'
import { learnerLevel } from '../villagers'
import { corpus } from '../voice'

const instructions = `
Scenes are the village's close-ups (the campfire, the forest, the kitchen, the square; lani.scene/v0,
companion/SCENES.md): tappable objects that teach pack words, and happenings (someone is there at a
time of day, on some weekdays, by chance) with a short dialog of Slovene choices the learner answers.
list_scenes shows what exists and what's on when, get_scene one scene's spec, publish_scene adds or
changes a scene (a tutor scene with a curated id replaces it; remove_scene brings the curated one
back). To stage a happening for a weak point or a season, load lani-studio ("Scenes"). The app pays
the dialog rewards and counts learned words itself (words_learned): nothing to persist here. A thing or a
happening can need what the village has built ("needs": a building, a project's steps, or "before" it: France
talks of the mill he wants until it grinds); the app draws and offers it only then. The
village's culture pack brings places only it has (companion/cultures/<id>/scenes, "culture" in
list_scenes: the landscape the learner reaches by tapping the horizon, from "spot:horizon"; a place a village
project leads into once it is finished, from "project:<id>", like the vineyard from its terraces). A happening
with "stories": true (Stari Janez by the fire in the evening) plays tonight's story of its person's instead of a
dialog (list_stories, publish_story): the app gets that scene with its teller's stories. A dialog (and a request, a
line) names only people who are in the village and whom the learner has met (get_village: people.known): the app keeps
a happening that names anyone else back until they are (Zala's "Anton toči med!" waits for Anton), and publish_scene
says which. A happening may have several dialogs, its variants ("dialogs": ids of the scene's dialogs or "variants"):
the app plays one not heard yet, then, once all are heard, the one heard longest ago after a few days, never the same
twice in a row. A dialog may count something with the village's number of the day ("count": {n} and forms like
{ovca|ovci|ovce|ovc}, the wrong choices "wrong_form": companion/SCENES.md "Numbers"). dialog_variants_heard: the
learner has heard every dialog of a happening; write a fresh one with publish_dialog_variant (the same situation, other
words, another point of grammar, at their level, around a weak spot the note names), no message to the learner needed;
remove_dialog_variant takes one back. In an art with a stage (companion/SCENES.md "Stage directions": the living room,
the tent) a line or a reply can move its people ("act": {"zala": {"to": "behind-door", "pose": "hide"}}: to a spot of the
stage, in a pose; "act_stays": true keeps it until midnight), and a learner's turn can be answered by tapping the picture
(each choice's "tap": a spot, a slot of the art or a person; a tap turn may have 4 choices): Zala hides, and the learner
finds her ("Za vrati si!").
Every text you write for the app (a scene, a story, a villager, an arrival, a reading, a module, a pack, a role-play, a
grammar page) speaks to whoever learns: never write the learner's name or their gender out. Their name is {learner} (its
cases {learner:gen}, {learner:dat}, {learner:acc}, {learner:loc}, {learner:ins}; {learner:poss}a hiša: "Markova hiša", "Anina hiša"),
and every form that agrees with them is a pair, the man's first: "Si {m:lačen|f:lačna}?", "{m:Rad|f:Rada} bi kavo.",
"{m:fant|f:dekle}", "Sei {m:stanco|f:stanca}?", "{m:lad|f:lass}", in the translations too; a turn's choices still differ
for a woman. The app and the bridge say it to the learner of the profile (companion/SCENES.md "The learner in the
content"); publish_* refuses a malformed one.
`.trim()

function sceneInfo(s: LoadedScene) {
  const { schema: _, objects, people, happenings, dialogs, variants, ...meta } = s
  const tutor = new Set(variants.filter(d => d.source === 'tutor').map(d => d.id))
  return {
    ...meta,
    objects: objects.length,
    people: people.map(p => `${p.id} (${p.name})`),
    happenings: happenings.map(h => ({
      id: h.id, title: h.title, who: h.who, when: h.when, weekdays: h.weekdays, chance: h.chance, dialog: h.dialog,
      ...(h.dialogs ? { dialogs: h.dialogs } : {}), ...(h.dialogs?.some(d => tutor.has(d)) ? { tutor_variants: h.dialogs.filter(d => tutor.has(d)) } : {}),
      reward: h.reward, needs: h.needs, ...(h.guests ? { guests: true } : {}), ...(h.stories ? { stories: true } : {}),
    })),
    dialogs: dialogs.map(d => d.id),
    ...(variants.length ? { variants: variants.map(d => d.id) } : {}),
  }
}

export const scenes: FeatureFactory = ({ scenes, stories, packs, scenarios, villagers, events, voice, cfg, culture, game, grammar, channel, learner, addressee, log }) => {
  const asks = new VariantAsks(join(cfg.appDir, 'variant-asks.json'))
  // what the last check saw (the day, and what was heard): a village write that changes none of it (most) costs nothing
  let seen = ''
  /**
   * Asks the tutor for a fresh variant of the happenings whose every dialog the learner has heard, a few a day
   * (VariantAsks, <data>/app/variant-asks.json): after a village write ([doc]) or at start, as stories_low does.
   */
  async function askForVariants(doc: GameDoc | undefined, now = new Date()) {
    try {
      const state = doc?.state as { dialogsHeard?: unknown } | undefined
      if (!state?.dialogsHeard) return // nothing heard yet (or an older app)
      const today = localDate(now)
      const key = `${today}|${JSON.stringify(state.dialogsHeard)}`
      if (key === seen) return
      seen = key
      const due = asks.due(allHeard(scenes.resolved(), state), today)
      if (!due.length) return
      const weak = weakSpots(learner.state().databases?.mistakes_db)
      const nameOf = (id: string) => villagers.get(id)?.name
      for (const a of due) {
        asks.mark(a, today) // before sending: a failing channel mustn't ask again on every write
        await channel.notify(variantNote(a, learnerLevel(cfg.dataDir), weak, nameOf), {
          kind: 'dialog_variants_heard',
          conversation_id: 'main',
          msg_id: `variants-${a.key.replace('/', '-')}-${Date.now().toString(36)}`,
        })
        log(`variants: asked for a fresh dialog of ${a.key}`)
      }
    } catch (e) {
      log(`variants: ${(e as Error).message}`) // a background job must never end the process
    }
  }
  // a scene's `needs` may name the village projects of the culture (the mill, the bridge)
  const projects = cultureProjects(culture.dir, culture.id)
  const refs = () => ({ packs: packs.all(), scenarios: scenarios.all(), villagers: villagers.all(), projects })
  /** The grammar pages [scene]'s choices name that the book lacks: said back, not refused (the page may come next). */
  const lacking = (scene: Scene): string => {
    const pages = new Set(grammar.all().map(p => p.id))
    const missing = [...new Set(dialogGrammar([...scene.dialogs, ...scene.variants]).map(([, g]) => g).filter(g => !pages.has(g)))]
    return missing.length ? `\nNote: no grammar page ${missing.map(g => `"${g}"`).join(', ')} yet (list_grammar; publish_grammar): those whys lead nowhere` : ''
  }
  /**
   * What the app keeps back of [scene] for now: a happening whose texts name someone who isn't in the village or whom the
   * learner hasn't met (companion/VILLAGERS.md, "Who a text may name"); said after publishing, empty when nothing waits.
   */
  const waiting = (scene: Scene): string => {
    const state = game.read()?.state
    const known = knownIds(state)
    if (!known) return ''
    const names = new Names([...villagers.all(), ...residentsNamed(state)], scene.language)
    const held = sceneMentions(scene, names)
      .map(m => ({ ...m, named: m.named.filter(id => !known.has(id)) }))
      .filter(m => m.named.length)
    if (!held.length) return ''
    return (
      `\n⚠ Waits in the app until everyone it names is in the village and met (get_village: people.known): ` +
      held.map(m => `happening "${m.happening}" names ${m.named.map(id => `${names.names.get(id) ?? id} (${id})`).join(', ')}`).join('; ') +
      '. Name only people who are here and met, or keep it for later.'
    )
  }
  /** A scene's title as the app shows it: "target · base" in the learner's pair. */
  const titleOf = (s: LoadedScene['title']) => {
    const { target, base } = learnerLangs(cfg.dataDir, culture.manifest?.language ?? 'sl')
    return labelShown(s, target, base)
  }
  return {
    instructions,
    routes: [
      // the village's own app: without what is meant for guests (a visitor gets it: /town/scenes, features/towns.ts); a scene
      // where someone tells stories comes with their stories (the app picks tonight's)
      // as far as the app draws them (?arts=, forApp): an older app gets no place it can't paint
      {
        method: 'GET',
        path: '/scenes',
        addressed: true,
        handle: ({ url }) => {
          const told = stories.resolved()
          return json(forApp(scenes.resolved(), artsOf(url)).map(s => attachStories(forHost(s), told)))
        },
      },
      {
        method: 'GET',
        path: /^\/scenes\/([a-z0-9-]+)$/,
        addressed: true,
        handle: ({ url, params: [id] }) => {
          const s = forApp([scenes.resolve(id)].filter(x => x !== undefined), artsOf(url))[0]
          return s ? json(attachStories(forHost(s), stories.resolved())) : json({ error: 'not found' }, 404)
        },
      },
    ],
    tools: [
      {
        name: 'list_scenes',
        description: "List the village's scenes (curated and tutor-written): where each opens, its people, and its happenings with their times, weekdays and chances.",
        inputSchema: { type: 'object', properties: {} },
        handle: () => ok(JSON.stringify(scenes.all().map(sceneInfo), null, 2)),
      },
      {
        name: 'get_scene',
        description: 'One scene as its lani.scene/v0 spec (objects, people, happenings, dialogs), to change and publish again.',
        inputSchema: { type: 'object', properties: { id: { type: 'string', description: 'The scene id' } }, required: ['id'] },
        handle: args => {
          const s = typeof args.id === 'string' ? scenes.get(args.id) : undefined
          return s ? ok(JSON.stringify(s, null, 2)) : fail(`no scene "${args.id}"; list_scenes shows the ids`)
        },
      },
      {
        name: 'publish_scene',
        description:
          'Validate a lani.scene/v0 scene and publish it to the app (replaces a tutor scene with the same id; a curated id is replaced until remove_scene). Returns validation errors instead of publishing if invalid. Load the lani-studio skill before using. Say the learner {learner} and what agrees with them {m:…|f:…} (companion/SCENES.md, the learner in the content).',
        inputSchema: {
          type: 'object',
          properties: {
            scene: { type: 'object', description: 'The scene (lani.scene/v0)' },
            note: { type: 'string', description: 'One-line announcement shown in the app' },
          },
          required: ['scene'],
        },
        handle: args => {
          const v = validateScene(args.scene, refs())
          if (!v.ok) return fail(`scene invalid, nothing published:\n${v.errors}`)
          const note = noteIn.safeParse(args.note)
          if (!note.success) return badNote()
          const replaced = scenes.isCurated(v.scene.id)
          scenes.publish(v.scene)
          events.emit({ type: 'scene_published', id: v.scene.id, title: titleOf(v.scene.title), emoji: v.scene.emoji, note: note.data ?? undefined })
          const resolved = scenes.resolve(v.scene.id)
          // The villagers decide the voices; only the scene's own texts are queued.
          if (resolved) voice.enqueue(corpus({ packs: [], modules: [], scenarios: [], scenes: [resolved], villagers: villagers.all(), learner: addressee() }).filter(i => i.source === `scene:${resolved.id}`))
          return ok(
            `published scene ${v.scene.id} (${v.scene.objects.length} objects, ${v.scene.happenings.length} happenings, ${v.scene.dialogs.length} dialogs)` +
              (replaced ? `; it replaces the curated scene "${v.scene.id}" until remove_scene` : '') +
              waiting(v.scene) +
              lacking(v.scene),
          )
        },
      },
      {
        name: 'remove_scene',
        description: 'Remove a tutor-published scene. A curated scene with the same id shows again.',
        inputSchema: { type: 'object', properties: { id: { type: 'string', description: 'The scene id' } }, required: ['id'] },
        handle: args => {
          const id = typeof args.id === 'string' ? args.id : ''
          if (!scenes.remove(id)) return fail(`no tutor scene "${id}" to remove`)
          events.emit({ type: 'scene_removed', id })
          return ok(`removed tutor scene ${id}${scenes.get(id) ? ' (the curated one is back)' : ''}`)
        },
      },
      {
        name: 'publish_dialog_variant',
        description:
          "Add a fresh dialog to a happening of a scene (a variant: companion/SCENES.md \"Variants\"), e.g. when dialog_variants_heard says the learner has heard all its dialogs. The same situation (the happening's title, time and person), other words, another point of grammar, at the learner's level; a dialog of lani.scene/v0 (3-8 lines, the person's line first, a reaction on every wrong choice, optionally a count with {n}, a memory of its own). It plays the next time the happening comes, marked as the tutor's. Validates it with the scene; a tutor variant with the same id is replaced. Load the lani-studio skill before using. Say the learner {learner} and what agrees with them {m:…|f:…} (companion/SCENES.md, the learner in the content).",
        inputSchema: {
          type: 'object',
          properties: {
            scene: { type: 'string', description: 'The scene id (list_scenes)' },
            happening: { type: 'string', description: 'The happening id in that scene' },
            dialog: { type: 'object', description: 'The dialog (lani.scene/v0: id, lines, optional count, memory, talk, sky_stays, fx_stays)' },
            note: { type: 'string', description: 'Optional one-line announcement shown in the app' },
          },
          required: ['scene', 'happening', 'dialog'],
        },
        handle: args => {
          const sceneId = typeof args.scene === 'string' ? args.scene : ''
          const hid = typeof args.happening === 'string' ? args.happening : ''
          const s = scenes.get(sceneId)
          if (!s) return fail(`no scene "${sceneId}"; list_scenes shows the ids`)
          const h = s.happenings.find(x => x.id === hid)
          if (!h) return fail(`scene ${sceneId} has no happening "${hid}" (its happenings: ${s.happenings.map(x => x.id).join(', ')})`)
          if (h.stories) return fail(`${sceneId}/${hid} tells tonight's story: it has no dialogs (publish_story writes a story)`)
          const given = (args.dialog ?? {}) as Record<string, unknown>
          const did = typeof given.id === 'string' ? given.id : ''
          // the tutor's own variant with this id is replaced; any other dialog of the scene keeps its id
          const mine = (d: Dialog) => d.id === did && d.source === 'tutor'
          if ([...s.dialogs, ...s.variants].some(d => d.id === did && !mine(d))) return fail(`scene ${sceneId} has a dialog "${did}" already: give the variant an id of its own ("${hid}-…")`)
          if (given.guests !== undefined) return fail('a variant is for whoever its happening is for: leave "guests" out')
          const { source: _source, published_at: _at, culture: _culture, ...spec } = s
          const candidate = {
            ...spec,
            variants: [...s.variants.filter(d => !mine(d)), { ...given, source: 'tutor' }],
            happenings: s.happenings.map(x => (x.id !== hid ? x : { ...x, dialogs: [...variantsOf(x).filter(v => v !== did), did] })),
          }
          const v = validateScene(candidate, refs())
          if (!v.ok) return fail(`dialog invalid, nothing published:\n${v.errors}`)
          const note = noteIn.safeParse(args.note)
          if (!note.success) return badNote()
          const dialog = v.scene.variants.find(d => d.id === did)!
          scenes.publishVariant(sceneId, hid, dialog)
          events.emit({ type: 'variant_published', scene: sceneId, happening: hid, id: did, title: titleOf(s.title), emoji: h.marker, note: note.data ?? undefined })
          const resolved = scenes.resolve(sceneId)
          if (resolved) voice.enqueue(corpus({ packs: [], modules: [], scenarios: [], scenes: [resolved], villagers: villagers.all(), learner: addressee() }).filter(i => i.source === `scene:${resolved.id}`))
          const now = resolved?.happenings.find(x => x.id === hid)
          return ok(
            `published variant ${did} of ${sceneId}/${hid} (its dialogs now: ${variantsOf(now ?? h).join(', ')}); it plays the next time the happening comes, as the one not heard yet` +
              waiting({ ...v.scene, happenings: v.scene.happenings.filter(x => x.id === hid).map(x => ({ ...x, dialogs: [did] })) }),
          )
        },
      },
      {
        name: 'remove_dialog_variant',
        description: 'Remove a dialog variant the tutor published (publish_dialog_variant) from a happening.',
        inputSchema: {
          type: 'object',
          properties: { scene: { type: 'string' }, happening: { type: 'string' }, id: { type: 'string', description: 'The variant\'s dialog id' } },
          required: ['scene', 'happening', 'id'],
        },
        handle: args => {
          const [sceneId, hid, id] = [args.scene, args.happening, args.id].map(x => (typeof x === 'string' ? x : ''))
          if (!scenes.removeVariant(sceneId, hid, id)) return fail(`no tutor variant "${id}" of ${sceneId}/${hid} to remove (list_scenes: tutor_variants)`)
          events.emit({ type: 'variant_removed', scene: sceneId, happening: hid, id })
          return ok(`removed the tutor's variant ${id} of ${sceneId}/${hid}`)
        },
      },
    ],
    // the learner heard every dialog of a happening: the tutor writes a fresh one (only the session that serves the app asks)
    start() {
      if (!cfg.serveApp || process.env.LANI_VARIANTS === 'off') return
      game.onWrite(doc => void askForVariants(doc))
      void askForVariants(game.read())
    },
  }
}
