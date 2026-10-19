// The bridge's parts without running anything, for both places it runs (docs/plans/05-built/phone-runtime.md):
// - on a node (bridge.ts): its MCP channel, its voice store (ElevenLabs, Gepard, the clips in SQLite), the HTTP server;
// - on the phone (src/phone/entry.ts): in QuickJS inside the app, its channel the in-app tutor, no voice store.
// What differs is the [Platform]: the config, the channel, the voices, the recordings' leveller. Everything else, the
// shared stores (the context), the features, the guide and the tools, is made here, the same in both.
import { existsSync } from 'node:fs'
import { join } from 'node:path'
import { addresseeOf } from './addressee'
import { ArrivalStore } from './arrivals'
import { CardStore, cardsDir } from './cards'
import { useCastFile } from './cast'
import type { Config } from './config'
import { log } from './config'
import { courseDir, CourseStore } from './course'
import { cultureDir, cultureProjects, resolveCulture, tutorOf, validateCulture, type Manifest } from './cultures'
import { Events } from './events'
import { Family } from './family'
import type { Ctx, Feature, FeatureFactory } from './feature'
import { arrivals } from './features/arrivals'
import { cards as cardsFeature } from './features/cards'
import { chat } from './features/chat'
import { counting } from './features/counting'
import { formsCards } from './features/forms-cards'
import { dev } from './features/dev'
import { course as courseFeature } from './features/course'
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
import { offlineVoice } from './features/offline-voice'
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
import { tutorSession } from './features/tutor'
import { villagers } from './features/villagers'
import { voice } from './features/voice'
import { GameStore } from './game'
import { DrillStore, drillsDir } from './drills'
import { grammarDir, GrammarStore } from './grammar'
import { setting } from './env'
import { addressed, bearer, json, loadToken, router, type Role, type Route } from './http'
import { FormsBook } from './forms'
import { Learner } from './learner'
import { channelRules, INSTRUCTIONS_LIMIT, learnerFacts, learnerInstructions, learnerLangs, profileFromEnv, type DataPlace, type Profile } from './learners'
import { Lexicon } from './lexicon'
import { Outbox } from './outbox'
import { PackStore, packsDirs } from './packs'
import { Devices, loadBridgeKey, loadTownKey } from './pairing'
import { guardRoutes, guardTools, LeaseGuard, selfParty } from './lease'
import { ReadingStore } from './readings'
import { relayFromEnv } from './relay'
import { Prints } from './prints'
import type { Profiles } from './profiles'
import { ScenarioStore } from './scenarios'
import { SceneStore, scenesDirs } from './scenes'
import { StoryStore, storiesDirs } from './stories'
import { ModuleStore } from './store'
import { ok, type ChannelPort, type Tool } from './tool'
import { VillagerStore } from './villagers'
import { VisitTalks } from './visits'
import type { RecordingLeveller, VoiceStore } from './voice'
import { resultsDirOf } from './paths'

/**
 * The built-in features in their order, each with a stable name (the in-app tutor's guide topics are named after them:
 * src/cli/tutor-export.ts). The channel instructions are the features' paragraphs in this order, and so is the tool list.
 * A new feature: a module exporting `(ctx: Ctx) => Feature`, added to this list.
 */
export const FEATURES: readonly { name: string; factory: FeatureFactory }[] = [
  { name: 'chat', factory: chat },
  { name: 'modules', factory: modules },
  { name: 'grammar', factory: grammarFeature },
  { name: 'cards', factory: cardsFeature },
  { name: 'course', factory: courseFeature },
  { name: 'counting', factory: counting },
  { name: 'forms-cards', factory: formsCards },
  { name: 'drills', factory: drillsFeature },
  { name: 'rhythm', factory: rhythm },
  { name: 'game', factory: game },
  { name: 'packs', factory: packs },
  { name: 'lexicon', factory: lexicon },
  { name: 'sentences', factory: sentences },
  { name: 'scenarios', factory: scenarios },
  { name: 'scenes', factory: scenes },
  { name: 'stories', factory: stories },
  { name: 'readings', factory: readings },
  { name: 'villagers', factory: villagers },
  { name: 'arrivals', factory: arrivals },
  { name: 'culture', factory: cultureFeature },
  { name: 'family', factory: family },
  { name: 'voice', factory: voice },
  { name: 'offline-voice', factory: offlineVoice },
  { name: 'stt', factory: stt },
  { name: 'reviews', factory: reviews },
  { name: 'level', factory: level },
  { name: 'events', factory: events },
  { name: 'releases', factory: releases },
  { name: 'pairing', factory: pairing },
  { name: 'towns', factory: towns },
  { name: 'friendship', factory: friendship },
  { name: 'questions', factory: townQuestions },
  // the developer's data from the app (app/dev/): routes only, nothing for the tutor
  { name: 'dev', factory: dev },
  // the app's own tutor as a client of the channel (/tutor/*: features/tutor.ts): routes only, over the tools above
  { name: 'tutor', factory: tutorSession },
]

