// Photos in the tutor chat (companion/README.md "Tutor chat", "Photos"): the app's 📷 by the chat's input takes one or
// picks one from the gallery (a sign, a menu, a letter, a textbook page, a handwritten worksheet, a thing at home), and
// sends it before its message: PUT /chat/images/<id>, the message's client id, scaled, upright and without EXIF. The node
// keeps it under <data>/app/chat-images/<YYYY-MM>/<id>.<jpg|png|webp> (the node's month at the upload; the folders 0700,
// the file 0600), takes only a JPEG, PNG or WebP by its bytes, and strips the metadata left in it once more (a second
// net: the app strips it already). The message names it (POST /message image: {id}), and the tutor's chat event carries
// its absolute path (data.image), which the tutor opens with the Read tool. The app gets it back with GET (another phone,
// a reinstall) and deletes it with DELETE ("🗑️" on the photo).
//
// Never committed: the data repository's .gitignore keeps /app/chat-images/ out (lani_data_repo.py writes the line and
// keeps it in with each commit; the bridge adds it at its start to a .gitignore without it). Kept 12 months, as the phone
// keeps its chat: an older month's folder goes at the start and with each new photo.
import { chmodSync, existsSync, mkdirSync, readdirSync, readFileSync, renameSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { randomBytes } from 'node:crypto'
import type { Log } from './config'
import { keepOut } from './ignore'

/** The largest photo the node takes (413 beyond). The app sends ~1,568 px on the long side, a few hundred KB. */
export const CHAT_IMAGE_MAX = 8 * 1024 * 1024
/** How many months of photos are kept, this one included (the phone's chat keeps a year: ChatStore.KEEP_MONTHS). */
export const CHAT_IMAGES_KEEP_MONTHS = 12
/** The .gitignore line that keeps the photos out of the data repository. */
export const CHAT_IMAGES_IGNORE = '/app/chat-images/'
/** A photo's id: the app's client id of its message (32 hex characters), as client ids go. */
export const CHAT_IMAGE_ID = /^[\w-]{8,64}$/

export type ImageType = 'jpeg' | 'png' | 'webp'
const EXT: Record<ImageType, string> = { jpeg: 'jpg', png: 'png', webp: 'webp' }
const MIME: Record<ImageType, string> = { jpeg: 'image/jpeg', png: 'image/png', webp: 'image/webp' }
const OF_EXT: Record<string, ImageType> = { jpg: 'jpeg', png: 'png', webp: 'webp' }
const MONTH = /^\d{4}-\d{2}$/

/** A kept photo, as the routes answer it: its size in pixels and bytes, its type, its file in the data directory. */
export type ChatImageInfo = { id: string; width: number; height: number; bytes: number; type: string; file: string }

/** What the tutor's chat event carries of it (data.image): where to open it, how big it is. */
export type ChatImageEvent = { path: string; width: number; height: number; bytes: number; type: string }

// --- the bytes ------------------------------------------------------------------------------------------------------

const ascii = (b: Uint8Array, at: number, n: number) => String.fromCharCode(...b.subarray(at, at + n))
const u16be = (b: Uint8Array, at: number) => (b[at]! << 8) | b[at + 1]!
const u32be = (b: Uint8Array, at: number) => ((b[at]! << 24) >>> 0) + ((b[at + 1]! << 16) | (b[at + 2]! << 8) | b[at + 3]!)
const u24le = (b: Uint8Array, at: number) => b[at]! | (b[at + 1]! << 8) | (b[at + 2]! << 16)
const u32le = (b: Uint8Array, at: number) => (b[at]! | (b[at + 1]! << 8) | (b[at + 2]! << 16)) + ((b[at + 3]! << 24) >>> 0)
const PNG_SIGNATURE = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]

/** What [b] is by its first bytes: a JPEG, a PNG, a WebP, or null (anything else, whatever its Content-Type said). */
export function sniff(b: Uint8Array): ImageType | null {
  if (b.length >= 3 && b[0] === 0xff && b[1] === 0xd8 && b[2] === 0xff) return 'jpeg'
  if (b.length >= 8 && PNG_SIGNATURE.every((x, i) => b[i] === x)) return 'png'
  if (b.length >= 12 && ascii(b, 0, 4) === 'RIFF' && ascii(b, 8, 4) === 'WEBP') return 'webp'
  return null
}

/** A JPEG's segment: its marker, where it starts (at its 0xFF) and ends, and its data's start (after the length). */
type Segment = { marker: number; start: number; end: number; data: number }

/**
 * A JPEG's segments in order, the entropy-coded data after each start of scan as one segment of its own (marker 0):
 * null when the file isn't whole up to its scans. A progressive JPEG's tables between its scans are segments too.
 */
