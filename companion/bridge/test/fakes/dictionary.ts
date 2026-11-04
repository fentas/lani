// A fake of the outside dictionaries (src/dictionaries.ts): the smoke (test/smoke/dictionaries.ts) and the dev bridge talk
// to it through LANI_DICTIONARY_SOURCE, never to a real site. It answers each host's request under /<host>/…, as that
// site does: Wiktionary's Action API, DWDS's snippet API, fran.si's search page. [words] is what it knows; a word's page
// can be hostile, slow, huge, an error or a redirect (the knobs on [st]). [st.requests] keeps every request as it came.

/** A Wiktionary page's plain text in the API's shape, with [section]'s language among others. */
const extract = (word: string, section: string, body: string) =>
  `== Afrikaans ==\n\n=== Noun ===\nnot this one\n\n== ${section} ==\n\n=== Etymology ===\nFrom somewhere.\n\n=== Pronunciation ===\nIPA: /x/\n\n=== Noun ===\n${word} f\n\n${body}\n\n=== References ===\nA book.\n\n== Zulu ==\n\n=== Noun ===\nnor this one`

export type FakeWord = { section?: string; text?: string; page?: string; wortart?: string }

export function fakeDictionary(words: Record<string, FakeWord> = {}) {
  const st = {
    words,
    requests: [] as { url: string; path: string; search: string; agent: string | null; cookie: string | null; method: string; at: number }[],
    /** Ms before any answer. */
    delayMs: 0,
    /** The status of every answer (0: as usual). */
    status: 0,
    /** Every answer is this big, in bytes. */
    hugeBytes: 0,
    /** Every answer redirects here. */
    redirect: '',
    /** Requests in progress now, and the most there ever were per host. */
    open: new Map<string, number>(),
    peak: new Map<string, number>(),
  }
  const server = Bun.serve({
    hostname: '127.0.0.1',
    port: 0,
    async fetch(req) {
      const u = new URL(req.url)
      const host = u.pathname.split('/')[1]
      st.requests.push({ url: req.url, path: u.pathname, search: u.search, agent: req.headers.get('user-agent'), cookie: req.headers.get('cookie'), method: req.method, at: Date.now() })
      st.open.set(host, (st.open.get(host) ?? 0) + 1)
      st.peak.set(host, Math.max(st.peak.get(host) ?? 0, st.open.get(host)!))
      try {
        if (st.delayMs) await Bun.sleep(st.delayMs)
        if (st.redirect) return new Response(null, { status: 302, headers: { location: st.redirect } })
        if (st.status) return new Response('down', { status: st.status })
        if (st.hugeBytes) return new Response('x'.repeat(st.hugeBytes))
        if (host === 'en.wiktionary.org') {
          const title = u.searchParams.get('titles') ?? ''
          const w = st.words[title]
          const pages = [w?.section ? { title, extract: extract(title, w.section, w.text ?? '') } : { title, missing: true }]
          return Response.json({ query: { pages } })
        }
        if (host === 'www.dwds.de') {
          const q = u.searchParams.get('q') ?? ''
          const w = st.words[q]
          return Response.json(w?.wortart ? [{ input: q, lemma: q, wortart: w.wortart, url: `https://www.dwds.de/wb/${q}` }] : [])
        }
        if (host === 'fran.si') {
          const w = st.words[u.searchParams.get('Query') ?? '']
          return new Response(w?.page ?? '<html><body><main>Ni zadetkov.</main></body></html>', { headers: { 'content-type': 'text/html' } })
        }
        return new Response('not found', { status: 404 })
      } finally {
        st.open.set(host, st.open.get(host)! - 1)
      }
    },
  })
  return { st, url: `http://127.0.0.1:${server.port}`, stop: () => server.stop(true) }
}