/** What the voices of the village need to know when they are made: its language and the voice its tutor speaks in. */
export type VoiceSetting = { env: Record<string, string | undefined>; language: string; culture: { id: string; manifest?: Manifest } }

/**
 * The village's voices on this platform, asked for in three steps so the bridge is made in the same order everywhere:
 * the engines when the culture is known ([Platform.voices]), the store inside the context, the profiles (one voice per
 * person, sharing the store's database) once the context is there, and [ready] after both.
 */
export type Voicing = {
  store: (o: { appDir: string; ctx: () => Ctx }) => VoiceStore
  profiles: (ctx: Ctx) => Profiles
  ready?: (ctx: Ctx) => void
}

/** What the place the bridge runs in brings: a node (bridge.ts) or the phone (src/phone/entry.ts). */
export type Platform<C extends ChannelPort = ChannelPort> = {
  /** Where the project and the learner's data are (a node: config.ts loadConfig; the phone: the app's files). */
  cfg: Config
  /** The tutor's channel: a node's MCP server (channel.ts), the phone's in-app tutor (src/phone/channel.ts). */
  channel: C
  voices: (s: VoiceSetting) => Voicing
  /** Brings the family's recordings to the voice clips' loudness (a node's ffmpeg); none: as they came. */
  level?: RecordingLeveller
  /** Runs [fn] when the bridge ends (a node: the process's exit; the phone: PhoneBridge.stop). */
  onExit?: (fn: () => void) => void
  /**
   * Whether the culture pack is checked when the bridge is built (validateCulture: what's wrong is logged). A node
   * does (the default); the phone leaves it out (its content was checked when the APK was built, and the check is most
   * of the boot's time in QuickJS), unless LANI_CULTURE_CHECK=1.
   */
  checkCulture?: boolean
}

export type BuildOptions = {
  /** More features after the built-in ones (a test's probe tool). */
  extraFeatures?: FeatureFactory[]
}

/** What [buildBridge] makes: the context, the features, the guide, the rules and the tools, nothing started yet. */
export type BuiltBridge<C extends ChannelPort = ChannelPort> = {
  cfg: Config
  ctx: Ctx & { channel: C }
  profile: Profile
  /** Whether a request carries the bridge token (phones paired by QR code: ctx.devices). */
  isApp: (req: Request) => boolean
  /** The features in registration order with their names (FEATURES', then `extra-<n>` for BridgeOptions.extraFeatures). */
  named: { name: string; feature: Feature }[]
  features: Feature[]
  /** The channel's whole guide (the channel_guide tool): the features' paragraphs for this learner. */
  guide: () => string
  /** The channel instructions that fit Claude Code's limit, read again for every session; [rules] as they were at the start. */
  rulesNow: () => string
  rules: string
  guideTool: Tool
  /** Every MCP tool in registration order: channel_guide, then the features'. */
  tools: Tool[]
  /** The default learner's data repository (resultsDir), when their data is one. */
  place: DataPlace
}

/**
 * The bridge's parts without running anything: the shared stores, the features, the guide and the tool list, on
 * [platform]'s config and channel. No server, timer or background job is started (the features' `start()` is the
 * caller's), so a script can build the bridge to read its tools and guide.
 */
