// The phone bridge's shims (phone/shims) against Bun's own URL, URLSearchParams, Buffer and node:path, on the inputs the
// bridge meets (the app's request URLs, the towns' and the services', file: URLs, the bytes' encodings, the paths): the
// smoke's phone section runs it. The host's UTF-8 and base64 are Bun's here. Prints "all the same", or each difference.
import { resolve } from 'node:path'

const P = resolve(import.meta.dir, '../phone/shims')
;(globalThis as any).__lani = {
  utf8Encode: (s: string) => new Int8Array(new TextEncoder().encode(s).buffer),
  utf8Decode: (b: Int8Array) => new TextDecoder().decode(new Uint8Array(b.buffer, b.byteOffset, b.byteLength)),
  base64Encode: (b: Int8Array, url: boolean) => Buffer.from(b.buffer, b.byteOffset, b.byteLength).toString(url ? 'base64url' : 'base64'),
  base64Decode: (s: string) => new Int8Array(Buffer.from(s, 'base64').buffer.slice(0)),
}
const { URL: U, URLSearchParams: Q } = await import(P + '/url.ts')
const { Buffer: B } = await import(P + '/buffer.ts')
const path = await import(P + '/path.ts')
const np = await import('node:path')
let bad = 0
const eq = (what: string, a: unknown, b: unknown) => { if (JSON.stringify(a) !== JSON.stringify(b)) { bad++; console.log('DIFF', what, '\n  shim  ', JSON.stringify(a), '\n  native', JSON.stringify(b)) } }
const urls: [string, string?][] = [
  ['http://lani.phone/lookup?w=hi%C5%A1a'], ['http://lani.phone/lookup?w=hiša&x=a+b'], ['https://Example.COM:443/a/../b/./c?q=1#frag'],
  ['http://127.0.0.1:8790/town/hello'], ['http://user:pa ss@host:8080/p a th?q=a b#h a'], ['/relative?x', 'http://base.example/dir/file'],
  ['../up', 'http://base.example/a/b/c'], ['?only', 'http://base.example/a/b?old#h'], ['#h2', 'http://base.example/a/b?old#h'],
  ['//other.host/x', 'https://base.example/a'], ['file:///data/user/0/x%20y/z.json'], ['http://[::1]:19110/health'],
  ['http://x/a%2Fb/c'], ['http://x/ž?č=š#ć'], ['http://x'], ['https://glas.lani.team/v1/resolve?text=Dober%20dan!'],
  ["http://x/p?a='b'"], ['http://x/a/b/../../..'], ['http://x/%7Efoo'], ['https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/sl/x.onnx?download=true'],
]
for (const [u, b] of urls) {
  try {
    const s = new U(u, b), n = new URL(u, b)
    for (const k of ['href', 'protocol', 'host', 'hostname', 'port', 'pathname', 'search', 'hash', 'origin', 'username', 'password'] as const) eq(`${u} ${b ?? ''} .${k}`, (s as any)[k], (n as any)[k])
    eq(`${u} params`, [...s.searchParams], [...n.searchParams])
  } catch (e) { bad++; console.log('THROW', u, b, (e as Error).message) }
}
// (a broken escape, "%E0%A4%A", WHATWG decodes to U+FFFD; the shim leaves it as it is: the app never sends one)
for (const q of ['a=1&b=2&a=3', 'w=hi%C5%A1a', 'x=a+b%2Bc', 'empty=&novalue&=v', 'sp=a%20b']) {
  const s = new Q(q), n = new URLSearchParams(q)
  eq(`params ${q}`, [...s], [...n]); eq(`params ${q} toString`, s.toString(), n.toString())
}
const u1 = new U('http://x/p?a=1'); u1.searchParams.set('b', 'č d'); const u2 = new URL('http://x/p?a=1'); u2.searchParams.set('b', 'č d'); eq('set params href', u1.href, u2.href)
for (const s of ['hello', 'žabe čez', '', '🙂 emoji', 'a'.repeat(100) + 'š']) {
  for (const enc of ['utf8', 'hex', 'base64', 'base64url', 'latin1'] as const) eq(`buffer ${s.slice(0, 10)} ${enc}`, B.from(s).toString(enc), Buffer.from(s).toString(enc))
  eq(`bytelength ${s.slice(0, 10)}`, B.byteLength(s), Buffer.byteLength(s))
}
eq('from hex', [...B.from('00ff10ab', 'hex')], [...Buffer.from('00ff10ab', 'hex')])
eq('from b64url', [...B.from('AQIDBA-_', 'base64url')], [...Buffer.from('AQIDBA-_', 'base64url')])
eq('from b64 nopad', [...B.from('AQID', 'base64')], [...Buffer.from('AQID', 'base64')])
eq('readUInt32BE', B.from([0xff, 1, 2, 3, 4]).readUInt32BE(0), Buffer.from([0xff, 1, 2, 3, 4]).readUInt32BE(0))
eq('subarray hex', B.from('abcdef').subarray(2, 4).toString('hex'), Buffer.from('abcdef').subarray(2, 4).toString('hex'))
eq('concat', B.concat([B.from('ab'), B.from('cd')]).toString(), Buffer.concat([Buffer.from('ab'), Buffer.from('cd')]).toString())
eq('equals', B.from('ab').equals(B.from('ab')), true)
eq('json', JSON.stringify(B.from('ab')), JSON.stringify(Buffer.from('ab')))
const pp: [string, string[]][] = [['join', ['a', 'b/../c', './d']], ['join', ['/a/', '/b/']], ['join', ['a', '']], ['dirname', ['/a/b/']], ['dirname', ['a']], ['dirname', ['/']], ['basename', ['/a/b.json', '.json']], ['basename', ['/a/b/']], ['extname', ['/a/b.tar.gz']], ['extname', ['.hidden']], ['relative', ['/a/b/c', '/a/d']], ['normalize', ['a//b/./c/..']], ['normalize', ['/../x']], ['resolve', ['/a', 'b', '../c']], ['resolve', ['/a/b/', '/c/']]]
for (const [f, a] of pp) eq(`path.${f}(${a})`, (path as any)[f](...a), (np as any)[f](...a))
console.log(bad ? `${bad} differences` : 'all the same')
process.exit(bad ? 1 : 0)
