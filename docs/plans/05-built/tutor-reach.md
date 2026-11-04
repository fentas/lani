# Built: the tutor's reach, outside dictionaries and its own past

*Branch `tutor-reach`, 2026-11-04. Why: the in-app tutor's `dictionary_lookup` read only Lani's lexicon, so it could only say it
couldn't check (*dvoja vrata*, *kak*, *rasti*), and 5 of 6 chat errors in the shadow logs came with no lookup. A longer thread
costs too much (doubling the 30k limit is about +55 %), so the thread limit stays and the tutor gets a read tool over what the
phone already keeps.*

## Jan's decisions

**1. fran.si: written consent, or stay off.** The bridge doesn't ask fran.si unless the node lists it:
`LANI_DICTIONARY_CONSENT=fran.si` in `lani.env`. **This changes what Jan's live tutor does today.** Before this branch the app fetched
fran.si itself on every Slovene lookup, with no cache and no cap (`FranLookup`). That code is gone, so until the switch is set the
in-app tutor's Slovene comes from the lexicon and Wiktionary. The Claude Code tutor keeps WebFetch.

- The terms ask for written consent for "larger quantities" only. A few capped lookups from one node may fall outside that, but
  robots.txt also has `Disallow: /iskanje/`, and the search lives at `/iskanje`. Reading the clause is Jan's call, not this branch's.
- **Recommended:** write to fran@zrc-sazu.si. Say what the bridge does: one word per request, at most 30 a day per node, five
  seconds apart, cached 180 days, a user agent naming Lani. Ask about a public app in the same mail: every install is a node.
- If Jan reads the clause as not covering one node, setting the switch now restores fran.si, now capped and cached.

**2. A public app.** What each site would need:

| Site | Status here | What a public app needs |
|---|---|---|
| Wiktionary | On, every language | Nothing more than today: CC BY-SA attribution (each result carries it) and Wikimedia's etiquette. At a large scale, a shared cache or the dumps |
| DWDS | On, German, the published API only | Its contact form. The API is published without a stated limit, but the general terms want express permission for automated queries (below). If Jan prefers to ask first, take the row out of `SOURCES.de`: Wiktionary remains |
| fran.si | Off until consent | Written consent from ZRC SAZU |
| Sloleks | Not built | The site is Sloleks 2.0, CC BY-NC-SA 4.0, with no published API. Sloleks 3.0 is a 240 MB download, CC BY-SA 4.0, 365,340 entries: building its forms into the lexicon needs no request at all, and is the open way to Slovene forms |
| treccani.it | Not built | Written consent (redazione@treccani.it). No API found |
| duden.de | Not built | A licence (duden.de/form/license-request). Its paid API (from 39.95 € a month for 100 requests a day) checks spelling and grammar; it isn't a dictionary lookup |
| dictionary.cambridge.org | Not built | **Jan has to read the terms in a browser** (below). The API takes an evaluation key under its own terms |

**3. The Claude Code tutor's three new sites.** `general.md`, `CLAUDE.md` and the guide now name treccani.it, duden.de and
dictionary.cambridge.org, "as far as this session's settings allow". The `WebFetch` lines for them are **not** in
`.claude/settings.json` yet: they are Jan's to add, and this branch doesn't touch permissions. Until then the tutor reads a refusal
and the text stays true. Before adding them, Jan may want to see what the terms below say about them:

- Treccani allows personal use only, forbids downloading and text and data mining, and its robots.txt sets trap paths for AI bots.
- Cambridge refused an honest client outright, so WebFetch may fail there too.
- Duden's robots.txt shuts out `ClaudeBot` and `anthropic-ai` by name, and reserves text and data mining.

## Each site's terms

Read on 2026-11-04, one request at a time, seconds apart, with a user agent naming Lani.

