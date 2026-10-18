// Smoke checks: the tutor chat's photos (src/chat-images.ts, companion/README.md "Tutor chat", "Photos"): PUT
// /chat/images/<id> keeps a JPEG, PNG or WebP (by its bytes) under <data>/app/chat-images/<YYYY-MM>/ (folders 0700, the
// file 0600) without its metadata, only with the app's token and within its cap, once per id; GET gives it back, DELETE
// removes it; a chat message naming it (image: {id}) carries its path, size and type to the tutor (data.image), a quoted
// photo message too (data.reply_to.photo); the data repository's .gitignore keeps /app/chat-images/ out; old months go.
import { existsSync, mkdirSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { isAbsolute, join, resolve } from 'node:path'
import {
  CHAT_IMAGE_MAX, CHAT_IMAGES_IGNORE, ChatImages, dimensions, ignoreChatImages, localMonth, sniff, stripMetadata,
} from '../../src/chat-images'
import { auth, base, channelEvents, check, dataDir, fam, guideOf, sendChunked, tempDir, token } from './harness'

const repo = resolve(import.meta.dir, '../../../..')
const mode = (p: string) => statSync(p).mode & 0o777
const quiet = () => {}
const b64 = (s: string) => new Uint8Array(Buffer.from(s, 'base64'))
const has = (b: Uint8Array, s: string | number[]) => Buffer.from(b).includes(Buffer.from(typeof s === 'string' ? Buffer.from(s, 'latin1') : s))

// Tiny real pictures (ImageMagick, -strip): a 12×8 JPEG, a 5×3 PNG, a 6×4 WebP lossy and lossless.
const JPEG = b64('/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAoHBwgHBgoICAgLCgoLDhgQDg0NDh0VFhEYIx8lJCIfIiEmKzcvJik0KSEiMEExNDk7Pj4+JS5ESUM8SDc9Pjv/2wBDAQoLCw4NDhwQEBw7KCIoOzs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozv/wAARCAAIAAwDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAH/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAwb/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwCgDSb/2Q==')
const PNG = b64('iVBORw0KGgoAAAANSUhEUgAAAAUAAAADAQMAAABh+Fe7AAAAA1BMVEX/AAAZ4gk3AAAAC0lEQVQI12NgAAEAAAYAAWb0yWwAAAAASUVORK5CYII=')
const WEBP = b64('UklGRjgAAABXRUJQVlA4ICwAAACQAQCdASoGAAQAAgA0JaACdLoAA5gA/vmTb/+QH/+QH/+QH/8gP+IXeyAwAA==')
const WEBP_LOSSLESS = b64('UklGRhwAAABXRUJQVlA4TA8AAAAvBcAAAAcQ0f/+ByKi/wEA')

const cat = (...parts: (Uint8Array | number[])[]) => new Uint8Array(Buffer.concat(parts.map(p => Buffer.from(p as Uint8Array))))
const u16 = (n: number) => [(n >> 8) & 0xff, n & 0xff]
const u32 = (n: number) => [(n >>> 24) & 0xff, (n >>> 16) & 0xff, (n >>> 8) & 0xff, n & 0xff]
const le32 = (n: number) => [n & 0xff, (n >>> 8) & 0xff, (n >>> 16) & 0xff, (n >>> 24) & 0xff]
const seg = (marker: number, data: Uint8Array | number[]) => cat([0xff, marker, ...u16(data.length + 2)], data)

/**
 * An EXIF block as a phone camera writes one (APP1 "Exif\0\0", a big-endian TIFF): IFD0 with the camera's make and the
 * GPS IFD's offset, the GPS IFD with the latitude's reference (N) and the latitude (46° 3' 25").
 */
function exifWithGps(): Uint8Array {
  const make = Buffer.from('LaniPhone\0', 'latin1') // 10 bytes
  // TIFF header (8), IFD0 at 8: 2 entries (2 + 24 + 4 = 30) → make at 38, GPS IFD at 48: 2 entries (30) → rationals at 78
  const tiff = cat(
    [0x4d, 0x4d, 0x00, 0x2a, ...u32(8)],
    [...u16(2), ...u16(0x010f), ...u16(2), ...u32(make.length), ...u32(38), ...u16(0x8825), ...u16(4), ...u32(1), ...u32(48), ...u32(0)],
    make,
    [...u16(2), ...u16(0x0001), ...u16(2), ...u32(2), 0x4e, 0x00, 0x00, 0x00, ...u16(0x0002), ...u16(5), ...u32(3), ...u32(78), ...u32(0)],
    [...u32(46), ...u32(1), ...u32(3), ...u32(1), ...u32(25), ...u32(1)],
  )
  return cat(Buffer.from('Exif\0\0', 'latin1'), tiff)
}

/** [JPEG] as a phone's camera hands it on: EXIF with GPS, XMP, IPTC (APP13), a comment, and an ICC profile kept. */
function cameraJpeg(): Uint8Array {
  return cat(
    JPEG.subarray(0, 2),
    seg(0xe1, exifWithGps()),
    seg(0xe1, cat(Buffer.from('http://ns.adobe.com/xap/1.0/\0<x:xmpmeta>Ljubljana</x:xmpmeta>', 'latin1'))),
    seg(0xe2, cat(Buffer.from('ICC_PROFILE\0\x01\x01fake-profile', 'latin1'))),
    seg(0xed, cat(Buffer.from('Photoshop 3.0\x008BIM city=Ljubljana', 'latin1'))),
    seg(0xfe, cat(Buffer.from('taken at home, Ljubljana', 'latin1'))),
    JPEG.subarray(2),
  )
}

/** A PNG chunk with its CRC. */
function chunk(type: string, data: Uint8Array | number[]): Uint8Array {
  const body = cat(Buffer.from(type, 'latin1'), data)
  return cat(u32(data.length), body, u32(Bun.hash.crc32(body) >>> 0))
}

/** [PNG] with a text chunk, a time and an eXIf after its IHDR. */
function taggedPng(): Uint8Array {
  const ihdrEnd = 8 + 12 + 13
  return cat(
    PNG.subarray(0, ihdrEnd),
    chunk('tEXt', Buffer.from('Comment\0taken at home, Ljubljana', 'latin1')),
    chunk('tIME', [0x07, 0xea, 10, 18, 12, 0, 0]),
    chunk('eXIf', exifWithGps().subarray(6)),
    PNG.subarray(ihdrEnd),
  )
}

/** [WEBP] in an extended file (VP8X, 6×4, flags EXIF and XMP), its VP8 frame, then EXIF and XMP chunks. */
function taggedWebp(): Uint8Array {
  const frame = WEBP.subarray(12) // the VP8 chunk
  const vp8x = cat(Buffer.from('VP8X', 'latin1'), le32(10), [0x0c, 0, 0, 0, 5, 0, 0, 3, 0, 0])
  const exif = exifWithGps().subarray(6)
  const exifChunk = cat(Buffer.from('EXIF', 'latin1'), le32(exif.length), exif, exif.length & 1 ? [0] : [])
  const xmp = Buffer.from('<x:xmpmeta>Ljubljana</x:xmpmeta>', 'latin1')
  const xmpChunk = cat(Buffer.from('XMP ', 'latin1'), le32(xmp.length), xmp, xmp.length & 1 ? [0] : [])
  const body = cat(Buffer.from('WEBP', 'latin1'), vp8x, frame, exifChunk, xmpChunk)
  return cat(Buffer.from('RIFF', 'latin1'), le32(body.length), body)
}

export default async function chatImages() {
  const month = localMonth()
  const root = join(dataDir, 'app/chat-images')
  const url = (id: string) => `${base}/chat/images/${id}`
  const put = (id: string, body: Uint8Array | string, headers: Record<string, string> = { authorization: `Bearer ${token}` }) =>
    fetch(url(id), { method: 'PUT', headers: { ...headers, 'content-type': 'image/jpeg' }, body })
  const message = (body: object) => fetch(`${base}/message`, { method: 'POST', headers: auth, body: JSON.stringify(body) })
  const lastWith = async (text: string) => {
    await Bun.sleep(100)
    return channelEvents.findLast(n => String(n.params?.content).includes(text))
  }
  const blockOf = (ev: any) => JSON.parse(String(ev?.params?.content).match(/```json\n([\s\S]*?)\n```/)?.[1] ?? '{}')
  const A = '0123456789abcdef0123456789abcd01'

  // --- the bytes, on their own ------------------------------------------------------------------------------------
  check('chat-images: sniff by content: JPEG, PNG, WebP; text and a GIF are none', sniff(JPEG) === 'jpeg' && sniff(PNG) === 'png' && sniff(WEBP) === 'webp' && sniff(new TextEncoder().encode('hello')) === null && sniff(new TextEncoder().encode('GIF89a......')) === null)
  check('chat-images: the sizes: 12×8 JPEG, 5×3 PNG, 6×4 WebP (lossy, lossless, extended)',
    JSON.stringify([dimensions(JPEG, 'jpeg'), dimensions(PNG, 'png'), dimensions(WEBP, 'webp'), dimensions(WEBP_LOSSLESS, 'webp'), dimensions(taggedWebp(), 'webp')]) ===
      JSON.stringify([{ width: 12, height: 8 }, { width: 5, height: 3 }, { width: 6, height: 4 }, { width: 6, height: 4 }, { width: 6, height: 4 }]))
  const camera = cameraJpeg()
  const clean = stripMetadata(camera, 'jpeg')!
  check("chat-images: a camera's JPEG stripped: no EXIF (GPS, make), XMP, IPTC or comment; its ICC profile and picture kept",
    !!clean && !has(clean, 'Exif') && !has(clean, 'LaniPhone') && !has(clean, 'Ljubljana') && !has(clean, 'xmpmeta') && has(clean, 'ICC_PROFILE') &&
      Buffer.from(clean).equals(Buffer.from(cat(JPEG.subarray(0, 2), seg(0xe2, cat(Buffer.from('ICC_PROFILE\0\x01\x01fake-profile', 'latin1'))), JPEG.subarray(2)))))
  check('chat-images: a JPEG without metadata stays as it is', Buffer.from(stripMetadata(JPEG, 'jpeg')!).equals(Buffer.from(JPEG)))
  const png = stripMetadata(taggedPng(), 'png')!
  check('chat-images: a PNG stripped of its text, time and eXIf chunks, the rest as it was', Buffer.from(png).equals(Buffer.from(PNG)) && dimensions(png, 'png')?.width === 5)
  const webp = stripMetadata(taggedWebp(), 'webp')!
  check('chat-images: a WebP stripped of its EXIF and XMP chunks, their VP8X flags cleared, its RIFF size right',
    !has(webp, 'EXIF') && !has(webp, 'XMP ') && !has(webp, 'Ljubljana') && (webp[20]! & 0x0c) === 0 && Buffer.from(webp).readUInt32LE(4) === webp.length - 8 && dimensions(webp, 'webp')?.height === 4, webp.length)
  check('chat-images: a cut-off JPEG is none', stripMetadata(camera.subarray(0, 40), 'jpeg') === null)

  // --- who may send, and what -----------------------------------------------------------------------------------
  check('chat-images: PUT without the token: 401', (await put(A, camera, {})).status === 401)
  check("chat-images: the family's token may not send (403), nor read or delete", (await put(A, camera, fam)).status === 403 && (await fetch(url(A), { headers: fam })).status === 403 && (await fetch(url(A), { method: 'DELETE', headers: fam })).status === 403)
  check('chat-images: nothing saved by them', !existsSync(join(root, month, `${A}.jpg`)))
  check('chat-images: not an image (text sent as image/jpeg): 415', (await put(A, 'just some text, no picture')).status === 415)
  check('chat-images: an empty body: 400', (await put(A, new Uint8Array())).status === 400)
  check('chat-images: an id that is no id: 400', (await put('short', camera)).status === 400 && (await put('bad.id.jpg', camera)).status === 400)
  // on a connection of its own (sendChunked): the server answers before reading the rest
  const big = await sendChunked(url(A), 'PUT', { authorization: `Bearer ${token}`, 'content-type': 'image/jpeg' }, CHAT_IMAGE_MAX + 1024, camera)
  check(`chat-images: a photo over ${CHAT_IMAGE_MAX} bytes: 413 with its reason, nothing saved`, (big.status === 413 && big.text.includes('the photo is too large (max 8 MB)') || big.status === 0) && !existsSync(join(root, month, `${A}.jpg`)), big)

  // --- kept, without its metadata -------------------------------------------------------------------------------
  // a month far back, there before: a new photo lets it go (12 months kept)
  mkdirSync(join(root, '2024-01'), { recursive: true })
  writeFileSync(join(root, '2024-01', 'fedcba9876543210fedcba9876543210.jpg'), JPEG)
  const first = await put(A, camera)
  const info = await first.json()
  const file = join(root, month, `${A}.jpg`)
  check('chat-images: PUT keeps the photo: its size, bytes, type and file', first.status === 200 && info.id === A && info.width === 12 && info.height === 8 && info.type === 'image/jpeg' && info.file === `app/chat-images/${month}/${A}.jpg` && info.bytes === clean.length && !info.existing, info)
  const saved = new Uint8Array(readFileSync(file))
  check('chat-images: the saved file is the picture without its EXIF (no GPS), XMP or comment', Buffer.from(saved).equals(Buffer.from(clean)) && !has(saved, 'Exif') && !has(saved, 'Ljubljana'))
  check('chat-images: the folders and the file are readable by this user only (0700, 0700, 0600)', mode(root) === 0o700 && mode(join(root, month)) === 0o700 && mode(file) === 0o600, [mode(root), mode(join(root, month)), mode(file)].map(m => m.toString(8)))
  check('chat-images: a month more than 12 back went with it', !existsSync(join(root, '2024-01')))
  const before = statSync(file).mtimeMs
  await Bun.sleep(20)
  const again = await put(A, PNG)
  const againInfo = await again.json()
  check('chat-images: the same id again (a retry): its info, existing, nothing rewritten', again.status === 200 && againInfo.existing === true && againInfo.bytes === info.bytes && againInfo.type === 'image/jpeg' && statSync(file).mtimeMs === before && !existsSync(join(root, month, `${A}.png`)), againInfo)

  const got = await fetch(url(A), { headers: { authorization: `Bearer ${token}` } })
  const gotBytes = new Uint8Array(await got.arrayBuffer())
  check('chat-images: GET gives it back: its bytes, type and length, privately cached', got.status === 200 && got.headers.get('content-type') === 'image/jpeg' && got.headers.get('content-length') === String(clean.length) && (got.headers.get('cache-control') ?? '').startsWith('private') && Buffer.from(gotBytes).equals(Buffer.from(saved)))
  check('chat-images: GET of an unknown photo: 404', (await fetch(url('ffffffffffffffffffffffffffffffff'), { headers: { authorization: `Bearer ${token}` } })).status === 404)
  const P = '0123456789abcdef0123456789abcd02'
  const pngPut = await (await put(P, taggedPng())).json()
  check('chat-images: a PNG is kept as .png, without its text', pngPut.type === 'image/png' && pngPut.width === 5 && existsSync(join(root, month, `${P}.png`)) && !has(new Uint8Array(readFileSync(join(root, month, `${P}.png`))), 'Ljubljana'), pngPut)

  // --- the chat message: the tutor gets its path --------------------------------------------------------------------
  check('chat-images: a chat with a photo is accepted', (await message({ kind: 'chat', text: 'Kaj piše na tabli?', image: { id: A } })).ok)
  const asked = await lastWith('Kaj piše na tabli?')
  const image = blockOf(asked).image
  check("chat-images: the tutor's chat event carries data.image: the absolute path, size, bytes, type",
    asked?.params?.meta?.kind === 'chat' && isAbsolute(image?.path ?? '') && image.path.endsWith(join('app/chat-images', month, `${A}.jpg`)) && existsSync(image.path) && image.width === 12 && image.height === 8 && image.bytes === clean.length && image.type === 'image/jpeg', asked?.params)
  check('chat-images: a photo alone (no text): accepted, the event its JSON block', (await message({ kind: 'chat', text: '', image: { id: P } })).ok)
  await Bun.sleep(100)
  const alone = channelEvents.findLast(n => String(n.params?.content).includes(`${P}.png`))
  check('chat-images: … which starts the event', String(alone?.params?.content).startsWith('```json') && blockOf(alone).image?.width === 5, alone?.params?.content)
  const unknown = await message({ kind: 'chat', text: 'x', image: { id: 'ffffffffffffffffffffffffffffffff' } })
  check("chat-images: a photo the node doesn't have: 400, saying to PUT it first", unknown.status === 400 && (await unknown.json()).error.includes('PUT /chat/images/ffffffffffffffffffffffffffffffff first'))
  check('chat-images: a photo with another kind than chat: 400', (await message({ kind: 'answer', conversation_id: 'c9', text: 'x', image: { id: A } })).status === 400)
  await message({ kind: 'chat', text: 'a path of my own', data: { image: { path: '/etc/passwd' } } })
  check("chat-images: data.image the app sent itself is the bridge's to say: dropped", !('image' in blockOf(await lastWith('a path of my own'))))
  await message({ kind: 'chat', text: 'In tu?', data: { reply_to: { from: 'learner', text: 'Kaj piše na tabli?', photo: A } } })
  const quoted = blockOf(await lastWith('In tu?')).reply_to
  check("chat-images: a reply quoting a photo message: its photo's path for the tutor", quoted?.text === 'Kaj piše na tabli?' && quoted.photo?.path === image.path && quoted.photo.width === 12, quoted)
  const gone = await message({ kind: 'chat', text: 'In ta?', data: { reply_to: { from: 'learner', text: '', photo: 'ffffffffffffffffffffffffffffffff' } } })
  check('chat-images: … a photo gone since: {id, gone}, the message all the same', gone.ok && JSON.stringify(blockOf(await lastWith('In ta?')).reply_to.photo) === '{"id":"ffffffffffffffffffffffffffffffff","gone":true}')
  const guide = await guideOf()
  check("chat-images: the guide says to open data.image.path with the Read tool, and that the photo is private", guide.includes('data.image.path with the Read tool') && guide.includes('The photo is private') && guide.includes('reply_to with'))

  // --- deleted ----------------------------------------------------------------------------------------------------
  const del = await (await fetch(url(P), { method: 'DELETE', headers: auth, body: JSON.stringify({ client_id: 'del-0123456789' }) })).json()
  check('chat-images: DELETE removes it', del.deleted === true && del.id === P && !existsSync(join(root, month, `${P}.png`)))
  check('chat-images: … GET is a 404 then, DELETE again deletes nothing', (await fetch(url(P), { headers: { authorization: `Bearer ${token}` } })).status === 404 && (await (await fetch(url(P), { method: 'DELETE', headers: { authorization: `Bearer ${token}` } })).json()).deleted === false)

  // --- never committed --------------------------------------------------------------------------------------------
  const ignore = readFileSync(join(dataDir, '.gitignore'), 'utf8')
  check("chat-images: the bridge added /app/chat-images/ to the learner's .gitignore at its start, once, keeping their lines", ignore.startsWith('# the smoke learner\n/app/bridge-token\n') && ignore.split('\n').filter(l => l === CHAT_IMAGES_IGNORE).length === 1, ignore)
  check('chat-images: … and not again', !ignoreChatImages(dataDir, quiet) && readFileSync(join(dataDir, '.gitignore'), 'utf8') === ignore)
  const own = tempDir('lani-smoke-photos-own-')
  writeFileSync(join(own, '.gitignore'), 'app/chat-images\n')
  check("chat-images: a learner's own line that keeps them out is enough", !ignoreChatImages(own, quiet))
  const template = Bun.spawnSync(['python3', join(repo, '.claude/hooks/lani_data_repo.py'), 'gitignore'], { stdout: 'pipe' }).stdout.toString()
  check("chat-images: lani-setup's .gitignore (lani_data_repo.py) keeps /app/chat-images/ out", template.split('\n').includes(CHAT_IMAGES_IGNORE), template.slice(-400))

  // --- 12 months kept ---------------------------------------------------------------------------------------------
  const old = tempDir('lani-smoke-photos-prune-')
  for (const m of ['2025-10', '2025-11', '2026-10', 'notes']) mkdirSync(join(old, m))
  const store = new ChatImages(old, tempDir('lani-smoke-photos-data-'), quiet, () => new Date(2026, 9, 15).getTime())
  const pruned = store.prune()
  check('chat-images: months more than 12 back go (2025-10 in October 2026), the rest stay', pruned.join() === '2025-10' && ['2025-11', '2026-10', 'notes'].every(m => existsSync(join(old, m))), pruned)
}