export function buildBridge<C extends ChannelPort>(platform: Platform<C>, o: BuildOptions = {}): BuiltBridge<C> {
  const env = process.env
  const { cfg } = platform
  const { projectDir, appDir, dataDir } = cfg
  // Which learner this bridge serves: lani-session sets LANI_PROFILE for every profile but the default.
  const profile = profileFromEnv(env)
  const isApp = bearer(loadToken(join(appDir, 'bridge-token'), env.LANI_BRIDGE_TOKEN, log, 'app'))
  // Phones paired by QR code have tokens of their own; the bridge token above keeps working.
  const devices = new Devices(appDir, { log })
  platform.onExit?.(() => devices.flushSeen())
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
  const voicing = platform.voices({ env, language, culture })
  /** The village projects of the culture (its projects.json), which a scene's `needs` may name. */
  const villageProjects = cultureProjects(culturesDir, culture.id)
  const lexiconDir = env.LANI_LEXICON_DIR || join(projectDir, 'companion/lexicon')
  const bridgeKey = loadBridgeKey(appDir)
  // the town's own key (app/town-key.pem), else the bridge's: what towns, friends and the relay know the town by
  const townKey = loadTownKey(appDir)

  // what the app's own tutor's session reads of the session (features/tutor.ts): set once the features are built
  let sessionTools: Tool[] = []
  let sessionRules: () => string = () => ''
  const ctx: Ctx & { channel: C } = {
    session: { tools: () => sessionTools, rules: () => sessionRules() },
    cfg,
    log,
    profile,
    devices,
    bridgeKey,
    townKey,
    // who writes the learner's data (lease.ts): the node, or the phone with LANI_SIDE=phone; without a lease file, both
    lease: new LeaseGuard(dataDir, selfParty(env), env),
    // towns and the family page through the relay (companion/relay): only with LANI_RELAY_URL set; it speaks as the town
    relay: relayFromEnv(env, townKey, log),
    // The home language's databases, and another language's (visits) in languages/<code>/ (docs/DB_SCRIPTS.md).
    learner: new Learner(projectDir, dataDir, language),
    addressee: () => addresseeOf(dataDir, language),
    events: new Events(),
    channel: platform.channel,
    outbox: new Outbox(join(appDir, 'client-ids.json'), log),
    // The curated modules, scenarios and scenes are the village's language's: Slovene ones in their directories, another
    // language's in a subdirectory of its code (companion/modules/it), none yet: the tutor writes them for the learner.
    modules: new ModuleStore(join(appDir, 'modules'), ofLanguage(env.LANI_MODULES_DIR ?? join(projectDir, 'companion/modules')), log),
    // The curated word packs of the village's language, then its culture pack's own (its festivals' and its places' words).
    packs: new PackStore(packsDirs(env.LANI_PACKS_DIR ?? join(projectDir, 'companion/packs'), culturesDir, culture.id, language), join(appDir, 'packs'), log),
    // The grammar book of the village's language (companion/grammar/<language>: sl, it, de, en), then the tutor's pages.
    grammar: new GrammarStore(grammarDir(env.LANI_GRAMMAR_DIR ?? join(projectDir, 'companion/grammar'), language), language, join(appDir, 'grammar'), log),
    // The cheat sheet's cards no grammar page fits (companion/cards/<language>), then the tutor's (publish_card).
    cards: new CardStore(cardsDir(env.LANI_CARDS_DIR ?? join(projectDir, 'companion/cards'), language), language, join(appDir, 'cards'), log),
    // The school's course of the village's language (companion/course/<language>: sl, it, de, en), then the tutor's
    // homework for its units (<data>/app/course).
    course: new CourseStore(courseDir(env.LANI_COURSE_DIR ?? join(projectDir, 'companion/course'), language), language, join(appDir, 'course'), log),
    // Its printed sheets (companion/prints, the generator's own code): from the same course, grammar book, packs and
    // dictionary the bridge serves.
    prints: new Prints(language, {
      root: projectDir,
      course: courseDir(env.LANI_COURSE_DIR ?? join(projectDir, 'companion/course'), language),
      grammar: grammarDir(env.LANI_GRAMMAR_DIR ?? join(projectDir, 'companion/grammar'), language),
      packs: env.LANI_PACKS_DIR ?? join(projectDir, 'companion/packs'),
      lexicon: lexiconDir,
    }, log),
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
      level: platform.level,
    }),
    voice: voicing.store({ appDir, ctx: () => ctx }),
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
  ctx.profiles = voicing.profiles(ctx)
  voicing.ready?.(ctx)
  // The pack the village is in, checked once against its cast, (its scenes) the packs and the grammar book its requests and
  // questions name: what's wrong is logged (the app checks the pack too).
  if (platform.checkCulture ?? true) {
    const checked = validateCulture(culturesDir, culture.id, ctx.villagers.all(), { packs: ctx.packs.all(), scenarios: ctx.scenarios.all() }, new Set(ctx.grammar.all().map(p => p.id)))
    if (!checked.ok) log(`culture ${culture.id} has problems:\n${checked.errors}`)
  }

  // The channel instructions are the features' paragraphs in this order (FEATURES), and so is the tool list.
  const named = [...FEATURES, ...(o.extraFeatures ?? []).map((factory, i) => ({ name: `extra-${i + 1}`, factory }))].map(({ name, factory }) => ({ name, feature: factory(ctx) }))
  const features: Feature[] = named.map(f => f.feature)
  // What comes through the relay is answered by the features' own routes, as if it came over HTTP, but only ever as
  // another town (its signed requests) or the family page (its token): never with the app's role.
  if (ctx.relay) {
    ctx.relay.attach(router(guardRoutes(addressed(features.flatMap(f => f.routes ?? []), ctx.addressee), ctx.lease), req => (ctx.family.authorized(req) ? 'family' : null), log))
    ctx.relay.provideFamily(() => (familyOnRelay(env) ? ctx.family.tokenSha256 : null))
  }

  // The default learner set up by lani-setup, their data a repository of its own: the instructions name them from their
  // profile and say where their data and session results are.
  const place: DataPlace = profile.isDefault && existsSync(join(dataDir, '.git')) ? { resultsDir: resultsDirOf(dataDir) } : {}
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
  const guideTool: Tool = {
    name: 'channel_guide',
    description: "This channel's full instructions: each kind of inbound message and when to use each tool. Call it once before your first reply, and again after the conversation is compacted.",
    inputSchema: { type: 'object', properties: {} },
    handle: () => ok(guide()),
  }
  // The tutor's writing tools fail while another side holds the learner's data (the lease: lease.ts).
  const tools = guardTools([guideTool, ...features.flatMap(f => f.tools ?? [])], ctx.lease)
  sessionTools = tools
  sessionRules = rulesNow
  return { cfg, ctx, profile, isApp, named, features, guide, rulesNow, rules, guideTool, tools, place }
}