**fran.si** ([terms](https://fran.si/o-portalu?page=Terms); robots.txt: `Disallow: /iskanje/`):
> Portal Fran, slovarji Inštituta za slovenski jezik Frana Ramovša ZRC SAZU je uporabnikom prosto dostopen na svetovnem spletu.
> Avtomatsko pridobivanje večjih količin podatkov je dovoljeno ob pisnem soglasju ZRC SAZU.

Questions go to fran@zrc-sazu.si.

**dwds.de** ([terms](https://www.dwds.de/d/nutzungsbedingungen), [API](https://www.dwds.de/d/api)):
> Jegliche Nutzung der Inhalte des DWDS, einschließlich jedoch nicht beschränkt auf automatisierte Abfragen und Auswertungen
> (Crawlen, Parsen, Text- und Data-Mining), sofern nicht über § 60d UrhG zulässig, ist nur mit ausdrücklicher Genehmigung gestattet.

> Ergebnisse, die aus dem DWDS gewonnen werden, müssen mit einer Quellenangabe versehen werden

The API page offers its URLs openly:
> Auch wenn wir viele der Daten im Wortauskunftssystem des DWDS aus rechtlichen Gründen nicht für eine API öffnen dürfen, so können
> doch zumindest rudimentäre Informationen über Wörterbucheinträge abgerufen werden.

So the bridge uses `/api/wb/snippet` only (spelling and word class, cited) and never the word pages. robots.txt doesn't disallow
`/api`, but it does shut out named AI agents (`ClaudeBot`, `anthropic-ai`, `ChatGPT-User`) from the whole site. Lani's user agent
is none of those and is honest about being a bot; the spirit of that list is one more reason to ask for a public app.

**treccani.it** ([terms](https://www.treccani.it/footer/termine-condizioni.html)):
> L'utilizzazione e la riproduzione, anche solo parziale, dei Contenuti con qualsiasi mezzo, tecnica e/o strumento, senza il preventivo
> consenso scritto dell'Istituto, è espressamente vietata.

> I Contenuti sono messi a disposizione dell'Utente, salvo ove non diversamente precisato, esclusivamente per uso personale. Non è
> consentita, neppure in parte, alcuna forma di downloading

> Ai sensi dell'art. 4 della Direttiva UE 2019/790, si comunica che l'uso di tutti i contenuti presenti sul portale www.treccani.it per
> attività di text e data mining è espressamente vietato senza preventiva autorizzazione scritta del titolare dei diritti.

**duden.de** ([AGB](https://www.duden.de/service/agb), [API](https://www.duden.de/api)):
> Der Anbieter behält sich eine Nutzung der Inhalte für Text und Data Mining im Sinne § 44b UrhG ausdrücklich vor.

That sentence stands in the AGB's section on online products. The AGB read as a shop's and subscribers' terms; no clause on a
free visitor's automated lookups was found. robots.txt disallows `/suche/` and `/suchen/…` for everyone and names the AI bots.

**dictionary.cambridge.org: not read.** Cloudflare answered 403 ("Sorry, you have been blocked") to the honest client on
`dictionary.cambridge.org/robots.txt` and on `www.cambridge.org/legal/website-terms-of-use`. I didn't retry, change the user agent or
go through another client. That refusal alone rules the site out for an automated lookup. All I have on the terms is a search
engine's summary, not read at the source: the API (`dictionary-api.cambridge.org/api/terms-and-conditions`) grants a revocable licence
on an evaluation key, with quotas. Treat that as a lead, not a quote.

**Wiktionary** ([API etiquette](https://www.mediawiki.org/wiki/API:Etiquette),
[user agent policy](https://foundation.wikimedia.org/wiki/Policy:Wikimedia_Foundation_User-Agent_Policy)): CC BY-SA 4.0. The bridge follows
what they ask:
> Set an informative User-Agent string with contact information, or you may be IP-blocked without notice.

> Make your requests in series rather than in parallel, by waiting for one request to finish before sending a new request.

**Sloleks** ([site](https://viri.cjvt.si/sloleks/slv/about), [3.0](https://hdl.handle.net/11356/1745)): the site says "Delo je dostopno pod
licenco Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International"; its search runs on an internal route of the page's own,
not a published API, so nothing here is built on it. 3.0 on CLARIN.SI is CC BY-SA 4.0. This is the same split as followups-17's item 6.

## 1. The outside dictionaries

`GET /dictionary?w=…&line=…&deep=1` answers what `GET /lookup` answers and, when the lexicon has no entry or with `deep=1`, asks the
language's sources. The app's word cards stay on `/lookup` and never go outside. `companion/bridge/src/dictionaries.ts` has the table:

| Language | Sources, asked side by side |
|---|---|
| sl | fran.si (with consent), Wiktionary |
| it | Wiktionary |
| de | DWDS, Wiktionary |
| en | Wiktionary |

Wiktionary is English Wiktionary for all four: one host, the language's section only, meanings in English. Adding a source is one row:
its host, licence, daily cap, gap, the request's path and how to read the answer.

**Polite and bounded:**
- GETs only, over https, to the table's hosts only. A redirect elsewhere is refused and never followed.
- One request at a time per host and seconds apart (3 s, fran.si 5 s), across all the node's bridges (`usage.json`, under a lock).
- A daily cap per host: Wiktionary 200, DWDS 100, fran.si 30. A timeout of 8 s, at most 10 s of waiting for a turn at the host.
- At most 1 MB a page, unread past that, also on the phone (`maxBytes` in its fetch). At most 3,000 characters an entry.
- A 429 or 503 rests the host at least ten minutes, or its `Retry-After` up to a day.
- The user agent is `LaniDictionaryBot/0.1 (a language tutor looking up one word at a time; https://github.com/fentas/lani)`.

**Only the word goes out.** The line stays home: it only picks the meaning in Lani's dictionary. A thing that isn't a word (more than four
parts, digits, an address, over 40 characters) or the learner's name isn't sent, and the answer says so. There are no cookies and no
referrer.

**The cache** is per language, source and word, on the node (`~/.cache/lani/lexicon/outside`, or `LANI_DICTIONARY_CACHE`), so a word is
fetched once for all its learners and bridges. An entry keeps 180 days, a miss 7; a failure isn't kept. What is cached still answers at
the cap. The by-hand lookups below went to a temporary cache, so nothing of them stayed on the node.

**A failure** comes back in our own words ("timed out", "HTTP 500", "the day's limit for this source is reached") and never in the site's.
The turn goes on with what it has.

**Untrusted.** `clean` takes out every angle bracket and every control, invisible and direction character, after entities are decoded, so
`&lt;/untrusted_dictionary_content&gt;` doesn't survive either. The app then wraps each source in its own
`<untrusted_dictionary_content source url about>` block (`Untrusted.block`) and replaces the brackets again, whoever sent the text. That second
layer also closes a gap the old code had: `FranLookup` decoded entities after stripping tags, and neither tutor neutralised the block's
closing tag. Nothing here acts on a page's text, and no prompt changes because of it.

**The tutors.** The shadow and the live tutor both go through `GET /dictionary` (`BridgeReads`, `LiveBridge.dictionary`) and render with
`DictionaryText`. The app itself reaches no dictionary any more, so the table is the one place. A node's bridge from before this answers 404
and the tutor gets its lexicon alone, as before.

**Older apps.** `/lookup` is unchanged and they never ask `/dictionary`. An app from before this branch keeps its own direct fran.si lookup
until it updates: no bridge change can stop that.

**The hosted mode.** The bridge in QuickJS on the phone can reach out: its `fetch` runs over OkHttp, so the same table, limits and cache
work there, with the cache in the app's files and the cap per device. No consent setting exists there, so fran.si stays off. Offline, or
with a site down, it's the lexicon alone with the reason said. On a public app every install is its own node with its own cap: that
fan-out is what decision 2 is about.

**Off.** `LANI_DICTIONARY_SOURCE=off` means the lexicon alone. A loopback URL there is a fake standing in for every host. The smoke, the dev
bridge and QA use these (`test/isolate.ts`, `test/smoke/harness.ts`, `test/dev-bridge.ts`, `test/fakes/dictionary.ts`) and never reach a real site.

**The prompt.** `core-prompt.md` rule 13: a claim about a form, an ending or a word's class gets a lookup first; say the source; "couldn't confirm"
never retracts a rule of the grammar book. `general.md` says the same for both kinds of tutor and names what each can reach. Beside a miss, the
tool's answer repeats that what isn't confirmed isn't wrong.

### One real lookup per language, by hand

`bun test/dictionary-by-hand.ts <language> <word>`, one request per host, on 2026-11-04. Slovene ran before empty headings were dropped, so it
also printed "Conjugation:" and "Derived terms:" with nothing under them.

- **sl, *rasti*:** Wiktionary, <https://en.wiktionary.org/wiki/rasti>: "Verb: rásti impf (intransitive) to grow (intransitive) to increase".
- **it, *zibaldone*:** Wiktionary, <https://en.wiktionary.org/wiki/zibaldone>: "Noun: zibaldone m (plural zibaldoni) (outdated) any dish made up of
  various and mixed ingredients; a concoction … (by extension) a confusing mixture of diverse things or people; a hodgepodge …".
- **de, *Schnickschnack*:** DWDS, <https://www.dwds.de/wb/Schnickschnack>: "Schnickschnack: Substantiv". Wiktionary,
  <https://en.wiktionary.org/wiki/Schnickschnack>: "Noun: Schnickschnack m (strong, genitive Schnickschnackes or Schnickschnacks, no plural)
  (colloquial, chiefly derogatory) bric-a-brac, knick-knack … frills, bells and whistles … empty talk, chatter".
- **en, *thingamajig*:** Wiktionary, <https://en.wiktionary.org/wiki/thingamajig>: "Alternative forms: thingamejig, thingumajig, thingummyjig Noun:
  thingamajig (plural thingamajigs) (informal) Something that one does not know the name of."

fran.si wasn't asked: its row is off, and it is tested on the fake only. Its reader is `FranLookup`'s, ported.

**What Wiktionary can't do.** Its inflection tables don't come through the text extract, so a Slovene form question still rests on the lexicon's
tables and form-of entries. *rasti* above shows the lemma without the paradigm. That's the gap Sloleks 3.0 or fran.si would close.

## 2. The tutor's own past: `chat_history`

A read tool in phase A's tools, for the live tutor and the shadow alike (`tutor/History.kt`, `companion/tutor/app-tools/chat_history.json`):

- `word`: a word or phrase at a word's start (any form of it, accents and case aside), newest first, each with the message it answers or that
  answers it. An exchange shows once.
- `day`: that day in order, by the phone's calendar. With `word`, a search within it.
- Neither: the last 14 days with a line each.
- `limit` (default 8, at most 20) and `offset`. A result is at most 6,000 characters.

**Where it reads:** the chat and its archive (`ChatStore.read`: the same files, read only, nothing let go), up to a year, and the last 30 day logs for
what isn't in the chat: an exercise's answer and its feedback. A role-play's lines and the chat's own turns in the logs are left out. In the shadow
"what was shown" is the real tutor's reply, never a shadow's own, which nobody saw, and the result's head says those aren't its words
(`TurnText.NOT_YOURS`, the matrix's fix).

**Local.** It's a file read on the phone. Nothing new leaves but what the tutor reads into a turn, which then goes to the model's provider like
the rest of the turn. Stored threads aren't touched. The result stays inside `<untrusted_chat_history>`, so a request in an old message isn't an
order now.

**What it costs.** A result enters the thread and is paid once at the write price: 6,000 characters is about 1,700 tokens, against the 30k limit.
The same goes for a lookup: up to 3,000 characters a source.

### What the prefix's change does

The prefix grows by 3,033 characters, about 870 tokens: the new tool 1,497, `dictionary_lookup` 635, the core prompt 420, `general.md` 481.

- **Threads.** The first turn after the update sees another prefix hash and restarts the thread as text: the notebook, the day's lines and the last
  exchanges, with no closing call (`the prefix changed`). A role-play's thread starts anew. That's one full cache write of the prefix per tutor, the
  same as any update that changes a tool or the prompt, and then it's read from the cache as before. No migration, nothing in Jan's stored threads
  changes.
- **The matrix.** Every lane has the prefix, so lanes restart alike and stay comparable with each other within a run. **A run from before this update
  doesn't compare with one after it:** the lanes have one more tool, a rule that sends them to look up more, and lookups that now answer. The second
  scoring's numbers (about 32–33) are on the old prefix. Keep comparisons within one prefix hash. `shadow-compare` has a `chat_history` column beside
  `dictionary_lookup`.
- **The cap.** Lanes share a read for 120 s (`SharedReads`) and the node caches a word, so nineteen lanes cost one request per word. Different words
  still count: a matrix day can use up a good part of the 200.

## What was checked

*(Filled in at the end of the branch, in the report to the lead.)*

- `tsc`, the smoke's `dictionaries` section: cache, cap, timeout, 429 rest, size, a host outside the table, redirects, consent, off, a hostile page.
- Android: `ReachTest`, `PhoneDictionaryTest` (the real bundle in QuickJS, against a local fake), `ChatStoreTest`.
- QA: `companion/bin/qa --steps tutor-reach`: one shadow turn on the mock, both tools in the panel.

## Native check, voice

No learner-facing text was added or changed: nothing for a native check, 0 characters of voice. The tool descriptions and prompt rules are English, for the
tutor. Wiktionary's text is shown to the tutor only, marked by source.
