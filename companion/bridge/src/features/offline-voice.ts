// The phone's offline voice (../offline-voice.ts): the Piper voice of a language, fetched once into the node's cache
// when the app asks for it, and its files for the app to download. The app's only (its token): the family page never
// speaks offline.
import type { FeatureFactory } from '../feature'
import { json } from '../http'
import { catalogFrom, OfflineVoices, piperCacheDir, piperSource } from '../offline-voice'

export const offlineVoice: FeatureFactory = ({ log }) => {
  const voices = new OfflineVoices({ cacheDir: piperCacheDir(), source: piperSource(), catalog: catalogFrom(process.env.LANI_PIPER_CATALOG?.trim() || undefined, log), log })
  const missing = (language: string) => json({ error: `no offline voice for "${language}"` }, 404)
  return {
    routes: [
      // Every voice of the catalog: its status (missing, fetching with its progress, ready with its files, failed with
      // why), its size, licence and attribution.
      { method: 'GET', path: '/voice/offline', handle: () => json(voices.list()) },
      {
        method: 'GET',
        path: /^\/voice\/offline\/([a-z]{2,3})$/,
        handle: ({ params: [language] }) => {
          const e = voices.entry(language)
          return e ? json(e) : missing(language)
        },
      },
      // Fetches the voice unless it is there (200) or on its way (202); after a failure, again. Asking twice is harmless.
      {
        method: 'POST',
        path: /^\/voice\/offline\/([a-z]{2,3})$/,
        handle: ({ params: [language] }) => {
          const e = voices.start(language)
          return e ? json(e, e.status === 'ready' ? 200 : 202) : missing(language)
        },
      },
      // A file of a ready voice ("model.onnx", "espeak-ng-data/lang/zls/sl"): only the paths its list names. Bun answers
      // a Range request (206), so the app can resume the model's download.
      {
        method: 'GET',
        path: /^\/voice\/offline\/([a-z]{2,3})\/(.+)$/,
        handle: ({ params: [language, raw] }) => {
          let path: string
          try {
            path = decodeURIComponent(raw)
          } catch {
            return json({ error: 'not found' }, 404)
          }
          const f = voices.file(language, path)
          if (!f) return json({ error: 'not found' }, 404)
          return new Response(Bun.file(f.path), {
            headers: { 'content-type': 'application/octet-stream', 'cache-control': 'private, max-age=31536000, immutable' },
          })
        },
      },
    ],
  }
}
