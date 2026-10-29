# Built: faces in the authored dialogs

*Built 2026-10-29, branch `faces-content` (Part B of "Faces: the character shown in talks and dialogs, with
expressions"). Jan: "maybe we can have the character shown on the free speech / dialogs - with different expressions."
Part A (branch `faces`) draws the faces in the app and adds the `face` field to the bridge; this part writes them into
the content that is already there. Content only: no code, no SCENES.md.*

## What

Every authored dialog in the repo got its faces, read whole and directed. A face is one of the contract's names:

`happy, laugh, surprised, curious, confused, thinking, worried, sad, cross, shy, proud`

(`neutral` is the resting face, the default, and is never written). It is the optional key `"face"` in exactly the
three places of the contract:

- on a spoken line, one with `who`: `{ "who": "babica", "face": "curious", "sl": "Dober večer, {learner}! Si lačen?", … }`;
- on a choice's `reply`: `"reply": { "face": "laugh", "sl": "Na zdravje? Z juho? Hi hi!", … }`;
- on a turn's `puzzled`: `"puzzled": { "face": "confused", "sl": "Hm? Kako, prosim?", … }`.

The files:

| Area | Files | Who reads them |
|---|---|---|
| Slovene scenes | `companion/scenes/*.json` (19): their `dialogs` and `variants` | Jan (adult) |
| Cultures' scenes | `companion/cultures/{friuli,kaernten,lakeland,primorska}/scenes/*.json` (17) | Friuli: Teo (about seven, learning Italian); Primorska: Jan; Kärnten, Lakeland: written as gently as Friuli |
| Project steps | `companion/cultures/primorska/project-steps/*.json` (13), every step at A1 and A2 | Jan |
| Celebrations | `companion/cultures/primorska/celebrations/*.json` (5), an age's party | Jan |
| Arrivals | `companion/cultures/*/arrivals.json` (4): each villager's meeting at every level, its `late` line, the templates | all |
| Keepers' talks | `companion/cultures/*/world.json` (4): the charcoal burner's meeting, talks, tales and his pile's cycle | all |

Not done, on purpose:

- **The evening stories** (`companion/cultures/*/stories/*.json`, 46 stories, 1,193 replies in their turns). A story's
  lines have no `who` (the teller narrates), so only its turns' replies could carry a face, and Part A's brief doesn't
  show the teller in the story panel yet. When it does, the replies are the next round (the same tool does them).
- **Test fixtures** (`companion/android/app/src/test/resources/…`, `src/debug/assets/scenes-fixture.json`): they belong
  to the code's tests, Part A's.
- **Villagers' lines** (`villagers/*.json` `lines`: greet, thanks, …) and the role-play scenarios: no line shape of the
  contract.

## How it was directed

Like a director reading each dialog whole, with the happening's title and time above it, and the person's character in
mind (the villagers' `personality`):

- **Spoken lines** keep the resting face unless the line carries a feeling. About half carry one, 1,556 of 3,154: a real
  question to the learner is curious ("Si lačen?", Zala's "Zakaj je med sladek? Zakaj?"); news and a discovery are
  surprised ("Oči!" between the trees at night); worried and sad where the story says so (the rain on the hay, a lost
  sheep, the pile breaking out, Janez's Ana); pride in a craft, a dish, a story is proud; counting, measuring,
  remembering is thinking; a compliment received and the small children are shy; delight and good news are happy.
  Routine greetings, instructions and goodbyes stay unmarked.
- **A right choice's reply** is nearly always marked: mostly happy, laugh where it is playful, proud where the person's
  pride shows ("Seveda diši. Babičina juha!"), worried or surprised only where its text says so ("Ojej! Počakaj."). A
  gruff smith's matter-of-fact "Prav." keeps the resting face: 19 right replies are unmarked, 18 of them Tone's,
  Bruno's, Toni's, Dixon's and the charcoal burner's, one France's ("Ja. Malo. Damo ga prašičem.").
- **A wrong choice's reaction** (the person takes the slip at face value) is confused when they didn't get it ("Hm?
  Kako, prosim?"), surprised when it startles them ("Jutro? Saj je že večer!"), curious when they ask back ("Prosim? Kaj
  še rabiš? Žlico?"), laugh when it is funny ("Na zdravje? Z juho? Hi hi!"), shy when it lands as a compliment
  ("Sladka? Misliš mene? Hi hi, hvala!"), and cross only mild and comic (being said ti by an elder; Micka's "Jaz? Kaj pa delam ves večer?
  Kuham!" at "Lahko pomagate?"). Never mocking.
- **Puzzled** is confused; one shush is worried (Tine's "Pst! Tiho, mačka bo slišala!").
- **The characters show:** Micka warm (happy, a soft laugh, worried "ojej", proud of her cooking, shy at a compliment);
  Janez dry and proverbial (thinking, proud of his sayings and his Triglav, a mild cross at ti); Luka and the children
  laugh a lot; Tine is shy and proud of his counting; Zala curious and proud when she corrects someone; Tone gruff (few
  faces, proud of his work, mild cross at a loose ending, shy when thanked); Ančka confused (she hears badly), surprised
  and curious; France thinking (he counts) and worried (the weather); Anton quiet and happy, worried for his bees; the
  charcoal burner sad at the end of his craft. The Friuli, Kärnten and Lakeland casts mirror them, gentler for the
  children's villages: more happy and laugh, cross rare and playful.

Placement: right after `who` on a line, first in a reply or a puzzled, so it reads like a script's direction ("BABICA
(curious): …"). The key was inserted into the text, never re-serialized: each file keeps its own formatting (the scenes'
`{ "who": … }`, the arrivals' compact `{"who": …}`, multi-line objects), and a line grows by `"face": "…", ` only.

## The proof: nothing else changed

A script (kept in the session's scratchpad, not committed) checked every one of the 62 files against the base commit
(`a37c1765`, main when the branch started):

1. **Deep-equal with the faces dropped:** both versions parsed, every string `face` key removed, compared with key order
   (`json.dumps` of both). Equal for all 62.
2. **Byte-equal with the snippets taken out:** from the new text, `, "face": "<name>"` after a `who` and `"face":
   "<name>", ` after a reply's or puzzled's `{` removed; the result is the old file byte for byte. Equal for all 62.
3. **Only the contract:** every face is one of the 11 names (none `neutral`), and every face sits on a spoken line, a
   reply or a puzzled object (a position check on the parsed file). The scarecrow's `"fx": { "face": 1 }` in na-njivi (a
   number, the scarecrow's drawn face) was left alone and isn't counted.

```
$ faces.py check --rev a37c1765 <the 62 files>   # run in companion/
ok   scenes/kuhinja.json: 118 faces
ok   scenes/na-njivi.json: 130 faces
ok   scenes/na-stolpu.json: 70 faces
ok   scenes/na-trznici.json: 84 faces
ok   scenes/na-vasi.json: 109 faces
ok   scenes/ob-ognju.json: 98 faces
ok   scenes/ob-potoku.json: 143 faces
ok   scenes/pri-cebelnjaku.json: 68 faces
ok   scenes/pri-ribniku.json: 99 faces
ok   scenes/v-cerkvi.json: 74 faces
ok   scenes/v-delavnici.json: 69 faces
ok   scenes/v-gozdu.json: 154 faces
ok   scenes/v-hisi.json: 82 faces
ok   scenes/v-kleti.json: 72 faces
ok   scenes/v-kovacnici.json: 75 faces
ok   scenes/v-mlinu.json: 144 faces
ok   scenes/v-podstresju.json: 63 faces
ok   scenes/v-soli.json: 113 faces
ok   scenes/v-sotoru.json: 104 faces
ok   cultures/friuli/scenes/al-fuoco.json: 32 faces
ok   cultures/friuli/scenes/allo-stagno.json: 33 faces
ok   cultures/friuli/scenes/al-ruscello.json: 30 faces
ok   cultures/friuli/scenes/a-scuola.json: 106 faces
ok   cultures/friuli/scenes/il-mare.json: 36 faces
ok   cultures/friuli/scenes/in-cucina.json: 46 faces
ok   cultures/friuli/scenes/nel-bosco.json: 37 faces
ok   cultures/friuli/scenes/nel-campo.json: 39 faces
ok   cultures/friuli/scenes/nella-fucina.json: 37 faces
ok   cultures/friuli/scenes/nella-tenda.json: 56 faces
ok   cultures/friuli/scenes/nel-mulino.json: 124 faces
ok   cultures/kaernten/scenes/am-feuer.json: 28 faces
ok   cultures/kaernten/scenes/am-see.json: 54 faces
ok   cultures/lakeland/scenes/by-the-fire.json: 30 faces
ok   cultures/lakeland/scenes/on-the-fell.json: 58 faces
ok   cultures/primorska/scenes/na-gricih.json: 68 faces
ok   cultures/primorska/scenes/v-gorah.json: 94 faces
ok   cultures/primorska/project-steps/balinisce.json: 85 faces
ok   cultures/primorska/project-steps/cebelji_travnik.json: 98 faces
ok   cultures/primorska/project-steps/gasilski_dom.json: 100 faces
ok   cultures/primorska/project-steps/igrisce.json: 88 faces
ok   cultures/primorska/project-steps/kapelica.json: 98 faces
ok   cultures/primorska/project-steps/mestna_ura.json: 122 faces
ok   cultures/primorska/project-steps/mlaj.json: 79 faces
ok   cultures/primorska/project-steps/mlin.json: 100 faces
ok   cultures/primorska/project-steps/most.json: 92 faces
ok   cultures/primorska/project-steps/razgledni_stolp.json: 120 faces
ok   cultures/primorska/project-steps/toplar.json: 107 faces
ok   cultures/primorska/project-steps/vinograd.json: 118 faces
ok   cultures/primorska/project-steps/vodnjak_na_trgu.json: 106 faces
ok   cultures/primorska/celebrations/mesto.json: 19 faces
ok   cultures/primorska/celebrations/tabor.json: 23 faces
ok   cultures/primorska/celebrations/trg.json: 21 faces
ok   cultures/primorska/celebrations/vas.json: 24 faces
ok   cultures/primorska/celebrations/zaselek.json: 19 faces
ok   cultures/friuli/arrivals.json: 308 faces
ok   cultures/kaernten/arrivals.json: 301 faces
ok   cultures/lakeland/arrivals.json: 298 faces
ok   cultures/primorska/arrivals.json: 267 faces
ok   cultures/friuli/world.json: 13 faces
ok   cultures/kaernten/world.json: 14 faces
ok   cultures/lakeland/world.json: 13 faces
ok   cultures/primorska/world.json: 280 faces
total 5690 {'laugh': 1339, 'happy': 1316, 'confused': 703, 'proud': 631, 'surprised': 513, 'curious': 486, 'worried': 246, 'thinking': 224, 'shy': 101, 'cross': 70, 'sad': 61}
$ echo $?
0
```

The checks of the app and the bridge, unchanged: today's bridge strips the key (`dialogLine`, `reply`, `dialogReply` are
`z.object`s; the strict objects around them hold `lines`, not the lines' keys), the app reads scenes, arrivals, steps
and parties leniently (`data.json`, `ignoreUnknownKeys`), and the strict culture reader keeps a keeper's talks as raw
`JsonObject`s. The bridge's `bunx tsc --noEmit -p .` and the smoke pass unchanged (numbers below). Once Part A merges, its
crossCheck validates the names too.

## Counts

Faces in all: **5,690**. Of the 3,154 spoken lines, 1,556 carry a face (49 %); of the 1,705 right replies, 1,686; of the 2,411 wrong choices' reactions, all; of the 37 puzzled reactions, all.

| Area | Files | Lines | Right replies | Wrong replies | Puzzled | Faces |
|---|---|---|---|---|---|---|
| Slovene scenes | 19 | 349/699 | 574/580 | 932/932 | 14/14 | 1869 |
| Cultures' scenes | 17 | 189/394 | 300/305 | 396/396 | 23/23 | 908 |
| Project steps | 13 | 383/736 | 314/314 | 616/616 | – | 1313 |
| Celebrations | 5 | 35/59 | 33/33 | 38/38 | – | 106 |
| Arrivals | 4 | 459/981 | 374/381 | 341/341 | – | 1174 |
| Keepers' talks | 4 | 141/285 | 91/92 | 88/88 | – | 320 |
| **all** | **62** | **1556/3154** | **1686/1705** | **2411/2411** | **37/37** | **5690** |

| Face | Count |
|---|---|
| laugh | 1339 |
| happy | 1316 |
| confused | 703 |
| proud | 631 |
| surprised | 513 |
| curious | 486 |
| worried | 246 |
| thinking | 224 |
| shy | 101 |
| cross | 70 |
| sad | 61 |

### Slovene scenes

| File | Lines | Right replies | Wrong replies | Puzzled | Faces | happy | laugh | surprised | curious | confused | thinking | worried | sad | cross | shy | proud |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| scenes/kuhinja | 23/44 | 38/38 | 57/57 | – | 118 | 29 | 17 | 14 | 13 | 21 | 1 | 8 | 1 | 2 | 4 | 8 |
| scenes/na-njivi | 25/46 | 38/39 | 67/67 | – | 130 | 21 | 29 | 13 | 14 | 23 | 5 | 10 |  |  | 3 | 12 |
| scenes/na-stolpu | 15/28 | 23/23 | 32/32 | – | 70 | 11 | 16 | 11 | 6 | 10 |  | 10 | 2 | 2 | 1 | 1 |
| scenes/na-trznici | 16/32 | 27/27 | 41/41 | – | 84 | 18 | 23 | 7 | 8 | 14 | 1 | 4 |  | 1 |  | 8 |
| scenes/na-vasi | 19/39 | 34/34 | 56/56 | – | 109 | 15 | 28 | 13 | 10 | 12 | 4 | 8 | 4 | 3 | 1 | 11 |
| scenes/ob-ognju | 18/38 | 31/31 | 49/49 | – | 98 | 24 | 27 | 11 | 12 | 10 | 3 | 2 |  | 1 | 2 | 6 |
| scenes/ob-potoku | 29/55 | 42/42 | 72/72 | – | 143 | 32 | 30 | 12 | 10 | 25 | 11 | 6 | 3 | 3 | 2 | 9 |
| scenes/pri-cebelnjaku | 14/24 | 21/21 | 33/33 | – | 68 | 14 | 10 | 5 | 8 | 10 | 5 | 2 | 1 | 2 | 1 | 10 |
| scenes/pri-ribniku | 19/36 | 29/29 | 51/51 | – | 99 | 17 | 22 | 10 | 10 | 16 | 3 | 2 | 3 | 3 |  | 13 |
| scenes/v-cerkvi | 13/28 | 24/24 | 37/37 | – | 74 | 15 | 18 | 4 | 10 | 12 |  | 3 | 3 | 3 | 1 | 5 |
| scenes/v-delavnici | 13/27 | 24/24 | 32/32 | – | 69 | 15 | 16 | 3 | 8 | 7 | 4 | 1 | 1 | 2 | 1 | 11 |
| scenes/v-gozdu | 29/60 | 49/49 | 76/76 | – | 154 | 32 | 33 | 16 | 17 | 25 | 6 | 15 |  | 1 | 3 | 6 |
| scenes/v-hisi | 15/30 | 28/28 | 39/39 | – | 82 | 10 | 24 | 8 | 7 | 9 | 5 | 1 | 2 | 2 | 4 | 10 |
| scenes/v-kleti | 15/29 | 23/23 | 34/34 | – | 72 | 13 | 25 | 7 | 6 | 10 |  | 3 |  | 1 |  | 7 |
| scenes/v-kovacnici | 13/28 | 21/26 | 41/41 | – | 75 | 14 | 13 | 6 | 3 | 15 | 1 | 1 |  | 7 | 4 | 11 |
| scenes/v-mlinu | 23/54 | 36/36 | 71/71 | 14/14 | 144 | 29 | 32 | 14 | 9 | 37 | 2 | 5 | 2 |  | 2 | 12 |
| scenes/v-podstresju | 12/24 | 22/22 | 29/29 | – | 63 | 10 | 20 | 6 | 3 | 7 |  | 5 | 4 | 3 | 3 | 2 |
| scenes/v-soli | 20/39 | 33/33 | 60/60 | – | 113 | 14 | 25 | 9 | 20 | 12 | 8 | 2 | 3 | 2 | 2 | 16 |
| scenes/v-sotoru | 18/38 | 31/31 | 55/55 | – | 104 | 24 | 25 | 13 | 10 | 13 | 3 | 8 |  | 1 | 3 | 4 |
| **all** | **349/699** | **574/580** | **932/932** | **14/14** | **1869** | **357** | **433** | **182** | **184** | **288** | **62** | **96** | **29** | **39** | **37** | **162** |

### Cultures' scenes

| File | Lines | Right replies | Wrong replies | Puzzled | Faces | happy | laugh | surprised | curious | confused | thinking | worried | sad | cross | shy | proud |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| friuli/a-scuola | 22/47 | 30/30 | 54/54 | – | 106 | 25 | 27 | 7 | 14 | 17 | 2 | 2 |  |  | 1 | 11 |
| friuli/al-fuoco | 6/12 | 13/13 | 13/13 | – | 32 | 10 | 9 | 2 | 3 | 4 |  | 1 | 1 |  |  | 2 |
| friuli/al-ruscello | 7/16 | 11/11 | 12/12 | – | 30 | 10 | 7 | 2 | 2 | 3 | 3 | 2 |  |  |  | 1 |
| friuli/allo-stagno | 9/18 | 12/12 | 12/12 | – | 33 | 6 | 10 | 7 | 2 | 4 | 2 | 1 |  |  |  | 1 |
| friuli/il-mare | 9/18 | 12/12 | 15/15 | – | 36 | 7 | 6 | 5 | 5 | 7 | 2 | 2 |  |  |  | 2 |
| friuli/in-cucina | 9/18 | 19/19 | 18/18 | – | 46 | 15 | 12 | 6 | 6 | 4 |  | 2 |  |  |  | 1 |
| friuli/nel-bosco | 8/17 | 12/12 | 17/17 | – | 37 | 11 | 7 | 5 | 3 | 4 | 2 | 5 |  |  |  |  |
| friuli/nel-campo | 9/18 | 14/14 | 16/16 | – | 39 | 11 | 10 | 2 | 3 | 6 | 1 | 3 |  |  |  | 3 |
| friuli/nel-mulino | 24/47 | 37/37 | 40/40 | 23/23 | 124 | 37 | 21 | 16 | 4 | 35 | 3 | 3 |  |  |  | 5 |
| friuli/nella-fucina | 8/17 | 9/14 | 20/20 | – | 37 | 8 | 5 | 7 |  | 7 | 2 | 2 |  |  | 2 | 4 |
| friuli/nella-tenda | 11/24 | 21/21 | 24/24 | – | 56 | 17 | 18 | 3 | 5 | 3 |  | 5 |  |  | 2 | 3 |
| kaernten/am-feuer | 6/12 | 11/11 | 11/11 | – | 28 | 8 | 9 | 1 | 3 | 3 |  | 1 | 1 |  |  | 2 |
| kaernten/am-see | 12/26 | 19/19 | 23/23 | – | 54 | 10 | 10 | 4 | 5 | 10 |  | 4 |  |  |  | 11 |
| lakeland/by-the-fire | 6/13 | 11/11 | 13/13 | – | 30 | 4 | 10 | 3 | 4 | 2 |  | 3 |  |  |  | 4 |
| lakeland/on-the-fell | 12/25 | 22/22 | 24/24 | – | 58 | 14 | 13 | 5 | 7 | 10 |  | 3 |  |  |  | 6 |
| primorska/na-gricih | 13/28 | 20/20 | 35/35 | – | 68 | 10 | 19 | 2 | 6 | 13 | 6 | 6 | 1 | 1 |  | 4 |
| primorska/v-gorah | 18/38 | 27/27 | 49/49 | – | 94 | 13 | 26 | 11 | 8 | 16 | 1 | 2 | 1 |  | 1 | 15 |
| **all** | **189/394** | **300/305** | **396/396** | **23/23** | **908** | **216** | **219** | **88** | **80** | **148** | **24** | **47** | **4** | **1** | **6** | **75** |

### Project steps

| File | Lines | Right replies | Wrong replies | Puzzled | Faces | happy | laugh | surprised | curious | confused | thinking | worried | sad | cross | shy | proud |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| primorska/balinisce | 25/45 | 20/20 | 40/40 | – | 85 | 18 | 19 | 9 | 5 | 8 | 7 | 2 | 2 | 2 |  | 13 |
| primorska/cebelji_travnik | 27/54 | 24/24 | 47/47 | – | 98 | 22 | 28 | 11 | 6 | 5 | 4 | 8 | 2 |  |  | 12 |
| primorska/gasilski_dom | 29/60 | 24/24 | 47/47 | – | 100 | 18 | 31 | 13 | 5 | 15 | 3 | 2 | 1 | 2 | 2 | 8 |
| primorska/igrisce | 28/50 | 20/20 | 40/40 | – | 88 | 19 | 24 | 10 | 10 | 11 | 2 | 5 |  |  | 1 | 6 |
| primorska/kapelica | 27/54 | 24/24 | 47/47 | – | 98 | 25 | 21 | 15 | 3 | 13 | 2 | 2 | 2 | 2 | 1 | 12 |
| primorska/mestna_ura | 38/66 | 28/28 | 56/56 | – | 122 | 20 | 22 | 10 | 7 | 23 | 15 | 5 |  | 2 |  | 18 |
| primorska/mlaj | 24/50 | 20/20 | 35/35 | – | 79 | 19 | 20 | 10 | 10 | 8 | 3 | 3 |  |  |  | 6 |
| primorska/mlin | 31/54 | 24/24 | 45/45 | – | 100 | 22 | 24 | 14 | 6 | 13 | 4 | 5 |  |  |  | 12 |
| primorska/most | 23/59 | 24/24 | 45/45 | – | 92 | 20 | 24 | 13 | 3 | 12 | 6 | 5 |  |  |  | 9 |
| primorska/razgledni_stolp | 35/67 | 28/28 | 57/57 | – | 120 | 25 | 27 | 9 | 10 | 23 | 10 | 2 |  | 2 | 2 | 10 |
| primorska/toplar | 29/54 | 26/26 | 52/52 | – | 107 | 24 | 21 | 9 | 5 | 20 | 7 | 6 |  | 1 |  | 14 |
| primorska/vinograd | 34/63 | 28/28 | 56/56 | – | 118 | 27 | 43 | 9 | 7 | 10 | 5 | 8 |  |  |  | 9 |
| primorska/vodnjak_na_trgu | 33/60 | 24/24 | 49/49 | – | 106 | 17 | 19 | 10 | 12 | 21 | 6 | 3 |  | 3 | 3 | 12 |
| **all** | **383/736** | **314/314** | **616/616** | **–** | **1313** | **276** | **323** | **142** | **89** | **182** | **74** | **56** | **7** | **14** | **9** | **141** |

### Celebrations

| File | Lines | Right replies | Wrong replies | Puzzled | Faces | happy | laugh | surprised | curious | confused | thinking | worried | sad | cross | shy | proud |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| primorska/mesto | 5/11 | 7/7 | 7/7 | – | 19 | 5 | 6 | 1 | 2 | 1 |  |  |  |  |  | 4 |
| primorska/tabor | 7/11 | 6/6 | 10/10 | – | 23 | 10 | 7 | 4 |  | 1 | 1 |  |  |  |  |  |
| primorska/trg | 9/13 | 6/6 | 6/6 | – | 21 | 7 | 7 | 1 | 2 | 1 |  |  |  |  |  | 3 |
| primorska/vas | 8/12 | 8/8 | 8/8 | – | 24 | 8 | 10 | 2 | 2 |  |  |  |  |  |  | 2 |
| primorska/zaselek | 6/12 | 6/6 | 7/7 | – | 19 | 9 | 6 |  | 1 | 1 |  |  |  |  |  | 2 |
| **all** | **35/59** | **33/33** | **38/38** | **–** | **106** | **39** | **36** | **8** | **7** | **4** | **1** | **0** | **0** | **0** | **0** | **11** |

### Arrivals

| File | Lines | Right replies | Wrong replies | Puzzled | Faces | happy | laugh | surprised | curious | confused | thinking | worried | sad | cross | shy | proud |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| friuli/arrivals | 119/255 | 99/100 | 90/90 | – | 308 | 81 | 90 | 24 | 25 | 7 | 12 | 5 | 1 | 2 | 13 | 48 |
| kaernten/arrivals | 124/265 | 89/91 | 88/88 | – | 301 | 95 | 64 | 17 | 25 | 17 | 12 | 5 | 1 | 2 | 12 | 51 |
| lakeland/arrivals | 109/237 | 105/107 | 84/84 | – | 298 | 75 | 76 | 12 | 25 | 23 | 12 | 9 | 1 | 3 | 9 | 53 |
| primorska/arrivals | 107/224 | 81/83 | 79/79 | – | 267 | 69 | 57 | 16 | 33 | 10 | 11 | 3 | 2 | 7 | 14 | 45 |
| **all** | **459/981** | **374/381** | **341/341** | **–** | **1174** | **320** | **287** | **69** | **108** | **57** | **47** | **22** | **5** | **14** | **48** | **197** |

### Keepers' talks

| File | Lines | Right replies | Wrong replies | Puzzled | Faces | happy | laugh | surprised | curious | confused | thinking | worried | sad | cross | shy | proud |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| friuli/world | 4/7 | 5/5 | 4/4 | – | 13 | 4 | 2 | 1 | 1 | 1 |  | 2 |  |  |  | 2 |
| kaernten/world | 4/7 | 6/6 | 4/4 | – | 14 | 4 | 2 | 1 | 1 | 1 |  | 2 |  |  |  | 3 |
| lakeland/world | 4/7 | 5/5 | 4/4 | – | 13 | 5 | 3 |  | 1 | 1 |  | 2 |  |  |  | 1 |
| primorska/world | 129/264 | 75/76 | 76/76 | – | 280 | 95 | 34 | 22 | 15 | 21 | 16 | 19 | 16 | 2 | 1 | 39 |
| **all** | **141/285** | **91/92** | **88/88** | **–** | **320** | **108** | **41** | **24** | **18** | **24** | **16** | **25** | **16** | **2** | **1** | **45** |

## Examples per face

A few per face, with where they are (file, the dialog or group). The faces are on the line or the reply quoted.

| Face | Examples |
|---|---|
| happy | Micka's "Še malo?" when the learner likes her soup (ob-ognju `juha`); Luka's "Bravo! Ta je prava." at the right spruce (project-steps/mlaj, step 1, A1); Rosa's "Che bravo! Siediti, siediti." (friuli/arrivals, rosa A1); Davide's "Prego! L'appendo qui, sopra il tavolo." (friuli nella-tenda `lanterna`) |
| laugh | Micka's "Na zdravje? Z juho? Hi hi!" (ob-ognju `juha`); Marko's "Dober tek? Ha ha, rebule ne ješ, {learner}, piješ jo!" (na-trznici `rebula-cena`); Davide's "Buon appetito? L'erba è per le pecore, non per me!" (friuli nella-tenda `pioggia`); Sam's "In Cumbria? It always rains, mate." (lakeland/arrivals, sam A2) |
| surprised | Micka's "Dober dan? Petelin je komaj zapel!" (kuhinja `kava`); Luka's "Vidiš tam, med drevesi? Oči!" on watch (na-stolpu `straza`); Ančka's "Glejte, zvonik ima oko!" (project-steps/mestna_ura, step 2); the keeper's "Hitro! Glej, ogenj! Kopa se je odprla!" (primorska/world, the breakout) |
| curious | Micka's "Dober večer, {learner}! Si lačen?" (ob-ognju `juha`); Zala's "Zakaj je med sladek? Zakaj?" (pri-cebelnjaku `med-zakaj`); Giulia's "Piacere mio! Perché sei così {m:alto\|f:alta}?" (friuli/arrivals, giulia A1); Davide's "Hai i fiammiferi?" (friuli nella-tenda `lanterna`) |
| confused | Micka's "Vas? Katero vas? Jaz pečem potico!" at vas for vam (kuhinja `potica`); France's puzzled "Hm? Kako, prosim?" (v-mlinu, 14 turns); Ančka's "Kaj? Kdo je to? Glasneje!" (primorska/arrivals, ancka A1); Davide's "Eh? Che cosa hai detto?" (friuli nella-tenda `lanterna`) |
| thinking | Janez's "Pod lipo se je vas vedno zbirala. Tu smo plesali, tu smo se ženili." (na-vasi `klop`); France's "Sedem polen sem naložil. Sedem, sedem." (ob-ognju `drva`); Tone's "Zvonik je širok tri metre. Kako široka bo ura?" (project-steps/mestna_ura, step 1); Bepi's "Chi va piano va sano e va lontano." (friuli/arrivals, bepi A2) |
| worried | Luka's "{learner}! Hitro! Dež gre. Seno mora pod streho, na kozolec!" (na-njivi `seno`); Janez's "{learner}, si {m:videl\|f:videla} mojega psa?" (na-vasi `muri`); Davide's "Piove forte! Posso entrare?" (friuli nella-tenda `pioggia`); Davide's "… e Bianca? Dov'è Bianca?!" (friuli/arrivals, davide A2) |
| sad | Janez's "Ana je rada pela. Vsak večer, tukaj, pri peči." (v-hisi `fotografija`); his "Tvojo? … Hm. Mlad si, {m:sinko\|f:{learner}}. Jaz pa nisem." (pri-ribniku `ribe-scuka`); the charcoal burner's "Zdaj sem samo jaz. Zadnji oglar v dolini." (primorska/world, his tales); Tine's "Uboga mačka. Jutri bo spet poskusila." (v-mlinu `macka`) |
| cross (mild, comic) | Micka's "Jaz? Kaj pa delam ves večer? Kuham!" at "Lahko pomagate?" (ob-ognju `juha-sol`); Tone's "Dvajset žeblji? Hm. Ponovi." (v-kovacnici `narocilo`); Nejc in bed: "Ti tudi, {learner}. Burja, tiho bodi!" (v-podstresju `burja`); Mojca's "Ovce? Med poukom? Ta dva!" (v-soli `dvojina-vceraj`) |
| shy | Micka's "Ah, pojdi no! Pij, dokler je vroča." at "Vaša kava je najboljša" (kuhinja `kava`); Tone's "Hm. Delo prinaša srečo. Pojdi." when thanked for the horseshoe (v-kovacnici `podkev`); Tine's "Jaz malo. Ponoči." afraid of the scarecrow (na-njivi `strasilo`); Lorenzo's "Mi racconti una storia? Per favore!" (friuli nella-tenda `storia`) |
| proud | Micka's "Seveda diši. Babičina juha!" (ob-ognju `juha`); Janez's "Dvesto! Starejša je od cerkve." about the linden (na-vasi `klop`); the keeper's "To je moja kopa." (primorska/world); Davide's "Io sì. Un pastore ha sempre i fiammiferi!" (friuli nella-tenda `lanterna`) |

## Problems in the content, noticed and not changed

Found while reading every dialog; none of it is changed here (the faces were set as the text stands).

**Who says it.** In an arrival a choice's reply is said by whoever spoke last before the turn (`Arrivals.kt`,
`repliers`). In these turns the line before is the adult's, but the replies are written in the child's voice, so the
adult would say them (the faces follow the speaker the app shows):

- primorska/arrivals `cast.zala.A2`, the turn after Micka's "In jaz imam spet koga razvajati!": Micka would say "Hvala!
  Mi boš pokazal ovce?" and "Dobrodošel? Saj nisem fant, sem deklica!".
- primorska/arrivals `cast.zala.B1` (items 7–8): Micka would say "Zdaj te bom pa prvič popravila!".
- primorska/arrivals `cast.nejc.A2` (items 7–8): Vida would say "Jaz sem punca!".
- friuli/arrivals `cast.giulia.A2` (7–8): Rosa would say "Ma io sono una bambina!".
- friuli/arrivals `cast.lorenzo.A2` (8–9): Franco would say "i cavalli sono i miei amici!".
- friuli/arrivals `cast.tommaso.A2` (8–9): Marisa would say "mi hai scambiato per Giulia?".

Kärnten and Lakeland are fine: there the child has a line just before the turn. Either the child gets a line before the
turn, or the replies move to the adult's voice.

**Ti or vi (for the native check).** The villager files say Vida and Mojca say vi to the learner, but some lines say ti:
celebrations/vas A1 #2, Vida: "{learner}, pomagaj mi."; celebrations/mesto A2 #6–7, Mojca: "kaj nosiš na čelu
sprevoda?", "Visoko jo dvigni"; project-steps/igrisce step 3, A1 and A2 #7, Mojca: "potegni za vrvi!". It may be meant
(a party, the children around), so it is a question, not a fix.

**Facts a line gets wrong:**

- project-steps/vinograd step 5, A1 #11: "Še devetinštirideset!" The A1 telling never says there are fifty vines (only A2
  does).
- project-steps/mlin step 6, both levels, #7: the `done` mark sits on Micka's baking line ("Prva moka! Kaj spečemo iz
  nje?"), while the wheel already turns at line #2 ("Kolo se vrti!"). Possibly meant (the first flour is the work done),
  worth a look with scene-coherence.md in mind.
- primorska/world `cycle.stages.bel-dim.talks[3]` A1 #3: "Sneg? Poleti?" assumes summer, but the pile's season runs from
  spring to autumn, so the line can play in May or October.
- primorska/arrivals `cast.vida.A2` #4: "V Trnovem imam gostilno" is in the present; Nejc's A2 arrival says "Prej sem
  živel v Trnovem, v gostilni", so Vida's line would be the past ("Imela sem gostilno").

**Small text slips:**

- na-vasi `klop-sneg`: the reaction to the wrong "Zima, za božič!" quotes it as "Zimo za božič? To si želiš za darilo?";
  a reaction repeats what the learner said (Zima …?).
- v-podstresju `vstani-ribnik`: "Po šoli bom prišel pote." For the native check: "pote" or "po te" (prišel bom po te).
- v-sotoru `zgodba`: the English of the reaction to "Janez me je povedal" reads as a gloss ("Me je povedal: Janez told
  you, like a story? Are you a fairy tale?") rather than a translation.

**In the cultures' scenes:**

- friuli/a-scuola `cartina-citta` #3: Elena says "mia nonna è di Nova Gorica" (the present); her arrival (A2 #5, B1 #5)
  has the grandmother in the past ("era di Nova Gorica"). The two don't agree.
- friuli/nel-mulino `molenda` #6–7: the wrong choice's reaction ("Tanto? Tanto cosa? Non ho capito, sai.") and the turn's
  puzzled ("Eh? Non ho capito, sai.") say almost the same; the own-trap reaction has nothing of its own.

## Tests

On the branch rebased onto main (`a37c1765`, which hadn't moved), with this part's ports:

- the bridge's `bunx tsc --noEmit -p .`: clean;
- the smoke, `LANI_SMOKE_PORT=8960 LANI_SMOKE_FRIULI_PORT=8963 LANI_SMOKE_TOWN_PORT=8966 bun test/smoke.ts`: all checks
  passed, 2,849 ✅ and none failed, the same 2,849 as on the base before any face (today's schema strips the key, as it
  should);
- the proof above on all 62 files: exit 0.

No Gradle, no emulator: nothing of the app changed, and Part A's QA shows the faces on screen.

## What's left

- The evening stories' replies (above), once the story panel shows its teller.
- After Part A merges: rebase, run the smoke with its crossCheck of the names, and look at a few dialogs on the emulator
  (Part A's QA) to see whether the density reads right on screen: about half the lines, nearly every reply. If the
  resting face should hold more, drop the line faces of the openers first.
- The machine-written texts are as they were; the faces add nothing for the native check, but the problems above do.
