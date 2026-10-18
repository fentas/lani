// node:zlib for the phone's bridge: gzip and gunzip of a whole buffer, by the host (java.util.zip); a downloaded
// dictionary comes gzipped (lexicon-download.ts).
import { Buffer } from './buffer'
import { fromHost, host, toHost } from './host'

const bytes = (b: ArrayBufferView | string) => (typeof b === 'string' ? Buffer.from(b) : new Uint8Array(b.buffer, b.byteOffset, b.byteLength))

export const gunzipSync = (b: ArrayBufferView) => Buffer.view(fromHost(host().gunzip(toHost(bytes(b)))))
export const gzipSync = (b: ArrayBufferView | string) => Buffer.view(fromHost(host().gzip(toHost(bytes(b)))))

export default { gunzipSync, gzipSync }
