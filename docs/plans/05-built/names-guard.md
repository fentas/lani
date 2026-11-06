# The name check on the bridge (`names-guard`, built 2026-11-06)

Every clip goes through one ElevenLabs account, and its use policy can suspend an account for hateful or harassing text. The voice
service (glas) checks every name before a voice says it, but the bridge had two ways round that check:

1. On a `rejected` answer from `/v1/names/resolve` (`invalid_name`, `blocked_name`, `name_review`), `NameStore.nameFromLibrary`
   returned null, and `nameClip()` then voiced the name itself with the node's own ElevenLabs key.
2. `NameStore.refuses()` used only the shape rules (`nameOk`), so a learner called "Kurba" passed, and the name was voiced by the node's
   key or by the service's `/v1/clips/say`. With `LANI_VOICE_NAMES` off nothing was checked at all: every line with the name was voiced
   whole, ahead of time too.

Now a name the check or the service refuses is said by no paid voice, on any path: the phone says the line.

## What

- **The same check on the bridge** (`companion/bridge/src/name-check.ts`): the service's rules and lists, to the letter, so a node with no
  service set up refuses what the service refuses on its lists. A test holds the two to the same verdict on 4,825 names.
- **The service's word wins** where the service is set up: any `rejected` answer ends with no clip, and its refusal holds for every line
  with the name, for a while.
- **Every path:** the check runs wherever a node voices, `LANI_VOICE_NAMES` set or not. The phone's own bridge has no voice store and
  checks nothing.

## How

### The check

`name-check.ts` is glas's `src/name-lists.ts` and the start of its `NameCheck.classify`:

1. **The shape rules** (`nameOk`, as before), the name read in NFKC first ("Ｍojca" is "mojca"), and only a name that folds to plain
   letters: a voice reads a small capital or an IPA letter as the letter it looks like, and no list would know the word ("ᴋurba").
2. **The blocklists:** LDNOOBW in its 28 languages, profane-words, Lani's own list (its Slovene expanded to every form Sloleks 3.0 has)
   and the shape rules' short list. Each word, each part of a word, the whole name and the parts run together, as written and with the
   marks off ("scheisse" is "scheiße", "Dĭck" is "Dick"). The blocklist wins over a real name.
3. **The keep list:** names a list would take that are voiced all the same (Marica, Guido, Péter), as a whole name or a whole word of one.

