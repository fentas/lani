// lani-bridge: a Claude Code channel that connects the Lani Android app to the always-on tutor
// session. The app talks HTTP. Two ways to run it (docs/plans/04-bridge-service.md):
// - classic: Claude Code spawns it over stdio inside the tutor session (src/index.ts);
// - service: it runs on its own (src/service.ts, companion/bin/lani-bridge), and the session's shim (src/shim.ts)
//   reaches it over a unix socket, so a code change needs only `lani-bridge restart`, not a tutor restart.
//
// This file only wires the node's parts: its config, its MCP channel and its voices (the node's [Platform]); the shared
// stores (the context) and the features are build.ts's (buildBridge, which starts nothing: src/cli/tutor-export.ts reads
// the tools and the guide from it; the phone builds the same bridge, src/phone/entry.ts); then the channel and the app's
// API (startBridge). Each feature (src/features/*.ts) brings its HTTP routes, MCP tools and a paragraph of channel
// instructions.
import { renderDeep } from './addressee'
import { runService } from './bridge-service'
import { castVoiced } from './cast'
import { Channel, type ChannelOptions } from './channel'
import { loadConfig, log } from './config'
import { tutorOf } from './cultures'
import { appApi, buildBridge as build, type BuiltBridge as Built, type Platform, type Voicing, type VoiceSetting } from './build'
import type { FeatureFactory } from './feature'
import { router, serve } from './http'
import { enginesFromEnv, ffmpegRecordingLeveller, gepardPace, lazyModelUpgrade, redoDaily, revoiceDaily, sloveneNarrators, VoiceStore } from './voice'
import { Profiles } from './profiles'
import { voiceDir } from './paths'

export { FEATURES, familyOnRelay } from './build'

export type BridgeOptions = {
  /** classic: over stdio in the tutor session; service: on its own, the session's shim on a unix socket. */
  mode: 'stdio' | 'service'
  /** More features after the built-in ones (a test's probe tool). */
  extraFeatures?: FeatureFactory[]
}

/** The node's bridge: its MCP channel (classic or service). */
export type BuiltBridge = Built<Channel>

/**
 * The node's voices: ElevenLabs and the local Gepard worker, as the environment configures them (the Slovene village
 * speaks with both; another language's with ElevenLabs while its cast has voices, else with the phone's text-to-speech),
 * the clips in the voice store (SQLite), the voice profiles in its database.
 */
export function nodeVoices({ env, language, culture }: VoiceSetting): Voicing {
  const { eleven, gepard } = enginesFromEnv(env, log, language)
  const engines = language === 'sl' ? [eleven, gepard] : castVoiced() ? [eleven] : []
  if (!engines.length) log(`voice: the ${culture.id} voice cast has no voices yet: the app speaks with the phone's text-to-speech`)
  return {
    store: ({ appDir, ctx }) =>
      new VoiceStore({
        appDir,
        // the voice cache: $LANI_VOICE_CACHE (lani.env's), else <data>/app/voice, the old place while only it has the clips
        dir: voiceDir(appDir, env),
        engines,
        log,
        queueDelayMs: env.LANI_VOICE_QUEUE_DELAY_MS ? Number(env.LANI_VOICE_QUEUE_DELAY_MS) : undefined,
        // short narrator words from before the carrier sentence, the narrators' Slovene voices, a swapped voice's clips and
        // (lazyModel) an older model's, re-voiced a day as the app asks for them
        revoiceDaily: revoiceDaily(env),
        lazyModel: lazyModelUpgrade(env),
        // Jan's re-records on request (a long press on 🔊) a day, on their own count
        redoDaily: redoDaily(env),
        // the local voice's pace: its own unless LANI_VOICE_GEPARD_TEMPO or LANI_VOICE_GEPARD_PAUSES says
        pace: gepardPace(env, log),
        // the narrators of a Slovene village speak with their Slovene ElevenLabs voices (Nina, Luka), the local voice as their
        // fallback, never the English library voices (LANI_VOICE_NARRATOR_SL: elevenlabs, or gepard for the local voice first)
        narrators: sloveneNarrators(language, env, log),
        language,
        onUpdated: made => ctx().events.emit({ type: 'voice_updated', clips: made }),
      }),
    profiles: ctx =>
      new Profiles(ctx.voice.database, {
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
      }),
    ready: ctx => log(`voice: elevenlabs ${eleven.configured ? 'configured' : 'not configured'}, local worker checked on demand, clips in ${ctx.voice.dir}`),
    // a visited town's lines in its own cast's voices (features/visit-voices.ts): ElevenLabs in any language, the local
    // worker for a Slovene town; the same engines as the village's (one quota guard, one gate)
    engines: () => ({ eleven, gepard }),
  }
}

/** The node's platform: the config (the learner's data directory, which ensure_data_dir.py resolves), its MCP channel, its voices. */
export function nodePlatform(o: BridgeOptions): Platform<Channel> {
  // The service serves the app always (its features' jobs for the app run too: scene variants, the dictionary download).
  const cfg = loadConfig(process.env, { service: o.mode === 'service' })
  return { cfg, channel: new Channel(), voices: nodeVoices, level: ffmpegRecordingLeveller(), onExit: fn => process.on('exit', fn) }
}

/**
 * The bridge's parts without running anything: the config (the learner's data directory, which ensure_data_dir.py
 * resolves), the shared stores, the features, the guide and the tool list. No server, timer or background job is
 * started (the features' `start()` is startBridge's), so a script can build the bridge to read its tools and guide.
 */
export function buildBridge(o: BridgeOptions): BuiltBridge {
  return build(nodePlatform(o), { extraFeatures: o.extraFeatures })
}

export async function startBridge(o: BridgeOptions) {
  // A long-running channel: one failing background job must not take the app's connection down.
  process.on('unhandledRejection', e => log(`unhandled rejection: ${(e as Error)?.stack ?? e}`))
  process.on('uncaughtException', e => log(`uncaught exception: ${e.stack ?? e}`))

  const built = buildBridge(o)
  const { cfg, ctx, profile, features, rules, rulesNow, tools } = built
  const { dataDir } = cfg
  const { culture } = ctx

  const channel: ChannelOptions = {
    instructions: rules,
    tools,
    // A child doesn't approve the tutor's tool use: those prompts stay in the terminal and Remote Control.
    permissionRelay: !profile.child,
    onPermissionRequest: p => ctx.events.emit({ type: 'permission_request', ...p }),
  }
  const { routes, roleOf } = appApi(built)
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
    // the bridge that serves the app is the town: it alone answers its mail on the relay
    ctx.relay?.start()
  }
}
