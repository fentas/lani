// lani-bridge: a Claude Code channel that connects the Lani Android app to the always-on tutor
// session. The app talks HTTP. Two ways to run it (docs/plans/04-bridge-service.md):
// - classic: Claude Code spawns it over stdio inside the tutor session (src/index.ts);
// - service: it runs on its own (src/service.ts, companion/bin/lani-bridge), and the session's shim (src/shim.ts)
//   reaches it over a unix socket, so a code change needs only `lani-bridge restart`, not a tutor restart.
//
// This file only wires the parts: the shared stores (the context), then the features. Each feature
// (src/features/*.ts) brings its HTTP routes, MCP tools and a paragraph of channel instructions.
import { existsSync } from 'node:fs'
import { join } from 'node:path'
import { addresseeOf, renderDeep } from './addressee'
import { ArrivalStore } from './arrivals'
import { runService } from './bridge-service'
import { castVoiced, useCastFile } from './cast'
import { Channel, ok, type ChannelOptions } from './channel'
import { loadConfig, log } from './config'
import { cultureDir, cultureProjects, resolveCulture, tutorOf, validateCulture } from './cultures'
import { Events } from './events'
import { Family } from './family'
import type { Ctx, Feature, FeatureFactory } from './feature'
import { arrivals } from './features/arrivals'
import { chat } from './features/chat'
import { culture as cultureFeature } from './features/culture'
import { events } from './features/events'
import { family, familyWords } from './features/family'
import { friendship } from './features/friendship'
import { game } from './features/game'
import { drills as drillsFeature } from './features/drills'
import { grammar as grammarFeature } from './features/grammar'
import { level } from './features/level'
import { lexicon } from './features/lexicon'
import { modules } from './features/modules'
import { packs } from './features/packs'
import { pairing } from './features/pairing'
import { townQuestions } from './features/questions'
import { readings } from './features/readings'
import { releases } from './features/releases'
import { reviews } from './features/reviews'
import { rhythm } from './features/rhythm'
import { scenarios } from './features/scenarios'
import { scenes } from './features/scenes'
import { sentences } from './features/sentences'
import { stories } from './features/stories'
import { stt } from './features/stt'
import { towns } from './features/towns'
import { villagers } from './features/villagers'
import { voice } from './features/voice'
import { GameStore } from './game'
import { DrillStore, drillsDir } from './drills'
import { grammarDir, GrammarStore } from './grammar'
import { addressed, bearer, json, loadToken, router, serve, type Route } from './http'
import { FormsBook } from './forms'
import { Learner } from './learner'
import { channelRules, INSTRUCTIONS_LIMIT, learnerFacts, learnerInstructions, learnerLangs, profileFromEnv } from './learners'
import { Lexicon } from './lexicon'
import { Outbox } from './outbox'
import { PackStore, packsDirs } from './packs'
import { Devices, loadBridgeKey } from './pairing'
import { ReadingStore } from './readings'
import { ScenarioStore } from './scenarios'
import { SceneStore, scenesDirs } from './scenes'
import { StoryStore, storiesDirs } from './stories'
import { ModuleStore } from './store'
import { VillagerStore } from './villagers'
import { VisitTalks } from './visits'
import { enginesFromEnv, ffmpegRecordingLeveller, VoiceStore } from './voice'
import { Profiles } from './profiles'
import { resultsDirOf, voiceDir } from './paths'

export type BridgeOptions = {
  /** classic: over stdio in the tutor session; service: on its own, the session's shim on a unix socket. */
  mode: 'stdio' | 'service'
  /** More features after the built-in ones (a test's probe tool). */
  extraFeatures?: FeatureFactory[]
}