function jpegSegments(b: Uint8Array): Segment[] | null {
  const out: Segment[] = [{ marker: 0xd8, start: 0, end: 2, data: 2 }]
  let i = 2
  while (i < b.length) {
    if (b[i] !== 0xff) return null
    let m = i + 1
    while (m < b.length && b[m] === 0xff) m++ // fill bytes
    if (m >= b.length) return null
    const marker = b[m]!
    if (marker === 0xd9) {
      out.push({ marker, start: i, end: m + 1, data: m + 1 })
      return out // the end of the image: what follows is dropped
    }
    if (marker === 0x01 || (marker >= 0xd0 && marker <= 0xd7)) {
      out.push({ marker, start: i, end: m + 1, data: m + 1 })
      i = m + 1
      continue
    }
    if (m + 3 > b.length) return null
    const len = u16be(b, m + 1)
    if (len < 2 || m + 1 + len > b.length) return null
    const end = m + 1 + len
    out.push({ marker, start: i, end, data: m + 3 })
    i = end
    if (marker === 0xda) {
      // the scan's data, up to the next marker that isn't a stuffed 0xFF00 or a restart
      let j = i
      while (j < b.length) {
        if (b[j] === 0xff && j + 1 < b.length && b[j + 1] !== 0x00 && !(b[j + 1]! >= 0xd0 && b[j + 1]! <= 0xd7) && b[j + 1] !== 0xff) break
        j++
      }
      out.push({ marker: 0, start: i, end: j, data: i })
      i = j
    }
  }
  return out // no end marker: a decoder takes it as it is
}

/** Whether a JPEG segment is metadata to drop: APP1 (Exif, XMP), APP3 to APP13, APP15, a comment; APP2 unless ICC. */
function jpegMetadata(b: Uint8Array, s: Segment): boolean {
  const m = s.marker
  if (m === 0xe2) return ascii(b, s.data, 12) !== 'ICC_PROFILE\0'
  return m === 0xe1 || (m >= 0xe3 && m <= 0xed) || m === 0xef || m === 0xfe
}

/** A JPEG's start of frame (SOF0–SOF15 but DHT, JPG and DAC). */
const isSof = (m: number) => m >= 0xc0 && m <= 0xcf && m !== 0xc4 && m !== 0xc8 && m !== 0xcc

type Chunk = { type: string; start: number; end: number; data: number; size: number }

/** A PNG's chunks after the signature, up to IEND; null when one is cut off. */
function pngChunks(b: Uint8Array): Chunk[] | null {
  const out: Chunk[] = []
  let i = 8
  while (i + 12 <= b.length) {
    const size = u32be(b, i)
    const type = ascii(b, i + 4, 4)
    const end = i + 12 + size
    if (end > b.length) return null
    out.push({ type, start: i, end, data: i + 8, size })
    i = end
    if (type === 'IEND') return out
  }
  return out.length ? out : null
}

/** A WebP's chunks after its header (each padded to an even size); null when one is cut off. */
function webpChunks(b: Uint8Array): Chunk[] | null {
  const out: Chunk[] = []
  const total = Math.min(b.length, 8 + u32le(b, 4))
  let i = 12
  while (i + 8 <= total) {
    const type = ascii(b, i, 4)
    const size = u32le(b, i + 4)
    const end = i + 8 + size + (size & 1)
    if (i + 8 + size > b.length) return null
    out.push({ type, start: i, end: Math.min(end, b.length), data: i + 8, size })
    i = end
  }
  return out.length ? out : null
}

/** The PNG chunks that carry text or a time: what a camera or an editor writes about the picture. */
const PNG_METADATA = new Set(['tEXt', 'zTXt', 'iTXt', 'eXIf', 'tIME'])

/** [b] ([type]) without its metadata: a JPEG's EXIF, XMP, IPTC and comments, a PNG's text and time, a WebP's EXIF and XMP. Null when it isn't whole. */
export function stripMetadata(b: Uint8Array, type: ImageType): Uint8Array | null {
  const parts: Uint8Array[] = []
  if (type === 'jpeg') {
    const segs = jpegSegments(b)
    if (!segs) return null
    for (const s of segs) if (!jpegMetadata(b, s)) parts.push(b.subarray(s.start, s.end))
  } else if (type === 'png') {
    const chunks = pngChunks(b)
    if (!chunks) return null
    parts.push(b.subarray(0, 8))
    for (const c of chunks) if (!PNG_METADATA.has(c.type)) parts.push(b.subarray(c.start, c.end))
  } else {
    const chunks = webpChunks(b)
    if (!chunks) return null
    parts.push(b.subarray(0, 12))
    for (const c of chunks) {
      if (c.type === 'EXIF' || c.type === 'XMP ') continue
      if (c.type === 'VP8X' && c.size >= 10) {
        const x = b.slice(c.start, c.end)
        x[8] = x[8]! & ~0x0c // the flags: no EXIF (0x08), no XMP (0x04) any more
        parts.push(x)
      } else parts.push(b.subarray(c.start, c.end))
    }
  }
  const out = new Uint8Array(parts.reduce((n, p) => n + p.length, 0))
  let at = 0
  for (const p of parts) {
    out.set(p, at)
    at += p.length
  }
  if (type === 'webp') {
    const size = out.length - 8
    out[4] = size & 0xff
    out[5] = (size >>> 8) & 0xff
    out[6] = (size >>> 16) & 0xff
    out[7] = (size >>> 24) & 0xff
  }
  return out
}

