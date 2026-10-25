// The voices of the bridge in the app: none of its own (docs/plans/05-built/phone-runtime.md). A node makes and keeps
// the clips (voice.ts: ElevenLabs, the local Gepard worker, the store in SQLite); the phone speaks with its own
// text-to-speech until the voice service (glas) is hosted. So the voice store here has no clips: /voice/say and
// /voice/prepare answer that there is none (204, url null), a re-record fails with why, voice_status says "no voices
// on this device", and what a publish asks to be voiced is let go. The voice profiles (one voice per person: their
// archetype, pitch and pace) work as on a node, their table in a JSON file (profiles-table.ts); no voice is designed.
import { join } from 'node:path'
import type { Voicing, VoiceSetting } from '../build'
import { castInfo, playerVoices } from '../cast'
import { log } from '../config'
import { tutorOf } from '../cultures'
import { renderDeep } from '../addressee'
import { Profiles } from '../profiles'
import type { Database } from 'bun:sqlite'
import { normalizeText } from '../family'
import { explanationNorm, type Clip, type CorpusItem, type ElevenLabs, type Redo, type VoiceName, type VoiceStore } from '../voice'
import { ProfilesTable } from './profiles-table'

export const NO_VOICES = "no voices on this device: the app speaks with the phone's text-to-speech"

/** What the features call of the voice store, answered as a store with no engine and no clips. */
export class PhoneVoiceStore implements Pick<VoiceStore, 'dir' | 'language' | 'redoDaily' | 'explains' | 'normOf' | 'best' | 'cost' | 'index' | 'path' | 'voice' | 'say' | 'requested' | 'redosToday' | 'redo' | 'resumeRevoice' | 'watch' | 'enqueue' | 'dropVoice' | 'status'> {
  readonly dir: string
  readonly redoDaily = 0

  constructor(
    appDir: string,
    readonly language: string,
    /** The voice profiles' table (what a node's profiles keep in the store's database). */
    readonly database: ProfilesTable,
  ) {
    this.dir = join(appDir, 'voice')
  }

  explains(lang?: string): lang is string {
    return !!lang && lang !== this.language
  }

  normOf(text: string, lang?: string): string {
    return this.explains(lang) ? explanationNorm(lang, text) : normalizeText(text)
  }

  best(_norm: string, _voice: VoiceName): Clip | undefined {
    return undefined
  }

  cost(text: string, _voice: VoiceName, _lang?: string): number {
    return text.trim().length
  }

  index(): Record<string, Record<VoiceName, string>> {
    return {}
  }

  path(_file: string): string | undefined {
    return undefined
  }

  async voice(): Promise<Clip | null> {
    return null
  }

  async say(): Promise<Clip | null> {
    return null
  }

  requested(_file: string) {}

  redosToday(): number {
    return 0
  }

  async redo(): Promise<Redo> {
    return { status: 'failed', message: NO_VOICES }
  }

  resumeRevoice() {}

  watch() {}

  enqueue(_items: CorpusItem[]) {}

  dropVoice(_voice: VoiceName) {}

  async status(_items?: CorpusItem[]) {
    return { device: NO_VOICES, clips: { total: 0, chars: 0, by: [] }, engines: {}, cast: castInfo(), player: playerVoices(), queued: 0 } as unknown as Awaited<ReturnType<VoiceStore['status']>>
  }
}

/** No ElevenLabs on the phone: no subscription, so the profiles never design a voice. */
const noElevenLabs = {
  configured: false,
  subscription: async () => null,
  ready: async () => false,
  preview: async () => null,
  design: async () => null,
  createVoice: async () => null,
  deleteVoice: async () => undefined,
} as unknown as ElevenLabs

/** The phone's voices for build.ts: the store with no clips, the profiles over their JSON table. */
export function phoneVoices({ language, culture }: VoiceSetting): Voicing {
  let table: ProfilesTable | undefined
  return {
    store: ({ appDir }) => {
      table = new ProfilesTable(join(appDir, 'voice-profiles.json'))
      return new PhoneVoiceStore(appDir, language, table) as unknown as VoiceStore
    },
    profiles: ctx =>
      new Profiles(table as unknown as Database, {
        eleven: noElevenLabs,
        cast: () => renderDeep(ctx.villagers.all(), ctx.addressee()),
        language,
        accent: tutorOf(culture.manifest).voice,
        log,
        auto: false,
        onUpdated: person => ctx.events.emit({ type: 'voice_updated', clips: 0, person }),
      }),
    ready: () => log(`voice: ${NO_VOICES}`),
  }
}