export async function startBridge(o: BridgeOptions) {
  // A long-running channel: one failing background job must not take the app's connection down.
  process.on('unhandledRejection', e => log(`unhandled rejection: ${(e as Error)?.stack ?? e}`))
  process.on('uncaughtException', e => log(`uncaught exception: ${e.stack ?? e}`))

  const env = process.env
  // The service serves the app always (its features' jobs for the app run too: scene variants, the dictionary download).
  const cfg = loadConfig(env, { service: o.mode === 'service' })
  const { projectDir, appDir, dataDir } = cfg
  // Which learner this bridge serves: lani-session sets LANI_PROFILE for every profile but the default.
  const profile = profileFromEnv(env)
  const isApp = bearer(loadToken(join(appDir, 'bridge-token'), env.LANI_BRIDGE_TOKEN, log, 'app'))
  // Phones paired by QR code have tokens of their own; the bridge token above keeps working.
  const devices = new Devices(appDir, { log })
  process.on('exit', () => devices.flushSeen())
  // The learner's village's culture pack (companion/cultures): its cast is the curated villagers; a pack that can't be
  // played (a stub, unknown) leaves the default, as the app does.
  const culturesDir = env.LANI_CULTURES_DIR ?? join(projectDir, 'companion/cultures')
  const culture = resolveCulture(culturesDir, profile.culture)
  if (culture.why) log(`culture: ${culture.why}; playing ${culture.id}`)
  // What the village speaks, and who speaks it: a pack in another language has its own voice cast (voice-cast.json in the
  // pack). Its clips are in its language; the local Gepard worker speaks only Slovene; a cast with no voices yet makes no
  // clips at all, and the app speaks with the phone's text-to-speech.
  const language = culture.manifest?.language ?? 'sl'
  /** A curated directory of Slovene content as it is, of another language in its subdirectory (companion/modules/it). */
  const ofLanguage = (root: string) => (language === 'sl' ? root : join(root, language))
  const castFile = join(cultureDir(culturesDir, culture.id), 'voice-cast.json')
  if (existsSync(castFile)) useCastFile(castFile)
  const { eleven, gepard } = enginesFromEnv(env, log, language)
  const engines = language === 'sl' ? [eleven, gepard] : castVoiced() ? [eleven] : []
  if (!engines.length) log(`voice: the ${culture.id} voice cast has no voices yet: the app speaks with the phone's text-to-speech`)
  /** The village projects of the culture (its projects.json), which a scene's `needs` may name. */
  const villageProjects = cultureProjects(culturesDir, culture.id)
  const lexiconDir = env.LANI_LEXICON_DIR || join(projectDir, 'companion/lexicon')

  const ctx: Ctx = {
    cfg,
    log,
    profile,
    devices,
    bridgeKey: loadBridgeKey(appDir),
    // The home language's databases, and another language's (visits) in languages/<code>/ (docs/DB_SCRIPTS.md).
    learner: new Learner(projectDir, dataDir, language),
    addressee: () => addresseeOf(dataDir, language),
    events: new Events(),
    channel: new Channel(),
    outbox: new Outbox(join(appDir, 'client-ids.json'), log),
    // The curated modules, scenarios and scenes are the village's language's: Slovene ones in their directories, another
    // language's in a subdirectory of its code (companion/modules/it), none yet: the tutor writes them for the learner.
    modules: new ModuleStore(join(appDir, 'modules'), ofLanguage(env.LANI_MODULES_DIR ?? join(projectDir, 'companion/modules')), log),
    // The curated word packs of the village's language, then its culture pack's own (its festivals' and its places' words).
    packs: new PackStore(packsDirs(env.LANI_PACKS_DIR ?? join(projectDir, 'companion/packs'), culturesDir, culture.id, language), join(appDir, 'packs'), log),
    // The grammar book of the village's language (companion/grammar/<language>: sl, it, de, en), then the tutor's pages.
    grammar: new GrammarStore(grammarDir(env.LANI_GRAMMAR_DIR ?? join(projectDir, 'companion/grammar'), language), language, join(appDir, 'grammar'), log),
    // The car's audio drills of the village's language (companion/drills/<language>): what they name checked against the
    // grammar book, the packs and the cast (read lazily: the context is being built here).
    drills: new DrillStore(drillsDir(env.LANI_DRILLS_DIR ?? join(projectDir, 'companion/drills'), language), language, () => ({ grammar: new Set(ctx.grammar.all().map(p => p.id)), packs: ctx.packs.all(), villagers: ctx.villagers.all() }), log),
    scenarios: new ScenarioStore(ofLanguage(env.LANI_SCENARIOS_DIR ?? join(projectDir, 'companion/scenarios')), join(appDir, 'scenarios'), log),
    // Scenes reference pack words, scenarios and villagers, so the store reads those through the context (lazily: it is being built here).
    // The curated scenes of the village's language, then its culture pack's own places (its landscape at the horizon).
    // What a scene's things and happenings need of the village names the culture's own projects (the mill, the bridge).
    scenes: new SceneStore(scenesDirs(env.LANI_SCENES_DIR ?? join(projectDir, 'companion/scenes'), culturesDir, culture.id, language), join(appDir, 'scenes'), () => ({ packs: ctx.packs.all(), scenarios: ctx.scenarios.all(), villagers: ctx.villagers.all(), projects: villageProjects }), log, join(appDir, 'variants')),
    // The storyteller's evening stories: the culture pack's own (its stories/), then the tutor's; their words in the packs, their teller in the cast.
    stories: new StoryStore(storiesDirs(culturesDir, culture.id), join(appDir, 'stories'), () => ({ packs: ctx.packs.all(), villagers: ctx.villagers.all() }), log),
    // The reading corner: the culture pack's readings (its readings/, read per request), then the tutor's; who reads them in the cast.
    readings: new ReadingStore(join(cultureDir(culturesDir, culture.id), 'readings'), culture.id, join(appDir, 'readings'), language, () => ctx.villagers.all(), log),
    culture: { ...culture, dir: culturesDir },
    villagers: new VillagerStore(env.LANI_VILLAGERS_DIR ?? join(cultureDir(culturesDir, culture.id), 'villagers'), join(appDir, 'villagers'), log),
    // How the people who join the village are introduced: the tutor's (the culture pack's arrivals.json is the app's).
    arrivals: new ArrivalStore(join(appDir, 'arrivals'), log),
    game: new GameStore(join(appDir, 'game.json')),
    family: new Family({
      appDir,
      dataDir,
      log,
      words: () => familyWords(ctx),
      notify: async (content, meta) => void (await ctx.channel.notify(content, meta)),
      emit: e => ctx.events.emit(e),
      // recordings at the loudness of the voice clips (ffmpeg, when the node has it)
      level: ffmpegRecordingLeveller(),
    }),
    voice: new VoiceStore({
      appDir,
      // the voice cache: $LANI_VOICE_CACHE (lani.env's), else <data>/app/voice, the old place while only it has the clips
      dir: voiceDir(appDir, env),
      engines,
      log,
      queueDelayMs: env.LANI_VOICE_QUEUE_DELAY_MS ? Number(env.LANI_VOICE_QUEUE_DELAY_MS) : undefined,
      // short narrator words from before the carrier sentence, re-voiced a day as the app asks for them
      revoiceDaily: env.LANI_VOICE_REVOICE_DAILY ? Number(env.LANI_VOICE_REVOICE_DAILY) : undefined,
      onUpdated: made => ctx.events.emit({ type: 'voice_updated', clips: made }),
    }),
    // Set right below: the profiles share the voice store's database.
    profiles: undefined as unknown as Profiles,
    visitTalks: new VisitTalks(),
    // The village language's dictionary and forms book (companion/lexicon, LANI_LEXICON_DIR for another): read at the first
    // lookup, a download for a language the repository lacks (features/lexicon.ts).
    dictionary: {
      dict: new Lexicon({ dir: lexiconDir, language, base: learnerLangs(dataDir, language).base, tutorFile: join(appDir, 'lexicon.json'), log }),
      book: new FormsBook({ dir: lexiconDir, language, log }),
      language,
    },
  }
  ctx.profiles = new Profiles(ctx.voice.database, {
    eleven,
    sttUrl: env.LANI_STT_URL || 'http://127.0.0.1:8796',
    // their lines as they say them to this learner (Voice Design hears a sample of them)
    cast: () => renderDeep(ctx.villagers.all(), ctx.addressee()),
    language,
    accent: tutorOf(culture.manifest).voice,
    log,
    auto: env.LANI_VOICE_DESIGN !== 'off' && engines.includes(eleven),
    onVoiceChanged: voice => ctx.voice.dropVoice(voice),
    onUpdated: person => ctx.events.emit({ type: 'voice_updated', clips: 0, person }),
  })
  log(`voice: elevenlabs ${eleven.configured ? 'configured' : 'not configured'}, local worker checked on demand, clips in ${ctx.voice.dir}`)
  // The pack the village is in, checked once against its cast, (its scenes) the packs and the grammar book its requests and
  // questions name: what's wrong is logged (the app checks the pack too).
  const checked = validateCulture(culturesDir, culture.id, ctx.villagers.all(), { packs: ctx.packs.all(), scenarios: ctx.scenarios.all() }, new Set(ctx.grammar.all().map(p => p.id)))
  if (!checked.ok) log(`culture ${culture.id} has problems:\n${checked.errors}`)

  // The channel instructions are the features' paragraphs in this order, and so is the tool list.
  // A new feature: a module exporting `(ctx: Ctx) => Feature`, added to this list.
  const features: Feature[] = [chat, modules, grammarFeature, drillsFeature, rhythm, game, packs, lexicon, sentences, scenarios, scenes, stories, readings, villagers, arrivals, cultureFeature, family, voice, stt, reviews, level, events, releases, pairing, towns, friendship, townQuestions, ...(o.extraFeatures ?? [])].map(f => f(ctx))

  // The default learner set up by lani-setup, their data a repository of its own: the instructions name them from their
  // profile and say where their data and session results are.
  const place = profile.isDefault && existsSync(join(dataDir, '.git')) ? { resultsDir: resultsDirOf(dataDir) } : {}
  // Another learner's bridge names them and says where their data is; the default's paragraphs are unchanged.
  const guide = () =>
    learnerInstructions(features.flatMap(f => f.instructions ?? []), profile, learnerFacts(dataDir), dataDir, {
      id: culture.id, language, region: culture.manifest?.region.en, ...tutorOf(culture.manifest),
    }, place)
  // Claude Code cuts a server's instructions at INSTRUCTIONS_LIMIT characters; the guide is several times
  // that. The instructions are the rules that fit, and the tool hands out the whole guide.
  const rulesNow = () => channelRules(profile, learnerFacts(dataDir), dataDir, place)
  const rules = rulesNow()
  if (rules.length > INSTRUCTIONS_LIMIT) log(`the channel rules are ${rules.length} characters: Claude Code cuts them at ${INSTRUCTIONS_LIMIT}`)
  const guideTool = {
    name: 'channel_guide',
    description: "This channel's full instructions: each kind of inbound message and when to use each tool. Call it once before your first reply, and again after the conversation is compacted.",
    inputSchema: { type: 'object', properties: {} },
    handle: () => ok(guide()),
  }

  const channel: ChannelOptions = {
    instructions: rules,
    tools: [guideTool, ...features.flatMap(f => f.tools ?? [])],
    // A child doesn't approve the tutor's tool use: those prompts stay in the terminal and Remote Control.
    permissionRelay: !profile.child,
    onPermissionRequest: p => ctx.events.emit({ type: 'permission_request', ...p }),
  }
  const health: Route = { method: '*', path: '/health', access: 'public', handle: () => json({ ok: true }) }
  const roleOf = (req: Request) => (isApp(req) || devices.authorize(req) ? 'app' : ctx.family.authorized(req) ? 'family' : null)
  // what speaks to the learner goes out rendered for them ({learner}, {m:…|f:…}: addressee.ts)
  const routes = addressed([health, ...features.flatMap(f => f.routes ?? [])], ctx.addressee)
  const where = `data ${dataDir}, culture ${culture.id}${profile.isDefault ? '' : `, profile ${profile.id}${profile.child ? ' (child)' : ''}`}`

  // The service: the app's API always, the session's shim on the control socket, the instructions asked again for
  // every session that attaches.
  if (o.mode === 'service') return runService({ ctx, features, channel: { ...channel, instructions: rulesNow }, routes, roleOf, where })

  await ctx.channel.connect(channel)
  for (const f of features) f.start?.()
  // Claude Code closed the channel: stop, so an orphaned bridge doesn't keep the port from the next one.
  process.stdin.on('end', () => process.exit(0))

  // Only the tutor session serves the app (Config.serveApp). If another one already does, the MCP tools
  // keep working without HTTP.
  if (!cfg.serveApp) {
    log(`no app API in this session (not the tutor: lani-session serves it); data ${dataDir}`)
  } else if (serve({ host: cfg.host, port: cfg.port, fetch: router(routes, roleOf, log), log })) {
    log(`app API on http://${cfg.host}:${cfg.port}, ${where}`)
  }
}