/** [b]'s size in pixels ([type]): a JPEG's frame, a PNG's IHDR, a WebP's canvas (VP8X) or its frame (VP8, VP8L); null when it has none. */
export function dimensions(b: Uint8Array, type: ImageType): { width: number; height: number } | null {
  const ok = (width: number, height: number) => (width > 0 && height > 0 ? { width, height } : null)
  if (type === 'jpeg') {
    const sof = jpegSegments(b)?.find(s => isSof(s.marker) && s.end - s.data >= 5)
    return sof ? ok(u16be(b, sof.data + 3), u16be(b, sof.data + 1)) : null
  }
  if (type === 'png') {
    const ihdr = pngChunks(b)?.[0]
    return ihdr?.type === 'IHDR' && ihdr.size >= 8 ? ok(u32be(b, ihdr.data), u32be(b, ihdr.data + 4)) : null
  }
  const chunks = webpChunks(b) ?? []
  const vp8x = chunks.find(c => c.type === 'VP8X' && c.size >= 10)
  if (vp8x) return ok(u24le(b, vp8x.data + 4) + 1, u24le(b, vp8x.data + 7) + 1)
  const vp8 = chunks.find(c => c.type === 'VP8 ' && c.size >= 10)
  if (vp8 && b[vp8.data + 3] === 0x9d && b[vp8.data + 4] === 0x01 && b[vp8.data + 5] === 0x2a) {
    return ok(((b[vp8.data + 6]! | (b[vp8.data + 7]! << 8)) & 0x3fff), ((b[vp8.data + 8]! | (b[vp8.data + 9]! << 8)) & 0x3fff))
  }
  const vp8l = chunks.find(c => c.type === 'VP8L' && c.size >= 5)
  if (vp8l && b[vp8l.data] === 0x2f) {
    const bits = u32le(b, vp8l.data + 1)
    return ok((bits & 0x3fff) + 1, ((bits >>> 14) & 0x3fff) + 1)
  }
  return null
}

// --- the store ------------------------------------------------------------------------------------------------------

