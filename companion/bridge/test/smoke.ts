// End-to-end smoke test: plays Claude Code (MCP client over stdio) and the app (HTTP).
// Run: bun test/smoke.ts   — uses a throwaway data dir and port (LANI_SMOKE_PORT, default 8799).
// The sections in test/smoke/ share one bridge and run in this order (later ones use what earlier
// ones published: the family word list needs the packs, the voice checks the published content).
import './isolate' // first: never this machine's lani.env
import '../src/env' // first: LANI_* from the FLUENT_* names of a node set up before the rename
import { done } from './smoke/harness'
import core from './smoke/core'
import chat from './smoke/chat'
import modules from './smoke/modules'
import grammar from './smoke/grammar'
import drills from './smoke/drills'
import ispy from './smoke/ispy'
import events from './smoke/events'
import reviews from './smoke/reviews'
import outbox from './smoke/outbox'
import releases from './smoke/releases'
import game from './smoke/game'
import packs from './smoke/packs'
import lexicon from './smoke/lexicon'
import forms from './smoke/forms'
import sentences from './smoke/sentences'
import meanings from './smoke/meanings'
import rhythm from './smoke/rhythm'
import scenarios from './smoke/scenarios'
import scenes from './smoke/scenes'
import variants from './smoke/variants'
import dialogWords from './smoke/dialog-words'
import stories from './smoke/stories'
import readings from './smoke/readings'
import villagers from './smoke/villagers'
import learner from './smoke/learner'
import arrivals from './smoke/arrivals'
import cultures from './smoke/cultures'
import friuli from './smoke/friuli'
import kaernten from './smoke/kaernten'
import lakeland from './smoke/lakeland'
import pairs from './smoke/pairs'
import family from './smoke/family'
import offlineVoice from './smoke/offline-voice'
import voice from './smoke/voice'
import stt from './smoke/stt'
import languages from './smoke/languages'
import level from './smoke/level'
import pairing from './smoke/pairing'
import towns from './smoke/towns'
import service from './smoke/service'
import rename from './smoke/rename'
import setup from './smoke/setup'

// pairing runs last: it fills the bridge's limit on wrong pairing codes. towns starts two more bridges of its own, service
// its bridge services and their shims (docs/plans/04-bridge-service.md); setup a bridge configured by lani.env alone. level raises the learner to A2 (and their
// Italian): it runs late, after the checks that read the level.
for (const section of [core, chat, modules, grammar, drills, ispy, events, reviews, outbox, releases, game, packs, lexicon, forms, sentences, meanings, rhythm, scenarios, scenes, variants, dialogWords, stories, readings, villagers, learner, arrivals, cultures, friuli, kaernten, lakeland, pairs, family, offlineVoice, voice, stt, towns, languages, service, level, pairing, rename, setup]) await section()
await done()
