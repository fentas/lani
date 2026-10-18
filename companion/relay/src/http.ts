// HTTP helpers: JSON answers and errors (every answer of the relay's own says so in X-Lani-Relay, so a bridge never
// takes it for the town's), and body limits.

export class HttpError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly headers: Record<string, string> = {},
  ) {
    super(message)
  }
}

/** X-Lani-Relay on the relay's own answers: "error", "away" (the town isn't there), "ok"; "tunnel" on a town's. */
export const RELAY_HEADER = 'x-lani-relay'

export const json = (body: unknown, status = 200, headers: Record<string, string> = {}) =>
  Response.json(body, { status, headers: { [RELAY_HEADER]: status >= 400 ? 'error' : 'ok', ...headers } })

export const fail = (status: number, code: string, error: string, headers: Record<string, string> = {}) => json({ error, code }, status, headers)

/** The request's body, at most [max] bytes (413 beyond); read from the stream, so a body without a length can't grow past it. */
export async function readBody(req: Request, max: number): Promise<Uint8Array> {
  const tooLarge = () => new HttpError(413, 'too_large', `the body is too large (at most ${max} bytes)`)
  if (Number(req.headers.get('content-length') ?? 0) > max) throw tooLarge()
  if (!req.body) return new Uint8Array()
  const chunks: Uint8Array[] = []
  let size = 0
  const reader = req.body.getReader()
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    size += value.byteLength
    if (size > max) {
      await reader.cancel().catch(() => {})
      throw tooLarge()
    }
    chunks.push(value)
  }
  const out = new Uint8Array(size)
  let at = 0
  for (const c of chunks) {
    out.set(c, at)
    at += c.byteLength
  }
  return out
}

/** A JSON body, or a 400. */
export function parseJson(body: Uint8Array): any {
  try {
    return JSON.parse(new TextDecoder().decode(body))
  } catch {
    throw new HttpError(400, 'bad_request', 'expected a JSON body')
  }
}

/** Bytes as a response's body (bun:sqlite's and the reader's Uint8Array are one). */
export const asBody = (b: Uint8Array | null | undefined): BodyInit => (b ?? new Uint8Array()) as Uint8Array<ArrayBuffer>