/**
 * The app's API on [b]: /health and the features' routes, what speaks to the learner rendered for them ({learner},
 * {m:…|f:…}: addressee.ts); and who a request is: the app (the bridge token, a paired phone's), the family, or nobody.
 */
export function appApi(b: Pick<BuiltBridge, 'ctx' | 'isApp' | 'features'>): { routes: Route[]; roleOf: (req: Request) => Role } {
  const { ctx, isApp, features } = b
  const health: Route = { method: '*', path: '/health', access: 'public', handle: () => json({ ok: true }) }
  const roleOf = (req: Request): Role => (isApp(req) || ctx.devices.authorize(req) ? 'app' : ctx.family.authorized(req) ? 'family' : null)
  // the app's writes are refused (423) while another side holds the learner's data (the lease: lease.ts)
  const routes = guardRoutes(addressed([health, ...features.flatMap(f => f.routes ?? [])], ctx.addressee), ctx.lease)
  return { routes, roleOf }
}

/**
 * Whether the family page goes through the relay: LANI_RELAY_FAMILY "on" or "off"; unset, when the town has no address
 * of its own (LANI_TOWN_URL=relay, the phone's bridge). A node's family page stays on its tailnet unless it is turned on.
 */
export function familyOnRelay(env: Record<string, string | undefined> = process.env): boolean {
  const v = env.LANI_RELAY_FAMILY?.trim()
  if (v === 'on' || v === '1') return true
  if (v === 'off' || v === '0') return false
  return setting('LANI_TOWN_URL', env)?.trim() === 'relay'
}
