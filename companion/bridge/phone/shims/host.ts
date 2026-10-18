// The host the phone's bridge runs on: `globalThis.__lani`, the functions the app's Kotlin defines in the QuickJS runtime
// before the bundle runs (si.lanisce.lani.phone.Host, docs/plans/05-built/phone-runtime.md "The host API"). Every one is
// synchronous: a slow one (a fetch, a timer) starts in Kotlin and comes back later as a call of the bundle's loop
// (loop.ts). Bytes cross as Int8Array (Kotlin's ByteArray), structured values as JSON text.

export type Host = {
  // --- the bridge's files: only under the app's <filesDir>/lani (and, in the JVM tests, the repository, read only) ---
  fsReadText(path: string): string
  fsReadBytes(path: string): Int8Array
  /** [append]: at the end; [exclusive]: fails with EEXIST when the file is there (open's 'wx'). */
  fsWriteText(path: string, text: string, append: boolean, exclusive: boolean): void
  fsWriteBytes(path: string, bytes: Int8Array, append: boolean, exclusive: boolean): void
  fsExists(path: string): boolean
  /** JSON: {type: "file"|"dir"|"other", size, mtimeMs, ctimeMs, birthtimeMs, atimeMs, mode}. */
  fsStat(path: string): string
  /** JSON: [[name, "file"|"dir"|"other"], …], in the directory's order. */
  fsReaddir(path: string): string
  fsMkdir(path: string, recursive: boolean): void
  fsRename(from: string, to: string): void
  fsUnlink(path: string): void
  fsRmdir(path: string): void
  fsRm(path: string, recursive: boolean, force: boolean): void
  /** [exclusive]: EEXIST when [to] is there (a hard link's semantics, by a copy). */
  fsCopy(from: string, to: string, exclusive: boolean): void
  fsRealpath(path: string): string
  /** Fails as a write there would (outside the roots, read only); the mode itself is left as the app's files have it. */
  fsChmod(path: string): void
  // --- time ---
  /** The zone's id (Europe/Ljubljana) and its offset at [ms] in minutes, as Date.getTimezoneOffset counts it. */
  tz(): string
  tzOffset(ms: number): number
  // --- crypto ---
  randomBytes(n: number): Int8Array
  randomUUID(): string
  /** [alg]: sha256, sha1, sha512, md5. */
  hash(alg: string, data: Int8Array): Int8Array
  hmac(alg: string, key: Int8Array, data: Int8Array): Int8Array
  /** A new EC P-256 private key, PKCS#8 PEM (with its public key, as OpenSSL writes it). */
  ecGenerate(): string
  /** The public key of a PKCS#8 PEM private key, SubjectPublicKeyInfo DER. */
  ecPublic(pem: string): Int8Array
  /** ECDSA with SHA-256 over [data], DER (Java's SHA256withECDSA, node's sign('sha256', …)). */
  ecSign(pem: string, data: Int8Array): Int8Array
  ecVerify(spki: Int8Array, data: Int8Array, signature: Int8Array): boolean
  // --- text and bytes ---
  utf8Encode(text: string): Int8Array
  utf8Decode(bytes: Int8Array): string
  base64Encode(bytes: Int8Array, url: boolean): string
  base64Decode(text: string): Int8Array
  gunzip(bytes: Int8Array): Int8Array
  gzip(bytes: Int8Array): Int8Array
  /** [a] against [b] as the locale's collator orders them (ICU: what node's localeCompare does): -1, 0 or 1. */
  collate(a: string, b: string, locale: string): number
  // --- the loop (loop.ts) ---
  setTimer(id: number, ms: number): void
  clearTimer(id: number): void
  /** Starts a request; its answer comes as __laniLoop.fetched(id), read with [fetchResult] and [fetchBody]. */
  fetch(id: number, method: string, url: string, headers: string, body: Int8Array | null, timeoutMs: number, redirect: string): void
  fetchCancel(id: number): void
  /** JSON: {status, statusText, headers: [[name, value]…], url, redirected} or {error}. */
  fetchResult(id: number): string
  fetchBody(id: number): Int8Array | null
  /** Blocks the bridge's thread [ms] (Bun.sleepSync: a lock's retry). */
  sleep(ms: number): void
  // --- the app's side (entry.ts) ---
  /** JSON: {projectDir, dataDir, home}: where the bridge's files are. */
  config(): string
  /** JSON: the environment the bridge sees as process.env. */
  env(): string
  log(level: string, text: string): void
  /** A request the app made (entry.ts handle): JSON {method, url, headers: [[name, value]…]}, and its body. */
  request(id: number): string
  requestBody(id: number): Int8Array | null
  /** The answer to request [id]: JSON {status, headers: [[name, value]…], stream}, its body text or bytes (none for a stream). */
  respond(id: number, head: string, text: string | null, bytes: Int8Array | null): void
  /** A stream's next part, and its end ([error]: why it broke off). */
  chunk(id: number, text: string | null, bytes: Int8Array | null): void
  end(id: number, error: string | null): void
  /** A tool call the in-app tutor made: JSON {name, args}; its result JSON (a ToolResult). */
  toolCall(id: number): string
  toolDone(id: number, result: string): void
  /** An event into the in-app tutor's session: the content and meta Claude Code's channel gets. */
  channel(content: string, meta: string): void
}

