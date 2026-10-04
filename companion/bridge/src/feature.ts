// The one shape every feature has: routes for the app, tools for the session, a paragraph of
// channel instructions, and optional background work. Features get the shared context.
import type { ArrivalStore } from './arrivals'
import type { Profiles } from './profiles'
import type { Channel, Tool } from './channel'
import type { Config, Log } from './config'
import type { DrillStore } from './drills'
import type { Manifest } from './cultures'
import type { Events } from './events'
import type { Family } from './family'
import type { GameStore } from './game'
import type { GrammarStore } from './grammar'
import type { Route } from './http'
import type { Learner } from './learner'
import type { Profile } from './learners'
import type { Outbox } from './outbox'
import type { PackStore } from './packs'
import type { BridgeKey, Devices } from './pairing'
import type { ReadingStore } from './readings'
import type { ScenarioStore } from './scenarios'
import type { SceneStore } from './scenes'
import type { StoryStore } from './stories'
import type { ModuleStore } from './store'
import type { VillagerStore } from './villagers'
import type { VisitTalks } from './visits'
import type { VoiceStore } from './voice'

export type Feature = {
  /** A paragraph of channel instructions; the paragraphs are joined in feature order. */
  instructions?: string
  routes?: Route[]
  tools?: Tool[]
  /** Background work (timers, watchers), started once the channel is up. */
  start?: () => void
}

export type Ctx = {
  cfg: Config
  log: Log
  /** Which learner this bridge serves (LANI_PROFILE; "default" is today's setup) and whether they are a child. */
  profile: Profile
  /** Phones paired by QR code, each with its own token (pairing.ts). */
  devices: Devices
  /** The bridge's own key: its fingerprint is in the pairing QR code. */
  bridgeKey: BridgeKey
  learner: Learner
  events: Events
  channel: Channel
  outbox: Outbox
  modules: ModuleStore
  packs: PackStore
  /** The grammar book of the village's language (grammar.ts): the curated pages and the tutor's. */
  grammar: GrammarStore
  /** The car's audio drills of the village's language (drills.ts): the curated ones. */
  drills: DrillStore
  scenarios: ScenarioStore
  scenes: SceneStore
  /** The storyteller's evening stories (stories.ts): the culture pack's, and the tutor's. */
  stories: StoryStore
  /** The reading corner's readings (readings.ts): the culture pack's, and the tutor's. */
  readings: ReadingStore
  villagers: VillagerStore
  /** The tutor's introductions of people who joined the village (arrivals.ts); the culture pack's are the app's. */
  arrivals: ArrivalStore
  /**
   * The culture pack the village is in (companion/cultures/<id>, cultures.ts): [id] the one played, [wanted] the
   * profile's, [why] it isn't played when they differ; [dir] where the packs are.
   */
  culture: { id: string; wanted: string; why?: string; manifest?: Manifest; dir: string }
  /** One voice per person: their archetype, pitch and pace, and a voice of their own once there is room (profiles.ts). */
  profiles: Profiles
  game: GameStore
  voice: VoiceStore
  family: Family
  /** The role-plays started on visits to other towns (their brief rides along every turn: chat.ts, features/towns.ts). */
  visitTalks: VisitTalks
}

export type FeatureFactory = (ctx: Ctx) => Feature
