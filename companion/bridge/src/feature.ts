// The one shape every feature has: routes for the app, tools for the session, a paragraph of
// channel instructions, and optional background work. Features get the shared context.
import type { Addressee } from './addressee'
import type { ArrivalStore } from './arrivals'
import type { CardStore } from './cards'
import type { Profiles } from './profiles'
import type { Config, Log } from './config'
import type { CourseStore } from './course'
import type { DrillStore } from './drills'
import type { Manifest } from './cultures'
import type { Events } from './events'
import type { Family } from './family'
import type { FormsBook } from './forms'
import type { CaseTriggersFile } from './case-triggers'
import type { GameStore } from './game'
import type { GrammarStore } from './grammar'
import type { Route } from './http'
import type { Learner } from './learner'
import type { LeaseGuard } from './lease'
import type { Profile } from './learners'
import type { Lexicon } from './lexicon'
import type { Outbox } from './outbox'
import type { PackStore } from './packs'
import type { BridgeKey, Devices } from './pairing'
import type { ReadingStore } from './readings'
import type { RelayClient } from './relay'
import type { Prints } from './prints'
import type { ScenarioStore } from './scenarios'
import type { SceneStore } from './scenes'
import type { StoryStore } from './stories'
import type { ModuleStore } from './store'
import type { ChannelPort, Tool } from './tool'
import type { VillagerStore } from './villagers'
import type { VisitEngines } from './visit-voices'
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
  /** The town's key (pairing.ts loadTownKey): app/town-key.pem, else the bridge's key. Its fingerprint is the town's id. */
  townKey: BridgeKey
  /** Who writes the learner's data (lease.ts): the app's writes and the tutor's writing tools are refused while it isn't this side's. */
  lease: LeaseGuard
  learner: Learner
  /**
   * Who the content speaks to (addressee.ts): the learner profile's name, its cases and their gender, read again when the
   * profile changes. What the bridge serves the app, speaks or hands the tutor is rendered for them.
   */
  addressee: () => Addressee
  events: Events
  /** Events into the tutor's session, and tool approvals: the node's MCP channel (channel.ts), the phone's (src/phone/channel.ts). */
  channel: ChannelPort
  /**
   * The session as the tutor gets it, for the app's own tutor's session (features/tutor.ts): every tool the session
   * lists (channel_guide, then the features'), and its instructions now. Read lazily: they exist once every feature is
   * built.
   */
  session: { tools: () => Tool[]; rules: () => string }
  outbox: Outbox
  modules: ModuleStore
  packs: PackStore
  /** The grammar book of the village's language (grammar.ts): the curated pages and the tutor's. */
  grammar: GrammarStore
  /** The cheat sheet's cards of the village's language that aren't a grammar page's (cards.ts): the curated ones and the tutor's. */
  cards: CardStore
  /** The school's course of the village's language (course.ts): the curated outline and units, the tutor's homework. */
  course: CourseStore
  /** The course's printed sheets (prints.ts, companion/prints): a unit's sheet and a level's booklet as HTML to print. */
  prints: Prints
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
  /**
   * This node's own engines, for what isn't its village's: a visited town's lines in the host's cast voices
   * (features/visit-voices.ts). The same instances as the store's (one quota guard, one gate at ElevenLabs); none on the
   * phone.
   */
  voiceEngines: VisitEngines
  family: Family
  /** The role-plays started on visits to other towns (their brief rides along every turn: chat.ts, features/towns.ts). */
  visitTalks: VisitTalks
  /**
   * This town's relay client (relay.ts) when LANI_RELAY_URL is set; undefined (off) otherwise. The towns publish their
   * state through it; startBridge starts it once the app's API is up.
   */
  relay?: RelayClient
  /**
   * The village language's dictionary (lexicon.ts: Wiktionary's, the supplement, the tutor's glosses; read at its first
   * use) and its forms book (forms.ts), one of each: the word lookup serves them, and the check of a dialog's woven words
   * reads which lemma a form is of (dialog-words.ts); and the case lens's trigger table (case-triggers.ts: what decides a
   * word's case, companion/grammar/<language>/data/case-triggers.json; none for a language without one).
   */
  dictionary: { dict: Lexicon; book: FormsBook; language: string; triggers: CaseTriggersFile }
}

export type FeatureFactory = (ctx: Ctx) => Feature