declare global {
  // eslint-disable-next-line no-var
  var __lani: Host
}

export const host = (): Host => globalThis.__lani

/** [u] as the host takes bytes: an Int8Array over exactly its bytes (quickjs-kt copies a typed array's whole buffer). */
export function toHost(u: Uint8Array | ArrayBuffer): Int8Array {
  if (u instanceof ArrayBuffer) return new Int8Array(u)
  if (u.byteOffset === 0 && u.byteLength === u.buffer.byteLength) return new Int8Array(u.buffer as ArrayBuffer)
  return new Int8Array(copyOf(u).buffer as ArrayBuffer)
}

/** A fresh copy of [u]'s bytes, its buffer exactly them (a Buffer's slice shares its memory, as node's does). */
export function copyOf(u: Uint8Array): Uint8Array {
  const c = new Uint8Array(u.byteLength)
  c.set(u)
  return c
}

/** The host's bytes as a Uint8Array over the same memory. */
export const fromHost = (b: Int8Array): Uint8Array => new Uint8Array(b.buffer, b.byteOffset, b.byteLength)

export type ErrnoError = Error & { code?: string; errno?: number; syscall?: string; path?: string; dest?: string }

const ERRNO: Record<string, number> = { ENOENT: -2, EEXIST: -17, ENOTDIR: -20, EISDIR: -21, EACCES: -13, EPERM: -1, ENOTEMPTY: -39, EROFS: -30, EINVAL: -22, EIO: -5, ENOSYS: -38 }

/**
 * An error as node's fs throws it: "ENOENT: no such file or directory, open '/x'", with its code, errno, syscall and path.
 * The host throws "<CODE>: <text>" (si.lanisce.lani.phone.HostFs); anything else is an EIO.
 */
export function errno(e: unknown, syscall: string, path?: string, dest?: string): ErrnoError {
  const raw = String((e as Error)?.message ?? e)
  const m = /(E[A-Z]+): ([^,\n]*)/.exec(raw)
  const code = m?.[1] ?? 'EIO'
  const text = m?.[2] ?? raw
  const err = new Error(`${code}: ${text}, ${syscall}${path !== undefined ? ` '${path}'` : ''}${dest !== undefined ? ` -> '${dest}'` : ''}`) as ErrnoError
  err.code = code
  err.errno = ERRNO[code] ?? -5
  err.syscall = syscall
  if (path !== undefined) err.path = path
  if (dest !== undefined) err.dest = dest
  return err
}

/** Runs a host call that may fail, its failure as node's error for [syscall] on [path]. */
export function sys<T>(syscall: string, path: string | undefined, f: () => T, dest?: string): T {
  try {
    return f()
  } catch (e) {
    throw errno(e, syscall, path, dest)
  }
}