/** The node's local month of [ms]: YYYY-MM. */
export function localMonth(ms = Date.now()): string {
  const d = new Date(ms)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

/** The month [n] months before [month] (YYYY-MM). */
function monthsBefore(month: string, n: number): string {
  const [y, m] = month.split('-').map(Number) as [number, number]
  const k = y * 12 + (m - 1) - n
  return `${Math.floor(k / 12)}-${String((k % 12) + 1).padStart(2, '0')}`
}

/**
 * The tutor chat's photos in [dir] (<data>/app/chat-images): one folder a month, a file a photo named by its id. [dataDir]
 * is the learner's data directory, whose .gitignore keeps them out ([ignoreChatImages]).
 */
export class ChatImages {
  constructor(
    readonly dir: string,
    private readonly dataDir: string,
    private readonly log: Log,
    private readonly clock: () => number = Date.now,
  ) {}

  private months(): string[] {
    return existsSync(this.dir) ? readdirSync(this.dir).filter(f => MONTH.test(f)).sort().reverse() : []
  }

  /** Where photo [id] is kept (the newest month first), with its type; null when it isn't. */
  find(id: string): { path: string; month: string; type: ImageType } | null {
    if (!CHAT_IMAGE_ID.test(id)) return null
    for (const month of this.months()) {
      for (const [ext, type] of Object.entries(OF_EXT)) {
        const path = join(this.dir, month, `${id}.${ext}`)
        if (existsSync(path)) return { path, month, type }
      }
    }
    return null
  }

  /** Photo [id] as the routes answer it, read from its file; null when it isn't kept (or isn't a picture any more). */
  info(id: string): ChatImageInfo | null {
    const f = this.find(id)
    if (!f) return null
    const bytes = new Uint8Array(readFileSync(f.path))
    const size = dimensions(bytes, f.type)
    if (!size) return null
    return { id, ...size, bytes: bytes.length, type: MIME[f.type], file: `app/chat-images/${f.month}/${id}.${EXT[f.type]}` }
  }

  /** What the tutor's event carries of photo [id] (data.image): its absolute path and size; null when it isn't kept. */
  event(id: string): ChatImageEvent | null {
    const f = this.find(id)
    const i = this.info(id)
    return f && i ? { path: f.path, width: i.width, height: i.height, bytes: i.bytes, type: i.type } : null
  }

  /** The bytes and type of photo [id], for GET; null when it isn't kept. */
  read(id: string): { bytes: Uint8Array<ArrayBuffer>; type: string } | null {
    const f = this.find(id)
    return f ? { bytes: new Uint8Array(readFileSync(f.path)), type: MIME[f.type] } : null
  }

  /**
   * Keeps [raw] as photo [id] in this month's folder, its metadata stripped: what it is then, `existing` when the id was
   * kept already (a retry: nothing is written again). A string when it isn't a JPEG, PNG or WebP with a size.
   */
  put(id: string, raw: Uint8Array): { info: ChatImageInfo; existing: boolean } | string {
    const kept = this.info(id)
    if (kept) return { info: kept, existing: true }
    const type = sniff(raw)
    if (!type) return 'not a JPEG, PNG or WebP image'
    const bytes = stripMetadata(raw, type)
    const size = bytes && dimensions(bytes, type)
    if (!bytes || !size) return `not a whole ${type === 'jpeg' ? 'JPEG' : type === 'png' ? 'PNG' : 'WebP'} image (no size in it)`
    this.ready()
    const month = localMonth(this.clock())
    const folder = join(this.dir, month)
    mkdirSync(folder, { recursive: true, mode: 0o700 })
    chmodSync(folder, 0o700)
    const path = join(folder, `${id}.${EXT[type]}`)
    const tmp = `${path}.tmp-${process.pid}-${randomBytes(3).toString('hex')}`
    writeFileSync(tmp, bytes, { mode: 0o600 })
    chmodSync(tmp, 0o600)
    renameSync(tmp, path)
    this.log(`chat: a photo kept (${Math.round(bytes.length / 1024)} KB, ${size.width}×${size.height}${bytes.length < raw.length ? `, ${raw.length - bytes.length} bytes of metadata stripped` : ''}): ${path}`)
    return { info: { id, ...size, bytes: bytes.length, type: MIME[type], file: `app/chat-images/${month}/${id}.${EXT[type]}` }, existing: false }
  }

  /** Deletes photo [id] wherever it is kept; whether there was one. */
  remove(id: string): boolean {
    let gone = false
    for (let f = this.find(id); f; f = this.find(id)) {
      rmSync(f.path, { force: true })
      gone = true
    }
    if (gone) this.log(`chat: photo ${id} deleted`)
    return gone
  }

  /** Deletes the month folders more than [CHAT_IMAGES_KEEP_MONTHS] before this month (by their names). Returns them. */
  prune(keep = CHAT_IMAGES_KEEP_MONTHS): string[] {
    const oldest = monthsBefore(localMonth(this.clock()), keep - 1)
    const gone = this.months().filter(m => m < oldest)
    for (const m of gone) rmSync(join(this.dir, m), { recursive: true, force: true })
    if (gone.length) this.log(`chat: photos of ${gone.join(', ')} deleted (kept ${keep} months)`)
    return gone
  }

  /** The folder (0700), the .gitignore line (the learner may have edited it since the start) and the months kept. */
  private ready() {
    mkdirSync(this.dir, { recursive: true, mode: 0o700 })
    chmodSync(this.dir, 0o700)
    ignoreChatImages(this.dataDir, this.log)
    this.prune()
  }

  /** At the bridge's start: the .gitignore line and the months kept, if the folder is there. Never throws. */
  start() {
    try {
      ignoreChatImages(this.dataDir, this.log)
      if (existsSync(this.dir)) {
        chmodSync(this.dir, 0o700)
        this.prune()
      }
    } catch (e) {
      this.log(`chat: ${(e as Error).message}`)
    }
  }
}

/**
 * The learner's data repository keeps app/chat-images/ out: a .gitignore (or a repository) without a line that does gets
 * [CHAT_IMAGES_IGNORE] appended, once (ignore.ts keepOut). A plain data directory without a .gitignore is left alone.
 * Returns whether it added it.
 */
export function ignoreChatImages(dataDir: string, log: Log): boolean {
  return keepOut(dataDir, {
    line: CHAT_IMAGES_IGNORE,
    samples: ['app/chat-images/2026-01/0123456789abcdef0123456789abcdef.jpg'],
    comment: "Photos from the tutor chat (the app's 📷, PUT /chat/images): stay on this machine (added by the bridge).",
  }, m => log(`chat: ${m}`))
}