A name gets one of three verdicts: `invalid` and `blocked` are refused, `clear` goes on. Whether a name nobody knows waits for approval (two
words, or a list's word hidden inside it) stays the service's: it needs all 98,492 given names and a review list. On a node without the
service such a name is voiced as before, by the node's own key for its own learner.

**The given names.** The service's third step, a real given name, changes the second step in one way: a given name is looked up whole and
by its words only, and a list's word that matches only with the marks off doesn't count against it. So Peter is no French "péter", Mosè no
German "Möse", Beom-sik no Turkish "sik". Without the given names the bridge would block those. With all of them it would carry a 288 KB
list it needs 0.7 % of. `names-sync` keeps the given names one of whose lookups a list has: 711. For every other given name the lists find
nothing either way, so the verdict is the same (the test checks all 711, and 1,500 others).

### The service's word

With `GLAS_URL` and `GLAS_KEY` set, `NameStore` asks the service for its word on the learner's name: `POST /v1/names/resolve` with the name,
kind `arch`, the narrator `female` (every language's cast has her) and `make: false`. That ask voices nothing, and the service records
nothing of it but a count of its verdicts.

| The service answers | The bridge | Held for |
|---|---|---|
| `rejected` with `blocked_name` or `invalid_name` (its lists, or Jan's `glas names block`) | every line with the name: the phone | 24 hours |
| `rejected` with `name_review` (two words nobody knows, or a list's word inside: waiting for approval), `name_limit`, or any other code | the same | 1 hour |
| `rejected` with `unknown_voice` or `invalid_kind` (said before the service reads the name) | no word on the name: the check alone | not held |
| `ready`, `missing` | the name goes on | 1 hour |
| nothing (out of reach, paused, an HTTP error) | the last word stands, or none: the check alone | — |

`SERVICE_WORD_MS` in `name-store.ts` has the two times. A refusal that has run out is asked again before the next line that waits for it,
so a name approved meanwhile is voiced at once; a word that let the name through is asked again behind the next line. The bridge asks
once at its start (`warm`), so the paths that can't wait have the word.

A name clip's own ask (`nameFromLibrary`, in the line's voice and kind) rejected with **any** code, `unknown_voice` too, ends that line at
the phone: the node never voices a name the service rejected. Its refusal of the name is held as above.

### Every path

| Path | Before | Now | Test |
|---|---|---|---|
| A line of the content, name lines on | the shape rules only; a name clip the service rejected was voiced by the node | the check, then the service's word: the phone (204) | `store: a name on a blocklist`, `library: a name the service refuses (…)`, `library: a name clip the service rejects, whatever the code`; glas smoke `…the node's name store lets the phone say every line with it` |
| A line voiced whole: name lines off, a line flagged `whole`, a text the content lacks | voiced whole with any name | the phone | `store: with name lines off a refused name is the phone's too`, `store: … in a declined form and a text the content lacks too`, `library: the service's refusal holds for every line with the name`, `library: warmed at the start …` |
| The learner's own lines (`player`) | the shape rules, name lines on only | the phone | `routes: a name on a blocklist: 204 for a line, the learner's own, a declined form …` |
| `/voice/prepare` (the day's audio) | as above | `url: null` | the same route check |
| `/voice/redo` | as above | 503 | `routes: … in a re-record`, `store: the voice store makes no take …` |
| `/voice/sample` (a voice to choose, "Živjo! Jaz sem Kurba.") | voiced, unchecked | `url: null`, the app speaks | `routes: a name on a blocklist … a voice's sample without a clip` |
| Preloading (`LANI_VOICE_PRELOAD`) | the shape rules | the check and the service's word, waited for | `library: a text voiced ahead waits for the service's word` |
| What a publish queues (`VoiceStore.enqueue`) | held back for the shape rules, name lines on only | held back; with the service, also until it has had its say | `store: the voice store makes no take … a publish's lines` |
| Any take of the voice store: a line, a Slovene narrator's, a re-record, the re-voice drains, a remake | — | none of a text with a refused name (`VoiceStore.withhold`) | `store: the voice store makes no take …`; glas smoke `…and the voice store makes no take of it, whatever asks` |
| A visit's line voiced on the visitor's node | voiced | not voiced here: the host's clip, else 204 | visit-voices smoke `… a line with the visitor's name, which the name check refuses` |
| `/voice/index` | every clip | a clip made before of a line with a refused name is left out, so the app asks and gets 204 | `store: a clip made before of a line with a refused name is left out of the app's index` |
| `voice-build` | voiced whole in `--run` | left out of the plan and the run, counted | a dry run on a synthetic learner called Kurba: "the 1938 line(s) that say it are left out" |

No log line names the learner: the bridge says "the learner's name is not voiced (the name check: blocked)" or "the voice service refuses
the learner's name (name_review)", once.

### The lists and the sync

`companion/bridge/name-lists/` is generated, never edited:

| File | What |
|---|---|
| `blocklist.json` | every list's entries as the source wrote them (glas's `blocklist.json.gz`, unzipped, an entry a line so a change reads as lines) |
| `given-listed.txt` | the 711 given names above |
| `keep.txt` | glas's keep list, with Jan's reasons |
| `sources.json` | the glas commit it came from, and the sources' pins (commits, the given names' query and date) |
| `NOTICE` | the sources' licences, as glas has them |

```sh
companion/bin/names-sync <glas checkout>           # writes name-lists/ and test/fixtures/name-check-verdicts.json
companion/bin/names-sync <glas checkout> --check   # writes nothing; exit 1 when the bridge's copy is behind
cd companion/bridge && bun test/name-check.ts      # the bridge against glas's verdicts
```

It reads glas's `library/names/` and runs glas's own `NameCheck.classify` (so the checkout needs `bun install` once): the fixture is the
service's verdict on each probe, not a copy of the bridge's. The probes: the names of glas's README and tests, ten entries of each list and
the ways round them (a capital, a mark, a second word, a hyphen, run into another word), the 711 given names, 1,500 others spread evenly,
and the keep list beside a list's word. A commit or a date alone is no change. It also says when `NOTICE.md` doesn't name a pinned commit.

Today: 36 lists, 7,652 entries, 82 kept names; 4,825 probes (587 invalid, 1,664 blocked, 2,218 known, 356 unknown), all agreeing.

## How to use it

Nothing to set: every node checks. When the service's lists change, run `names-sync` against a checkout and commit what it writes; the
smoke's `name-lines` section runs the parity test.

## Decisions, and why

- **"Blocked → the phone" counts `invalid` too.** With name lines off, a name that failed the shape rules was voiced whole. Those are the
  ways round the lists: three words ("Kurba Kurba Kurba"), another script, a letter that reads as another. With name lines on they were
  the phone's already; now they are with name lines off too. A real name of three words is the phone's on a node; the learner can shorten it.
- **Any `rejected` code on a name clip's ask ends at the phone,** as the brief says, a code the bridge doesn't know yet among them. Only
  for the bridge's own ask about the name do `unknown_voice` and `invalid_kind` say nothing, since the service answers them before it
  reads the name.
- **Out of reach, the check alone decides.** The service's lists and the bridge's are the same, so what is lost while it is down is only
  the review of a name nobody knows, and lines aren't held back from a learner for an outage.
- **Lists that can't be read refuse every name** (the phone says each line with the name) and the log says why: a broken install fails
  safe.

## Tap counts

No screen changed: 0 taps before, 0 after. The app needs no change: a 204 or `url: null` is what it handles already for any line the store
can't make, Teo's 0.2.698 included.

## Native check and voice

No text was written or changed for the app, so nothing new to voice (0 characters) and no new native check. The lists' own native check
(`lani.txt`, machine-written) belongs to glas, and the bridge's copy follows it.

## Tests

- `bun test/name-check.ts` (in the smoke's `name-lines` section): 4,825 probes against glas's verdicts, and what the bridge refuses.
- `bun test/name-lines.ts` (the same section): the lists with name lines on and off, the voice store's guard, every service code, the hold
  and the ask again, the index, and a blocklisted name on a real bridge's routes. With the old handling of the service's refusal back (no word asked, a rejected clip
  voiced by the node), 11 of its checks fail.
- The smoke's `glas` section: the real HTTP client against the fake service, end to end; `visit-voices` (in `towns`): a visit's line.

## What's left

- **The service never hears a node's two-word name for review.** The bridge asks with `make: false`, and the service only looks then, so
  a name of two words nobody knows waits for an approval nobody is asked for (`glas names approve <name>` works by name). The service
  could count a `make: false` ask from a key on its review list.
- **The service's `/v1/clips/say` voices a line's text with no name check:** a line with the learner's name goes to the key's own space.
  The bridge never sends one with a refused name now, but another client could; the service could look up a line's words in its lists,
  as it does for a phrase.
- **A clip made before this of a line with a now-refused name** stays in the node's store. The app isn't given it, and nothing plays it.
- **The sync is by hand:** `names-sync --check` says when the copy is behind.
