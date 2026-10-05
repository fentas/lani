// Imported before @clack/prompts: without a terminal (a log, a test, CI) the output has no colours or cursor codes
// (Bun's util.styleText colours whatever the stream is, so they are taken off on the way out).
import { stripVTControlCharacters } from 'node:util'

for (const stream of [process.stdout, process.stderr]) {
  if (stream.isTTY || process.env.FORCE_COLOR) continue
  const write = stream.write.bind(stream) as (chunk: unknown, ...rest: unknown[]) => boolean
  stream.write = ((chunk: unknown, ...rest: unknown[]) =>
    write(typeof chunk === 'string' ? stripVTControlCharacters(chunk) : chunk instanceof Uint8Array ? stripVTControlCharacters(Buffer.from(chunk).toString('utf8')) : chunk, ...rest)) as typeof stream.write
}
