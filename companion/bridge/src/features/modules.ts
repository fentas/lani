// Practice modules (lani.module/v0): published by the tutor, served to the app.
import { badNote, fail, noteIn, ok } from '../channel'
import type { FeatureFactory } from '../feature'
import { json } from '../http'
import { validateModule } from '../spec'
import { corpus } from '../voice'

const instructions = `
To create, change or retire practice content in the app, load the lani-studio skill first, then use
publish_module / list_modules / get_module / rollback_module. Never write module files directly.
`.trim()

export const modules: FeatureFactory = ({ modules: store, events, voice, grammar }) => ({
  instructions,
  routes: [
    { method: 'GET', path: '/modules', handle: () => json(store.list()) },
    {
      method: 'GET',
      path: /^\/modules\/([a-z0-9-]+)$/,
      handle: ({ url, params: [id] }) => {
        const v = url.searchParams.get('version')
        const m = store.get(id, v ? Number(v) : undefined)
        return m ? json(m) : json({ error: 'not found' }, 404)
      },
    },
  ],
  tools: [
    {
      name: 'publish_module',
      description:
        'Validate a lani.module/v0 spec and publish it to the app as a new version. Returns validation errors instead of publishing if the spec is invalid. Load the lani-studio skill before using.',
      inputSchema: {
        type: 'object',
        properties: {
          spec: { type: 'object', description: 'The module spec (lani.module/v0). The bridge assigns the version.' },
          note: { type: 'string', description: 'One-line changelog shown in the app' },
        },
        required: ['spec'],
      },
      handle: args => {
        const v = validateModule(args.spec)
        if (!v.ok) return fail(`spec invalid, nothing published:\n${v.errors}`)
        const note = noteIn.safeParse(args.note)
        if (!note.success) return badNote()
        const version = store.publish(v.spec)
        events.emit({ type: 'module_published', id: v.spec.id, version, title: v.spec.title, note: note.data ?? undefined })
        voice.enqueue(corpus({ packs: [], modules: [v.spec], scenarios: [] }))
        // an exercise's grammar page the book doesn't have: said back, not refused (the page may come next)
        const pages = new Set(grammar.all().map(p => p.id))
        const missing = [...new Set(v.spec.exercises.flatMap(e => (e.grammar && !pages.has(e.grammar) ? [e.grammar] : [])))]
        const note_ = missing.length ? `\nNote: no grammar page ${missing.map(id => `"${id}"`).join(', ')} yet (list_grammar; publish_grammar)` : ''
        // a drill for a task (quest.helps) that isn't published or is another villager's: said back too, the app then
        // doesn't hold that task back behind the drill
        const q = v.spec.quest
        const task = q?.helps ? store.get(q.helps) : undefined
        const helps = !q?.helps
          ? ''
          : !task
            ? `\nNote: quest.helps names "${q.helps}", which isn't published (list_modules)`
            : task.quest?.giver !== q.giver
              ? `\nNote: quest.helps names "${q.helps}", whose request is ${task.quest ? `${task.quest.giver}'s` : "no villager's"}, not ${q.giver}'s: a drill helps a task at the same giver`
              : ''
        return ok(`published ${v.spec.id} v${version} (${v.spec.exercises.length} exercises)${note_}${helps}`)
      },
    },
    {
      name: 'list_modules',
      description: 'List published modules with their current version, level and target patterns.',
      inputSchema: { type: 'object', properties: {} },
      handle: () => ok(JSON.stringify(store.list(), null, 2)),
    },
    {
      name: 'get_module',
      description: 'Fetch a published module spec (current version unless version is given).',
      inputSchema: {
        type: 'object',
        properties: { id: { type: 'string' }, version: { type: 'number' } },
        required: ['id'],
      },
      handle: args => {
        const m = store.get(String(args.id), args.version)
        return m ? ok(JSON.stringify(m, null, 2)) : fail(`no module ${args.id}${args.version ? ` v${args.version}` : ''}`)
      },
    },
    {
      name: 'rollback_module',
      description: 'Point a module back to an earlier published version.',
      inputSchema: {
        type: 'object',
        properties: { id: { type: 'string' }, version: { type: 'number' } },
        required: ['id', 'version'],
      },
      handle: args => {
        if (!store.rollback(String(args.id), Number(args.version))) return fail('unknown module or version')
        events.emit({ type: 'module_published', id: args.id, version: Number(args.version), note: 'rollback' })
        return ok(`${args.id} now at v${args.version}`)
      },
    },
  ],
})
